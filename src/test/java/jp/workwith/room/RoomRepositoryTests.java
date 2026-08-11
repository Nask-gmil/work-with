package jp.workwith.room;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import jp.workwith.user.User;
import jp.workwith.user.UserRepository;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class RoomRepositoryTests {

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void createsAndFindsRooms() {
        String username = "room_repository_" + UUID.randomUUID().toString().replace("-", "");
        User user = userRepository.create(new User(null, username, "test-password", null));
        Room privateRoom = null;
        Room publicRoom = null;

        try {
            privateRoom = roomRepository.create(new Room(
                    null, "R7K9PX", "private", "Repository private", "focus",
                    "work-space-pic/room-forcus-task.png", 10, user.getUserId()));
            publicRoom = roomRepository.create(new Room(
                    null, null, "public", "Repository public", "casual",
                    "work-space-pic/room-speak-ok.png", 10, user.getUserId()));

            assertThat(privateRoom.getRoomId()).isPositive();
            assertThat(roomRepository.findById(privateRoom.getRoomId()))
                    .get()
                    .extracting(Room::getRoomName)
                    .isEqualTo("Repository private");
            assertThat(roomRepository.findByRoomCode("R7K9PX"))
                    .get()
                    .extracting(Room::getRoomId)
                    .isEqualTo(privateRoom.getRoomId());
            assertThat(roomRepository.findByRoomCode("MISSING")).isEmpty();
            assertThat(publicRoom.getRoomCode()).isNull();
            assertThat(roomRepository.findPublicRooms())
                    .extracting(Room::getRoomId)
                    .contains(publicRoom.getRoomId())
                    .doesNotContain(privateRoom.getRoomId());
            assertThat(roomRepository.findByCreatedBy(user.getUserId()))
                    .extracting(Room::getRoomId)
                    .contains(privateRoom.getRoomId(), publicRoom.getRoomId());
        } finally {
            if (privateRoom != null) {
                roomRepository.deleteById(privateRoom.getRoomId());
            }
            if (publicRoom != null) {
                roomRepository.deleteById(publicRoom.getRoomId());
            }
            userRepository.deleteById(user.getUserId());
        }
    }
}
