package jp.workwith.room;

import java.security.SecureRandom;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.dao.DuplicateKeyException;

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

    public RoomService(RoomRepository roomRepository, UserService userService) {
        this.roomRepository = roomRepository;
        this.userService = userService;
    }

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
            try {
                return roomRepository.create(room);
            } catch (DuplicateKeyException exception) {
                // 同時作成で重複した場合も、新しいコードで安全に再試行します。
            }
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

    public List<Room> findCreatedRooms(long userId) {
        return roomRepository.findByCreatedBy(userId);
    }

    public Room findPrivateRoomByCode(String roomCode) {
        String normalizedCode = roomCode == null ? "" : roomCode.trim().toUpperCase();
        return roomRepository.findByRoomCode(normalizedCode)
                .filter(room -> "private".equals(room.getRoomType()))
                .orElseThrow(RoomNotFoundException::new);
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
