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
import jp.workwith.realtime.RoomRealtimeNotifier;
import jp.workwith.seatassignment.SeatAssignmentService;
import jp.workwith.seatassignment.RoomParticipant;
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
    private final RoomRealtimeNotifier realtimeNotifier;

    public SeatAssignmentController(
            SeatAssignmentService seatAssignmentService,
            RoomService roomService,
            RoomRealtimeNotifier realtimeNotifier) {
        this.seatAssignmentService = seatAssignmentService;
        this.roomService = roomService;
        this.realtimeNotifier = realtimeNotifier;
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
            boolean left = seatAssignmentService.leaveRoom(roomId, userId);
            if (left) {
                realtimeNotifier.notifyParticipantsChanged(roomId);
            }
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
            RoomParticipant participant = seatAssignmentService.updateStatus(
                    roomId, userId, requestBody.getStatus());
            realtimeNotifier.notifyStatusChanged(roomId);
            return ResponseEntity.ok(SeatAssignmentResponse.from(participant));
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

    @PatchMapping("/me/work-content")
    public ResponseEntity<?> updateWorkContent(
            @PathVariable long roomId,
            @RequestBody UpdateWorkContentRequest requestBody,
            HttpServletRequest request) {
        try {
            long userId = ((Number) request.getSession(false)
                    .getAttribute(UserSession.LOGIN_USER_ID)).longValue();
            RoomParticipant participant = seatAssignmentService.updateWorkContent(
                    roomId, userId, requestBody.workContent());
            realtimeNotifier.notifyWorkContentChanged(roomId, userId);
            return ResponseEntity.ok(SeatAssignmentResponse.from(participant));
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
                    .body(new ApiErrorResponse("作業内容の更新に失敗しました"));
        }
    }
}
