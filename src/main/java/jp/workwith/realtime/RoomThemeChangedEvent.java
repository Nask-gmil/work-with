package jp.workwith.realtime;

public record RoomThemeChangedEvent(String type, long roomId, String theme) {
}
