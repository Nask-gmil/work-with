package jp.workwith.config;

import java.io.IOException;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jp.workwith.user.UserSession;
import jp.workwith.user.api.ApiErrorResponse;

/** APIを実行する前に、HttpSessionのログイン状態を共通で確認します。 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    private final ObjectMapper objectMapper;

    public AuthInterceptor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler) throws IOException {
        // CORSの事前確認を認証で妨げないよう、OPTIONSはそのまま通します。
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        // falseを指定すると、確認のためだけの新しいセッションを作りません。
        HttpSession session = request.getSession(false);
        Object loginUserId = session == null
                ? null
                : session.getAttribute(UserSession.LOGIN_USER_ID);

        if (loginUserId instanceof Number) {
            return true;
        }

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(
                response.getWriter(),
                new ApiErrorResponse("ログインが必要です。"));
        return false;
    }
}
