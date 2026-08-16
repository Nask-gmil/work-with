package jp.workwith.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** 機密値を受け取らず、抑制付きの定型セキュリティログだけを出力します。 */
@Component
public class SecurityEventLogger {

    private static final Logger LOGGER = LoggerFactory.getLogger(SecurityEventLogger.class);
    private static final int INVALID_ROOM_CODE_THRESHOLD = 5;
    private static final Duration INVALID_ROOM_CODE_WINDOW = Duration.ofMinutes(10);

    private final ConcurrentHashMap<String, Instant> lastLogged = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, BurstState> invalidRoomCodes = new ConcurrentHashMap<>();
    private final AtomicLong cleanupCounter = new AtomicLong();
    private final Duration suppressionWindow;
    private final Clock clock;
    private final Consumer<String> sink;

    @Autowired
    public SecurityEventLogger(
            @Value("${security.logging.suppression-seconds:60}") long suppressionSeconds) {
        this(Duration.ofSeconds(suppressionSeconds), Clock.systemUTC(), LOGGER::warn);
    }

    SecurityEventLogger(Duration suppressionWindow, Clock clock, Consumer<String> sink) {
        if (suppressionWindow.isNegative()) {
            throw new IllegalArgumentException("suppressionWindow must not be negative");
        }
        this.suppressionWindow = suppressionWindow;
        this.clock = clock;
        this.sink = sink;
    }

    public void rateLimit(String action, Long userId, String clientIdentity, Long roomId) {
        emit("RATE_LIMIT", action, userId, fingerprint(clientIdentity), roomId, null);
    }

    public void loginFailureBurst(String normalizedUsername, String clientIp, int attemptCount) {
        emit("LOGIN_FAILURE_BURST", "LOGIN", null,
                fingerprint(normalizedUsername + "|" + clientIp), null, attemptCount);
    }

    public void recordInvalidRoomCode(long userId) {
        Instant now = clock.instant();
        BurstState state = invalidRoomCodes.compute(userId, (ignored, existing) -> {
            if (existing == null || !now.isBefore(existing.firstAt().plus(INVALID_ROOM_CODE_WINDOW))) {
                return new BurstState(1, now);
            }
            return new BurstState(existing.count() + 1, existing.firstAt());
        });
        if (state.count() == INVALID_ROOM_CODE_THRESHOLD) {
            emit("INVALID_ROOM_CODE_BURST", "PRIVATE_ROOM_JOIN", userId, null, null, state.count());
        }
        cleanupOccasionally(now);
    }

    public void privateRoomForbidden(long userId, Long roomId, String action) {
        emit("PRIVATE_ROOM_FORBIDDEN", action, userId, null, roomId, null);
    }

    public void websocketForbidden(Long userId, Long roomId, String action, String clientIdentity) {
        emit("WEBSOCKET_FORBIDDEN", action, userId, fingerprint(clientIdentity), roomId, null);
    }

    public void invalidInput(long userId, String action) {
        emit("INVALID_INPUT", action, userId, null, null, null);
    }

    private void emit(String event, String action, Long userId, String clientHash,
            Long roomId, Integer attemptCount) {
        String actor = userId != null ? "user:" + userId : "client:" + clientHash;
        String key = event + '|' + action + '|' + actor + '|' + roomId;
        Instant now = clock.instant();
        Instant previous = lastLogged.putIfAbsent(key, now);
        if (previous != null && now.isBefore(previous.plus(suppressionWindow))) {
            return;
        }
        if (previous != null && !lastLogged.replace(key, previous, now)) {
            return;
        }
        StringBuilder message = new StringBuilder("SECURITY_EVENT")
                .append(" event=").append(event)
                .append(" action=").append(action)
                .append(" result=BLOCKED");
        if (userId != null) message.append(" userId=").append(userId);
        if (clientHash != null) message.append(" clientHash=").append(clientHash);
        if (roomId != null) message.append(" roomId=").append(roomId);
        if (attemptCount != null) message.append(" attemptCount=").append(attemptCount);
        sink.accept(message.toString());
    }

    private String fingerprint(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 8);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private void cleanupOccasionally(Instant now) {
        if ((cleanupCounter.incrementAndGet() & 255) != 0) return;
        invalidRoomCodes.entrySet().removeIf(entry ->
                !now.isBefore(entry.getValue().firstAt().plus(INVALID_ROOM_CODE_WINDOW)));
        lastLogged.entrySet().removeIf(entry ->
                !now.isBefore(entry.getValue().plus(suppressionWindow).plus(Duration.ofMinutes(10))));
    }

    public void clear() {
        lastLogged.clear();
        invalidRoomCodes.clear();
    }

    private record BurstState(int count, Instant firstAt) { }
}
