package jp.workwith.chat.api;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jp.workwith.realtime.RoomChatService;
import jp.workwith.room.RoomNotFoundException;
import jp.workwith.seatassignment.SeatAssignmentNotFoundException;
import jp.workwith.seatassignment.SeatAssignmentRoomMismatchException;
import jp.workwith.user.UserSession;
import jp.workwith.user.api.ApiErrorResponse;

@RestController
@RequestMapping("/api/rooms/{roomId}/chat-messages")
public class ChatHistoryController {

    private final RoomChatService roomChatService;

    public ChatHistoryController(RoomChatService roomChatService) {
        this.roomChatService = roomChatService;
    }

    @GetMapping
    public ResponseEntity<?> findGlobalHistory(
            @PathVariable long roomId,
            HttpServletRequest request) {
        long userId = ((Number) request.getSession(false)
                .getAttribute(UserSession.LOGIN_USER_ID)).longValue();
        try {
            return ResponseEntity.ok(roomChatService.findGlobalHistory(roomId, userId));
        } catch (RoomNotFoundException exception) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiErrorResponse(exception.getMessage()));
        } catch (SeatAssignmentNotFoundException
                | SeatAssignmentRoomMismatchException exception) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new ApiErrorResponse("現在この部屋に着席していません"));
        } catch (DataAccessException | IllegalStateException exception) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiErrorResponse("チャット履歴を取得できませんでした"));
        }
    }

    @GetMapping("/private/{otherUserId}")
    public ResponseEntity<?> findPrivateHistory(
            @PathVariable long roomId,
            @PathVariable long otherUserId,
            HttpServletRequest request) {
        long userId = ((Number) request.getSession(false)
                .getAttribute(UserSession.LOGIN_USER_ID)).longValue();
        try {
            return ResponseEntity.ok(
                    roomChatService.findPrivateHistory(roomId, userId, otherUserId));
        } catch (IllegalArgumentException
                | SeatAssignmentNotFoundException
                | SeatAssignmentRoomMismatchException exception) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new ApiErrorResponse("現在同じ部屋にいる相手を選択してください"));
        } catch (RoomNotFoundException exception) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiErrorResponse(exception.getMessage()));
        } catch (DataAccessException | IllegalStateException exception) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiErrorResponse("個別チャット履歴を取得できませんでした"));
        }
    }
}
