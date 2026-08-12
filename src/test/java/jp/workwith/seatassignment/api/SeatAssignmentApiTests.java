package jp.workwith.seatassignment.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
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
class SeatAssignmentApiTests {

    @Autowired private MockMvc mockMvc;
    @Autowired private SeatAssignmentRepository seatAssignmentRepository;
    @Autowired private SeatRepository seatRepository;
    @Autowired private RoomRepository roomRepository;
    @Autowired private UserRepository userRepository;

    @Test
    void returnsCurrentAssignmentsAndHandlesEmptyMissingAndUnauthorizedRequests() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        User user = userRepository.create(new User(
                null, "assignment_api_" + suffix, "password", null));
        Room room = roomRepository.create(new Room(
                null, "P" + suffix.substring(0, 5).toUpperCase(), "private",
                "Assignment API", "focus", null, 1, user.getUserId()));
        seatRepository.create(new Seat(null, room.getRoomId(), 1, 26.0, 31.6));
        Seat seat = seatRepository.findByRoomId(room.getRoomId()).getFirst();
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(UserSession.LOGIN_USER_ID, user.getUserId());

        try {
            mockMvc.perform(get("/api/rooms/{roomId}/seat-assignments", room.getRoomId())
                    .session(session))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isEmpty());

            LocalDateTime startedAt = LocalDateTime.of(2026, 8, 12, 14, 30);
            seatAssignmentRepository.create(new SeatAssignment(
                    seat.getSeatId(), user.getUserId(), "working", "Java学習",
                    startedAt, startedAt.plusMinutes(1)));

            mockMvc.perform(get("/api/rooms/{roomId}/seat-assignments", room.getRoomId())
                    .session(session))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].seatId").value(seat.getSeatId()))
                    .andExpect(jsonPath("$[0].userId").value(user.getUserId()))
                    .andExpect(jsonPath("$[0].status").value("working"))
                    .andExpect(jsonPath("$[0].workContent").value("Java学習"))
                    .andExpect(jsonPath("$[0].startedAt").value("2026-08-12T14:30:00"))
                    .andExpect(jsonPath("$[0].lastHeartbeatAt").value("2026-08-12T14:31:00"));

            mockMvc.perform(get("/api/rooms/{roomId}/seat-assignments", Long.MAX_VALUE)
                    .session(session))
                    .andExpect(status().isNotFound());
            mockMvc.perform(get("/api/rooms/{roomId}/seat-assignments", room.getRoomId()))
                    .andExpect(status().isUnauthorized());
        } finally {
            seatAssignmentRepository.deleteBySeatId(seat.getSeatId());
            seatRepository.deleteByRoomId(room.getRoomId());
            roomRepository.deleteById(room.getRoomId());
            userRepository.deleteById(user.getUserId());
        }
    }
}
