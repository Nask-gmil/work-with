package jp.workwith.room;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import jp.workwith.seat.Seat;
import jp.workwith.seat.SeatRepository;
import jp.workwith.seatassignment.SeatAssignment;
import jp.workwith.seatassignment.SeatAssignmentRepository;
import jp.workwith.user.User;
import jp.workwith.user.UserRepository;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class PublicRoomRoutingServiceTests {

    @Autowired private RoomService roomService;
    @Autowired private RoomRepository roomRepository;
    @Autowired private SeatRepository seatRepository;
    @Autowired private SeatAssignmentRepository assignmentRepository;
    @Autowired private UserRepository userRepository;

    @Test
    void usesRequestedRoomThenLowestAvailableRoomAndCreatesOnlyWhenAllAreFull() {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        String theme = "routing_" + suffix;
        List<User> users = createUsers(suffix, 5);
        List<Room> rooms = new ArrayList<>();
        Room first = createRoom("Routing", theme, 1);
        Room second = createRoom("Routing 2", theme, 2);
        rooms.add(first);
        rooms.add(second);

        try {
            Room direct = roomService.joinPublicRoom(first.getRoomId(), users.get(0).getUserId());
            assertThat(direct.getRoomId()).isEqualTo(first.getRoomId());

            Room routed = roomService.joinPublicRoom(first.getRoomId(), users.get(1).getUserId());
            assertThat(routed.getRoomId()).isEqualTo(second.getRoomId());
            assertThat(roomRepository.findPublicRoomsByTheme(theme)).hasSize(2);

            assign(seats(second).get(1), users.get(2));
            Room generated = roomService.joinPublicRoom(first.getRoomId(), users.get(3).getUserId());
            rooms.add(generated);
            assertThat(generated.getRoomType()).isEqualTo("public");
            assertThat(generated.getTheme()).isEqualTo(theme);
            assertThat(generated.getRoomCode()).isNull();
            assertThat(generated.getCreatedBy()).isNull();
            assertThat(generated.getMaxSeats()).isEqualTo(first.getMaxSeats());
            assertThat(seats(generated)).hasSize(first.getMaxSeats());
            SeatAssignment generatedAssignment = assignmentRepository
                    .findByUserId(users.get(3).getUserId()).orElseThrow();
            assertThat(seatRepository.findById(generatedAssignment.getSeatId()).orElseThrow()
                    .getSeatNumber()).isEqualTo(1);
        } finally {
            cleanup(rooms, users);
        }
    }

    private Room createRoom(String name, String theme, int maxSeats) {
        Room room = roomRepository.create(new Room(
                null, null, "public", name, theme, "background.png", maxSeats, null));
        List<Seat> seats = new ArrayList<>();
        for (int number = 1; number <= maxSeats; number++) {
            seats.add(new Seat(null, room.getRoomId(), number,
                    20.0 + number, 30.0 + number));
        }
        seatRepository.createAll(seats);
        return room;
    }

    private List<User> createUsers(String suffix, int count) {
        List<User> users = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            users.add(userRepository.create(new User(
                    null, "routing_" + index + "_" + suffix, "password", "male_a")));
        }
        return users;
    }

    private List<Seat> seats(Room room) {
        return seatRepository.findByRoomId(room.getRoomId());
    }

    private void assign(Seat seat, User user) {
        LocalDateTime now = LocalDateTime.of(2026, 8, 12, 20, 30);
        assignmentRepository.create(new SeatAssignment(
                seat.getSeatId(), user.getUserId(), "working", null, now, now));
    }

    private void cleanup(List<Room> rooms, List<User> users) {
        users.forEach(user -> assignmentRepository.findByUserId(user.getUserId())
                .ifPresent(assignment -> assignmentRepository.deleteBySeatId(
                        assignment.getSeatId())));
        rooms.forEach(room -> {
            seatRepository.deleteByRoomId(room.getRoomId());
            roomRepository.deleteById(room.getRoomId());
        });
        users.forEach(user -> userRepository.deleteById(user.getUserId()));
    }
}
