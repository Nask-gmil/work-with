package jp.workwith.realtime;

import java.security.Principal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.stereotype.Component;

import jp.workwith.room.PrivateRoomAccessDeniedException;
import jp.workwith.room.RoomNotFoundException;
import jp.workwith.room.RoomService;

@Component
public class RoomSubscriptionAuthorizationInterceptor implements ChannelInterceptor {

    private static final Pattern ROOM_TOPIC = Pattern.compile(
            "^/topic/room/(\\d+)(?:/presence)?$");

    private final RoomService roomService;

    public RoomSubscriptionAuthorizationInterceptor(RoomService roomService) {
        this.roomService = roomService;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        SimpMessageHeaderAccessor headers = SimpMessageHeaderAccessor.wrap(message);
        if (headers.getMessageType() != SimpMessageType.SUBSCRIBE) return message;

        String destination = headers.getDestination();
        Matcher matcher = destination == null ? null : ROOM_TOPIC.matcher(destination);
        if (matcher == null || !matcher.matches()) return message;

        Principal principal = headers.getUser();
        if (principal == null) throw new MessageDeliveryException("認証が必要です");
        try {
            long userId = Long.parseLong(principal.getName());
            long roomId = Long.parseLong(matcher.group(1));
            roomService.findAccessibleRoom(roomId, userId);
            return message;
        } catch (NumberFormatException | RoomNotFoundException
                | PrivateRoomAccessDeniedException exception) {
            throw new MessageDeliveryException(
                    message, "この部屋を購読する権限がありません", exception);
        }
    }
}
