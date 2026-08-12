package jp.workwith.seatassignment.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import jp.workwith.room.Room;
import jp.workwith.room.RoomRepository;
import jp.workwith.seat.Seat;
import jp.workwith.seat.SeatRepository;
import jp.workwith.seatassignment.SeatAssignment;
import jp.workwith.seatassignment.SeatAssignmentRepository;
import jp.workwith.user.User;
import jp.workwith.user.UserRepository;
import jp.workwith.user.UserSession;

@SpringBootTest
@AutoConfigureMockMvc
class SeatAssignmentHeartbeatApiTests {

    @Autowired private MockMvc mockMvc;
    @Autowired private RoomRepository roomRepository;
    @Autowired private SeatRepository seatRepository;
    @Autowired private SeatAssignmentRepository assignmentRepository;
    @Autowired private UserRepository userRepository;

    @Test
    void updatesOnlyTheSessionUsersHeartbeatInTheRequestedRoom() throws Exception {
        TestData data = createData();
        try {
            mockMvc.perform(post("/api/rooms/{roomId}/heartbeat", data.firstRoom().getRoomId())
                    .session(session(data.seatedUser())))
                    .andExpect(status().isNoContent());

            SeatAssignment updated = assignmentRepository.findByUserId(
                    data.seatedUser().getUserId()).orElseThrow();
            assertThat(updated.getLastHeartbeatAt()).isAfter(data.original().getLastHeartbeatAt());
            assertThat(updated.getSeatId()).isEqualTo(data.original().getSeatId());
            assertThat(updated.getStatus()).isEqualTo(data.original().getStatus());
            assertThat(updated.getWorkContent()).isEqualTo(data.original().getWorkContent());
            assertThat(updated.getStartedAt()).isEqualTo(data.original().getStartedAt());

            LocalDateTime successfulHeartbeat = updated.getLastHeartbeatAt();
            mockMvc.perform(post("/api/rooms/{roomId}/heartbeat", data.secondRoom().getRoomId())
                    .session(session(data.seatedUser())))
                    .andExpect(status().isConflict());
            assertThat(assignmentRepository.findByUserId(data.seatedUser().getUserId()))
                    .get().extracting(SeatAssignment::getLastHeartbeatAt)
                    .isEqualTo(successfulHeartbeat);

            mockMvc.perform(post("/api/rooms/{roomId}/heartbeat", data.firstRoom().getRoomId())
                    .session(session(data.unseatedUser())))
                    .andExpect(status().isConflict());
            assertThat(assignmentRepository.findByUserId(data.unseatedUser().getUserId())).isEmpty();

            mockMvc.perform(post("/api/rooms/{roomId}/heartbeat", data.firstRoom().getRoomId()))
                    .andExpect(status().isUnauthorized());
        } finally {
            cleanup(data);
        }
    }

    private TestData createData() {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        User seatedUser = userRepository.create(new User(
                null, "heartbeat_" + suffix, "password", "male_a"));
        User unseatedUser = userRepository.create(new User(
                null, "heartbeat_unseated_" + suffix, "password", "female_a"));
        Room firstRoom = createRoom("Heartbeat 1", seatedUser);
        Room secondRoom = createRoom("Heartbeat 2", seatedUser);
        Seat seat = seatRepository.findByRoomId(firstRoom.getRoomId()).getFirst();
        LocalDateTime startedAt = LocalDateTime.of(2026, 8, 12, 10, 0);
        LocalDateTime oldHeartbeat = LocalDateTime.of(2026, 8, 12, 10, 1);
        SeatAssignment original = new SeatAssignment(
                seat.getSeatId(), seatedUser.getUserId(), "break", "休憩中",
                startedAt, oldHeartbeat);
        assignmentRepository.create(original);
        return new TestData(firstRoom, secondRoom, seatedUser, unseatedUser, original);
    }

    private Room createRoom(String name, User creator) {
        Room room = roomRepository.create(new Room(
                null, null, "public", name, "focus", null, 1, creator.getUserId()));
        seatRepository.createAll(List.of(
                new Seat(null, room.getRoomId(), 1, 26.0, 31.6)));
        return room;
    }

    private MockHttpSession session(User user) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(UserSession.LOGIN_USER_ID, user.getUserId());
        return session;
    }

    private void cleanup(TestData data) {
        for (Room room : List.of(data.firstRoom(), data.secondRoom())) {
            seatRepository.findByRoomId(room.getRoomId()).forEach(seat ->
                    assignmentRepository.deleteBySeatId(seat.getSeatId()));
            seatRepository.deleteByRoomId(room.getRoomId());
            roomRepository.deleteById(room.getRoomId());
        }
        userRepository.deleteById(data.seatedUser().getUserId());
        userRepository.deleteById(data.unseatedUser().getUserId());
    }

    private record TestData(
            Room firstRoom,
            Room secondRoom,
            User seatedUser,
            User unseatedUser,
            SeatAssignment original) {}
}
