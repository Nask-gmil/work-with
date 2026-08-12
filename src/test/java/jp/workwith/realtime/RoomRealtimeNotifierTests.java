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
}
