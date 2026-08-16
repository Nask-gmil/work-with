package jp.workwith.realtime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;

import jp.workwith.seatassignment.SeatAssignmentNotFoundException;
import jp.workwith.user.UserSession;
import jp.workwith.security.SecurityEventLogger;

class RoomChatControllerTests {
    private final RoomChatService service = mock(RoomChatService.class);
    private final RoomRealtimeNotifier notifier = mock(RoomRealtimeNotifier.class);
    private final ChatRateLimitService rateLimitService = mock(ChatRateLimitService.class);
    private final SecurityEventLogger securityEventLogger = mock(SecurityEventLogger.class);
    private final RoomChatController controller = new RoomChatController(
            service, notifier, rateLimitService, securityEventLogger);

    RoomChatControllerTests() {
        when(rateLimitService.recordChatAttempt(any(Long.class)))
                .thenReturn(new ChatRateLimitService.RateLimitResult(true, 0));
        when(rateLimitService.recordDmAttempt(any(Long.class)))
                .thenReturn(new ChatRateLimitService.RateLimitResult(true, 0));
    }

    @Test
    void publishesUsingTheSessionUser() {
        RoomChatMessage message = new RoomChatMessage(
                "chat-message", 101L, 5L, 12L, "server-user", null, "こんにちは",
                LocalDateTime.now());
        when(service.createMessage(5L, 12L, null, "こんにちは")).thenReturn(message);
        when(service.findPublicTheme(5L)).thenReturn("focus");
        controller.sendChatMessage(5L, new RoomChatRequest(null, "こんにちは"), authenticatedHeaders(12L));
        verify(service).createMessage(5L, 12L, null, "こんにちは");
        verify(notifier).notifyChatMessage(message, "focus");
    }

    @Test
    void doesNotPublishForUnauthenticatedOrUnseatedUser() {
        controller.sendChatMessage(5L, new RoomChatRequest(null, "hello"),
                SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE));
        verify(notifier, never()).notifyChatMessage(any(), any());

        when(service.createMessage(5L, 12L, null, "hello"))
                .thenThrow(new SeatAssignmentNotFoundException());
        controller.sendChatMessage(5L, new RoomChatRequest(null, "hello"), authenticatedHeaders(12L));
        verify(notifier, never()).notifyChatMessage(any(), any());
    }

    @Test
    void doesNotPublishWhenDatabaseSaveFails() {
        when(service.createMessage(5L, 12L, null, "hello"))
                .thenThrow(new org.springframework.dao.DataAccessResourceFailureException("db"));

        controller.sendChatMessage(5L, new RoomChatRequest(null, "hello"), authenticatedHeaders(12L));

        verify(notifier, never()).notifyChatMessage(any(), any());
    }

    @Test
    void publishesPrivateMessageOnlyThroughUserDestinations() {
        RoomChatMessage message = new RoomChatMessage(
                "private-chat-message", 201L, 5L, 12L, "server-user", 13L,
                "secret", LocalDateTime.now());
        when(service.createMessage(5L, 12L, 13L, "secret")).thenReturn(message);

        controller.sendChatMessage(
                5L, new RoomChatRequest(13L, "secret"), authenticatedHeaders(12L));

        verify(notifier).notifyPrivateChatMessage(message);
        verify(notifier, never()).notifyChatMessage(any(), any());
    }

    @Test
    void stopsChatBeforeValidationSaveAndBroadcastWhenRateLimitIsExceeded() {
        when(rateLimitService.recordChatAttempt(12L))
                .thenReturn(new ChatRateLimitService.RateLimitResult(false, 42));

        controller.sendChatMessage(5L, new RoomChatRequest(null, "hello"), authenticatedHeaders(12L));

        verify(service, never()).createMessage(any(Long.class), any(Long.class), any(), any());
        verify(notifier, never()).notifyChatMessage(any(), any());
        verify(notifier).notifyChatError(12L,
                new ChatErrorMessage("chat-rate-limit",
                        "チャットの送信回数が多すぎます。少し時間を空けてから再度お試しください。", 42));
        verify(securityEventLogger).rateLimit("CHAT_SEND", 12L, null, 5L);
    }

    @Test
    void stopsDmBeforeValidationSaveAndDeliveryWhenRateLimitIsExceeded() {
        when(rateLimitService.recordDmAttempt(12L))
                .thenReturn(new ChatRateLimitService.RateLimitResult(false, 30));

        controller.sendChatMessage(5L, new RoomChatRequest(13L, "secret"), authenticatedHeaders(12L));

        verify(service, never()).createMessage(any(Long.class), any(Long.class), any(), any());
        verify(notifier, never()).notifyPrivateChatMessage(any());
        verify(notifier).notifyChatError(12L,
                new ChatErrorMessage("dm-rate-limit",
                        "DMの送信回数が多すぎます。少し時間を空けてから再度お試しください。", 30));
        verify(securityEventLogger).rateLimit("DM_SEND", 12L, null, 5L);
    }

    @Test
    void invalidChatAttemptStillConsumesTheSharedGlobalChatLimit() {
        ChatRateLimitService realLimiter = new ChatRateLimitService(
                1, Duration.ofMinutes(1), 15, Duration.ofMinutes(1), Clock.systemUTC());
        RoomChatController limitedController = new RoomChatController(
                service, notifier, realLimiter, securityEventLogger);
        String overLimit = "a".repeat(151);
        when(service.createMessage(5L, 12L, null, overLimit))
                .thenThrow(new IllegalArgumentException(
                        "メッセージは1文字以上150文字以内で入力してください"));

        limitedController.sendChatMessage(
                5L, new RoomChatRequest(null, overLimit), authenticatedHeaders(12L));
        limitedController.sendChatMessage(8L, new RoomChatRequest(null, "valid"), authenticatedHeaders(12L));

        verify(service).createMessage(5L, 12L, null, overLimit);
        verify(service, never()).createMessage(8L, 12L, null, "valid");
        verify(notifier, times(2)).notifyChatError(any(Long.class), any(ChatErrorMessage.class));
    }

    private SimpMessageHeaderAccessor authenticatedHeaders(long userId) {
        SimpMessageHeaderAccessor headers = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
        headers.setSessionAttributes(Map.of(UserSession.LOGIN_USER_ID, userId));
        return headers;
    }
}
