package jp.workwith.room;

/** ROOMSテーブルの1行をJavaで扱うためのクラスです。 */
public class Room {

    private final Long roomId;
    private final String roomCode;
    private final String roomType;
    private final String roomName;
    private final String theme;
    private final String backgroundUrl;
    private final int maxSeats;
    private final Long createdBy;

    public Room(
            Long roomId,
            String roomCode,
            String roomType,
            String roomName,
            String theme,
            String backgroundUrl,
            int maxSeats,
            Long createdBy) {
        this.roomId = roomId;
        this.roomCode = roomCode;
        this.roomType = roomType;
        this.roomName = roomName;
        this.theme = theme;
        this.backgroundUrl = backgroundUrl;
        this.maxSeats = maxSeats;
        this.createdBy = createdBy;
    }

    public Long getRoomId() {
        return roomId;
    }

    public String getRoomCode() {
        return roomCode;
    }

    public String getRoomType() {
        return roomType;
    }

    public String getRoomName() {
        return roomName;
    }

    public String getTheme() {
        return theme;
    }

    public String getBackgroundUrl() {
        return backgroundUrl;
    }

    public int getMaxSeats() {
        return maxSeats;
    }

    public Long getCreatedBy() {
        return createdBy;
    }
}
