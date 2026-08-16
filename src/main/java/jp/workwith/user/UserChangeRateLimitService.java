package jp.workwith.user;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** status・avatar・work_contentの変更試行を、userId単位の独立した枠で制限します。 */
@Service
public class UserChangeRateLimitService {

    private final FixedWindowLimiter statusLimiter;
    private final FixedWindowLimiter avatarLimiter;
    private final FixedWindowLimiter workContentLimiter;

    @Autowired
    public UserChangeRateLimitService(
            @Value("${status.rate-limit.max-attempts:10}") int statusMaxAttempts,
            @Value("${status.rate-limit.window-seconds:300}") long statusWindowSeconds,
            @Value("${avatar.rate-limit.max-attempts:10}") int avatarMaxAttempts,
            @Value("${avatar.rate-limit.window-seconds:300}") long avatarWindowSeconds,
            @Value("${work-content.rate-limit.max-attempts:20}") int workContentMaxAttempts,
            @Value("${work-content.rate-limit.window-seconds:600}") long workContentWindowSeconds) {
        this(statusMaxAttempts, Duration.ofSeconds(statusWindowSeconds),
                avatarMaxAttempts, Duration.ofSeconds(avatarWindowSeconds),
                workContentMaxAttempts, Duration.ofSeconds(workContentWindowSeconds),
                Clock.systemUTC());
    }

    UserChangeRateLimitService(
            int statusMaxAttempts,
            Duration statusWindow,
            int avatarMaxAttempts,
            Duration avatarWindow,
            int workContentMaxAttempts,
            Duration workContentWindow,
            Clock clock) {
        statusLimiter = new FixedWindowLimiter(statusMaxAttempts, statusWindow, clock);
        avatarLimiter = new FixedWindowLimiter(avatarMaxAttempts, avatarWindow, clock);
        workContentLimiter = new FixedWindowLimiter(
                workContentMaxAttempts, workContentWindow, clock);
    }

    public RateLimitResult recordStatusAttempt(long userId) {
        return statusLimiter.record(userId);
    }

    public RateLimitResult recordAvatarAttempt(long userId) {
        return avatarLimiter.record(userId);
    }

    public RateLimitResult recordWorkContentAttempt(long userId) {
        return workContentLimiter.record(userId);
    }

    public void clear() {
        statusLimiter.clear();
        avatarLimiter.clear();
        workContentLimiter.clear();
    }

    public record RateLimitResult(boolean allowed, long retryAfterSeconds) {
    }

    private static final class FixedWindowLimiter {
        private final ConcurrentHashMap<Long, AttemptWindow> attempts = new ConcurrentHashMap<>();
        private final AtomicLong cleanupCounter = new AtomicLong();
        private final int maxAttempts;
        private final Duration window;
        private final Clock clock;

        private FixedWindowLimiter(int maxAttempts, Duration window, Clock clock) {
            if (maxAttempts < 1 || window.isZero() || window.isNegative()) {
                throw new IllegalArgumentException("Rate Limit設定値は正の値にしてください");
            }
            this.maxAttempts = maxAttempts;
            this.window = window;
            this.clock = clock;
        }

        private RateLimitResult record(long userId) {
            Instant now = clock.instant();
            AttemptWindow current = attempts.compute(userId, (ignored, existing) -> {
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

        private void clear() {
            attempts.clear();
            cleanupCounter.set(0);
        }
    }

    private record AttemptWindow(int count, Instant startedAt) {
    }
}
