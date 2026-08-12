package jp.workwith.room;

import java.security.SecureRandom;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;

import jp.workwith.seat.SeatService;
import jp.workwith.seatassignment.AlreadyAssignedToAnotherRoomException;
import jp.workwith.seatassignment.SeatAssignmentService;
import jp.workwith.user.User;
import jp.workwith.user.UserNotFoundException;
import jp.workwith.user.UserService;

/** 部屋作成時の検証や部屋名の自動生成を担当します。 */
@Service
public class RoomService {

    private static final int MAX_SEATS = 10;
    private static final int ROOM_CODE_LENGTH = 6;
    private static final int ROOM_CODE_MAX_ATTEMPTS = 20;
    private static final String ROOM_CODE_CHARACTERS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final Set<String> ALLOWED_THEMES = Set.of("focus", "casual", "night");
    private static final Map<String, String> BACKGROUND_URLS = Map.of(
            "focus", "work-space-pic/room-forcus-task.png",
            "casual", "work-space-pic/room-speak-ok.png",
            "night", "work-space-pic/room-midnight-task.PNG");

    private final RoomRepository roomRepository;
    private final UserService userService;
    private final SeatService seatService;
    private final SeatAssignmentService seatAssignmentService;

    public RoomService(
            RoomRepository roomRepository,
            UserService userService,
            SeatService seatService,
            SeatAssignmentService seatAssignmentService) {
        this.roomRepository = roomRepository;
        this.userService = userService;
        this.seatService = seatService;
        this.seatAssignmentService = seatAssignmentService;
    }

    @Transactional
    public Room createPrivateRoom(
            long userId,
            String roomName,
            String theme,
            Integer maxSeats) {
        User creator = userService.findById(userId)
                .orElseThrow(UserNotFoundException::new);
        String normalizedName = normalizeRoomName(roomName, creator.getUsername());
        String normalizedTheme = normalizeTheme(theme);
        int normalizedMaxSeats = validateMaxSeats(maxSeats);

        // 事前確認とDBのUNIQUE制約の両方で、重複コードの保存を防止します。
        for (int attempt = 0; attempt < ROOM_CODE_MAX_ATTEMPTS; attempt++) {
            String roomCode = generateRoomCode();
            if (roomRepository.findByRoomCode(roomCode).isPresent()) {
                continue;
            }

            Room room = new Room(
                    null,
                    roomCode,
                    "private",
                    normalizedName,
                    normalizedTheme,
                    BACKGROUND_URLS.get(normalizedTheme),
                    normalizedMaxSeats,
                    userId);
            Room createdRoom;
            try {
                createdRoom = roomRepository.create(room);
            } catch (DuplicateKeyException exception) {
                // 同時作成で参加コードが重複した場合は、新しいコードで再試行します。
                continue;
            }

            // 座席生成の例外は捕捉せず、部屋のINSERTと一緒にロールバックさせます。
            seatService.createForRoom(createdRoom.getRoomId(), createdRoom.getMaxSeats());
            seatAssignmentService.autoAssignSeat(createdRoom.getRoomId(), userId);
            return createdRoom;
        }

        throw new IllegalStateException("参加コードを生成できませんでした");
    }

    public Room findById(long roomId) {
        return roomRepository.findById(roomId)
                .orElseThrow(RoomNotFoundException::new);
    }

    public List<Room> findPublicRooms() {
        return roomRepository.findPublicRooms();
    }

    public List<Room> findViewablePublicRooms(long userId) {
        long joinedRoomId = seatAssignmentService.findAssignedRoomId(userId)
                .orElseThrow(jp.workwith.seatassignment.SeatAssignmentNotFoundException::new);
        Room joinedRoom = findById(joinedRoomId);
        if (!"public".equals(joinedRoom.getRoomType())) return List.of();
        return roomRepository.findPublicRoomsByTheme(joinedRoom.getTheme());
    }

    public Room findViewablePublicRoom(long userId, long viewingRoomId) {
        return findViewablePublicRooms(userId).stream()
                .filter(room -> room.getRoomId() == viewingRoomId)
                .findFirst()
                .orElseThrow(RoomNotFoundException::new);
    }

    public List<Room> findCreatedRooms(long userId) {
        return roomRepository.findByCreatedBy(userId);
    }

    public Room findPrivateRoomByCode(String roomCode) {
        String normalizedCode = roomCode == null ? "" : roomCode.trim().toUpperCase();
        return roomRepository.findByRoomCode(normalizedCode)
                .filter(room -> "private".equals(room.getRoomType()))
                .orElseThrow(RoomNotFoundException::new);
    }

    @Transactional
    public ThemeUpdateResult updatePrivateRoomTheme(long roomId, long userId, String theme) {
        Room room = findById(roomId);
        if (!"private".equals(room.getRoomType())
                || room.getCreatedBy() == null
                || room.getCreatedBy() != userId) {
            throw new RoomThemeForbiddenException();
        }

        String normalizedTheme = normalizeTheme(theme);
        if (normalizedTheme.equals(room.getTheme())) {
            return new ThemeUpdateResult(room, false);
        }
        if (!roomRepository.updateTheme(roomId, normalizedTheme)) {
            throw new RoomNotFoundException();
        }
        return new ThemeUpdateResult(findById(roomId), true);
    }

    @Transactional
    public synchronized Room joinPublicRoom(long roomId, long userId) {
        Room requestedRoom = findById(roomId);
        if (!"public".equals(requestedRoom.getRoomType())) {
            // private部屋の存在をroomIdだけから確認できる抜け道も作りません。
            throw new RoomNotFoundException();
        }
        var assignedRoomId = seatAssignmentService.findAssignedRoomId(userId);
        if (assignedRoomId.isPresent()) {
            if (assignedRoomId.get() != roomId) {
                throw new AlreadyAssignedToAnotherRoomException();
            }
            seatAssignmentService.autoAssignSeat(roomId, userId);
            return requestedRoom;
        }

        if (seatService.hasAvailableSeat(requestedRoom.getRoomId())) {
            seatAssignmentService.autoAssignSeat(requestedRoom.getRoomId(), userId);
            return requestedRoom;
        }

        List<Room> categoryRooms = roomRepository.findPublicRoomsByTheme(
                requestedRoom.getTheme());
        for (Room room : categoryRooms) {
            if (room.getRoomId().equals(requestedRoom.getRoomId())) continue;
            if (seatService.hasAvailableSeat(room.getRoomId())) {
                seatAssignmentService.autoAssignSeat(room.getRoomId(), userId);
                return room;
            }
        }

        String baseName = Map.of(
                "focus", "静かに集中室",
                "casual", "雑談OK部屋",
                "night", "深夜勢の部屋")
                .getOrDefault(requestedRoom.getTheme(), requestedRoom.getRoomName());
        Room newRoom = roomRepository.create(new Room(
                null,
                null,
                "public",
                baseName + " " + (categoryRooms.size() + 1),
                requestedRoom.getTheme(),
                requestedRoom.getBackgroundUrl(),
                requestedRoom.getMaxSeats(),
                null));
        seatService.createForRoom(newRoom.getRoomId(), newRoom.getMaxSeats());
        seatAssignmentService.autoAssignSeat(newRoom.getRoomId(), userId);
        return newRoom;
    }

    @Transactional
    public Room joinPrivateRoom(String roomCode, long userId) {
        Room room = findPrivateRoomByCode(roomCode);
        seatAssignmentService.autoAssignSeat(room.getRoomId(), userId);
        return room;
    }

    private String generateRoomCode() {
        StringBuilder code = new StringBuilder(ROOM_CODE_LENGTH);
        for (int index = 0; index < ROOM_CODE_LENGTH; index++) {
            int characterIndex = SECURE_RANDOM.nextInt(ROOM_CODE_CHARACTERS.length());
            code.append(ROOM_CODE_CHARACTERS.charAt(characterIndex));
        }
        return code.toString();
    }

    private String normalizeRoomName(String roomName, String username) {
        String normalizedName = roomName == null ? "" : roomName.trim();
        if (normalizedName.isEmpty()) {
            return username + "の部屋";
        }
        if (normalizedName.length() > 100 || normalizedName.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("部屋名は100文字以内で入力してください");
        }
        return normalizedName;
    }

    private String normalizeTheme(String theme) {
        String normalizedTheme = theme == null ? "" : theme.trim();
        if (!ALLOWED_THEMES.contains(normalizedTheme)) {
            throw new IllegalArgumentException("テーマはfocus、casual、nightから選択してください");
        }
        return normalizedTheme;
    }

    private int validateMaxSeats(Integer maxSeats) {
        if (maxSeats == null || maxSeats < 1 || maxSeats > MAX_SEATS) {
            throw new IllegalArgumentException("座席数は1～10の範囲で指定してください");
        }
        return maxSeats;
    }
}
