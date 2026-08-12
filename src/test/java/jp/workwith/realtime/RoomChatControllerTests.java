package jp.workwith.realtime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;

import jp.workwith.seatassignment.SeatAssignmentNotFoundException;
import jp.workwith.user.UserSession;

class RoomChatControllerTests {
    private final RoomChatService service = mock(RoomChatService.class);
    private final RoomRealtimeNotifier notifier = mock(RoomRealtimeNotifier.class);
    private final RoomChatController controller = new RoomChatController(service, notifier);

    @Test
    void publishesUsingTheSessionUser() {
        RoomChatMessage message = new RoomChatMessage(
                "chat-message", 101L, 5L, 12L, "server-user", null, "こんにちは",
                LocalDateTime.now());
        when(service.createMessage(5L, 12L, null, "こんにちは")).thenReturn(message);
        controller.sendChatMessage(5L, new RoomChatRequest(null, "こんにちは"), authenticatedHeaders(12L));
        verify(service).createMessage(5L, 12L, null, "こんにちは");
        verify(notifier).notifyChatMessage(message);
    }

    @Test
    void doesNotPublishForUnauthenticatedOrUnseatedUser() {
        controller.sendChatMessage(5L, new RoomChatRequest(null, "hello"),
                SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE));
        verify(notifier, never()).notifyChatMessage(any());

        when(service.createMessage(5L, 12L, null, "hello"))
                .thenThrow(new SeatAssignmentNotFoundException());
        controller.sendChatMessage(5L, new RoomChatRequest(null, "hello"), authenticatedHeaders(12L));
        verify(notifier, never()).notifyChatMessage(any());
    }

    @Test
    void doesNotPublishWhenDatabaseSaveFails() {
        when(service.createMessage(5L, 12L, null, "hello"))
                .thenThrow(new org.springframework.dao.DataAccessResourceFailureException("db"));

        controller.sendChatMessage(5L, new RoomChatRequest(null, "hello"), authenticatedHeaders(12L));

        verify(notifier, never()).notifyChatMessage(any());
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
        verify(notifier, never()).notifyChatMessage(any());
    }

    private SimpMessageHeaderAccessor authenticatedHeaders(long userId) {
        SimpMessageHeaderAccessor headers = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
        headers.setSessionAttributes(Map.of(UserSession.LOGIN_USER_ID, userId));
        return headers;
    }
}
