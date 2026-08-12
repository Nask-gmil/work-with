package jp.workwith.chat.api;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockHttpSession;

import jp.workwith.chat.ChatHistoryMessage;
import jp.workwith.realtime.RoomChatService;
import jp.workwith.seatassignment.SeatAssignmentNotFoundException;
import jp.workwith.seatassignment.SeatAssignmentRoomMismatchException;
import jp.workwith.user.UserSession;

@SpringBootTest
@AutoConfigureMockMvc
class ChatHistoryApiTests {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private RoomChatService roomChatService;

    @Test
    void returnsHistoryWithMessageIdForSeatedUser() throws Exception {
        when(roomChatService.findGlobalHistory(5L, 12L)).thenReturn(List.of(
                new ChatHistoryMessage(101L, 5L, 3L, "userA", null, "<b>Hello</b>",
                        LocalDateTime.of(2026, 8, 12, 19, 30))));

        mockMvc.perform(get("/api/rooms/{roomId}/chat-messages", 5L)
                        .session(authenticatedSession(12L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].messageId").value(101))
                .andExpect(jsonPath("$[0].username").value("userA"))
                .andExpect(jsonPath("$[0].content").value("<b>Hello</b>"));
    }

    @Test
    void returnsEmptyArrayWhenRoomHasNoMessages() throws Exception {
        when(roomChatService.findGlobalHistory(5L, 12L)).thenReturn(List.of());
        mockMvc.perform(get("/api/rooms/{roomId}/chat-messages", 5L)
                        .session(authenticatedSession(12L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void rejectsUnauthenticatedUnseatedAndDifferentRoomUsers() throws Exception {
        mockMvc.perform(get("/api/rooms/{roomId}/chat-messages", 5L))
                .andExpect(status().isUnauthorized());

        when(roomChatService.findGlobalHistory(5L, 12L))
                .thenThrow(new SeatAssignmentNotFoundException());
        mockMvc.perform(get("/api/rooms/{roomId}/chat-messages", 5L)
                        .session(authenticatedSession(12L)))
                .andExpect(status().isForbidden());

        when(roomChatService.findGlobalHistory(8L, 12L))
                .thenThrow(new SeatAssignmentRoomMismatchException());
        mockMvc.perform(get("/api/rooms/{roomId}/chat-messages", 8L)
                        .session(authenticatedSession(12L)))
                .andExpect(status().isForbidden());
    }

    @Test
    void returnsOnlyTheAuthenticatedUsersPrivateConversation() throws Exception {
        when(roomChatService.findPrivateHistory(5L, 12L, 13L)).thenReturn(List.of(
                new ChatHistoryMessage(201L, 5L, 12L, "userA", 13L,
                        "secret", LocalDateTime.of(2026, 8, 12, 20, 0))));

        mockMvc.perform(get(
                        "/api/rooms/{roomId}/chat-messages/private/{otherUserId}", 5L, 13L)
                        .session(authenticatedSession(12L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].messageId").value(201))
                .andExpect(jsonPath("$[0].targetUserId").value(13))
                .andExpect(jsonPath("$[0].content").value("secret"));
    }

    private MockHttpSession authenticatedSession(long userId) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(UserSession.LOGIN_USER_ID, userId);
        return session;
    }
}
