package jp.workwith.room;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;

class RoomActionRateLimitServiceTests {

    @Test
    void appliesIndependentCreateAndJoinLimitsPerUser() {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-16T00:00:00Z"));
        RoomActionRateLimitService service = new RoomActionRateLimitService(
                2, Duration.ofHours(1), 10, Duration.ofMinutes(15), clock);

        assertThat(service.recordPrivateRoomCreateAttempt(1).allowed()).isTrue();
        assertThat(service.recordPrivateRoomCreateAttempt(1).allowed()).isTrue();
        assertThat(service.recordPrivateRoomCreateAttempt(1).allowed()).isFalse();
        assertThat(service.recordPrivateRoomCreateAttempt(2).allowed()).isTrue();

        for (int attempt = 0; attempt < 10; attempt++) {
            assertThat(service.recordRoomJoinAttempt(1).allowed()).isTrue();
        }
        assertThat(service.recordRoomJoinAttempt(1).allowed()).isFalse();
        assertThat(service.recordRoomJoinAttempt(2).allowed()).isTrue();
    }

    @Test
    void startsNewCreateAndJoinWindowsAfterExpiry() {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-16T00:00:00Z"));
        RoomActionRateLimitService service = new RoomActionRateLimitService(
                1, Duration.ofHours(1), 1, Duration.ofMinutes(15), clock);

        assertThat(service.recordPrivateRoomCreateAttempt(1).allowed()).isTrue();
        assertThat(service.recordPrivateRoomCreateAttempt(1).allowed()).isFalse();
        assertThat(service.recordRoomJoinAttempt(1).allowed()).isTrue();
        assertThat(service.recordRoomJoinAttempt(1).allowed()).isFalse();

        clock.advance(Duration.ofMinutes(15));
        assertThat(service.recordRoomJoinAttempt(1).allowed()).isTrue();
        assertThat(service.recordPrivateRoomCreateAttempt(1).allowed()).isFalse();

        clock.advance(Duration.ofMinutes(45));
        assertThat(service.recordPrivateRoomCreateAttempt(1).allowed()).isTrue();
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
