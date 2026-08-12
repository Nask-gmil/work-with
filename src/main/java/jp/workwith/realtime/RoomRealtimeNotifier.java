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
}
