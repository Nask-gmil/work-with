package jp.workwith.seatassignment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;

import jp.workwith.room.Room;
import jp.workwith.room.RoomRepository;
import jp.workwith.seat.Seat;
import jp.workwith.seat.SeatRepository;
import jp.workwith.user.User;
import jp.workwith.user.UserRepository;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class SeatAssignmentRepositoryTests {

    @Autowired private SeatAssignmentRepository seatAssignmentRepository;
    @Autowired private SeatRepository seatRepository;
    @Autowired private RoomRepository roomRepository;
    @Autowired private UserRepository userRepository;

    @Test
    void createsAndFindsAssignmentsInSeatNumberOrder() {
        TestData data = createTestData();
        LocalDateTime startedAt = LocalDateTime.of(2026, 8, 12, 10, 20, 30);

        try {
            SeatAssignment second = new SeatAssignment(
                    data.seats().get(1).getSeatId(), data.users().get(1).getUserId(),
                    "break", null, startedAt.plusMinutes(1), null);
            SeatAssignment first = new SeatAssignment(
                    data.seats().get(0).getSeatId(), data.users().get(0).getUserId(),
                    "working", "Java学習", startedAt, startedAt.plusMinutes(5));
            seatAssignmentRepository.create(second);
            seatAssignmentRepository.create(first);

            assertThat(seatAssignmentRepository.findBySeatId(first.getSeatId()))
                    .get().usingRecursiveComparison().isEqualTo(first);
            assertThat(seatAssignmentRepository.findByUserId(second.getUserId()))
                    .get().usingRecursiveComparison().isEqualTo(second);
            assertThat(seatAssignmentRepository.findByRoomId(data.room().getRoomId()))
                    .extracting(SeatAssignment::getSeatId)
                    .containsExactly(first.getSeatId(), second.getSeatId());
            assertThat(seatAssignmentRepository.deleteBySeatId(first.getSeatId())).isTrue();
            assertThat(seatAssignmentRepository.findBySeatId(first.getSeatId())).isEmpty();
        } finally {
            cleanup(data);
        }
    }

    @Test
    void rejectsTwoUsersOnTheSameSeat() {
        TestData data = createTestData();
        try {
            seatAssignmentRepository.create(assignment(data.seats().get(0), data.users().get(0), "working"));
            assertThatThrownBy(() -> seatAssignmentRepository.create(
                    assignment(data.seats().get(0), data.users().get(1), "break")))
                    .isInstanceOf(DataAccessException.class);
        } finally {
            cleanup(data);
        }
    }

    @Test
    void rejectsOneUserOnTwoSeats() {
        TestData data = createTestData();
        try {
            seatAssignmentRepository.create(assignment(data.seats().get(0), data.users().get(0), "working"));
            assertThatThrownBy(() -> seatAssignmentRepository.create(
                    assignment(data.seats().get(1), data.users().get(0), "break")))
                    .isInstanceOf(DataAccessException.class);
        } finally {
            cleanup(data);
        }
    }

    @Test
    void rejectsInvalidStatus() {
        TestData data = createTestData();
        try {
            assertThatThrownBy(() -> seatAssignmentRepository.create(
                    assignment(data.seats().get(0), data.users().get(0), "away")))
                    .isInstanceOf(DataAccessException.class);
        } finally {
            cleanup(data);
        }
    }

    @Test
    void findsOnlyHeartbeatsAtOrBeforeTheTimeoutBoundaryAndDeletesConditionally() {
        TestData data = createTestData();
        LocalDateTime cutoff = LocalDateTime.of(2026, 8, 12, 18, 32);
        SeatAssignment expiredWorking = new SeatAssignment(
                data.seats().get(0).getSeatId(), data.users().get(0).getUserId(),
                "working", null, cutoff.minusMinutes(10), cutoff);
        SeatAssignment activeBreak = new SeatAssignment(
                data.seats().get(1).getSeatId(), data.users().get(1).getUserId(),
                "break", null, cutoff.minusMinutes(10), cutoff.plusSeconds(1));

        try {
            seatAssignmentRepository.create(expiredWorking);
            seatAssignmentRepository.create(activeBreak);

            assertThat(seatAssignmentRepository.findExpiredAssignments(cutoff))
                    .extracting(ExpiredSeatAssignment::seatId)
                    .contains(expiredWorking.getSeatId())
                    .doesNotContain(activeBreak.getSeatId());
            assertThat(seatAssignmentRepository.deleteIfHeartbeatExpired(
                    activeBreak.getSeatId(), cutoff)).isFalse();
            assertThat(seatAssignmentRepository.deleteIfHeartbeatExpired(
                    expiredWorking.getSeatId(), cutoff)).isTrue();
            assertThat(seatAssignmentRepository.findBySeatId(activeBreak.getSeatId()))
                    .isPresent();
            assertThat(seatRepository.findById(expiredWorking.getSeatId())).isPresent();
        } finally {
            cleanup(data);
        }
    }

    private TestData createTestData() {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        User firstUser = userRepository.create(new User(null, "assignment_a_" + suffix, "password", null));
        User secondUser = userRepository.create(new User(null, "assignment_b_" + suffix, "password", null));
        Room room = roomRepository.create(new Room(
                null, "A" + suffix.substring(0, 5).toUpperCase(), "private",
                "Assignment repository", "focus", null, 2, firstUser.getUserId()));
        seatRepository.createAll(List.of(
                new Seat(null, room.getRoomId(), 1, 26.0, 31.6),
                new Seat(null, room.getRoomId(), 2, 37.0, 35.0)));
        return new TestData(room, seatRepository.findByRoomId(room.getRoomId()),
                List.of(firstUser, secondUser));
    }

    private SeatAssignment assignment(Seat seat, User user, String status) {
        LocalDateTime now = LocalDateTime.of(2026, 8, 12, 12, 0);
        return new SeatAssignment(seat.getSeatId(), user.getUserId(), status,
                "テスト", now, now);
    }

    private void cleanup(TestData data) {
        data.seats().forEach(seat -> seatAssignmentRepository.deleteBySeatId(seat.getSeatId()));
        seatRepository.deleteByRoomId(data.room().getRoomId());
        roomRepository.deleteById(data.room().getRoomId());
        data.users().forEach(user -> userRepository.deleteById(user.getUserId()));
    }

    private record TestData(Room room, List<Seat> seats, List<User> users) {}
}
