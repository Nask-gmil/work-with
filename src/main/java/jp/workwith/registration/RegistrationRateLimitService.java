package jp.workwith.registration;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** 新規登録試行をIP単位の固定時間窓で数えます。状態は単一インスタンスのメモリ内だけに保持します。 */
@Service
public class RegistrationRateLimitService {

    private final ConcurrentHashMap<String, AttemptWindow> attempts = new ConcurrentHashMap<>();
    private final AtomicLong cleanupCounter = new AtomicLong();
    private final int maxAttempts;
    private final Duration window;
    private final Clock clock;

    @Autowired
    public RegistrationRateLimitService(
            @Value("${registration.rate-limit.max-attempts:10}") int maxAttempts,
            @Value("${registration.rate-limit.window-seconds:600}") long windowSeconds) {
        this(maxAttempts, Duration.ofSeconds(windowSeconds), Clock.systemUTC());
    }

    RegistrationRateLimitService(int maxAttempts, Duration window, Clock clock) {
        if (maxAttempts < 1 || window.isZero() || window.isNegative()) {
            throw new IllegalArgumentException("Rate Limit設定値は正の値にしてください");
        }
        this.maxAttempts = maxAttempts;
        this.window = window;
        this.clock = clock;
    }

    public RateLimitResult recordAttempt(String clientIp) {
        Instant now = clock.instant();
        AttemptWindow current = attempts.compute(clientIp, (key, existing) -> {
            if (existing == null || !now.isBefore(existing.startedAt().plus(window))) {
                return new AttemptWindow(1, now);
            }
            return new AttemptWindow(existing.count() + 1, existing.startedAt());
        });

        if ((cleanupCounter.incrementAndGet() & 255) == 0) {
            attempts.entrySet().removeIf(entry ->
                    !now.isBefore(entry.getValue().startedAt().plus(window)));
        }

        if (current.count() <= maxAttempts) {
            return new RateLimitResult(true, 0);
        }
        long remaining = Duration.between(now, current.startedAt().plus(window)).toSeconds();
        return new RateLimitResult(false, Math.max(1, remaining));
    }

    public void clear() {
        attempts.clear();
        cleanupCounter.set(0);
    }

    private record AttemptWindow(int count, Instant startedAt) {
    }

    public record RateLimitResult(boolean allowed, long retryAfterSeconds) {
    }
}
