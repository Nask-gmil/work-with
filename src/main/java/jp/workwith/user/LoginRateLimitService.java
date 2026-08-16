package jp.workwith.user;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** ログイン失敗を、正規化済みusernameとクライアントIPの組み合わせ単位で制限します。 */
@Service
public class LoginRateLimitService {

    private final ConcurrentHashMap<LoginKey, FailureState> failures = new ConcurrentHashMap<>();
    private final AtomicLong cleanupCounter = new AtomicLong();
    private final int maxFailures;
    private final Duration window;
    private final Clock clock;

    @Autowired
    public LoginRateLimitService(
            @Value("${login.rate-limit.max-failures:10}") int maxFailures,
            @Value("${login.rate-limit.window-seconds:900}") long windowSeconds) {
        this(maxFailures, Duration.ofSeconds(windowSeconds), Clock.systemUTC());
    }

    LoginRateLimitService(int maxFailures, Duration window, Clock clock) {
        if (maxFailures < 1 || window.isZero() || window.isNegative()) {
            throw new IllegalArgumentException("Rate Limit設定値は正の値にしてください");
        }
        this.maxFailures = maxFailures;
        this.window = window;
        this.clock = clock;
    }

    /** DB検索やBCrypt照合より前に、現在ロック中かを確認します。 */
    public RateLimitResult check(String normalizedUsername, String clientIp) {
        Instant now = clock.instant();
        LoginKey key = new LoginKey(normalizedUsername, clientIp);
        FailureState state = failures.get(key);
        cleanupExpiredOccasionally(now);
        if (state == null || state.blockedUntil() == null || !now.isBefore(state.blockedUntil())) {
            return new RateLimitResult(true, 0);
        }
        return blockedResult(now, state.blockedUntil());
    }

    /** 認証失敗だけを加算し、上限に達した失敗そのものを429として扱います。 */
    public RateLimitResult recordFailure(String normalizedUsername, String clientIp) {
        Instant now = clock.instant();
        LoginKey key = new LoginKey(normalizedUsername, clientIp);
        FailureState current = failures.compute(key, (ignored, existing) -> {
            if (existing != null && existing.blockedUntil() != null
                    && now.isBefore(existing.blockedUntil())) {
                return existing;
            }
            int count = existing == null
                    || !now.isBefore(existing.firstFailureAt().plus(window))
                    ? 1 : existing.count() + 1;
            Instant firstFailureAt = count == 1 ? now : existing.firstFailureAt();
            Instant blockedUntil = count >= maxFailures ? now.plus(window) : null;
            return new FailureState(count, firstFailureAt, blockedUntil);
        });
        cleanupExpiredOccasionally(now);
        return current.blockedUntil() == null
                ? new RateLimitResult(true, 0)
                : blockedResult(now, current.blockedUntil());
    }

    /** 上限到達前の正常ログインでは、その組み合わせの失敗履歴を消します。 */
    public void reset(String normalizedUsername, String clientIp) {
        failures.remove(new LoginKey(normalizedUsername, clientIp));
    }

    public void clear() {
        failures.clear();
        cleanupCounter.set(0);
    }

    private RateLimitResult blockedResult(Instant now, Instant blockedUntil) {
        long remaining = Duration.between(now, blockedUntil).toSeconds();
        return new RateLimitResult(false, Math.max(1, remaining));
    }

    private void cleanupExpiredOccasionally(Instant now) {
        if ((cleanupCounter.incrementAndGet() & 255) != 0) {
            return;
        }
        failures.entrySet().removeIf(entry -> {
            FailureState state = entry.getValue();
            Instant expiresAt = state.blockedUntil() == null
                    ? state.firstFailureAt().plus(window) : state.blockedUntil();
            return !now.isBefore(expiresAt);
        });
    }

    public record RateLimitResult(boolean allowed, long retryAfterSeconds) {
    }

    private record LoginKey(String normalizedUsername, String clientIp) {
    }

    private record FailureState(int count, Instant firstFailureAt, Instant blockedUntil) {
    }
}
