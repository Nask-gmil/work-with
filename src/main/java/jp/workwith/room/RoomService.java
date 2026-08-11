package jp.workwith.room;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

import jp.workwith.user.User;
import jp.workwith.user.UserNotFoundException;
import jp.workwith.user.UserService;

/** 部屋作成時の検証や部屋名の自動生成を担当します。 */
@Service
public class RoomService {

    private static final int MAX_SEATS = 10;
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

        Room room = new Room(
                null,
                "private",
                normalizedName,
                normalizedTheme,
                BACKGROUND_URLS.get(normalizedTheme),
                normalizedMaxSeats,
                userId);
        return roomRepository.create(room);
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
