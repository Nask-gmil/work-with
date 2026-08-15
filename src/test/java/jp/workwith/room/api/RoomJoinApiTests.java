package jp.workwith.room.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import jp.workwith.room.Room;
import jp.workwith.room.RoomRepository;
import jp.workwith.room.RoomActionRateLimitService;
import jp.workwith.realtime.RoomRealtimeNotifier;
import jp.workwith.seat.Seat;
import jp.workwith.seat.SeatRepository;
import jp.workwith.seatassignment.SeatAssignment;
import jp.workwith.seatassignment.SeatAssignmentRepository;
import jp.workwith.user.User;
import jp.workwith.user.UserRepository;
import jp.workwith.user.UserSession;

@SpringBootTest
@AutoConfigureMockMvc
class RoomJoinApiTests {

    @Autowired private MockMvc mockMvc;
    @Autowired private RoomRepository roomRepository;
    @Autowired private SeatRepository seatRepository;
    @Autowired private SeatAssignmentRepository seatAssignmentRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private RoomActionRateLimitService roomActionRateLimitService;
    @MockitoBean private RoomRealtimeNotifier realtimeNotifier;

    @BeforeEach
    void resetRoomRateLimits() {
        roomActionRateLimitService.clear();
    }

    @Test
    void joinsPublicAndPrivateRoomsAndRejectsUnauthorizedInvalidAndConflictingJoins()
            throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        List<User> users = createUsers(suffix, 5);
        Room publicRoom = createRoom(null, "public", "Public", "focus", 3, users.get(0));
        Room privateRoom = createRoom("J" + suffix.substring(0, 5).toUpperCase(),
                "private", "Private", "night", 2, users.get(0));
        Room fullRoom = createRoom(null, "public", "Full", "casual", 1, users.get(0));
        List<Room> rooms = List.of(publicRoom, privateRoom, fullRoom);

        try {
            // 1番席を使用中にして、public参加者が2番席へ割り当てられることを確認します。
            Seat publicFirstSeat = seats(publicRoom).get(0);
            createAssignment(publicFirstSeat, users.get(0));

            mockMvc.perform(post("/api/rooms/{roomId}/join", publicRoom.getRoomId())
                    .session(session(users.get(1))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.roomId").value(publicRoom.getRoomId()))
                    .andExpect(jsonPath("$.roomType").value("public"));
            verify(realtimeNotifier).notifyParticipantsChanged(publicRoom.getRoomId());
            SeatAssignment publicAssignment = seatAssignmentRepository
                    .findByUserId(users.get(1).getUserId()).orElseThrow();
            assertThat(seatRepository.findById(publicAssignment.getSeatId()).orElseThrow()
                    .getSeatNumber()).isEqualTo(2);

            // 同じ部屋への再入室ではassignmentを増やしません。
            mockMvc.perform(post("/api/rooms/{roomId}/join", publicRoom.getRoomId())
                    .session(session(users.get(1))))
                    .andExpect(status().isOk());
            assertThat(seatAssignmentRepository.findByRoomId(publicRoom.getRoomId())).hasSize(2);

            mockMvc.perform(post("/api/rooms/private/join")
                    .session(session(users.get(2)))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"roomCode\":\"" + privateRoom.getRoomCode().toLowerCase() + "\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.roomId").value(privateRoom.getRoomId()))
                    .andExpect(jsonPath("$.roomType").value("private"));
            assertThat(seatAssignmentRepository.findByUserId(users.get(2).getUserId())).isPresent();
            verify(realtimeNotifier).notifyParticipantsChanged(privateRoom.getRoomId());

            clearInvocations(realtimeNotifier);

            // public用APIへprivateのroomIdを渡しても参加できません。
            mockMvc.perform(post("/api/rooms/{roomId}/join", privateRoom.getRoomId())
                    .session(session(users.get(3))))
                    .andExpect(status().isNotFound());
            assertThat(seatAssignmentRepository.findByUserId(users.get(3).getUserId())).isEmpty();

            // 別部屋に着席中のユーザーは移動せず409になります。
            mockMvc.perform(post("/api/rooms/private/join")
                    .session(session(users.get(1)))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"roomCode\":\"" + privateRoom.getRoomCode() + "\"}"))
                    .andExpect(status().isConflict());
            assertThat(seatAssignmentRepository.findByUserId(users.get(1).getUserId()))
                    .get().extracting(SeatAssignment::getSeatId)
                    .isEqualTo(publicAssignment.getSeatId());

            createAssignment(seats(fullRoom).getFirst(), users.get(3));
            mockMvc.perform(post("/api/rooms/{roomId}/join", fullRoom.getRoomId())
                    .session(session(users.get(4))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.roomType").value("public"))
                    .andExpect(jsonPath("$.theme").value("casual"));
            long routedRoomId = seatRepository.findById(seatAssignmentRepository
                    .findByUserId(users.get(4).getUserId()).orElseThrow().getSeatId())
                    .orElseThrow().getRoomId();
            assertThat(routedRoomId).isNotEqualTo(fullRoom.getRoomId());
            verify(realtimeNotifier).notifyParticipantsChanged(routedRoomId);
            clearInvocations(realtimeNotifier);

            mockMvc.perform(post("/api/rooms/private/join")
                    .session(session(users.get(4)))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"roomCode\":\"ZZZZZZ\"}"))
                    .andExpect(status().isNotFound());

            mockMvc.perform(post("/api/rooms/{roomId}/join", publicRoom.getRoomId()))
                    .andExpect(status().isUnauthorized());
            mockMvc.perform(post("/api/rooms/private/join")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"roomCode\":\"" + privateRoom.getRoomCode() + "\"}"))
                    .andExpect(status().isUnauthorized());
            verify(realtimeNotifier, never()).notifyParticipantsChanged(
                    org.mockito.ArgumentMatchers.anyLong());
        } finally {
            cleanup(rooms, users);
        }
    }

    private List<User> createUsers(String suffix, int count) {
        List<User> users = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            users.add(userRepository.create(new User(
                    null, "join_api_" + index + "_" + suffix, "password", "male_a")));
        }
        return users;
    }

    private Room createRoom(
            String roomCode, String type, String name, String theme, int maxSeats, User creator) {
        Room room = roomRepository.create(new Room(
                null, roomCode, type, name, theme, null, maxSeats, creator.getUserId()));
        List<Seat> seats = new ArrayList<>();
        for (int number = 1; number <= maxSeats; number++) {
            seats.add(new Seat(null, room.getRoomId(), number,
                    20.0 + number, 30.0 + number));
        }
        seatRepository.createAll(seats);
        return room;
    }

    private List<Seat> seats(Room room) {
        return seatRepository.findByRoomId(room.getRoomId());
    }

    private void createAssignment(Seat seat, User user) {
        LocalDateTime now = LocalDateTime.of(2026, 8, 12, 16, 0);
        seatAssignmentRepository.create(new SeatAssignment(
                seat.getSeatId(), user.getUserId(), "working", null, now, now));
    }

    private MockHttpSession session(User user) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(UserSession.LOGIN_USER_ID, user.getUserId());
        return session;
    }

    private void cleanup(List<Room> rooms, List<User> users) {
        users.forEach(user -> seatAssignmentRepository.findByUserId(user.getUserId())
                .ifPresent(assignment ->
                        seatAssignmentRepository.deleteBySeatId(assignment.getSeatId())));
        rooms.forEach(room -> {
            seats(room).forEach(seat -> seatAssignmentRepository.deleteBySeatId(seat.getSeatId()));
            seatRepository.deleteByRoomId(room.getRoomId());
            roomRepository.deleteById(room.getRoomId());
        });
        users.forEach(user -> userRepository.deleteById(user.getUserId()));
    }
}
