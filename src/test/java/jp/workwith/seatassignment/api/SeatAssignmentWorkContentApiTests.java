package jp.workwith.seatassignment.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

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
import jp.workwith.room.RoomRepository;
import jp.workwith.seat.Seat;
import jp.workwith.seat.SeatRepository;
import jp.workwith.seatassignment.SeatAssignment;
import jp.workwith.seatassignment.SeatAssignmentRepository;
import jp.workwith.user.User;
import jp.workwith.user.UserRepository;
import jp.workwith.user.UserSession;
import jp.workwith.user.UserChangeRateLimitService;

@SpringBootTest
@AutoConfigureMockMvc
class SeatAssignmentWorkContentApiTests {

    @Autowired private MockMvc mockMvc;
    @Autowired private RoomRepository roomRepository;
    @Autowired private SeatRepository seatRepository;
    @Autowired private SeatAssignmentRepository assignmentRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private UserChangeRateLimitService changeRateLimitService;
    @MockitoBean private RoomRealtimeNotifier realtimeNotifier;

    @BeforeEach
    void clearRateLimits() {
        changeRateLimitService.clear();
    }

    @Test
    void updatesOnlySessionUsersWorkContentAndCanClearIt() throws Exception {
        TestData data = createData();
        try {
            mockMvc.perform(patch("/api/rooms/{roomId}/seat-assignments/me/work-content",
                    data.room().getRoomId()).session(session(data.user()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"workContent\":\"" + "a".repeat(25) + "\",\"userId\":"
                            + data.other().getUserId() + "}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.userId").value(data.user().getUserId()))
                    .andExpect(jsonPath("$.workContent").value("a".repeat(25)))
                    .andExpect(jsonPath("$.status").value("break"));
            verify(realtimeNotifier).notifyWorkContentChanged(
                    data.room().getRoomId(), data.user().getUserId());
            SeatAssignment updated = assignmentRepository.findByUserId(
                    data.user().getUserId()).orElseThrow();
            assertThat(updated.getWorkContent()).isEqualTo("a".repeat(25));
            assertThat(updated.getStatus()).isEqualTo("break");
            assertThat(updated.getStartedAt()).isEqualTo(data.assignment().getStartedAt());
            assertThat(updated.getLastHeartbeatAt()).isEqualTo(data.assignment().getLastHeartbeatAt());
            assertThat(assignmentRepository.findByUserId(data.other().getUserId()))
                    .get().extracting(SeatAssignment::getWorkContent).isEqualTo("other work");

            mockMvc.perform(patch("/api/rooms/{roomId}/seat-assignments/me/work-content",
                    data.room().getRoomId()).session(session(data.user()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"workContent\":\"   \"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.workContent").doesNotExist());
            assertThat(assignmentRepository.findByUserId(data.user().getUserId()))
                    .get().extracting(SeatAssignment::getWorkContent).isNull();
        } finally {
            cleanup(data);
        }
    }

    @Test
    void rejectsTooLongUnseatedAndWrongRoomUpdatesWithoutNotification() throws Exception {
        TestData data = createData();
        Room otherRoom = roomRepository.create(new Room(
                null, null, "public", "Other", "focus", null, 1, null));
        try {
            mockMvc.perform(patch("/api/rooms/{roomId}/seat-assignments/me/work-content",
                    data.room().getRoomId()).session(session(data.user()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"workContent\":\"" + "a".repeat(26) + "\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message")
                            .value("作業内容は25文字以内で入力してください"));
            assertThat(assignmentRepository.findByUserId(data.user().getUserId()))
                    .get().extracting(SeatAssignment::getWorkContent).isEqualTo("old");
            mockMvc.perform(patch("/api/rooms/{roomId}/seat-assignments/me/work-content",
                    otherRoom.getRoomId()).session(session(data.user()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"workContent\":\"wrong\"}"))
                    .andExpect(status().isConflict());
            assignmentRepository.deleteBySeatId(data.otherAssignment().getSeatId());
            mockMvc.perform(patch("/api/rooms/{roomId}/seat-assignments/me/work-content",
                    data.room().getRoomId()).session(session(data.other()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"workContent\":\"missing\"}"))
                    .andExpect(status().isConflict());
            verify(realtimeNotifier, never()).notifyWorkContentChanged(
                    org.mockito.ArgumentMatchers.anyLong(),
                    org.mockito.ArgumentMatchers.anyLong());
        } finally {
            roomRepository.deleteById(otherRoom.getRoomId());
            cleanup(data);
        }
    }

    @Test
    void invalidAttemptsReachLimitWithoutUpdatingDatabaseOrNotifying() throws Exception {
        TestData data = createData();
        try {
            for (int attempt = 1; attempt <= 20; attempt++) {
                mockMvc.perform(patch("/api/rooms/{roomId}/seat-assignments/me/work-content",
                        data.room().getRoomId()).session(session(data.user()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"workContent\":\"" + "a".repeat(26) + "\"}"))
                        .andExpect(status().isBadRequest());
            }
            mockMvc.perform(patch("/api/rooms/{roomId}/seat-assignments/me/work-content",
                    data.room().getRoomId()).session(session(data.user()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"workContent\":\"new\"}"))
                    .andExpect(status().isTooManyRequests())
                    .andExpect(jsonPath("$.message").value(
                            "短時間の作業内容変更回数が多いため、一時的に変更を制限しています。"
                                    + "少し時間を空けてから再度お試しください。"));
            assertThat(assignmentRepository.findByUserId(data.user().getUserId()))
                    .get().extracting(SeatAssignment::getWorkContent).isEqualTo("old");
            verify(realtimeNotifier, never()).notifyWorkContentChanged(
                    org.mockito.ArgumentMatchers.anyLong(),
                    org.mockito.ArgumentMatchers.anyLong());
        } finally {
            cleanup(data);
        }
    }

    private TestData createData() {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        User user = userRepository.create(new User(null, "work_" + suffix, "password", "male_a"));
        User other = userRepository.create(new User(null, "work_other_" + suffix, "password", "female_a"));
        Room room = roomRepository.create(new Room(null, null, "public", "Work API", "focus", null, 2, null));
        seatRepository.createAll(List.of(
                new Seat(null, room.getRoomId(), 1, 26.0, 31.6),
                new Seat(null, room.getRoomId(), 2, 37.0, 35.0)));
        List<Seat> seats = seatRepository.findByRoomId(room.getRoomId());
        LocalDateTime now = LocalDateTime.of(2026, 8, 12, 20, 0);
        SeatAssignment assignment = new SeatAssignment(
                seats.get(0).getSeatId(), user.getUserId(), "break", "old", now, now);
        SeatAssignment otherAssignment = new SeatAssignment(
                seats.get(1).getSeatId(), other.getUserId(), "working", "other work", now, now);
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

    private record TestData(Room room, User user, User other,
            SeatAssignment assignment, SeatAssignment otherAssignment) {}
}
