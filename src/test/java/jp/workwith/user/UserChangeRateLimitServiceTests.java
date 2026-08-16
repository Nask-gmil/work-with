package jp.workwith.user;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;

class UserChangeRateLimitServiceTests {

    @Test
    void appliesConfiguredBoundariesIndependently() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        UserChangeRateLimitService service = service(clock);

        for (int attempt = 1; attempt <= 10; attempt++) {
            assertThat(service.recordStatusAttempt(1L).allowed()).isTrue();
            assertThat(service.recordAvatarAttempt(1L).allowed()).isTrue();
        }
        for (int attempt = 1; attempt <= 20; attempt++) {
            assertThat(service.recordWorkContentAttempt(1L).allowed()).isTrue();
        }
        assertThat(service.recordStatusAttempt(1L).allowed()).isFalse();
        assertThat(service.recordAvatarAttempt(1L).allowed()).isFalse();
        assertThat(service.recordWorkContentAttempt(1L).allowed()).isFalse();
    }

    @Test
    void oneLimitedActionDoesNotLimitTheOtherActions() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        UserChangeRateLimitService service = service(clock);

        for (int attempt = 0; attempt < 11; attempt++) {
            service.recordStatusAttempt(1L);
        }

        assertThat(service.recordStatusAttempt(1L).allowed()).isFalse();
        assertThat(service.recordAvatarAttempt(1L).allowed()).isTrue();
        assertThat(service.recordWorkContentAttempt(1L).allowed()).isTrue();
    }

    @Test
    void oneUsersLimitDoesNotAffectAnotherUser() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        UserChangeRateLimitService service = service(clock);

        for (int attempt = 0; attempt < 11; attempt++) {
            service.recordStatusAttempt(1L);
            service.recordAvatarAttempt(1L);
        }
        for (int attempt = 0; attempt < 21; attempt++) {
            service.recordWorkContentAttempt(1L);
        }

        assertThat(service.recordStatusAttempt(2L).allowed()).isTrue();
        assertThat(service.recordAvatarAttempt(2L).allowed()).isTrue();
        assertThat(service.recordWorkContentAttempt(2L).allowed()).isTrue();
    }

    @Test
    void eachActionCanBeUsedAgainAfterItsWindow() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        UserChangeRateLimitService service = service(clock);
        for (int attempt = 0; attempt < 11; attempt++) {
            service.recordStatusAttempt(1L);
            service.recordAvatarAttempt(1L);
        }
        for (int attempt = 0; attempt < 21; attempt++) {
            service.recordWorkContentAttempt(1L);
        }

        clock.advance(Duration.ofMinutes(5));
        assertThat(service.recordStatusAttempt(1L).allowed()).isTrue();
        assertThat(service.recordAvatarAttempt(1L).allowed()).isTrue();
        assertThat(service.recordWorkContentAttempt(1L).allowed()).isFalse();

        clock.advance(Duration.ofMinutes(5));
        assertThat(service.recordWorkContentAttempt(1L).allowed()).isTrue();
    }

    private UserChangeRateLimitService service(Clock clock) {
        return new UserChangeRateLimitService(
                10, Duration.ofMinutes(5),
                10, Duration.ofMinutes(5),
                20, Duration.ofMinutes(10), clock);
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
