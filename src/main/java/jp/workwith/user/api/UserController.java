package jp.workwith.user.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jp.workwith.user.DuplicateUsernameException;
import jp.workwith.user.InvalidCredentialsException;
import jp.workwith.user.User;
import jp.workwith.user.UserService;
import jp.workwith.user.UserSession;
import jp.workwith.user.UserNotFoundException;

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

    @PostMapping("/login")
    public ResponseEntity<?> login(
            @RequestBody LoginRequest request,
            HttpServletRequest httpRequest) {
        try {
            User user = userService.login(request.getUsername(), request.getPassword());

            // セッション固定攻撃を避けるため、既存セッションを破棄して作り直します。
            HttpSession existingSession = httpRequest.getSession(false);
            if (existingSession != null) {
                existingSession.invalidate();
            }
            HttpSession newSession = httpRequest.getSession(true);
            newSession.setAttribute(UserSession.LOGIN_USER_ID, user.getUserId());

            return ResponseEntity.ok(new LoginResponse(
                    user.getUserId(),
                    user.getUsername(),
                    user.getAvatarType()));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(new ApiErrorResponse(exception.getMessage()));
        } catch (InvalidCredentialsException exception) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ApiErrorResponse(exception.getMessage()));
        } catch (DataAccessException exception) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiErrorResponse("ログイン処理に失敗しました"));
        }
    }

    /** HttpSessionを確認し、現在ログイン中のユーザー情報を返します。 */
    @GetMapping("/me")
    public ResponseEntity<?> me(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return unauthorized();
        }

        Object loginUserId = session.getAttribute(UserSession.LOGIN_USER_ID);
        if (!(loginUserId instanceof Number userId)) {
            return unauthorized();
        }

        try {
            return userService.findById(userId.longValue())
                    .<ResponseEntity<?>>map(user -> ResponseEntity.ok(new LoginResponse(
                            user.getUserId(),
                            user.getUsername(),
                            user.getAvatarType())))
                    .orElseGet(this::unauthorized);
        } catch (DataAccessException exception) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiErrorResponse("ユーザー情報の取得に失敗しました"));
        }
    }

    /** 現在のセッションを破棄してログアウトします。 */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        return ResponseEntity.noContent().build();
    }

    /** HttpSessionのユーザーIDを使って、本人のアバターだけを更新します。 */
    @PatchMapping("/me/avatar")
    public ResponseEntity<?> updateAvatar(
            @RequestBody AvatarUpdateRequest requestBody,
            HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return unauthorized();
        }

        Object loginUserId = session.getAttribute(UserSession.LOGIN_USER_ID);
        if (!(loginUserId instanceof Number userId)) {
            return unauthorized();
        }

        try {
            User updatedUser = userService.updateAvatar(
                    userId.longValue(), requestBody.getAvatarType());
            return ResponseEntity.ok(new LoginResponse(
                    updatedUser.getUserId(),
                    updatedUser.getUsername(),
                    updatedUser.getAvatarType()));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest()
                    .body(new ApiErrorResponse(exception.getMessage()));
        } catch (UserNotFoundException exception) {
            return unauthorized();
        } catch (DataAccessException exception) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiErrorResponse("アバターの保存に失敗しました"));
        }
    }

    private ResponseEntity<ApiErrorResponse> unauthorized() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiErrorResponse("ログインが必要です"));
    }
}
