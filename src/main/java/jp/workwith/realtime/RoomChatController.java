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
import jp.workwith.security.SecurityEventLogger;

@Controller
public class RoomChatController {

    private static final Logger LOGGER = LoggerFactory.getLogger(RoomChatController.class);
    private final RoomChatService roomChatService;
    private final RoomRealtimeNotifier realtimeNotifier;
    private final ChatRateLimitService rateLimitService;
    private final SecurityEventLogger securityEventLogger;

    public RoomChatController(
            RoomChatService roomChatService,
            RoomRealtimeNotifier realtimeNotifier,
            ChatRateLimitService rateLimitService,
            SecurityEventLogger securityEventLogger) {
        this.roomChatService = roomChatService;
        this.realtimeNotifier = realtimeNotifier;
        this.rateLimitService = rateLimitService;
        this.securityEventLogger = securityEventLogger;
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
            securityEventLogger.websocketForbidden(null, roomId, "CHAT_SEND", null);
            return;
        }

        long userId = number.longValue();
        boolean dm = request != null && request.targetUserId() != null;
        ChatRateLimitService.RateLimitResult rateLimit = dm
                ? rateLimitService.recordDmAttempt(userId)
                : rateLimitService.recordChatAttempt(userId);
        if (!rateLimit.allowed()) {
            securityEventLogger.rateLimit(dm ? "DM_SEND" : "CHAT_SEND", userId, null, roomId);
            String type = dm ? "dm-rate-limit" : "chat-rate-limit";
            String message = dm
                    ? "DMの送信回数が多すぎます。少し時間を空けてから再度お試しください。"
                    : "チャットの送信回数が多すぎます。少し時間を空けてから再度お試しください。";
            realtimeNotifier.notifyChatError(
                    userId, new ChatErrorMessage(type, message, rateLimit.retryAfterSeconds()));
            return;
        }

        try {
            RoomChatMessage message = roomChatService.createMessage(
                    roomId,
                    userId,
                    request == null ? null : request.targetUserId(),
                    request == null ? null : request.content());
            if (message.targetUserId() == null) {
                realtimeNotifier.notifyChatMessage(
                        message, roomChatService.findPublicTheme(roomId));
            } else {
                realtimeNotifier.notifyPrivateChatMessage(message);
            }
        } catch (IllegalArgumentException exception) {
            realtimeNotifier.notifyChatError(userId,
                    new ChatErrorMessage("chat-validation", exception.getMessage(), 0));
            String content = request == null ? null : request.content();
            if (content != null && content.length() > 300) {
                securityEventLogger.invalidInput(userId, dm ? "DM_SEND" : "CHAT_SEND");
            }
        } catch (RoomNotFoundException
                | SeatAssignmentNotFoundException
                | SeatAssignmentRoomMismatchException
                | UserNotFoundException exception) {
            securityEventLogger.websocketForbidden(
                    userId, roomId, dm ? "DM_SEND" : "CHAT_SEND", null);
        } catch (RuntimeException exception) {
            // DB障害などで1件の送信に失敗しても、WebSocket接続全体は切断しません。
            LOGGER.error("WebSocketチャットの処理に失敗しました");
        }
    }
}
