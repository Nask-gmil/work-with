package jp.workwith.seatassignment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import jp.workwith.room.Room;
import jp.workwith.room.RoomNotFoundException;
import jp.workwith.room.RoomRepository;
import jp.workwith.seat.Seat;
import jp.workwith.seat.SeatRepository;
import jp.workwith.user.User;
import jp.workwith.user.UserRepository;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class SeatAssignmentServiceTests {

    @Autowired private SeatAssignmentService seatAssignmentService;
    @Autowired private SeatAssignmentRepository seatAssignmentRepository;
    @Autowired private SeatRepository seatRepository;
    @Autowired private RoomRepository roomRepository;
    @Autowired private UserRepository userRepository;

    @Test
    void assignsFirstThenSecondSeatAndReusesTheSmallestGap() {
        TestData data = createTestData(2, 3);
        try {
            SeatAssignment first = seatAssignmentService.autoAssignSeat(
                    data.rooms().get(0).getRoomId(), data.users().get(0).getUserId());
            SeatAssignment second = seatAssignmentService.autoAssignSeat(
                    data.rooms().get(0).getRoomId(), data.users().get(1).getUserId());

            assertThat(seatNumber(first)).isEqualTo(1);
            assertThat(seatNumber(second)).isEqualTo(2);
            assertThat(first.getStatus()).isEqualTo("working");
            assertThat(first.getWorkContent()).isNull();
            assertThat(first.getStartedAt()).isNotNull();
            assertThat(first.getLastHeartbeatAt()).isEqualTo(first.getStartedAt());

            // 2番席を空け、3番席を使用中にしても、人数ではなく実際の空席から2番を選びます。
            seatAssignmentRepository.deleteBySeatId(second.getSeatId());
            Seat thirdSeat = seats(data.rooms().get(0)).get(2);
            seatAssignmentRepository.create(new SeatAssignment(
                    thirdSeat.getSeatId(), data.users().get(2).getUserId(), "working",
                    null, first.getStartedAt(), first.getStartedAt()));

            SeatAssignment reused = seatAssignmentService.autoAssignSeat(
                    data.rooms().get(0).getRoomId(), data.users().get(3).getUserId());
            assertThat(seatNumber(reused)).isEqualTo(2);
        } finally {
            cleanup(data);
        }
    }

    @Test
    void returnsTheExistingAssignmentWhenEnteringTheSameRoomAgain() {
        TestData data = createTestData(1, 3);
        try {
            long roomId = data.rooms().getFirst().getRoomId();
            long userId = data.users().getFirst().getUserId();
            SeatAssignment first = seatAssignmentService.autoAssignSeat(roomId, userId);
            SeatAssignment second = seatAssignmentService.autoAssignSeat(roomId, userId);

            assertThat(second).usingRecursiveComparison().isEqualTo(first);
            assertThat(seatAssignmentRepository.findByRoomId(roomId)).hasSize(1);
        } finally {
            cleanup(data);
        }
    }

    @Test
    void rejectsAUserAlreadyAssignedToAnotherRoomWithoutMovingThem() {
        TestData data = createTestData(2, 3);
        try {
            long firstRoomId = data.rooms().get(0).getRoomId();
            long secondRoomId = data.rooms().get(1).getRoomId();
            long userId = data.users().getFirst().getUserId();
            SeatAssignment original = seatAssignmentService.autoAssignSeat(firstRoomId, userId);

            assertThatThrownBy(() -> seatAssignmentService.autoAssignSeat(secondRoomId, userId))
                    .isInstanceOf(AlreadyAssignedToAnotherRoomException.class)
                    .hasMessage("既に別の部屋に着席しています");
            assertThat(seatAssignmentRepository.findByUserId(userId))
                    .get().extracting(SeatAssignment::getSeatId)
                    .isEqualTo(original.getSeatId());
            assertThat(seatAssignmentRepository.findByRoomId(secondRoomId)).isEmpty();
        } finally {
            cleanup(data);
        }
    }

    @Test
    void rejectsAssignmentWhenTheRoomIsFullWithoutChangingExistingAssignments() {
        TestData data = createTestData(1, 3);
        try {
            long roomId = data.rooms().getFirst().getRoomId();
            for (int index = 0; index < 3; index++) {
                seatAssignmentService.autoAssignSeat(roomId, data.users().get(index).getUserId());
            }

            assertThatThrownBy(() -> seatAssignmentService.autoAssignSeat(
                    roomId, data.users().get(3).getUserId()))
                    .isInstanceOf(RoomFullException.class)
                    .hasMessage("部屋は満席です");
            assertThat(seatAssignmentRepository.findByRoomId(roomId)).hasSize(3);
        } finally {
            cleanup(data);
        }
    }

    @Test
    void rejectsAssignmentToAMissingRoom() {
        TestData data = createTestData(1, 1);
        try {
            assertThatThrownBy(() -> seatAssignmentService.autoAssignSeat(
                    Long.MAX_VALUE, data.users().getFirst().getUserId()))
                    .isInstanceOf(RoomNotFoundException.class);
            assertThat(seatAssignmentRepository.findByUserId(
                    data.users().getFirst().getUserId())).isEmpty();
        } finally {
            cleanup(data);
        }
    }

    @Test
    void leavesOnlyTheRequestedUsersSeatAndKeepsTheSeatItself() {
        TestData data = createTestData(1, 3);
        try {
            long roomId = data.rooms().getFirst().getRoomId();
            SeatAssignment first = seatAssignmentService.autoAssignSeat(
                    roomId, data.users().get(0).getUserId());
            SeatAssignment second = seatAssignmentService.autoAssignSeat(
                    roomId, data.users().get(1).getUserId());

            assertThat(seatAssignmentService.leaveRoom(roomId, data.users().get(0).getUserId()))
                    .isTrue();
            assertThat(seatAssignmentRepository.findByUserId(data.users().get(0).getUserId()))
                    .isEmpty();
            assertThat(seatAssignmentRepository.findByUserId(data.users().get(1).getUserId()))
                    .get().extracting(SeatAssignment::getSeatId).isEqualTo(second.getSeatId());
            assertThat(seatRepository.findById(first.getSeatId())).isPresent();
        } finally {
            cleanup(data);
        }
    }

    @Test
    void leaveIsIdempotentAndDoesNotDeleteAnAssignmentFromAnotherRoom() {
        TestData data = createTestData(2, 2);
        try {
            long firstRoomId = data.rooms().get(0).getRoomId();
            long secondRoomId = data.rooms().get(1).getRoomId();
            long userId = data.users().getFirst().getUserId();
            SeatAssignment assignment = seatAssignmentService.autoAssignSeat(firstRoomId, userId);

            assertThatThrownBy(() -> seatAssignmentService.leaveRoom(secondRoomId, userId))
                    .isInstanceOf(SeatAssignmentRoomMismatchException.class);
            assertThat(seatAssignmentRepository.findByUserId(userId))
                    .get().extracting(SeatAssignment::getSeatId)
                    .isEqualTo(assignment.getSeatId());

            assertThat(seatAssignmentService.leaveRoom(firstRoomId, userId)).isTrue();
            assertThat(seatAssignmentService.leaveRoom(firstRoomId, userId)).isFalse();

            SeatAssignment moved = seatAssignmentService.autoAssignSeat(secondRoomId, userId);
            assertThat(seatRepository.findById(moved.getSeatId()).orElseThrow().getRoomId())
                    .isEqualTo(secondRoomId);
        } finally {
            cleanup(data);
        }
    }

    @Test
    void updatesOnlyStatusAndCanSwitchItBack() {
        TestData data = createTestData(1, 1);
        try {
            long roomId = data.rooms().getFirst().getRoomId();
            long userId = data.users().getFirst().getUserId();
            SeatAssignment original = seatAssignmentService.autoAssignSeat(roomId, userId);

            RoomParticipant onBreak = seatAssignmentService.updateStatus(roomId, userId, "break");
            assertThat(onBreak.status()).isEqualTo("break");
            assertThat(onBreak.seatId()).isEqualTo(original.getSeatId());
            assertThat(onBreak.userId()).isEqualTo(original.getUserId());
            assertThat(onBreak.workContent()).isEqualTo(original.getWorkContent());
            assertThat(onBreak.startedAt()).isEqualTo(original.getStartedAt());
            assertThat(onBreak.lastHeartbeatAt()).isEqualTo(original.getLastHeartbeatAt());

            assertThat(seatAssignmentService.updateStatus(roomId, userId, "working").status())
                    .isEqualTo("working");
        } finally {
            cleanup(data);
        }
    }

    @Test
    void rejectsInvalidStatusWithoutChangingTheAssignment() {
        TestData data = createTestData(1, 1);
        try {
            long roomId = data.rooms().getFirst().getRoomId();
            long userId = data.users().getFirst().getUserId();
            seatAssignmentService.autoAssignSeat(roomId, userId);

            for (String invalidStatus : new String[] {"away", "", null}) {
                assertThatThrownBy(() -> seatAssignmentService.updateStatus(
                        roomId, userId, invalidStatus))
                        .isInstanceOf(IllegalArgumentException.class);
            }
            assertThat(seatAssignmentRepository.findByUserId(userId))
                    .get().extracting(SeatAssignment::getStatus).isEqualTo("working");
        } finally {
            cleanup(data);
        }
    }

    @Test
    void rejectsStatusUpdateWhenUnseatedOrAssignedToAnotherRoom() {
        TestData data = createTestData(2, 1);
        try {
            long firstRoomId = data.rooms().get(0).getRoomId();
            long secondRoomId = data.rooms().get(1).getRoomId();
            long userId = data.users().getFirst().getUserId();

            assertThatThrownBy(() -> seatAssignmentService.updateStatus(
                    firstRoomId, userId, "break"))
                    .isInstanceOf(SeatAssignmentNotFoundException.class);

            seatAssignmentService.autoAssignSeat(firstRoomId, userId);
            assertThatThrownBy(() -> seatAssignmentService.updateStatus(
                    secondRoomId, userId, "break"))
                    .isInstanceOf(SeatAssignmentRoomMismatchException.class);
            assertThat(seatAssignmentRepository.findByUserId(userId))
                    .get().extracting(SeatAssignment::getStatus).isEqualTo("working");
        } finally {
            cleanup(data);
        }
    }

    private int seatNumber(SeatAssignment assignment) {
        return seatRepository.findById(assignment.getSeatId()).orElseThrow().getSeatNumber();
    }

    private List<Seat> seats(Room room) {
        return seatRepository.findByRoomId(room.getRoomId());
    }

    private TestData createTestData(int roomCount, int seatsPerRoom) {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        List<User> users = new ArrayList<>();
        for (int index = 0; index < 5; index++) {
            users.add(userRepository.create(new User(
                    null, "auto_assign_" + index + "_" + suffix, "password", null)));
        }

        List<Room> rooms = new ArrayList<>();
        for (int roomIndex = 0; roomIndex < roomCount; roomIndex++) {
            Room room = roomRepository.create(new Room(
                    null, null, "private", "Auto assignment " + roomIndex,
                    "focus", null, seatsPerRoom, users.getFirst().getUserId()));
            rooms.add(room);
            List<Seat> roomSeats = new ArrayList<>();
            for (int seatIndex = 1; seatIndex <= seatsPerRoom; seatIndex++) {
                roomSeats.add(new Seat(
                        null, room.getRoomId(), seatIndex,
                        20.0 + seatIndex, 30.0 + seatIndex));
            }
            seatRepository.createAll(roomSeats);
        }
        return new TestData(rooms, users);
    }

    private void cleanup(TestData data) {
        for (Room room : data.rooms()) {
            seats(room).forEach(seat ->
                    seatAssignmentRepository.deleteBySeatId(seat.getSeatId()));
            seatRepository.deleteByRoomId(room.getRoomId());
            roomRepository.deleteById(room.getRoomId());
        }
        data.users().forEach(user -> userRepository.deleteById(user.getUserId()));
    }

    private record TestData(List<Room> rooms, List<User> users) {}
}
