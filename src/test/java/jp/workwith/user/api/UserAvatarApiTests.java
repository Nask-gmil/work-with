package jp.workwith.user.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import jp.workwith.realtime.RoomRealtimeNotifier;
import jp.workwith.room.Room;
import jp.workwith.room.RoomRepository;
import jp.workwith.seat.Seat;
import jp.workwith.seat.SeatRepository;
import jp.workwith.seatassignment.SeatAssignmentRepository;
import jp.workwith.seatassignment.SeatAssignmentService;
import jp.workwith.user.User;
import jp.workwith.user.UserRepository;
import jp.workwith.user.UserService;
import jp.workwith.user.UserSession;
import jp.workwith.user.UserChangeRateLimitService;

@SpringBootTest
@AutoConfigureMockMvc
class UserAvatarApiTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired private RoomRepository roomRepository;
    @Autowired private SeatRepository seatRepository;
    @Autowired private SeatAssignmentRepository assignmentRepository;
    @Autowired private SeatAssignmentService assignmentService;
    @Autowired private UserChangeRateLimitService changeRateLimitService;
    @MockitoBean private RoomRealtimeNotifier realtimeNotifier;

    @BeforeEach
    void clearRateLimits() {
        changeRateLimitService.clear();
    }

    @Test
    void updatesAvatarAndReturnsItFromCurrentUserApi() throws Exception {
        User user = createTestUser();
        MockHttpSession session = loggedInSession(user);

        try {
            mockMvc.perform(patch("/api/users/me/avatar")
                    .session(session)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"avatarType\":\"male_a\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.userId").value(user.getUserId()))
                    .andExpect(jsonPath("$.username").value(user.getUsername()))
                    .andExpect(jsonPath("$.avatarType").value("male_a"))
                    .andExpect(jsonPath("$.password").doesNotExist())
                    .andExpect(jsonPath("$.passwordHash").doesNotExist());

            mockMvc.perform(get("/api/users/me").session(session))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.avatarType").value("male_a"));

            // 初回選択だけでなく、将来の変更にも同じAPIを再利用できます。
            mockMvc.perform(patch("/api/users/me/avatar")
                    .session(session)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"avatarType\":\"female_b\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.avatarType").value("female_b"));

            assertThat(userRepository.findById(user.getUserId()))
                    .get()
                    .extracting(User::getAvatarType)
                    .isEqualTo("female_b");
            verify(realtimeNotifier, never()).notifyAvatarChanged(
                    org.mockito.ArgumentMatchers.anyLong(),
                    org.mockito.ArgumentMatchers.anyLong());
        } finally {
            userRepository.deleteById(user.getUserId());
        }
    }

    @Test
    void rejectsInvalidAvatarWithoutChangingDatabase() throws Exception {
        User user = createTestUser();
        MockHttpSession session = loggedInSession(user);

        try {
            mockMvc.perform(patch("/api/users/me/avatar")
                    .session(session)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"avatarType\":\"invalid_avatar\"}"))
                    .andExpect(status().isBadRequest());

            mockMvc.perform(patch("/api/users/me/avatar")
                    .session(session)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"avatarType\":\"\"}"))
                    .andExpect(status().isBadRequest());

            assertThat(userRepository.findById(user.getUserId()))
                    .get()
                    .extracting(User::getAvatarType)
                    .isNull();
            verify(realtimeNotifier, never()).notifyAvatarChanged(
                    org.mockito.ArgumentMatchers.anyLong(),
                    org.mockito.ArgumentMatchers.anyLong());
        } finally {
            userRepository.deleteById(user.getUserId());
        }
    }

    @Test
    void notifiesOnlyTheRoomWhereTheUpdatedUserIsSeated() throws Exception {
        User user = createTestUser();
        Room room = roomRepository.create(new Room(
                null, null, "public", "Avatar room", "focus", null, 1, null));
        seatRepository.createAll(java.util.List.of(
                new Seat(null, room.getRoomId(), 1, 26.0, 31.6)));
        assignmentService.autoAssignSeat(room.getRoomId(), user.getUserId());

        try {
            mockMvc.perform(patch("/api/users/me/avatar")
                    .session(loggedInSession(user))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"avatarType\":\"female_b\",\"userId\":999}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.userId").value(user.getUserId()))
                    .andExpect(jsonPath("$.avatarType").value("female_b"));

            verify(realtimeNotifier).notifyAvatarChanged(
                    room.getRoomId(), user.getUserId());
            assertThat(assignmentRepository.findByUserId(user.getUserId())).isPresent();
        } finally {
            seatRepository.findByRoomId(room.getRoomId()).forEach(seat ->
                    assignmentRepository.deleteBySeatId(seat.getSeatId()));
            seatRepository.deleteByRoomId(room.getRoomId());
            roomRepository.deleteById(room.getRoomId());
            userRepository.deleteById(user.getUserId());
        }
    }

    @Test
    void rejectsUnauthenticatedAndMissingUsers() throws Exception {
        mockMvc.perform(patch("/api/users/me/avatar")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"avatarType\":\"male_a\"}"))
                .andExpect(status().isUnauthorized());

        MockHttpSession missingUserSession = new MockHttpSession();
        missingUserSession.setAttribute(UserSession.LOGIN_USER_ID, Long.MAX_VALUE);
        mockMvc.perform(patch("/api/users/me/avatar")
                .session(missingUserSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"avatarType\":\"male_a\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidRechangeAttemptsReachLimitWithoutUpdatingDatabaseOrNotifying() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        User user = userRepository.create(new User(
                null, "avatar_limit_" + suffix.substring(0, 7), "password", "male_a"));
        MockHttpSession session = loggedInSession(user);
        try {
            for (int attempt = 1; attempt <= 10; attempt++) {
                mockMvc.perform(patch("/api/users/me/avatar")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"avatarType\":\"invalid_avatar\"}"))
                        .andExpect(status().isBadRequest());
            }
            mockMvc.perform(patch("/api/users/me/avatar")
                    .session(session)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"avatarType\":\"female_b\"}"))
                    .andExpect(status().isTooManyRequests())
                    .andExpect(jsonPath("$.message").value(
                            "短時間のアバター変更回数が多いため、一時的に変更を制限しています。"
                                    + "少し時間を空けてから再度お試しください。"));
            assertThat(userRepository.findById(user.getUserId()))
                    .get().extracting(User::getAvatarType).isEqualTo("male_a");
            verify(realtimeNotifier, never()).notifyAvatarChanged(
                    org.mockito.ArgumentMatchers.anyLong(),
                    org.mockito.ArgumentMatchers.anyLong());
        } finally {
            userRepository.deleteById(user.getUserId());
        }
    }

    private User createTestUser() {
        String username = "avatar_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        return userService.register(username, "avatar-test-password");
    }

    private MockHttpSession loggedInSession(User user) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(UserSession.LOGIN_USER_ID, user.getUserId());
        return session;
    }
}
