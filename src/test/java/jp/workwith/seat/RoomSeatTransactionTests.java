package jp.workwith.seat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import jp.workwith.room.RoomRepository;
import jp.workwith.room.RoomService;
import jp.workwith.user.User;
import jp.workwith.user.UserRepository;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class RoomSeatTransactionTests {

    @Autowired private RoomService roomService;
    @Autowired private RoomRepository roomRepository;
    @Autowired private UserRepository userRepository;

    @MockitoBean
    private SeatRepository seatRepository;

    @Test
    void rollsBackRoomWhenSeatCreationFails() {
        User user = userRepository.create(new User(
                null, "seat_tx_" + UUID.randomUUID().toString().replace("-", ""),
                "test-password", null));
        doThrow(new IllegalStateException("simulated seat creation failure"))
                .when(seatRepository).createAll(anyList());

        try {
            assertThatThrownBy(() -> roomService.createPrivateRoom(
                    user.getUserId(), "Rollback", "focus", 10))
                    .isInstanceOf(IllegalStateException.class);
            assertThat(roomRepository.findByCreatedBy(user.getUserId())).isEmpty();
        } finally {
            userRepository.deleteById(user.getUserId());
        }
    }
}
