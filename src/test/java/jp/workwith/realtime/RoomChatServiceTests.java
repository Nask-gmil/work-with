package jp.workwith.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import jp.workwith.room.Room;
import jp.workwith.room.RoomRepository;
import jp.workwith.chat.ChatMessage;
import jp.workwith.chat.ChatMessageRepository;
import jp.workwith.chat.ChatHistoryMessage;
import jp.workwith.seat.Seat;
import jp.workwith.seat.SeatRepository;
import jp.workwith.seatassignment.SeatAssignment;
import jp.workwith.seatassignment.SeatAssignmentNotFoundException;
import jp.workwith.seatassignment.SeatAssignmentRepository;
import jp.workwith.seatassignment.SeatAssignmentRoomMismatchException;
import jp.workwith.user.User;
import jp.workwith.user.UserRepository;

class RoomChatServiceTests {
    private final RoomRepository roomRepository = mock(RoomRepository.class);
    private final SeatAssignmentRepository assignmentRepository = mock(SeatAssignmentRepository.class);
    private final SeatRepository seatRepository = mock(SeatRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final ChatMessageRepository chatMessageRepository = mock(ChatMessageRepository.class);
    private final RoomChatService service = new RoomChatService(
            roomRepository, assignmentRepository, seatRepository, userRepository,
            chatMessageRepository);

    @BeforeEach
    void prepareSeatedUser() {
        when(roomRepository.findById(5L)).thenReturn(Optional.of(new Room(
                5L, null, "public", "Room 5", "focus", null, 10, null)));
        when(assignmentRepository.findByUserId(12L)).thenReturn(Optional.of(
                new SeatAssignment(51L, 12L, "working", null,
                        LocalDateTime.now(), LocalDateTime.now())));
        when(seatRepository.findById(51L)).thenReturn(Optional.of(
                new Seat(51L, 5L, 1, 10.0, 20.0)));
        when(assignmentRepository.findByUserId(13L)).thenReturn(Optional.of(
                new SeatAssignment(52L, 13L, "working", null,
                        LocalDateTime.now(), LocalDateTime.now())));
        when(seatRepository.findById(52L)).thenReturn(Optional.of(
                new Seat(52L, 5L, 2, 20.0, 20.0)));
        when(userRepository.findById(12L)).thenReturn(Optional.of(
                new User(12L, "server-user", "password", "male_a")));
        when(chatMessageRepository.create(org.mockito.ArgumentMatchers.any(ChatMessage.class)))
                .thenAnswer(invocation -> {
                    ChatMessage message = invocation.getArgument(0);
                    return new ChatMessage(
                            101L, message.roomId(), message.userId(), message.targetUserId(),
                            message.content(), message.sentAt());
                });
    }

    @Test
    void createsMessageFromServerSideUserAndTrimsOuterWhitespace() {
        RoomChatMessage message = service.createMessage(5L, 12L, null, "  こんにちは  ");
        assertThat(message.type()).isEqualTo("chat-message");
        assertThat(message.messageId()).isEqualTo(101L);
        assertThat(message.roomId()).isEqualTo(5L);
        assertThat(message.userId()).isEqualTo(12L);
        assertThat(message.username()).isEqualTo("server-user");
        assertThat(message.content()).isEqualTo("こんにちは");
        org.mockito.Mockito.verify(chatMessageRepository).create(
                org.mockito.ArgumentMatchers.argThat(saved ->
                        saved.targetUserId() == null
                                && saved.content().equals("こんにちは")
                                && saved.sentAt() != null));
    }

    @Test
    void rejectsEmptyAndOver500CharacterMessages() {
        assertThatThrownBy(() -> service.createMessage(5L, 12L, null, " \n "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.createMessage(5L, 12L, null, "a".repeat(501)))
                .isInstanceOf(IllegalArgumentException.class);
        org.mockito.Mockito.verify(chatMessageRepository, org.mockito.Mockito.never())
                .create(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsUserWhoIsNotSeated() {
        when(assignmentRepository.findByUserId(12L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.createMessage(5L, 12L, null, "hello"))
                .isInstanceOf(SeatAssignmentNotFoundException.class);
        org.mockito.Mockito.verify(chatMessageRepository, org.mockito.Mockito.never())
                .create(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsUserSeatedInAnotherRoom() {
        when(seatRepository.findById(51L)).thenReturn(Optional.of(
                new Seat(51L, 8L, 1, 10.0, 20.0)));
        assertThatThrownBy(() -> service.createMessage(5L, 12L, null, "hello"))
                .isInstanceOf(SeatAssignmentRoomMismatchException.class);
        org.mockito.Mockito.verify(chatMessageRepository, org.mockito.Mockito.never())
                .create(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void returnsLatestGlobalHistoryForSeatedUser() {
        List<ChatHistoryMessage> history = List.of(new ChatHistoryMessage(
                101L, 5L, 12L, "server-user", null, "hello", LocalDateTime.now()));
        when(chatMessageRepository.findLatestGlobalMessages(5L, 50)).thenReturn(history);

        assertThat(service.findGlobalHistory(5L, 12L)).isEqualTo(history);
        org.mockito.Mockito.verify(chatMessageRepository).findLatestGlobalMessages(5L, 50);
    }

    @Test
    void rejectsHistoryForUnseatedOrDifferentRoomUser() {
        when(assignmentRepository.findByUserId(12L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.findGlobalHistory(5L, 12L))
                .isInstanceOf(SeatAssignmentNotFoundException.class);
        org.mockito.Mockito.verify(chatMessageRepository, org.mockito.Mockito.never())
                .findLatestGlobalMessages(org.mockito.ArgumentMatchers.anyLong(),
                        org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void savesPrivateMessageOnlyWhenTargetIsSeatedInSameRoom() {
        RoomChatMessage message = service.createMessage(5L, 12L, 13L, "private");

        assertThat(message.type()).isEqualTo("private-chat-message");
        assertThat(message.targetUserId()).isEqualTo(13L);
        org.mockito.Mockito.verify(chatMessageRepository).create(
                org.mockito.ArgumentMatchers.argThat(saved ->
                        saved.userId() == 12L && Long.valueOf(13L).equals(saved.targetUserId())));
    }

    @Test
    void rejectsPrivateMessageToSelfOrUserInAnotherRoom() {
        assertThatThrownBy(() -> service.createMessage(5L, 12L, 12L, "self"))
                .isInstanceOf(IllegalArgumentException.class);

        when(seatRepository.findById(52L)).thenReturn(Optional.of(
                new Seat(52L, 8L, 2, 20.0, 20.0)));
        assertThatThrownBy(() -> service.createMessage(5L, 12L, 13L, "other room"))
                .isInstanceOf(SeatAssignmentRoomMismatchException.class);
    }

    @Test
    void returnsPrivateHistoryOnlyForCurrentSameRoomPair() {
        List<ChatHistoryMessage> history = List.of(new ChatHistoryMessage(
                201L, 5L, 12L, "server-user", 13L, "private", LocalDateTime.now()));
        when(chatMessageRepository.findLatestPrivateMessages(5L, 12L, 13L, 50))
                .thenReturn(history);

        assertThat(service.findPrivateHistory(5L, 12L, 13L)).isEqualTo(history);
    }
}
