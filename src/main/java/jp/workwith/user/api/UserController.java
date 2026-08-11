package jp.workwith.user.api;

import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jp.workwith.user.DuplicateUsernameException;
import jp.workwith.user.User;
import jp.workwith.user.UserService;

/** 新規ユーザー登録のHTTPリクエストを受け付けます。 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody UserRegistrationRequest request) {
        try {
            User createdUser = userService.register(request.getUsername(), request.getPassword());
            UserRegistrationResponse response = new UserRegistrationResponse(
                    createdUser.getUserId(),
                    createdUser.getUsername());
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(new ApiErrorResponse(exception.getMessage()));
        } catch (DuplicateUsernameException exception) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ApiErrorResponse(exception.getMessage()));
        } catch (DataAccessException exception) {
            // DB内部の詳細やパスワードを画面・ログへ返しません。
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiErrorResponse("ユーザー登録に失敗しました"));
        }
    }
}
