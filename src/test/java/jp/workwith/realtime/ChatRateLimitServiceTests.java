package jp.workwith.realtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;

class ChatRateLimitServiceTests {

    @Test
    void limitsChatAndDmIndependentlyForEachUser() {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-16T00:00:00Z"));
        ChatRateLimitService service = new ChatRateLimitService(
                2, Duration.ofSeconds(60), 3, Duration.ofSeconds(60), clock);

        assertThat(service.recordChatAttempt(1).allowed()).isTrue();
        assertThat(service.recordChatAttempt(1).allowed()).isTrue();
        assertThat(service.recordChatAttempt(1).allowed()).isFalse();
        assertThat(service.recordChatAttempt(2).allowed()).isTrue();

        assertThat(service.recordDmAttempt(1).allowed()).isTrue();
        assertThat(service.recordDmAttempt(1).allowed()).isTrue();
        assertThat(service.recordDmAttempt(1).allowed()).isTrue();
        assertThat(service.recordDmAttempt(1).allowed()).isFalse();
        assertThat(service.recordDmAttempt(2).allowed()).isTrue();
    }

    @Test
    void startsNewWindowsAfterExpiry() {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-16T00:00:00Z"));
        ChatRateLimitService service = new ChatRateLimitService(
                1, Duration.ofSeconds(60), 1, Duration.ofSeconds(90), clock);

        assertThat(service.recordChatAttempt(1).allowed()).isTrue();
        assertThat(service.recordChatAttempt(1).allowed()).isFalse();
        assertThat(service.recordDmAttempt(1).allowed()).isTrue();
        assertThat(service.recordDmAttempt(1).allowed()).isFalse();

        clock.advance(Duration.ofSeconds(60));
        assertThat(service.recordChatAttempt(1).allowed()).isTrue();
        assertThat(service.recordDmAttempt(1).allowed()).isFalse();

        clock.advance(Duration.ofSeconds(30));
        assertThat(service.recordDmAttempt(1).allowed()).isTrue();
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
