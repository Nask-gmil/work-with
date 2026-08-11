package jp.workwith.room.api;

/** プライベート部屋作成APIが受け取る項目です。userIdは受け取りません。 */
public class CreateRoomRequest {

    private String roomName;
    private String theme;
    private Integer maxSeats;

    public String getRoomName() {
        return roomName;
    }

    public void setRoomName(String roomName) {
        this.roomName = roomName;
    }

    public String getTheme() {
        return theme;
    }

    public void setTheme(String theme) {
        this.theme = theme;
    }

    public Integer getMaxSeats() {
        return maxSeats;
    }

    public void setMaxSeats(Integer maxSeats) {
        this.maxSeats = maxSeats;
    }
}
