package jp.workwith.room;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** 明示的なprivate部屋作成と部屋入室を、ログインユーザー単位で制限します。 */
@Service
public class RoomActionRateLimitService {

    private final FixedWindowLimiter createLimiter;
    private final FixedWindowLimiter joinLimiter;

    @Autowired
    public RoomActionRateLimitService(
            @Value("${room.create.rate-limit.max-attempts:2}") int createMaxAttempts,
            @Value("${room.create.rate-limit.window-seconds:3600}") long createWindowSeconds,
            @Value("${room.join.rate-limit.max-attempts:10}") int joinMaxAttempts,
            @Value("${room.join.rate-limit.window-seconds:900}") long joinWindowSeconds) {
        this(createMaxAttempts, Duration.ofSeconds(createWindowSeconds),
                joinMaxAttempts, Duration.ofSeconds(joinWindowSeconds), Clock.systemUTC());
    }

    RoomActionRateLimitService(
            int createMaxAttempts,
            Duration createWindow,
            int joinMaxAttempts,
            Duration joinWindow,
            Clock clock) {
        createLimiter = new FixedWindowLimiter(createMaxAttempts, createWindow, clock);
        joinLimiter = new FixedWindowLimiter(joinMaxAttempts, joinWindow, clock);
    }

    public RateLimitResult recordPrivateRoomCreateAttempt(long userId) {
        return createLimiter.record(userId);
    }

    public RateLimitResult recordRoomJoinAttempt(long userId) {
        return joinLimiter.record(userId);
    }

    /** Springテスト間でインメモリ状態を共有しないために使用します。 */
    public void clear() {
        createLimiter.clear();
        joinLimiter.clear();
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
            AttemptWindow current = attempts.compute(userId, (key, existing) -> {
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
