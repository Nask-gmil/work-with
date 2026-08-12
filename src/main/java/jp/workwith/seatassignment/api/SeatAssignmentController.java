package jp.workwith.seatassignment.api;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jp.workwith.room.RoomNotFoundException;
import jp.workwith.room.RoomService;
import jp.workwith.seatassignment.SeatAssignmentService;
import jp.workwith.seatassignment.SeatAssignmentNotFoundException;
import jp.workwith.seatassignment.SeatAssignmentRoomMismatchException;
import jp.workwith.user.UserSession;
import jp.workwith.user.api.ApiErrorResponse;

/** 部屋ごとの現在の座席割り当て一覧APIです。 */
@RestController
@RequestMapping("/api/rooms/{roomId}/seat-assignments")
public class SeatAssignmentController {

    private final SeatAssignmentService seatAssignmentService;
    private final RoomService roomService;

    public SeatAssignmentController(
            SeatAssignmentService seatAssignmentService,
            RoomService roomService) {
        this.seatAssignmentService = seatAssignmentService;
        this.roomService = roomService;
    }

    @GetMapping
    public ResponseEntity<?> findByRoomId(@PathVariable long roomId) {
        try {
            roomService.findById(roomId);
            List<SeatAssignmentResponse> response =
                    seatAssignmentService.findParticipantsByRoomId(roomId).stream()
                            .map(SeatAssignmentResponse::from)
                            .toList();
            return ResponseEntity.ok(response);
        } catch (RoomNotFoundException exception) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiErrorResponse(exception.getMessage()));
        } catch (DataAccessException exception) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiErrorResponse("座席割り当て情報の取得に失敗しました"));
        }
    }

    /** HttpSessionのログインユーザー本人だけを、指定した部屋から退席させます。 */
    @DeleteMapping("/me")
    public ResponseEntity<?> leaveRoom(
            @PathVariable long roomId,
            HttpServletRequest request) {
        try {
            long userId = ((Number) request.getSession(false)
                    .getAttribute(UserSession.LOGIN_USER_ID)).longValue();
            seatAssignmentService.leaveRoom(roomId, userId);
            return ResponseEntity.noContent().build();
        } catch (RoomNotFoundException exception) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiErrorResponse(exception.getMessage()));
        } catch (SeatAssignmentRoomMismatchException exception) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ApiErrorResponse(exception.getMessage()));
        } catch (DataAccessException | IllegalStateException exception) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiErrorResponse("退席処理に失敗しました"));
        }
    }

    @PatchMapping("/me/status")
    public ResponseEntity<?> updateStatus(
            @PathVariable long roomId,
            @RequestBody UpdateSeatAssignmentStatusRequest requestBody,
            HttpServletRequest request) {
        try {
            long userId = ((Number) request.getSession(false)
                    .getAttribute(UserSession.LOGIN_USER_ID)).longValue();
            return ResponseEntity.ok(SeatAssignmentResponse.from(
                    seatAssignmentService.updateStatus(roomId, userId, requestBody.getStatus())));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest()
                    .body(new ApiErrorResponse(exception.getMessage()));
        } catch (RoomNotFoundException exception) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiErrorResponse(exception.getMessage()));
        } catch (SeatAssignmentNotFoundException | SeatAssignmentRoomMismatchException exception) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ApiErrorResponse(exception.getMessage()));
        } catch (DataAccessException | IllegalStateException exception) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiErrorResponse("状態の更新に失敗しました"));
        }
    }
}
