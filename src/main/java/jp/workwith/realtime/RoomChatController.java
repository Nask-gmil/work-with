package jp.workwith.realtime;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

import jp.workwith.room.RoomNotFoundException;
import jp.workwith.seatassignment.SeatAssignmentNotFoundException;
import jp.workwith.seatassignment.SeatAssignmentRoomMismatchException;
import jp.workwith.user.UserNotFoundException;
import jp.workwith.user.UserSession;

@Controller
public class RoomChatController {

    private static final Logger LOGGER = LoggerFactory.getLogger(RoomChatController.class);
    private final RoomChatService roomChatService;
    private final RoomRealtimeNotifier realtimeNotifier;

    public RoomChatController(
            RoomChatService roomChatService,
            RoomRealtimeNotifier realtimeNotifier) {
        this.roomChatService = roomChatService;
        this.realtimeNotifier = realtimeNotifier;
    }

    @MessageMapping("/room/{roomId}/chat")
    public void sendChatMessage(
            @DestinationVariable long roomId,
            @Payload RoomChatRequest request,
            SimpMessageHeaderAccessor headerAccessor) {
        Map<String, Object> sessionAttributes = headerAccessor.getSessionAttributes();
        Object loginUserId = sessionAttributes == null
                ? null : sessionAttributes.get(UserSession.LOGIN_USER_ID);
        if (!(loginUserId instanceof Number number)) {
            LOGGER.warn("未認証のWebSocketチャット送信を拒否しました");
            return;
        }

        try {
            RoomChatMessage message = roomChatService.createMessage(
                    roomId,
                    number.longValue(),
                    request == null ? null : request.targetUserId(),
                    request == null ? null : request.content());
            if (message.targetUserId() == null) {
                realtimeNotifier.notifyChatMessage(message);
            } else {
                realtimeNotifier.notifyPrivateChatMessage(message);
            }
        } catch (IllegalArgumentException
                | RoomNotFoundException
                | SeatAssignmentNotFoundException
                | SeatAssignmentRoomMismatchException
                | UserNotFoundException exception) {
            LOGGER.warn("不正なWebSocketチャット送信を拒否しました: {}", exception.getMessage());
        } catch (RuntimeException exception) {
            // DB障害などで1件の送信に失敗しても、WebSocket接続全体は切断しません。
            LOGGER.error("WebSocketチャットの処理に失敗しました");
        }
    }
}
