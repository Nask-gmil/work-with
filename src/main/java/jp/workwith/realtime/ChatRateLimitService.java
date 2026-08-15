package jp.workwith.realtime;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** チャットとDMの送信試行を、ログインユーザー単位の別々の枠で制限します。 */
@Service
public class ChatRateLimitService {

    private final FixedWindowLimiter chatLimiter;
    private final FixedWindowLimiter dmLimiter;

    @Autowired
    public ChatRateLimitService(
            @Value("${chat.rate-limit.max-attempts:10}") int chatMaxAttempts,
            @Value("${chat.rate-limit.window-seconds:60}") long chatWindowSeconds,
            @Value("${dm.rate-limit.max-attempts:15}") int dmMaxAttempts,
            @Value("${dm.rate-limit.window-seconds:60}") long dmWindowSeconds) {
        this(chatMaxAttempts, Duration.ofSeconds(chatWindowSeconds),
                dmMaxAttempts, Duration.ofSeconds(dmWindowSeconds), Clock.systemUTC());
    }

    ChatRateLimitService(
            int chatMaxAttempts,
            Duration chatWindow,
            int dmMaxAttempts,
            Duration dmWindow,
            Clock clock) {
        chatLimiter = new FixedWindowLimiter(chatMaxAttempts, chatWindow, clock);
        dmLimiter = new FixedWindowLimiter(dmMaxAttempts, dmWindow, clock);
    }

    public RateLimitResult recordChatAttempt(long userId) {
        return chatLimiter.record(userId);
    }

    public RateLimitResult recordDmAttempt(long userId) {
        return dmLimiter.record(userId);
    }

    public void clear() {
        chatLimiter.clear();
        dmLimiter.clear();
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
