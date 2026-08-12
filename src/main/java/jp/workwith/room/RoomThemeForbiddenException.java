package jp.workwith.room;

public class RoomThemeForbiddenException extends RuntimeException {
    public RoomThemeForbiddenException() {
        super("この部屋のテーマを変更する権限がありません。");
    }
}
