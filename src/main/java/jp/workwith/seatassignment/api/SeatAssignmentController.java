package jp.workwith.seatassignment.api;

import java.util.List;

import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jp.workwith.room.RoomNotFoundException;
import jp.workwith.room.RoomService;
import jp.workwith.seatassignment.SeatAssignmentService;
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
                    seatAssignmentService.findByRoomId(roomId).stream()
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
}
