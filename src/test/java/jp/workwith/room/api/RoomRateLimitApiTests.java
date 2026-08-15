package jp.workwith.room.api;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import jp.workwith.realtime.RoomRealtimeNotifier;
import jp.workwith.room.Room;
import jp.workwith.room.RoomActionRateLimitService;
import jp.workwith.room.RoomNotFoundException;
import jp.workwith.room.RoomService;
import jp.workwith.user.UserSession;

@SpringBootTest
@AutoConfigureMockMvc
class RoomRateLimitApiTests {

    @Autowired private MockMvc mockMvc;
    @Autowired private RoomActionRateLimitService rateLimitService;
    @MockitoBean private RoomService roomService;
    @MockitoBean private RoomRealtimeNotifier realtimeNotifier;

    private final Room privateRoom = new Room(
            101L, "ABC234", "private", "Private", "focus", null, 10, 1L);
    private final Room publicRoom = new Room(
            201L, null, "public", "Public", "focus", null, 10, null);
    private final Room routedPublicRoom = new Room(
            202L, null, "public", "Public 2", "focus", null, 10, null);

    @BeforeEach
    void resetRateLimitsAndMocks() {
        rateLimitService.clear();
        clearInvocations(roomService, realtimeNotifier);
        when(roomService.createPrivateRoom(anyLong(), anyString(), anyString(), anyInt()))
                .thenReturn(privateRoom);
        when(roomService.joinPublicRoom(anyLong(), anyLong())).thenReturn(publicRoom);
        when(roomService.joinPrivateRoom(anyString(), anyLong())).thenReturn(privateRoom);
        when(roomService.findViewablePublicRoom(anyLong(), anyLong())).thenReturn(publicRoom);
    }

    @Test
    void allowsTwoCreatesThenReturns429WithoutAffectingAnotherUser() throws Exception {
        for (int attempt = 0; attempt < 2; attempt++) {
            performCreate(1L).andExpect(status().isCreated());
        }
        performCreate(1L)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.message").value(
                        "短時間に作成できる部屋数の上限に達しました。一定時間後に再度お試しください。"));
        performCreate(2L).andExpect(status().isCreated());

        verify(roomService, times(2))
                .createPrivateRoom(eq(1L), anyString(), anyString(), anyInt());
        verify(roomService).createPrivateRoom(eq(2L), anyString(), anyString(), anyInt());
    }

    @Test
    void allowsTenJoinAttemptsThenReturns429AndCountsInvalidCodes() throws Exception {
        when(roomService.joinPrivateRoom(anyString(), eq(1L)))
                .thenThrow(new RoomNotFoundException());

        for (int attempt = 0; attempt < 10; attempt++) {
            performPrivateJoin(1L, "WRONG" + attempt)
                    .andExpect(status().isNotFound());
        }
        performPrivateJoin(1L, "WRONG10")
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.message").value(
                        "短時間の入室回数が多いため、一時的に入室を制限しています。一定時間後に再度お試しください。"));
        performPrivateJoin(2L, "ABC234").andExpect(status().isOk());

        verify(roomService, times(10)).joinPrivateRoom(anyString(), eq(1L));
    }

    @Test
    void createAutoSeatingAndPublicAutoGenerationDoNotDoubleCount() throws Exception {
        performCreate(1L).andExpect(status().isCreated());
        performCreate(1L).andExpect(status().isCreated());

        when(roomService.joinPublicRoom(201L, 1L)).thenReturn(routedPublicRoom);
        for (int attempt = 0; attempt < 10; attempt++) {
            performPublicJoin(1L, 201L)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.roomId").value(202L));
        }
        performPublicJoin(1L, 201L).andExpect(status().isTooManyRequests());

        verify(roomService, times(2))
                .createPrivateRoom(eq(1L), anyString(), anyString(), anyInt());
        verify(roomService, times(10)).joinPublicRoom(201L, 1L);
    }

    @Test
    void viewingOtherPublicRoomsDoesNotConsumeJoinLimit() throws Exception {
        for (int view = 0; view < 12; view++) {
            mockMvc.perform(get("/api/rooms/public/viewable/{roomId}", 201L)
                    .session(session(1L)))
                    .andExpect(status().isOk());
        }
        for (int attempt = 0; attempt < 10; attempt++) {
            performPublicJoin(1L, 201L).andExpect(status().isOk());
        }
        performPublicJoin(1L, 201L).andExpect(status().isTooManyRequests());
    }

    private org.springframework.test.web.servlet.ResultActions performCreate(long userId)
            throws Exception {
        return mockMvc.perform(post("/api/rooms")
                .session(session(userId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"roomName\":\"Test\",\"theme\":\"focus\",\"maxSeats\":10}"));
    }

    private org.springframework.test.web.servlet.ResultActions performPublicJoin(
            long userId, long roomId) throws Exception {
        return mockMvc.perform(post("/api/rooms/{roomId}/join", roomId)
                .session(session(userId)));
    }

    private org.springframework.test.web.servlet.ResultActions performPrivateJoin(
            long userId, String roomCode) throws Exception {
        return mockMvc.perform(post("/api/rooms/private/join")
                .session(session(userId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"roomCode\":\"" + roomCode + "\"}"));
    }

    private MockHttpSession session(long userId) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(UserSession.LOGIN_USER_ID, userId);
        return session;
    }
}
