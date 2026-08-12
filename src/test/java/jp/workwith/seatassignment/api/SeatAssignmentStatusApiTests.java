package jp.workwith.seatassignment.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
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
class SeatAssignmentStatusApiTests {

    @Autowired private MockMvc mockMvc;
    @Autowired private RoomRepository roomRepository;
    @Autowired private SeatRepository seatRepository;
    @Autowired private SeatAssignmentRepository assignmentRepository;
    @Autowired private UserRepository userRepository;

    @Test
    void updatesOnlyTheSessionUsersStatus() throws Exception {
        TestData data = createData();
        try {
            mockMvc.perform(patch("/api/rooms/{roomId}/seat-assignments/me/status",
                    data.room().getRoomId())
                    .session(session(data.user()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"status\":\"break\",\"userId\":"
                            + data.other().getUserId() + "}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.userId").value(data.user().getUserId()))
                    .andExpect(jsonPath("$.username").value(data.user().getUsername()))
                    .andExpect(jsonPath("$.avatarType").value("male_a"))
                    .andExpect(jsonPath("$.status").value("break"))
                    .andExpect(jsonPath("$.workContent").value("API test"));

            SeatAssignment updated = assignmentRepository.findByUserId(
                    data.user().getUserId()).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo("break");
            assertThat(updated.getSeatId()).isEqualTo(data.assignment().getSeatId());
            assertThat(updated.getStartedAt()).isEqualTo(data.assignment().getStartedAt());
            assertThat(updated.getLastHeartbeatAt())
                    .isEqualTo(data.assignment().getLastHeartbeatAt());
            assertThat(assignmentRepository.findByUserId(data.other().getUserId()))
                    .get().extracting(SeatAssignment::getStatus).isEqualTo("working");
        } finally {
            cleanup(data);
        }
    }

    @Test
    void rejectsInvalidUnauthenticatedAndUnseatedRequests() throws Exception {
        TestData data = createData();
        try {
            for (String body : List.of("{\"status\":\"away\"}", "{\"status\":\"\"}", "{}")) {
                mockMvc.perform(patch("/api/rooms/{roomId}/seat-assignments/me/status",
                        data.room().getRoomId())
                        .session(session(data.user()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                        .andExpect(status().isBadRequest());
            }
            mockMvc.perform(patch("/api/rooms/{roomId}/seat-assignments/me/status",
                    data.room().getRoomId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"status\":\"break\"}"))
                    .andExpect(status().isUnauthorized());

            assignmentRepository.deleteBySeatId(data.otherAssignment().getSeatId());
            mockMvc.perform(patch("/api/rooms/{roomId}/seat-assignments/me/status",
                    data.room().getRoomId())
                    .session(session(data.other()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"status\":\"break\"}"))
                    .andExpect(status().isConflict());
            assertThat(assignmentRepository.findByUserId(data.user().getUserId()))
                    .get().extracting(SeatAssignment::getStatus).isEqualTo("working");
        } finally {
            cleanup(data);
        }
    }

    private TestData createData() {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        User user = userRepository.create(new User(
                null, "status_api_" + suffix, "password", "male_a"));
        User other = userRepository.create(new User(
                null, "status_other_" + suffix, "password", "female_a"));
        Room room = roomRepository.create(new Room(
                null, null, "public", "Status API", "focus", null, 2, user.getUserId()));
        seatRepository.createAll(List.of(
                new Seat(null, room.getRoomId(), 1, 26.0, 31.6),
                new Seat(null, room.getRoomId(), 2, 37.0, 35.0)));
        List<Seat> seats = seatRepository.findByRoomId(room.getRoomId());
        LocalDateTime now = LocalDateTime.of(2026, 8, 12, 17, 30);
        SeatAssignment assignment = new SeatAssignment(
                seats.get(0).getSeatId(), user.getUserId(), "working", "API test", now, now);
        SeatAssignment otherAssignment = new SeatAssignment(
                seats.get(1).getSeatId(), other.getUserId(), "working", null, now, now);
        assignmentRepository.create(assignment);
        assignmentRepository.create(otherAssignment);
        return new TestData(room, user, other, assignment, otherAssignment);
    }

    private MockHttpSession session(User user) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(UserSession.LOGIN_USER_ID, user.getUserId());
        return session;
    }

    private void cleanup(TestData data) {
        seatRepository.findByRoomId(data.room().getRoomId()).forEach(seat ->
                assignmentRepository.deleteBySeatId(seat.getSeatId()));
        seatRepository.deleteByRoomId(data.room().getRoomId());
        roomRepository.deleteById(data.room().getRoomId());
        userRepository.deleteById(data.user().getUserId());
        userRepository.deleteById(data.other().getUserId());
    }

    private record TestData(
            Room room,
            User user,
            User other,
            SeatAssignment assignment,
            SeatAssignment otherAssignment) {}
}
