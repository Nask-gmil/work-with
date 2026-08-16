package jp.workwith.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class SecurityEventLoggerTests {

    @Test
    void emitsSearchableFixedFieldsWithoutSensitiveInput() {
        List<String> messages = new ArrayList<>();
        SecurityEventLogger logger = new SecurityEventLogger(
                Duration.ofMinutes(1),
                Clock.fixed(Instant.parse("2026-08-16T00:00:00Z"), ZoneOffset.UTC),
                messages::add);

        logger.rateLimit("REGISTRATION", null, "203.0.113.10", null);

        assertThat(messages).hasSize(1);
        assertThat(messages.getFirst())
                .startsWith("SECURITY_EVENT event=RATE_LIMIT action=REGISTRATION result=BLOCKED")
                .contains("clientHash=")
                .doesNotContain("203.0.113.10", "password", "roomCode", "content");
    }

    @Test
    void suppressesRepeatedEventForSameActor() {
        List<String> messages = new ArrayList<>();
        SecurityEventLogger logger = new SecurityEventLogger(
                Duration.ofMinutes(1), Clock.systemUTC(), messages::add);

        logger.rateLimit("CHAT_SEND", 12L, null, 5L);
        logger.rateLimit("CHAT_SEND", 12L, null, 5L);

        assertThat(messages).hasSize(1);
    }

    @Test
    void logsInvalidRoomCodeOnlyOnFifthFailureWithoutTheCode() {
        List<String> messages = new ArrayList<>();
        SecurityEventLogger logger = new SecurityEventLogger(
                Duration.ZERO, Clock.systemUTC(), messages::add);

        for (int i = 0; i < 5; i++) logger.recordInvalidRoomCode(12L);

        assertThat(messages).singleElement()
                .asString()
                .contains("event=INVALID_ROOM_CODE_BURST", "attemptCount=5", "userId=12")
                .doesNotContain("roomCode");
    }
}
