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
import jp.workwith.user.LoginRateLimitService;
import jp.workwith.user.User;
import jp.workwith.user.UserChangeRateLimitService;
import jp.workwith.user.UserService;
import jp.workwith.user.UserSession;
import jp.workwith.user.UserNotFoundException;
import jp.workwith.realtime.RoomRealtimeNotifier;
import jp.workwith.seatassignment.SeatAssignmentService;
import jp.workwith.registration.ClientIpResolver;
import jp.workwith.registration.RegistrationRateLimitService;
import jp.workwith.registration.RegistrationRateLimitService.RateLimitResult;
import jp.workwith.registration.TurnstileService;
import jp.workwith.registration.TurnstileUnavailableException;
import jp.workwith.security.SecurityEventLogger;

/** 新規ユーザー登録のHTTPリクエストを受け付けます。 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;
    private final SeatAssignmentService seatAssignmentService;
    private final RoomRealtimeNotifier realtimeNotifier;
    private final RegistrationRateLimitService registrationRateLimitService;
    private final LoginRateLimitService loginRateLimitService;
    private final UserChangeRateLimitService changeRateLimitService;
    private final ClientIpResolver clientIpResolver;
    private final TurnstileService turnstileService;
    private final SecurityEventLogger securityEventLogger;

    public UserController(
            UserService userService,
            SeatAssignmentService seatAssignmentService,
            RoomRealtimeNotifier realtimeNotifier,
            RegistrationRateLimitService registrationRateLimitService,
            LoginRateLimitService loginRateLimitService,
            UserChangeRateLimitService changeRateLimitService,
            ClientIpResolver clientIpResolver,
            TurnstileService turnstileService,
            SecurityEventLogger securityEventLogger) {
        this.userService = userService;
        this.seatAssignmentService = seatAssignmentService;
        this.realtimeNotifier = realtimeNotifier;
        this.registrationRateLimitService = registrationRateLimitService;
        this.loginRateLimitService = loginRateLimitService;
        this.changeRateLimitService = changeRateLimitService;
        this.clientIpResolver = clientIpResolver;
        this.turnstileService = turnstileService;
        this.securityEventLogger = securityEventLogger;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(
            @RequestBody UserRegistrationRequest request,
            HttpServletRequest httpRequest) {
        String clientIp = clientIpResolver.resolve(httpRequest);
        RateLimitResult rateLimit = registrationRateLimitService.recordAttempt(clientIp);
        if (!rateLimit.allowed()) {
            securityEventLogger.rateLimit("REGISTRATION", null, clientIp, null);
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .header("Retry-After", Long.toString(rateLimit.retryAfterSeconds()))
                    .body(new ApiErrorResponse(
                            "新規登録の試行回数が多すぎます。しばらくしてから再試行してください。"));
        }

        try {
            if (!turnstileService.verify(request.getTurnstileToken(), clientIp)) {
                return ResponseEntity.badRequest()
                        .body(new ApiErrorResponse("確認に失敗しました。もう一度お試しください。"));
            }
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
        } catch (TurnstileUnavailableException exception) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(new ApiErrorResponse(
                            "登録処理を一時的に実行できません。時間を置いて再度お試しください。"));
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
        String normalizedUsername;
        try {
            normalizedUsername = userService.normalizeAndValidateUsername(request.getUsername());
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(new ApiErrorResponse(exception.getMessage()));
        }
        String clientIp = clientIpResolver.resolve(httpRequest);
        LoginRateLimitService.RateLimitResult currentLimit =
                loginRateLimitService.check(normalizedUsername, clientIp);
        if (!currentLimit.allowed()) {
            securityEventLogger.rateLimit("LOGIN", null,
                    normalizedUsername + "|" + clientIp, null);
            return loginRateLimited(currentLimit.retryAfterSeconds());
        }

        try {
            User user = userService.login(normalizedUsername, request.getPassword());
            loginRateLimitService.reset(normalizedUsername, clientIp);

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
            LoginRateLimitService.RateLimitResult updatedLimit =
                    loginRateLimitService.recordFailure(normalizedUsername, clientIp);
            if (updatedLimit.failureCount() == 5) {
                securityEventLogger.loginFailureBurst(
                        normalizedUsername, clientIp, updatedLimit.failureCount());
            }
            if (!updatedLimit.allowed()) {
                securityEventLogger.rateLimit("LOGIN", null,
                        normalizedUsername + "|" + clientIp, null);
                return loginRateLimited(updatedLimit.retryAfterSeconds());
            }
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ApiErrorResponse(exception.getMessage()));
        } catch (DataAccessException exception) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiErrorResponse("ログイン処理に失敗しました"));
        }
    }

    private ResponseEntity<ApiErrorResponse> loginRateLimited(long retryAfterSeconds) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", Long.toString(retryAfterSeconds))
                .body(new ApiErrorResponse(
                        "ログイン試行回数が多いため、一時的にログインを制限しています。"
                                + "時間を空けてから再度お試しください。"));
    }

    /** HttpSessionを確認し、現在ログイン中のユーザー情報を返します。 */
    @GetMapping("/me")
    public ResponseEntity<?> me(HttpServletRequest request) {
        long userId = getLoginUserId(request);

        try {
            return userService.findById(userId)
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
        request.getSession(false).invalidate();
        return ResponseEntity.noContent().build();
    }

    /** HttpSessionのユーザーIDを使って、本人のアバターだけを更新します。 */
    @PatchMapping("/me/avatar")
    public ResponseEntity<?> updateAvatar(
            @RequestBody AvatarUpdateRequest requestBody,
            HttpServletRequest request) {
        long userId = getLoginUserId(request);

        try {
            User currentUser = userService.findById(userId)
                    .orElseThrow(UserNotFoundException::new);
            if (currentUser.getAvatarType() != null) {
                UserChangeRateLimitService.RateLimitResult rateLimit =
                        changeRateLimitService.recordAvatarAttempt(userId);
                if (!rateLimit.allowed()) {
                    securityEventLogger.rateLimit("AVATAR_CHANGE", userId, null, null);
                    return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                            .header("Retry-After", Long.toString(rateLimit.retryAfterSeconds()))
                            .body(new ApiErrorResponse(
                                    "短時間のアバター変更回数が多いため、一時的に変更を制限しています。"
                                            + "少し時間を空けてから再度お試しください。"));
                }
            }
            User updatedUser = userService.updateAvatar(
                    userId, requestBody.getAvatarType());
            seatAssignmentService.findAssignedRoomId(userId)
                    .ifPresent(roomId -> realtimeNotifier.notifyAvatarChanged(roomId, userId));
            return ResponseEntity.ok(new LoginResponse(
                    updatedUser.getUserId(),
                    updatedUser.getUsername(),
                    updatedUser.getAvatarType()));
        } catch (IllegalArgumentException exception) {
            securityEventLogger.invalidInput(userId, "AVATAR_CHANGE");
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

    /** Interceptorが確認済みのセッションから、本人のuserIdを取得します。 */
    private long getLoginUserId(HttpServletRequest request) {
        return ((Number) request.getSession(false)
                .getAttribute(UserSession.LOGIN_USER_ID)).longValue();
    }
}
