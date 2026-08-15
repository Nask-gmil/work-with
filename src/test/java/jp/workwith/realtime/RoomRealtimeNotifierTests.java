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
    void sendsThemeChangedToTheSpecifiedRoomTopic() {
        SimpMessagingTemplate messagingTemplate = org.mockito.Mockito.mock(
                SimpMessagingTemplate.class);
        RoomRealtimeNotifier notifier = new RoomRealtimeNotifier(messagingTemplate);

        notifier.notifyThemeChanged(8L, "night");

        verify(messagingTemplate).convertAndSend(
                "/topic/room/8",
                new RoomThemeChangedEvent("theme-changed", 8L, "night"));
    }

    @Test
    void sendsWorkContentChangedToTheSpecifiedRoomTopic() {
        SimpMessagingTemplate messagingTemplate = org.mockito.Mockito.mock(
                SimpMessagingTemplate.class);
        RoomRealtimeNotifier notifier = new RoomRealtimeNotifier(messagingTemplate);

        notifier.notifyWorkContentChanged(8L, 21L);

        verify(messagingTemplate).convertAndSend(
                "/topic/room/8",
                new RoomWorkContentChangedEvent("work-content-changed", 8L, 21L));
    }

    @Test
    void sendsAvatarChangedToTheSpecifiedRoomTopic() {
        SimpMessagingTemplate messagingTemplate = org.mockito.Mockito.mock(
                SimpMessagingTemplate.class);
        RoomRealtimeNotifier notifier = new RoomRealtimeNotifier(messagingTemplate);

        notifier.notifyAvatarChanged(9L, 24L);

        verify(messagingTemplate).convertAndSend(
                "/topic/room/9",
                new RoomAvatarChangedEvent("avatar-changed", 9L, 24L));
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
    void sendsPublicChatMessageToItsThemeTopic() {
        SimpMessagingTemplate messagingTemplate = org.mockito.Mockito.mock(
                SimpMessagingTemplate.class);
        RoomRealtimeNotifier notifier = new RoomRealtimeNotifier(messagingTemplate);
        RoomChatMessage message = new RoomChatMessage(
                "chat-message", 102L, 8L, 12L, "user", null, "hello",
                java.time.LocalDateTime.now());

        notifier.notifyChatMessage(message, "focus");

        verify(messagingTemplate).convertAndSend("/topic/public-chat/focus", message);
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

    @Test
    void sendsChatErrorOnlyToTheSendingUserQueue() {
        SimpMessagingTemplate messagingTemplate = org.mockito.Mockito.mock(
                SimpMessagingTemplate.class);
        RoomRealtimeNotifier notifier = new RoomRealtimeNotifier(messagingTemplate);
        ChatErrorMessage error = new ChatErrorMessage("chat-rate-limit", "wait", 20);

        notifier.notifyChatError(12L, error);

        verify(messagingTemplate).convertAndSendToUser("12", "/queue/chat-errors", error);
    }
}
