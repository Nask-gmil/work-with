package jp.workwith.room;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import jp.workwith.seat.SeatService;
import jp.workwith.seatassignment.SeatAssignmentService;
import jp.workwith.user.User;
import jp.workwith.user.UserService;

class RoomServiceInputLimitTests {

    @Test
    void acceptsTenWithoutAssigningASeatAndRejectsElevenCharactersBeforeCreatingRoomSeats() {
        RoomRepository roomRepository = mock(RoomRepository.class);
        UserService userService = mock(UserService.class);
        SeatService seatService = mock(SeatService.class);
        SeatAssignmentService assignmentService = mock(SeatAssignmentService.class);
        RoomService service = new RoomService(
                roomRepository, userService, seatService, assignmentService);
        when(userService.findById(1L)).thenReturn(Optional.of(
                new User(1L, "user", "hash", null)));
        when(roomRepository.findByRoomCode(any())).thenReturn(Optional.empty());
        when(roomRepository.create(any())).thenAnswer(invocation -> {
            Room room = invocation.getArgument(0);
            return new Room(10L, room.getRoomCode(), room.getRoomType(), room.getRoomName(),
                    room.getTheme(), room.getBackgroundUrl(), room.getMaxSeats(), room.getCreatedBy());
        });

        Room created = service.createPrivateRoom(1L, "a".repeat(10), "focus", 10);
        assertThat(created.getRoomName()).hasSize(10);
        verify(seatService).createForRoom(10L, 10);
        verify(assignmentService, never()).autoAssignSeat(any(Long.class), any(Long.class));

        clearInvocations(roomRepository, seatService, assignmentService);
        assertThatThrownBy(() -> service.createPrivateRoom(
                1L, "a".repeat(11), "focus", 10))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("部屋名は10文字以内で入力してください");
        verify(roomRepository, never()).create(any());
        verify(seatService, never()).createForRoom(any(Long.class), any(Integer.class));
        verify(assignmentService, never()).autoAssignSeat(any(Long.class), any(Long.class));
    }
}
