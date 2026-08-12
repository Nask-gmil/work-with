package jp.workwith.realtime;

import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;

class RoomRealtimeNotifierTests {

    @Test
    void sendsParticipantsChangedToTheSpecifiedRoomTopic() {
        SimpMessagingTemplate messagingTemplate = org.mockito.Mockito.mock(
                SimpMessagingTemplate.class);
        RoomRealtimeNotifier notifier = new RoomRealtimeNotifier(messagingTemplate);

        notifier.notifyParticipantsChanged(5L);

        verify(messagingTemplate).convertAndSend(
                "/topic/room/5",
                new RoomRealtimeEvent("participants-changed", 5L));
    }

    @Test
    void sendsStatusChangedToTheSpecifiedRoomTopic() {
        SimpMessagingTemplate messagingTemplate = org.mockito.Mockito.mock(
                SimpMessagingTemplate.class);
        RoomRealtimeNotifier notifier = new RoomRealtimeNotifier(messagingTemplate);

        notifier.notifyStatusChanged(12L);

        verify(messagingTemplate).convertAndSend(
                "/topic/room/12",
                new RoomRealtimeEvent("status-changed", 12L));
    }

    @Test
    void sendsChatMessageOnlyToItsRoomTopic() {
        SimpMessagingTemplate messagingTemplate = org.mockito.Mockito.mock(
                SimpMessagingTemplate.class);
        RoomRealtimeNotifier notifier = new RoomRealtimeNotifier(messagingTemplate);
        RoomChatMessage message = new RoomChatMessage(
                "chat-message", 101L, 5L, 12L, "user", null, "hello",
                java.time.LocalDateTime.now());

        notifier.notifyChatMessage(message);

        verify(messagingTemplate).convertAndSend("/topic/room/5", message);
    }

    @Test
    void sendsPrivateMessageOnlyToSenderAndTargetUserQueues() {
        SimpMessagingTemplate messagingTemplate = org.mockito.Mockito.mock(
                SimpMessagingTemplate.class);
        RoomRealtimeNotifier notifier = new RoomRealtimeNotifier(messagingTemplate);
        RoomChatMessage message = new RoomChatMessage(
                "private-chat-message", 201L, 5L, 12L, "sender", 13L,
                "secret", java.time.LocalDateTime.now());

        notifier.notifyPrivateChatMessage(message);

        verify(messagingTemplate).convertAndSendToUser(
                "12", "/queue/private-chat", message);
        verify(messagingTemplate).convertAndSendToUser(
                "13", "/queue/private-chat", message);
    }
}
