package jp.workwith.realtime;

import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;

class RoomRealtimeNotifierTests {

    @Test
    void sendsTheMinimalEventToTheRoomsTopic() {
        SimpMessagingTemplate messagingTemplate = org.mockito.Mockito.mock(
                SimpMessagingTemplate.class);
        RoomRealtimeNotifier notifier = new RoomRealtimeNotifier(messagingTemplate);

        notifier.notifyParticipantsChanged(37L);

        verify(messagingTemplate).convertAndSend(
                "/topic/room/37",
                new RoomRealtimeEvent("participants-changed", 37L));
    }
}
