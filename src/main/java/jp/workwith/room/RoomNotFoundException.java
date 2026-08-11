package jp.workwith.room;

/** 指定された部屋が存在しない場合の例外です。 */
public class RoomNotFoundException extends RuntimeException {

    public RoomNotFoundException() {
        super("指定された部屋が見つかりません");
    }
}
