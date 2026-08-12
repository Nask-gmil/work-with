package jp.workwith.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.security.Principal;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.support.MessageBuilder;

import jp.workwith.room.PrivateRoomAccessDeniedException;
import jp.workwith.room.Room;
import jp.workwith.room.RoomService;

class RoomSubscriptionAuthorizationInterceptorTests {

    private final RoomService roomService = mock(RoomService.class);
    private final RoomSubscriptionAuthorizationInterceptor interceptor =
            new RoomSubscriptionAuthorizationInterceptor(roomService);
    private final MessageChannel channel = mock(MessageChannel.class);

    @Test
    void allowsAuthorizedRoomSubscription() {
        Room room = new Room(5L, "ABC234", "private", "Private", "focus", null, 10, 12L);
        when(roomService.findAccessibleRoom(5L, 12L)).thenReturn(room);
        Message<?> message = subscription("/topic/room/5", 12L);

        assertThat(interceptor.preSend(message, channel)).isSameAs(message);
    }

    @Test
    void rejectsUnauthorizedPrivateRoomAndPresenceSubscriptions() {
        when(roomService.findAccessibleRoom(5L, 99L))
                .thenThrow(new PrivateRoomAccessDeniedException());

        assertThatThrownBy(() -> interceptor.preSend(
                subscription("/topic/room/5", 99L), channel))
                .isInstanceOf(MessageDeliveryException.class);
        assertThatThrownBy(() -> interceptor.preSend(
                subscription("/topic/room/5/presence", 99L), channel))
                .isInstanceOf(MessageDeliveryException.class);
    }

    @Test
    void leavesPublicCategoryChatSubscriptionUnchanged() {
        Message<?> message = subscription("/topic/public-chat/focus", 99L);
        assertThat(interceptor.preSend(message, channel)).isSameAs(message);
    }

    private Message<?> subscription(String destination, long userId) {
        SimpMessageHeaderAccessor headers = SimpMessageHeaderAccessor.create(
                SimpMessageType.SUBSCRIBE);
        headers.setDestination(destination);
        Principal principal = () -> Long.toString(userId);
        headers.setUser(principal);
        headers.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders());
    }
}
