package jp.workwith.seat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;

import jp.workwith.room.Room;
import jp.workwith.room.RoomRepository;
import jp.workwith.user.User;
import jp.workwith.user.UserRepository;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class SeatRepositoryTests {

    @Autowired private SeatRepository seatRepository;
    @Autowired private RoomRepository roomRepository;
    @Autowired private UserRepository userRepository;

    @Test
    void createsAndFindsSeatsInSeatNumberOrder() {
        User user = userRepository.create(new User(
                null, "seat_repo_" + UUID.randomUUID().toString().replace("-", ""),
                "test-password", null));
        Room room = roomRepository.create(new Room(
                null, "S" + UUID.randomUUID().toString().replace("-", "").substring(0, 5).toUpperCase(),
                "private", "Seat repository", "focus", null, 3, user.getUserId()));

        try {
            seatRepository.createAll(List.of(
                    new Seat(null, room.getRoomId(), 3, 48.0, 38.0),
                    new Seat(null, room.getRoomId(), 1, 26.0, 31.6),
                    new Seat(null, room.getRoomId(), 2, 37.0, 35.0)));

            List<Seat> seats = seatRepository.findByRoomId(room.getRoomId());
            assertThat(seats).extracting(Seat::getSeatNumber).containsExactly(1, 2, 3);
            assertThat(seatRepository.findById(seats.getFirst().getSeatId())).isPresent();
            assertThatThrownBy(() -> seatRepository.create(
                    new Seat(null, room.getRoomId(), 1, 99.0, 99.0)))
                    .isInstanceOf(DataAccessException.class);
        } finally {
            seatRepository.deleteByRoomId(room.getRoomId());
            roomRepository.deleteById(room.getRoomId());
            userRepository.deleteById(user.getUserId());
        }
    }
}
