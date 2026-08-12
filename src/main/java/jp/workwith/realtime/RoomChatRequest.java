package jp.workwith.realtime;

public record RoomChatRequest(Long targetUserId, String content) {
}
