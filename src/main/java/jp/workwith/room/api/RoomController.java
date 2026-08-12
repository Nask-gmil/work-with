package jp.workwith.room.api;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jp.workwith.room.Room;
import jp.workwith.room.RoomNotFoundException;
import jp.workwith.room.RoomService;
import jp.workwith.room.RoomThemeForbiddenException;
import jp.workwith.room.ThemeUpdateResult;
import jp.workwith.realtime.RoomRealtimeNotifier;
import jp.workwith.seatassignment.AlreadyAssignedToAnotherRoomException;
import jp.workwith.seatassignment.RoomFullException;
import jp.workwith.user.UserNotFoundException;
import jp.workwith.user.UserSession;
import jp.workwith.user.api.ApiErrorResponse;

/** ROOMSに関するHTTPリクエストを受け付けます。 */
@RestController
@RequestMapping("/api/rooms")
public class RoomController {

    private final RoomService roomService;
    private final RoomRealtimeNotifier realtimeNotifier;

    public RoomController(RoomService roomService, RoomRealtimeNotifier realtimeNotifier) {
        this.roomService = roomService;
        this.realtimeNotifier = realtimeNotifier;
    }

    /** ログイン中の本人をcreatedByとして、private部屋を作成します。 */
    @PostMapping
    public ResponseEntity<?> create(
            @RequestBody CreateRoomRequest requestBody,
            HttpServletRequest request) {
        try {
            Room createdRoom = roomService.createPrivateRoom(
                    getLoginUserId(request),
                    requestBody.getRoomName(),
                    requestBody.getTheme(),
                    requestBody.getMaxSeats());
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(RoomResponse.from(createdRoom));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest()
                    .body(new ApiErrorResponse(exception.getMessage()));
        } catch (UserNotFoundException exception) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ApiErrorResponse("ログインが必要です。"));
        } catch (RoomFullException | AlreadyAssignedToAnotherRoomException exception) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ApiErrorResponse(exception.getMessage()));
        } catch (DataAccessException | IllegalStateException exception) {
            return serverError();
        }
    }

    @GetMapping("/public")
    public ResponseEntity<?> findPublicRooms() {
        try {
            return ResponseEntity.ok(toResponses(roomService.findPublicRooms()));
        } catch (DataAccessException exception) {
            return serverError();
        }
    }

    @GetMapping("/mine")
    public ResponseEntity<?> findMyRooms(HttpServletRequest request) {
        try {
            return ResponseEntity.ok(toResponses(
                    roomService.findCreatedRooms(getLoginUserId(request))));
        } catch (DataAccessException exception) {
            return serverError();
        }
    }

    /** ユーザー向け参加コードからprivate部屋を取得します。 */
    @GetMapping("/code/{roomCode}")
    public ResponseEntity<?> findByRoomCode(@PathVariable String roomCode) {
        try {
            return ResponseEntity.ok(RoomResponse.from(
                    roomService.findPrivateRoomByCode(roomCode)));
        } catch (RoomNotFoundException exception) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiErrorResponse(exception.getMessage()));
        } catch (DataAccessException exception) {
            return serverError();
        }
    }

    @GetMapping("/{roomId}")
    public ResponseEntity<?> findById(@PathVariable long roomId) {
        try {
            return ResponseEntity.ok(RoomResponse.from(roomService.findById(roomId)));
        } catch (RoomNotFoundException exception) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiErrorResponse(exception.getMessage()));
        } catch (DataAccessException exception) {
            return serverError();
        }
    }

    @PatchMapping("/{roomId}/theme")
    public ResponseEntity<?> updateTheme(
            @PathVariable long roomId,
            @RequestBody UpdateRoomThemeRequest requestBody,
            HttpServletRequest request) {
        try {
            ThemeUpdateResult result = roomService.updatePrivateRoomTheme(
                    roomId, getLoginUserId(request), requestBody.theme());
            if (result.changed()) {
                realtimeNotifier.notifyThemeChanged(roomId, result.room().getTheme());
            }
            return ResponseEntity.ok(RoomResponse.from(result.room()));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(new ApiErrorResponse(exception.getMessage()));
        } catch (RoomThemeForbiddenException exception) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new ApiErrorResponse(exception.getMessage()));
        } catch (RoomNotFoundException exception) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiErrorResponse(exception.getMessage()));
        } catch (DataAccessException exception) {
            return serverError();
        }
    }

    /** 公開されているpublic部屋だけへ、ログイン中の本人を自動着席させます。 */
    @PostMapping("/{roomId}/join")
    public ResponseEntity<?> joinPublicRoom(
            @PathVariable long roomId,
            HttpServletRequest request) {
        try {
            Room room = roomService.joinPublicRoom(roomId, getLoginUserId(request));
            realtimeNotifier.notifyParticipantsChanged(room.getRoomId());
            return ResponseEntity.ok(RoomResponse.from(room));
        } catch (RoomNotFoundException exception) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiErrorResponse(exception.getMessage()));
        } catch (RoomFullException | AlreadyAssignedToAnotherRoomException exception) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ApiErrorResponse(exception.getMessage()));
        } catch (DataAccessException exception) {
            return serverError();
        }
    }

    /** room_codeを確認してから、ログイン中の本人をprivate部屋へ自動着席させます。 */
    @PostMapping("/private/join")
    public ResponseEntity<?> joinPrivateRoom(
            @RequestBody PrivateRoomJoinRequest requestBody,
            HttpServletRequest request) {
        try {
            Room room = roomService.joinPrivateRoom(
                    requestBody.getRoomCode(), getLoginUserId(request));
            realtimeNotifier.notifyParticipantsChanged(room.getRoomId());
            return ResponseEntity.ok(RoomResponse.from(room));
        } catch (RoomNotFoundException exception) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiErrorResponse(exception.getMessage()));
        } catch (RoomFullException | AlreadyAssignedToAnotherRoomException exception) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ApiErrorResponse(exception.getMessage()));
        } catch (DataAccessException exception) {
            return serverError();
        }
    }

    private List<RoomResponse> toResponses(List<Room> rooms) {
        return rooms.stream().map(RoomResponse::from).toList();
    }

    private long getLoginUserId(HttpServletRequest request) {
        return ((Number) request.getSession(false)
                .getAttribute(UserSession.LOGIN_USER_ID)).longValue();
    }

    private ResponseEntity<ApiErrorResponse> serverError() {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiErrorResponse("部屋情報の処理に失敗しました"));
    }
}
