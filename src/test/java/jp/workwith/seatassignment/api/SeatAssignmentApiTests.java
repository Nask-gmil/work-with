package jp.workwith.seatassignment.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
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
                null, "assignment_api_" + suffix, "password-hash", "male_a"));
        User otherUser = userRepository.create(new User(
                null, "assignment_other_" + suffix, "other-password-hash", "female_b"));
        Room room = roomRepository.create(new Room(
                null, "P" + suffix.substring(0, 5).toUpperCase(), "private",
                "Assignment API", "focus", null, 1, user.getUserId()));
        seatRepository.create(new Seat(null, room.getRoomId(), 1, 26.0, 31.6));
        Seat seat = seatRepository.findByRoomId(room.getRoomId()).getFirst();
        Room otherRoom = roomRepository.create(new Room(
                null, null, "public", "Other room", "casual", null, 1, otherUser.getUserId()));
        seatRepository.create(new Seat(null, otherRoom.getRoomId(), 1, 26.0, 31.6));
        Seat otherSeat = seatRepository.findByRoomId(otherRoom.getRoomId()).getFirst();
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(UserSession.LOGIN_USER_ID, user.getUserId());
        MockHttpSession otherSession = new MockHttpSession();
        otherSession.setAttribute(UserSession.LOGIN_USER_ID, otherUser.getUserId());

        try {
            mockMvc.perform(get("/api/rooms/{roomId}/seat-assignments", room.getRoomId())
                    .session(session))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isEmpty());

            LocalDateTime startedAt = LocalDateTime.of(2026, 8, 12, 14, 30);
            String startedAtWithOffset = startedAt.atZone(ZoneId.systemDefault())
                    .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
            String heartbeatAtWithOffset = startedAt.plusMinutes(1)
                    .atZone(ZoneId.systemDefault())
                    .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
            seatAssignmentRepository.create(new SeatAssignment(
                    seat.getSeatId(), user.getUserId(), "working", "Java学習",
                    startedAt, startedAt.plusMinutes(1)));
            seatAssignmentRepository.create(new SeatAssignment(
                    otherSeat.getSeatId(), otherUser.getUserId(), "break", null,
                    startedAt, startedAt));

            mockMvc.perform(get("/api/rooms/{roomId}/seat-assignments", room.getRoomId())
                    .session(session))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].seatId").value(seat.getSeatId()))
                    .andExpect(jsonPath("$[0].userId").value(user.getUserId()))
                    .andExpect(jsonPath("$[0].username").value(user.getUsername()))
                    .andExpect(jsonPath("$[0].avatarType").value("male_a"))
                    .andExpect(jsonPath("$[0].status").value("working"))
                    .andExpect(jsonPath("$[0].workContent").value("Java学習"))
                    .andExpect(jsonPath("$[0].startedAt").value(startedAtWithOffset))
                    .andExpect(jsonPath("$[0].lastHeartbeatAt").value(heartbeatAtWithOffset))
                    .andExpect(jsonPath("$[0].password").doesNotExist());

            mockMvc.perform(get("/api/rooms/{roomId}/seat-assignments", room.getRoomId())
                    .session(otherSession))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$[0]").doesNotExist());

            mockMvc.perform(get("/api/rooms/{roomId}/seat-assignments", Long.MAX_VALUE)
                    .session(session))
                    .andExpect(status().isNotFound());
            mockMvc.perform(get("/api/rooms/{roomId}/seat-assignments", room.getRoomId()))
                    .andExpect(status().isUnauthorized());
        } finally {
            seatAssignmentRepository.deleteBySeatId(seat.getSeatId());
            seatAssignmentRepository.deleteBySeatId(otherSeat.getSeatId());
            seatRepository.deleteByRoomId(room.getRoomId());
            seatRepository.deleteByRoomId(otherRoom.getRoomId());
            roomRepository.deleteById(room.getRoomId());
            roomRepository.deleteById(otherRoom.getRoomId());
            userRepository.deleteById(user.getUserId());
            userRepository.deleteById(otherUser.getUserId());
        }
    }
}
