package jp.workwith.room.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jp.workwith.room.Room;
import jp.workwith.room.RoomRepository;
import jp.workwith.room.RoomActionRateLimitService;
import jp.workwith.seat.SeatRepository;
import jp.workwith.seat.Seat;
import jp.workwith.seatassignment.SeatAssignment;
import jp.workwith.seatassignment.SeatAssignmentRepository;
import jp.workwith.user.User;
import jp.workwith.user.UserRepository;
import jp.workwith.user.UserService;
import jp.workwith.user.UserSession;

@SpringBootTest
@AutoConfigureMockMvc
class RoomApiTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private SeatRepository seatRepository;

    @Autowired
    private SeatAssignmentRepository seatAssignmentRepository;

    @Autowired
    private RoomActionRateLimitService roomActionRateLimitService;

    @BeforeEach
    void resetRoomRateLimits() {
        roomActionRateLimitService.clear();
    }

    @Test
    void requiresLoginForRoomApis() throws Exception {
        mockMvc.perform(post("/api/rooms")
                .contentType(MediaType.APPLICATION_JSON)
                .content(roomJson("Java勉強部屋", "focus", 10)))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/rooms/public"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/rooms/code/A7K9PX"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createsPrivateRoomsFromSessionAndRetrievesThem() throws Exception {
        String username = "room_api_" + UUID.randomUUID().toString().replace("-", "");
        User user = userService.register(username, "room-api-password");
        User secondUser = userService.register(username + "_second", "room-api-password");
        MockHttpSession session = loggedInSession(user);
        MockHttpSession secondSession = loggedInSession(secondUser);
        List<Long> createdRoomIds = new ArrayList<>();

        try {
            MvcResult namedResult = mockMvc.perform(post("/api/rooms")
                    .session(session)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(roomJson("Java勉強部屋", "focus", 10)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.roomType").value("private"))
                    .andExpect(jsonPath("$.roomCode").value(
                            org.hamcrest.Matchers.matchesPattern("[A-HJ-NP-Z2-9]{6}")))
                    .andExpect(jsonPath("$.roomName").value("Java勉強部屋"))
                    .andExpect(jsonPath("$.theme").value("focus"))
                    .andExpect(jsonPath("$.maxSeats").value(10))
                    .andExpect(jsonPath("$.createdBy").value(user.getUserId()))
                    .andReturn();
            long namedRoomId = roomIdFrom(namedResult);
            String namedRoomCode = roomCodeFrom(namedResult);
            createdRoomIds.add(namedRoomId);

            Room savedRoom = roomRepository.findById(namedRoomId).orElseThrow();
            assertThat(savedRoom.getRoomType()).isEqualTo("private");
            assertThat(savedRoom.getCreatedBy()).isEqualTo(user.getUserId());
            assertThat(savedRoom.getRoomCode()).isEqualTo(namedRoomCode);
            assertThat(roomRepository.findByRoomCode(namedRoomCode))
                    .get()
                    .extracting(Room::getRoomId)
                    .isEqualTo(savedRoom.getRoomId());
            assertThat(seatAssignmentRepository.findByUserId(user.getUserId())).isPresent();

            MvcResult automaticNameResult = mockMvc.perform(post("/api/rooms")
                    .session(secondSession)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(roomJson("", "night", 5)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.roomName").value(secondUser.getUsername() + "の部屋"))
                    .andExpect(jsonPath("$.backgroundUrl")
                            .value("work-space-pic/room-midnight-task.PNG"))
                    .andReturn();
            long automaticNameRoomId = roomIdFrom(automaticNameResult);
            String automaticNameRoomCode = roomCodeFrom(automaticNameResult);
            createdRoomIds.add(automaticNameRoomId);
            assertThat(automaticNameRoomCode).isNotEqualTo(namedRoomCode);

            mockMvc.perform(get("/api/rooms/code/{roomCode}", namedRoomCode)
                    .session(session))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.roomId").value(namedRoomId))
                    .andExpect(jsonPath("$.roomCode").value(namedRoomCode));

            // 入力時は小文字でも、サーバー側で大文字へ正規化します。
            mockMvc.perform(get("/api/rooms/code/{roomCode}", namedRoomCode.toLowerCase())
                    .session(session))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.roomId").value(namedRoomId));

            mockMvc.perform(get("/api/rooms/code/ZZZZZZ").session(session))
                    .andExpect(status().isNotFound());

            mockMvc.perform(get("/api/rooms/{roomId}", namedRoomId).session(session))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.roomId").value(namedRoomId));

            mockMvc.perform(get("/api/rooms/{roomId}", namedRoomId).session(secondSession))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.roomCode").doesNotExist());
            mockMvc.perform(get("/api/rooms/code/{roomCode}", namedRoomCode)
                    .session(secondSession))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.roomCode").doesNotExist());

            mockMvc.perform(get("/api/rooms/mine").session(session))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[?(@.roomId == " + namedRoomId + ")]").exists())
                    .andExpect(jsonPath("$[?(@.roomId == " + automaticNameRoomId + ")]").doesNotExist());

            mockMvc.perform(get("/api/rooms/mine").session(secondSession))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[?(@.roomId == " + automaticNameRoomId + ")]").exists())
                    .andExpect(jsonPath("$[?(@.roomId == " + namedRoomId + ")]").doesNotExist());

            mockMvc.perform(get("/api/rooms/public").session(session))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[?(@.roomId == " + namedRoomId + ")]").doesNotExist())
                    .andExpect(jsonPath("$[?(@.roomId == " + automaticNameRoomId + ")]").doesNotExist());

            mockMvc.perform(get("/api/rooms/{roomId}", Long.MAX_VALUE).session(session))
                    .andExpect(status().isNotFound());

            roomActionRateLimitService.clear();
            mockMvc.perform(post("/api/rooms")
                    .session(session)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(roomJson("invalid", "focus", 11)))
                    .andExpect(status().isBadRequest());

            mockMvc.perform(post("/api/rooms")
                    .session(session)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(roomJson("invalid", "default", 10)))
                    .andExpect(status().isBadRequest());
        } finally {
            createdRoomIds.forEach(roomId -> {
                seatRepository.findByRoomId(roomId).forEach(seat ->
                        seatAssignmentRepository.deleteBySeatId(seat.getSeatId()));
                seatRepository.deleteByRoomId(roomId);
                roomRepository.deleteById(roomId);
            });
            userRepository.deleteById(user.getUserId());
            userRepository.deleteById(secondUser.getUserId());
        }
    }

    @Test
    void onlyPrivateRoomCreatorCanUpdateTheme() throws Exception {
        String username = "theme_api_" + UUID.randomUUID().toString().replace("-", "");
        User creator = userService.register(username, "theme-api-password");
        User otherUser = userService.register(username + "_other", "theme-api-password");
        long roomId = roomIdFrom(mockMvc.perform(post("/api/rooms")
                .session(loggedInSession(creator))
                .contentType(MediaType.APPLICATION_JSON)
                .content(roomJson("Theme room", "focus", 3)))
                .andExpect(status().isCreated())
                .andReturn());
        Room publicRoom = roomRepository.create(new Room(
                null, null, "public", "Public theme room", "focus",
                "work-space-pic/room-forcus-task.png", 10, null));

        try {
            mockMvc.perform(patch("/api/rooms/{roomId}/theme", roomId)
                    .session(loggedInSession(creator))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"theme\":\"night\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.theme").value("night"));
            assertThat(roomRepository.findById(roomId).orElseThrow().getTheme())
                    .isEqualTo("night");

            mockMvc.perform(patch("/api/rooms/{roomId}/theme", roomId)
                    .session(loggedInSession(otherUser))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"theme\":\"casual\"}"))
                    .andExpect(status().isForbidden());

            mockMvc.perform(patch("/api/rooms/{roomId}/theme", publicRoom.getRoomId())
                    .session(loggedInSession(creator))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"theme\":\"casual\"}"))
                    .andExpect(status().isForbidden());

            mockMvc.perform(patch("/api/rooms/{roomId}/theme", roomId)
                    .session(loggedInSession(creator))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"theme\":\"invalid\"}"))
                    .andExpect(status().isBadRequest());

            mockMvc.perform(patch("/api/rooms/{roomId}/theme", Long.MAX_VALUE)
                    .session(loggedInSession(creator))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"theme\":\"focus\"}"))
                    .andExpect(status().isNotFound());
        } finally {
            seatRepository.findByRoomId(roomId).forEach(seat ->
                    seatAssignmentRepository.deleteBySeatId(seat.getSeatId()));
            seatRepository.deleteByRoomId(roomId);
            roomRepository.deleteById(roomId);
            roomRepository.deleteById(publicRoom.getRoomId());
            userRepository.deleteById(creator.getUserId());
            userRepository.deleteById(otherUser.getUserId());
        }
    }

    @Test
    void privateRoomParticipantCanReadDetailsButUnrelatedUserCannot() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        User creator = userService.register("private_owner_" + suffix, "private-password");
        User participant = userService.register("private_member_" + suffix, "private-password");
        User unrelated = userService.register("private_other_" + suffix, "private-password");
        Room room = roomRepository.create(new Room(
                null, "Z" + suffix.substring(0, 5).toUpperCase(), "private",
                "Protected room", "focus", null, 2, creator.getUserId()));
        seatRepository.create(new Seat(null, room.getRoomId(), 1, 26.0, 31.6));
        Seat seat = seatRepository.findByRoomId(room.getRoomId()).getFirst();
        LocalDateTime now = LocalDateTime.now();
        seatAssignmentRepository.create(new SeatAssignment(
                seat.getSeatId(), participant.getUserId(), "working", null, now, now));

        try {
            mockMvc.perform(get("/api/rooms/{roomId}", room.getRoomId())
                    .session(loggedInSession(participant)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.roomCode").value(room.getRoomCode()));
            mockMvc.perform(get("/api/rooms/{roomId}", room.getRoomId())
                    .session(loggedInSession(unrelated)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.roomCode").doesNotExist());
        } finally {
            seatAssignmentRepository.deleteBySeatId(seat.getSeatId());
            seatRepository.deleteByRoomId(room.getRoomId());
            roomRepository.deleteById(room.getRoomId());
            userRepository.deleteById(creator.getUserId());
            userRepository.deleteById(participant.getUserId());
            userRepository.deleteById(unrelated.getUserId());
        }
    }

    private MockHttpSession loggedInSession(User user) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(UserSession.LOGIN_USER_ID, user.getUserId());
        return session;
    }

    private long roomIdFrom(MvcResult result) throws Exception {
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        return response.get("roomId").longValue();
    }

    private String roomCodeFrom(MvcResult result) throws Exception {
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        return response.get("roomCode").textValue();
    }

    private String roomJson(String roomName, String theme, int maxSeats) {
        return """
                {
                  "roomName": "%s",
                  "theme": "%s",
                  "maxSeats": %d
                }
                """.formatted(roomName, theme, maxSeats);
    }
}
