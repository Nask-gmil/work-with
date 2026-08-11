package jp.workwith.user.api;

/** アバター更新APIが受け取るデータです。userIdはセッションから取得します。 */
public class AvatarUpdateRequest {

    private String avatarType;

    public String getAvatarType() {
        return avatarType;
    }

    public void setAvatarType(String avatarType) {
        this.avatarType = avatarType;
    }
}
