package jp.workwith.realtime;

public record RoomWorkContentChangedEvent(String type, long roomId, long userId) {
}
