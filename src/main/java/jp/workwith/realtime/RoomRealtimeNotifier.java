package jp.workwith.realtime;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
public class RoomRealtimeNotifier {

    private final SimpMessagingTemplate messagingTemplate;

    public RoomRealtimeNotifier(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void notifyParticipantsChanged(long roomId) {
        messagingTemplate.convertAndSend(
                "/topic/room/" + roomId,
                new RoomRealtimeEvent("participants-changed", roomId));
    }

    public void notifyStatusChanged(long roomId) {
        messagingTemplate.convertAndSend(
                "/topic/room/" + roomId,
                new RoomRealtimeEvent("status-changed", roomId));
    }

    public void notifyThemeChanged(long roomId, String theme) {
        messagingTemplate.convertAndSend(
                "/topic/room/" + roomId,
                new RoomThemeChangedEvent("theme-changed", roomId, theme));
    }

    public void notifyWorkContentChanged(long roomId, long userId) {
        messagingTemplate.convertAndSend(
                "/topic/room/" + roomId,
                new RoomWorkContentChangedEvent("work-content-changed", roomId, userId));
    }

    public void notifyChatMessage(RoomChatMessage message) {
        messagingTemplate.convertAndSend("/topic/room/" + message.roomId(), message);
    }

    public void notifyPrivateChatMessage(RoomChatMessage message) {
        messagingTemplate.convertAndSendToUser(
                Long.toString(message.userId()), "/queue/private-chat", message);
        messagingTemplate.convertAndSendToUser(
                Long.toString(message.targetUserId()), "/queue/private-chat", message);
    }
}
