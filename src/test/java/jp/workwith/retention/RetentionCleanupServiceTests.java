package jp.workwith.retention;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class RetentionCleanupServiceTests {

    @Test
    void deletesChatsAfter24HoursAndPrivateRoomsAfter14DaysInForeignKeyOrder() {
        RetentionCleanupRepository repository = mock(RetentionCleanupRepository.class);
        RetentionCleanupService service = new RetentionCleanupService(repository, 24, 14);
        LocalDateTime now = LocalDateTime.of(2026, 8, 16, 12, 0);
        LocalDateTime chatCutoff = now.minusHours(24);
        LocalDateTime roomCutoff = now.minusDays(14);
        when(repository.deleteChatMessagesSentAtOrBefore(chatCutoff)).thenReturn(3);
        when(repository.findExpiredPrivateRoomIds(roomCutoff)).thenReturn(List.of(10L));
        when(repository.deleteChatMessagesByRoomId(10L)).thenReturn(2);
        when(repository.deleteExpiredPrivateRoom(10L, roomCutoff)).thenReturn(true);

        RetentionCleanupResult result = service.removeExpiredData(now);

        assertThat(result.deletedChatMessages()).isEqualTo(5);
        assertThat(result.deletedPrivateRoomIds()).containsExactly(10L);
        InOrder order = inOrder(repository);
        order.verify(repository).deleteChatMessagesSentAtOrBefore(chatCutoff);
        order.verify(repository).findExpiredPrivateRoomIds(roomCutoff);
        order.verify(repository).deleteSeatAssignmentsByRoomId(10L);
        order.verify(repository).deleteChatMessagesByRoomId(10L);
        order.verify(repository).deleteSeatsByRoomId(10L);
        order.verify(repository).deleteExpiredPrivateRoom(10L, roomCutoff);
    }

    @Test
    void doesNotDeletePublicRoomsBecauseRepositoryReturnsOnlyExpiredPrivateRooms() {
        RetentionCleanupRepository repository = mock(RetentionCleanupRepository.class);
        RetentionCleanupService service = new RetentionCleanupService(repository, 24, 14);
        LocalDateTime now = LocalDateTime.of(2026, 8, 16, 12, 0);
        when(repository.findExpiredPrivateRoomIds(now.minusDays(14))).thenReturn(List.of());

        RetentionCleanupResult result = service.removeExpiredData(now);

        assertThat(result.deletedPrivateRoomIds()).isEmpty();
        verify(repository).findExpiredPrivateRoomIds(now.minusDays(14));
    }
}
