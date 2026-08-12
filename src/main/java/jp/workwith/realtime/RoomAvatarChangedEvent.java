package jp.workwith.realtime;

public record RoomAvatarChangedEvent(String type, long roomId, long userId) {
}
