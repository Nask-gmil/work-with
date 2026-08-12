package jp.workwith.room;

import java.util.List;
import java.util.Map;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import jp.workwith.seat.SeatService;

/** ロビーに常設する3種類のpublic部屋と座席を、存在しない場合だけ作成します。 */
@Component
public class PublicRoomInitializer implements ApplicationRunner {

    private static final int MAX_SEATS = 10;
    private static final Map<String, String> ROOM_NAMES = Map.of(
            "focus", "静かに集中室",
            "casual", "雑談OK部屋",
            "night", "深夜勢の部屋");
    private static final Map<String, String> BACKGROUND_URLS = Map.of(
            "focus", "work-space-pic/room-forcus-task.png",
            "casual", "work-space-pic/room-speak-ok.png",
            "night", "work-space-pic/room-midnight-task.PNG");

    private final RoomRepository roomRepository;
    private final SeatService seatService;

    public PublicRoomInitializer(RoomRepository roomRepository, SeatService seatService) {
        this.roomRepository = roomRepository;
        this.seatService = seatService;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments arguments) {
        List<Room> existingRooms = roomRepository.findPublicRooms();
        ROOM_NAMES.forEach((theme, roomName) -> {
            boolean alreadyExists = existingRooms.stream()
                    .anyMatch(room -> theme.equals(room.getTheme()));
            if (alreadyExists) return;

            Room room = roomRepository.create(new Room(
                    null,
                    null,
                    "public",
                    roomName,
                    theme,
                    BACKGROUND_URLS.get(theme),
                    MAX_SEATS,
                    null));
            seatService.createForRoom(room.getRoomId(), room.getMaxSeats());
        });
    }
}
