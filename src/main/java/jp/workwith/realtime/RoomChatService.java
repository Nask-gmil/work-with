package jp.workwith.realtime;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jp.workwith.chat.ChatMessage;
import jp.workwith.chat.ChatMessageRepository;
import jp.workwith.chat.ChatHistoryMessage;
import jp.workwith.room.RoomNotFoundException;
import jp.workwith.room.RoomRepository;
import jp.workwith.seat.Seat;
import jp.workwith.seat.SeatRepository;
import jp.workwith.seatassignment.SeatAssignment;
import jp.workwith.seatassignment.SeatAssignmentNotFoundException;
import jp.workwith.seatassignment.SeatAssignmentRepository;
import jp.workwith.seatassignment.SeatAssignmentRoomMismatchException;
import jp.workwith.user.User;
import jp.workwith.user.UserNotFoundException;
import jp.workwith.user.UserRepository;

@Service
public class RoomChatService {

    public static final int MAX_CONTENT_LENGTH = 500;
    public static final int HISTORY_LIMIT = 50;

    private final RoomRepository roomRepository;
    private final SeatAssignmentRepository seatAssignmentRepository;
    private final SeatRepository seatRepository;
    private final UserRepository userRepository;
    private final ChatMessageRepository chatMessageRepository;

    public RoomChatService(
            RoomRepository roomRepository,
            SeatAssignmentRepository seatAssignmentRepository,
            SeatRepository seatRepository,
            UserRepository userRepository,
            ChatMessageRepository chatMessageRepository) {
        this.roomRepository = roomRepository;
        this.seatAssignmentRepository = seatAssignmentRepository;
        this.seatRepository = seatRepository;
        this.userRepository = userRepository;
        this.chatMessageRepository = chatMessageRepository;
    }

    @Transactional
    public RoomChatMessage createMessage(
            long roomId, long userId, Long targetUserId, String rawContent) {
        String content = rawContent == null ? "" : rawContent.trim();
        if (content.isEmpty() || content.length() > MAX_CONTENT_LENGTH) {
            throw new IllegalArgumentException("メッセージは1文字以上500文字以内で入力してください");
        }

        validateSeatedUser(roomId, userId);
        if (targetUserId != null) {
            if (targetUserId == userId) {
                throw new IllegalArgumentException("自分自身へ個別チャットは送信できません");
            }
            validateSeatedUser(roomId, targetUserId);
        }
        User user = userRepository.findById(userId).orElseThrow(UserNotFoundException::new);
        LocalDateTime sentAt = LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS);
        ChatMessage savedMessage = chatMessageRepository.create(new ChatMessage(
                null, roomId, userId, targetUserId, content, sentAt));

        return new RoomChatMessage(
                targetUserId == null ? "chat-message" : "private-chat-message",
                savedMessage.messageId(), roomId, userId, user.getUsername(),
                targetUserId, savedMessage.content(), savedMessage.sentAt());
    }

    @Transactional(readOnly = true)
    public List<ChatHistoryMessage> findGlobalHistory(long roomId, long userId) {
        validateSeatedUser(roomId, userId);
        return chatMessageRepository.findLatestGlobalMessages(roomId, HISTORY_LIMIT);
    }

    @Transactional(readOnly = true)
    public List<ChatHistoryMessage> findPrivateHistory(
            long roomId, long userId, long otherUserId) {
        validateSeatedUser(roomId, userId);
        if (userId == otherUserId) {
            throw new IllegalArgumentException("自分自身との個別履歴は取得できません");
        }
        validateSeatedUser(roomId, otherUserId);
        return chatMessageRepository.findLatestPrivateMessages(
                roomId, userId, otherUserId, HISTORY_LIMIT);
    }

    private void validateSeatedUser(long roomId, long userId) {
        roomRepository.findById(roomId).orElseThrow(RoomNotFoundException::new);
        SeatAssignment assignment = seatAssignmentRepository.findByUserId(userId)
                .orElseThrow(SeatAssignmentNotFoundException::new);
        Seat seat = seatRepository.findById(assignment.getSeatId())
                .orElseThrow(() -> new IllegalStateException("着席中の座席が見つかりません"));
        if (seat.getRoomId() != roomId) {
            throw new SeatAssignmentRoomMismatchException();
        }
    }
}
