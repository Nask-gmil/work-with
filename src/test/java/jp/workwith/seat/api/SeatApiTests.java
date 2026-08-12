package jp.workwith.seat.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.ObjectMapper;

import jp.workwith.room.RoomRepository;
import jp.workwith.seat.SeatRepository;
import jp.workwith.seatassignment.SeatAssignmentRepository;
import jp.workwith.user.User;
import jp.workwith.user.UserRepository;
import jp.workwith.user.UserService;
import jp.workwith.user.UserSession;

@SpringBootTest
@AutoConfigureMockMvc
class SeatApiTests {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserService userService;
    @Autowired private UserRepository userRepository;
    @Autowired private RoomRepository roomRepository;
    @Autowired private SeatRepository seatRepository;
    @Autowired private SeatAssignmentRepository seatAssignmentRepository;

    @Test
    void createsMaxSeatsAndReturnsThemInOrder() throws Exception {
        User user = userService.register(
                "seat_api_" + UUID.randomUUID().toString().replace("-", ""),
                "seat-api-password");
        User unrelatedUser = userService.register(
                "seat_api_other_" + UUID.randomUUID().toString().replace("-", ""),
                "seat-api-password");
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(UserSession.LOGIN_USER_ID, user.getUserId());
        Long roomId = null;

        try {
            MvcResult result = mockMvc.perform(post("/api/rooms")
                    .session(session)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"roomName":"Seat API", "theme":"focus", "maxSeats":10}
                            """))
                    .andExpect(status().isCreated())
                    .andReturn();
            roomId = objectMapper.readTree(result.getResponse().getContentAsString())
                    .get("roomId").longValue();

            assertThat(seatRepository.findByRoomId(roomId)).hasSize(10);
            mockMvc.perform(get("/api/rooms/{roomId}/seats", roomId).session(session))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(10))
                    .andExpect(jsonPath("$[0].seatNumber").value(1))
                    .andExpect(jsonPath("$[0].posX").value(26.0))
                    .andExpect(jsonPath("$[9].seatNumber").value(10))
                    .andExpect(jsonPath("$[9].posY").value(69.0));
            MockHttpSession unrelatedSession = new MockHttpSession();
            unrelatedSession.setAttribute(UserSession.LOGIN_USER_ID, unrelatedUser.getUserId());
            mockMvc.perform(get("/api/rooms/{roomId}/seats", roomId)
                    .session(unrelatedSession))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$").isMap());
            mockMvc.perform(get("/api/rooms/{roomId}/seats", Long.MAX_VALUE).session(session))
                    .andExpect(status().isNotFound());
            mockMvc.perform(get("/api/rooms/{roomId}/seats", roomId))
                    .andExpect(status().isUnauthorized());
        } finally {
            if (roomId != null) {
                seatRepository.findByRoomId(roomId).forEach(seat ->
                        seatAssignmentRepository.deleteBySeatId(seat.getSeatId()));
                seatRepository.deleteByRoomId(roomId);
                roomRepository.deleteById(roomId);
            }
            userRepository.deleteById(user.getUserId());
            userRepository.deleteById(unrelatedUser.getUserId());
        }
    }
}
