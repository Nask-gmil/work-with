package jp.workwith.room.api;

/** private部屋の正式な入室に必要な参加コードです。 */
public class PrivateRoomJoinRequest {

    private String roomCode;

    public String getRoomCode() {
        return roomCode;
    }

    public void setRoomCode(String roomCode) {
        this.roomCode = roomCode;
    }
}
