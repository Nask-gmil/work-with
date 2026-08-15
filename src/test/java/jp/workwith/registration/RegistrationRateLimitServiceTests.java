package jp.workwith.registration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;

class RegistrationRateLimitServiceTests {

    @Test
    void allowsConfiguredAttemptsThenBlocks() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        RegistrationRateLimitService service =
                new RegistrationRateLimitService(2, Duration.ofMinutes(10), clock);

        assertThat(service.recordAttempt("192.0.2.1").allowed()).isTrue();
        assertThat(service.recordAttempt("192.0.2.1").allowed()).isTrue();
        RegistrationRateLimitService.RateLimitResult blocked =
                service.recordAttempt("192.0.2.1");
        assertThat(blocked.allowed()).isFalse();
        assertThat(blocked.retryAfterSeconds()).isEqualTo(600);
    }

    @Test
    void startsNewWindowAfterExpiry() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        RegistrationRateLimitService service =
                new RegistrationRateLimitService(1, Duration.ofMinutes(10), clock);

        assertThat(service.recordAttempt("192.0.2.2").allowed()).isTrue();
        assertThat(service.recordAttempt("192.0.2.2").allowed()).isFalse();
        clock.advance(Duration.ofMinutes(10));
        assertThat(service.recordAttempt("192.0.2.2").allowed()).isTrue();
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
