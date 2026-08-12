package jp.workwith.room;

public class PrivateRoomAccessDeniedException extends RuntimeException {

    public PrivateRoomAccessDeniedException() {
        super("このプライベート部屋を閲覧する権限がありません");
    }
}
