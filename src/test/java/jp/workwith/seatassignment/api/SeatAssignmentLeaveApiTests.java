package jp.workwith.seatassignment.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
class SeatAssignmentLeaveApiTests {

    @Autowired private MockMvc mockMvc;
    @Autowired private RoomRepository roomRepository;
    @Autowired private SeatRepository seatRepository;
    @Autowired private SeatAssignmentRepository seatAssignmentRepository;
    @Autowired private UserRepository userRepository;

    @Test
    void leavesAsTheSessionUserAndCanThenJoinAnotherRoom() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        User user = userRepository.create(new User(
                null, "leave_api_" + suffix, "password", "male_a"));
        User otherUser = userRepository.create(new User(
                null, "leave_other_" + suffix, "password", "female_a"));
        Room firstRoom = createPublicRoom("First", user);
        Room secondRoom = createPublicRoom("Second", user);
        List<Seat> firstSeats = seatRepository.findByRoomId(firstRoom.getRoomId());
        SeatAssignment userAssignment = assignment(firstSeats.get(0), user);
        SeatAssignment otherAssignment = assignment(firstSeats.get(1), otherUser);
        seatAssignmentRepository.create(userAssignment);
        seatAssignmentRepository.create(otherAssignment);
        MockHttpSession session = session(user);

        try {
            mockMvc.perform(delete("/api/rooms/{roomId}/seat-assignments/me",
                    secondRoom.getRoomId()).session(session))
                    .andExpect(status().isConflict());
            assertThat(seatAssignmentRepository.findByUserId(user.getUserId())).isPresent();

            // 本文のuserIdは無視され、HttpSessionの本人だけが退席します。
            mockMvc.perform(delete("/api/rooms/{roomId}/seat-assignments/me",
                    firstRoom.getRoomId())
                    .session(session)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"userId\":" + otherUser.getUserId() + "}"))
                    .andExpect(status().isNoContent());
            assertThat(seatAssignmentRepository.findByUserId(user.getUserId())).isEmpty();
            assertThat(seatAssignmentRepository.findByUserId(otherUser.getUserId())).isPresent();
            assertThat(seatRepository.findById(userAssignment.getSeatId())).isPresent();

            mockMvc.perform(delete("/api/rooms/{roomId}/seat-assignments/me",
                    firstRoom.getRoomId()).session(session))
                    .andExpect(status().isNoContent());

            mockMvc.perform(post("/api/rooms/{roomId}/join", secondRoom.getRoomId())
                    .session(session))
                    .andExpect(status().isOk());
            assertThat(seatAssignmentRepository.findByUserId(user.getUserId())).isPresent();

            mockMvc.perform(delete("/api/rooms/{roomId}/seat-assignments/me",
                    firstRoom.getRoomId()))
                    .andExpect(status().isUnauthorized());
        } finally {
            cleanup(firstRoom);
            cleanup(secondRoom);
            userRepository.deleteById(user.getUserId());
            userRepository.deleteById(otherUser.getUserId());
        }
    }

    private Room createPublicRoom(String name, User creator) {
        Room room = roomRepository.create(new Room(
                null, null, "public", name, "focus", null, 2, creator.getUserId()));
        seatRepository.createAll(List.of(
                new Seat(null, room.getRoomId(), 1, 26.0, 31.6),
                new Seat(null, room.getRoomId(), 2, 37.0, 35.0)));
        return room;
    }

    private SeatAssignment assignment(Seat seat, User user) {
        LocalDateTime now = LocalDateTime.of(2026, 8, 12, 17, 0);
        return new SeatAssignment(
                seat.getSeatId(), user.getUserId(), "working", null, now, now);
    }

    private MockHttpSession session(User user) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(UserSession.LOGIN_USER_ID, user.getUserId());
        return session;
    }

    private void cleanup(Room room) {
        seatRepository.findByRoomId(room.getRoomId()).forEach(seat ->
                seatAssignmentRepository.deleteBySeatId(seat.getSeatId()));
        seatRepository.deleteByRoomId(room.getRoomId());
        roomRepository.deleteById(room.getRoomId());
    }
}
