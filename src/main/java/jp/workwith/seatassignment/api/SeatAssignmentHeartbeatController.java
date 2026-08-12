package jp.workwith.seatassignment.api;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jp.workwith.room.RoomNotFoundException;
import jp.workwith.seatassignment.SeatAssignmentNotFoundException;
import jp.workwith.seatassignment.SeatAssignmentRoomMismatchException;
import jp.workwith.seatassignment.SeatAssignmentService;
import jp.workwith.user.UserSession;
import jp.workwith.user.api.ApiErrorResponse;

@RestController
@RequestMapping("/api/rooms/{roomId}/heartbeat")
public class SeatAssignmentHeartbeatController {

    private final SeatAssignmentService seatAssignmentService;

    public SeatAssignmentHeartbeatController(SeatAssignmentService seatAssignmentService) {
        this.seatAssignmentService = seatAssignmentService;
    }

    @PostMapping
    public ResponseEntity<?> updateHeartbeat(
            @PathVariable long roomId,
            HttpServletRequest request) {
        try {
            long userId = ((Number) request.getSession(false)
                    .getAttribute(UserSession.LOGIN_USER_ID)).longValue();
            seatAssignmentService.updateHeartbeat(roomId, userId);
            return ResponseEntity.noContent().build();
        } catch (RoomNotFoundException exception) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiErrorResponse(exception.getMessage()));
        } catch (SeatAssignmentNotFoundException | SeatAssignmentRoomMismatchException exception) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ApiErrorResponse(exception.getMessage()));
        } catch (DataAccessException | IllegalStateException exception) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiErrorResponse("heartbeatの更新に失敗しました"));
        }
    }
}
