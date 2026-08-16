package jp.workwith.user;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;

class LoginRateLimitServiceTests {

    @Test
    void tenthFailureStartsBlockAndCorrectPasswordMustBeRejectedBeforeAuthentication() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        LoginRateLimitService service = new LoginRateLimitService(10, Duration.ofMinutes(15), clock);

        for (int attempt = 1; attempt <= 9; attempt++) {
            assertThat(service.check("user", "192.0.2.1").allowed()).isTrue();
            assertThat(service.recordFailure("user", "192.0.2.1").allowed()).isTrue();
        }
        LoginRateLimitService.RateLimitResult tenth =
                service.recordFailure("user", "192.0.2.1");
        assertThat(tenth.allowed()).isFalse();
        assertThat(tenth.retryAfterSeconds()).isEqualTo(900);
        assertThat(service.check("user", "192.0.2.1").allowed()).isFalse();
    }

    @Test
    void allowsLoginAgainAfterBlockPeriodWithoutActuallyWaiting() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        LoginRateLimitService service = new LoginRateLimitService(2, Duration.ofMinutes(15), clock);

        service.recordFailure("user", "192.0.2.2");
        service.recordFailure("user", "192.0.2.2");
        clock.advance(Duration.ofMinutes(15));

        assertThat(service.check("user", "192.0.2.2").allowed()).isTrue();
    }

    @Test
    void successfulLoginResetsFailuresIncludingJapaneseUsername() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        LoginRateLimitService service = new LoginRateLimitService(4, Duration.ofMinutes(15), clock);

        for (int attempt = 0; attempt < 3; attempt++) {
            service.recordFailure("田中", "192.0.2.3");
        }
        service.reset("田中", "192.0.2.3");

        assertThat(service.recordFailure("田中", "192.0.2.3").allowed()).isTrue();
        assertThat(service.recordFailure("田中", "192.0.2.3").allowed()).isTrue();
        assertThat(service.recordFailure("田中", "192.0.2.3").allowed()).isTrue();
        assertThat(service.recordFailure("田中", "192.0.2.3").allowed()).isFalse();
    }

    @Test
    void separatesDifferentUsernamesAndDifferentClientIps() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        LoginRateLimitService service = new LoginRateLimitService(1, Duration.ofMinutes(15), clock);

        assertThat(service.recordFailure("userA", "192.0.2.4").allowed()).isFalse();
        assertThat(service.check("userB", "192.0.2.4").allowed()).isTrue();
        assertThat(service.check("userA", "192.0.2.5").allowed()).isTrue();
    }

    @Test
    void failureWindowExpiresBeforeTheLimitIsReached() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        LoginRateLimitService service = new LoginRateLimitService(2, Duration.ofMinutes(15), clock);

        assertThat(service.recordFailure("missing", "192.0.2.6").allowed()).isTrue();
        clock.advance(Duration.ofMinutes(15));
        assertThat(service.recordFailure("missing", "192.0.2.6").allowed()).isTrue();
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
