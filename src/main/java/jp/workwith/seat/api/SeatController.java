package jp.workwith.seat.api;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jp.workwith.room.RoomNotFoundException;
import jp.workwith.room.PrivateRoomAccessDeniedException;
import jp.workwith.room.RoomService;
import jp.workwith.seat.SeatService;
import jp.workwith.user.api.ApiErrorResponse;
import jp.workwith.user.UserSession;

/** 部屋ごとの座席一覧APIです。ログイン判定はAuthInterceptorに任せます。 */
@RestController
@RequestMapping("/api/rooms/{roomId}/seats")
public class SeatController {

    private final SeatService seatService;
    private final RoomService roomService;

    public SeatController(SeatService seatService, RoomService roomService) {
        this.seatService = seatService;
        this.roomService = roomService;
    }

    @GetMapping
    public ResponseEntity<?> findByRoomId(
            @PathVariable long roomId, HttpServletRequest request) {
        try {
            // 空配列と「存在しない部屋」を区別するため、先に部屋の存在を確認します。
            long userId = ((Number) request.getSession(false)
                    .getAttribute(UserSession.LOGIN_USER_ID)).longValue();
            roomService.findAccessibleRoom(roomId, userId);
            List<SeatResponse> response = seatService.findByRoomId(roomId).stream()
                    .map(SeatResponse::from)
                    .toList();
            return ResponseEntity.ok(response);
        } catch (PrivateRoomAccessDeniedException exception) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new ApiErrorResponse(exception.getMessage()));
        } catch (RoomNotFoundException exception) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiErrorResponse(exception.getMessage()));
        } catch (DataAccessException exception) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiErrorResponse("座席情報の取得に失敗しました"));
        }
    }
}
