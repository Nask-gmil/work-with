package jp.workwith.chat;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import jp.workwith.room.Room;
import jp.workwith.room.RoomRepository;
import jp.workwith.user.User;
import jp.workwith.user.UserRepository;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ChatMessageRepositoryTests {

    @Autowired private ChatMessageRepository chatMessageRepository;
    @Autowired private RoomRepository roomRepository;
    @Autowired private UserRepository userRepository;

    @Test
    void savesGlobalMessagesWithGeneratedIdsNullTargetAndSentAt() {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        User user = userRepository.create(new User(
                null, "chat_repo_" + suffix, "password", "male_a"));
        Room room = roomRepository.create(new Room(
                null, null, "private", "Chat repository", "focus", null,
                10, user.getUserId()));
        LocalDateTime sentAt = LocalDateTime.of(2026, 8, 12, 19, 15, 30, 123_000_000);
        ChatMessage first = null;
        ChatMessage second = null;

        try {
            first = chatMessageRepository.create(new ChatMessage(
                    null, room.getRoomId(), user.getUserId(), null, "こんにちは", sentAt));
            second = chatMessageRepository.create(new ChatMessage(
                    null, room.getRoomId(), user.getUserId(), null, "よろしく", sentAt.plusSeconds(1)));

            assertThat(first.messageId()).isPositive();
            assertThat(second.messageId()).isNotEqualTo(first.messageId());
            long firstMessageId = first.messageId();
            assertThat(chatMessageRepository.findById(firstMessageId))
                    .get().satisfies(saved -> {
                        assertThat(saved.roomId()).isEqualTo(room.getRoomId());
                        assertThat(saved.userId()).isEqualTo(user.getUserId());
                        assertThat(saved.targetUserId()).isNull();
                        assertThat(saved.content()).isEqualTo("こんにちは");
                        assertThat(saved.sentAt()).isEqualTo(sentAt);
                    });
        } finally {
            if (second != null) chatMessageRepository.deleteById(second.messageId());
            if (first != null) chatMessageRepository.deleteById(first.messageId());
            roomRepository.deleteById(room.getRoomId());
            userRepository.deleteById(user.getUserId());
        }
    }

    @Test
    void findsOnlyLatest50GlobalMessagesForRoomInStableOldestFirstOrder() {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        User user = userRepository.create(new User(
                null, "chat_history_" + suffix, "password", "female_a"));
        Room firstRoom = roomRepository.create(new Room(
                null, null, "private", "History one", "focus", null,
                10, user.getUserId()));
        Room secondRoom = roomRepository.create(new Room(
                null, null, "private", "History two", "focus", null,
                10, user.getUserId()));
        LocalDateTime sameSentAt = LocalDateTime.of(2026, 8, 12, 20, 0);
        List<Long> createdMessageIds = new ArrayList<>();

        try {
            for (int index = 0; index < 60; index++) {
                ChatMessage saved = chatMessageRepository.create(new ChatMessage(
                        null, firstRoom.getRoomId(), user.getUserId(), null,
                        "message-" + index, sameSentAt));
                createdMessageIds.add(saved.messageId());
            }
            createdMessageIds.add(chatMessageRepository.create(new ChatMessage(
                    null, firstRoom.getRoomId(), user.getUserId(), user.getUserId(),
                    "private-message", sameSentAt.plusSeconds(1))).messageId());
            createdMessageIds.add(chatMessageRepository.create(new ChatMessage(
                    null, secondRoom.getRoomId(), user.getUserId(), null,
                    "other-room", sameSentAt.plusSeconds(2))).messageId());

            List<ChatHistoryMessage> history =
                    chatMessageRepository.findLatestGlobalMessages(firstRoom.getRoomId(), 50);

            assertThat(history).hasSize(50);
            assertThat(history).extracting(ChatHistoryMessage::content)
                    .containsExactlyElementsOf(java.util.stream.IntStream.range(10, 60)
                            .mapToObj(index -> "message-" + index).toList());
            assertThat(history).extracting(ChatHistoryMessage::username)
                    .containsOnly(user.getUsername());
            assertThat(history).extracting(ChatHistoryMessage::roomId)
                    .containsOnly(firstRoom.getRoomId());
        } finally {
            createdMessageIds.forEach(chatMessageRepository::deleteById);
            roomRepository.deleteById(secondRoom.getRoomId());
            roomRepository.deleteById(firstRoom.getRoomId());
            userRepository.deleteById(user.getUserId());
        }
    }

    @Test
    void findsOnlyPrivateMessagesBetweenRequestedTwoUsers() {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        User firstUser = userRepository.create(new User(
                null, "private_a_" + suffix, "password", "male_a"));
        User secondUser = userRepository.create(new User(
                null, "private_b_" + suffix, "password", "male_b"));
        User thirdUser = userRepository.create(new User(
                null, "private_c_" + suffix, "password", "female_a"));
        Room room = roomRepository.create(new Room(
                null, null, "private", "Private history", "focus", null,
                10, firstUser.getUserId()));
        LocalDateTime sentAt = LocalDateTime.of(2026, 8, 12, 21, 0);
        List<Long> messageIds = new ArrayList<>();

        try {
            messageIds.add(chatMessageRepository.create(new ChatMessage(
                    null, room.getRoomId(), firstUser.getUserId(), secondUser.getUserId(),
                    "A to B", sentAt)).messageId());
            messageIds.add(chatMessageRepository.create(new ChatMessage(
                    null, room.getRoomId(), secondUser.getUserId(), firstUser.getUserId(),
                    "B to A", sentAt.plusSeconds(1))).messageId());
            messageIds.add(chatMessageRepository.create(new ChatMessage(
                    null, room.getRoomId(), secondUser.getUserId(), thirdUser.getUserId(),
                    "B to C", sentAt.plusSeconds(2))).messageId());

            List<ChatHistoryMessage> history = chatMessageRepository.findLatestPrivateMessages(
                    room.getRoomId(), firstUser.getUserId(), secondUser.getUserId(), 50);

            assertThat(history).extracting(ChatHistoryMessage::content)
                    .containsExactly("A to B", "B to A");
            assertThat(history).extracting(ChatHistoryMessage::username)
                    .containsExactly(firstUser.getUsername(), secondUser.getUsername());
        } finally {
            messageIds.forEach(chatMessageRepository::deleteById);
            roomRepository.deleteById(room.getRoomId());
            userRepository.deleteById(thirdUser.getUserId());
            userRepository.deleteById(secondUser.getUserId());
            userRepository.deleteById(firstUser.getUserId());
        }
    }
}
