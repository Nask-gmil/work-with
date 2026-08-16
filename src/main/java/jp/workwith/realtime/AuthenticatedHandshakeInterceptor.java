package jp.workwith.realtime;

import java.util.Map;

import jakarta.servlet.http.HttpSession;

import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import jp.workwith.user.UserSession;
import jp.workwith.security.SecurityEventLogger;

@Component
public class AuthenticatedHandshakeInterceptor implements HandshakeInterceptor {

    private final SecurityEventLogger securityEventLogger;

    public AuthenticatedHandshakeInterceptor(SecurityEventLogger securityEventLogger) {
        this.securityEventLogger = securityEventLogger;
    }

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler webSocketHandler,
            Map<String, Object> attributes) {
        if (!(request instanceof ServletServerHttpRequest servletRequest)) {
            securityEventLogger.websocketForbidden(null, null, "HANDSHAKE", null);
            return false;
        }
        HttpSession session = servletRequest.getServletRequest().getSession(false);
        Object loginUserId = session == null
                ? null
                : session.getAttribute(UserSession.LOGIN_USER_ID);
        if (!(loginUserId instanceof Number)) {
            securityEventLogger.websocketForbidden(null, null, "HANDSHAKE", null);
            return false;
        }
        attributes.put(UserSession.LOGIN_USER_ID, loginUserId);
        return true;
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler webSocketHandler,
            Exception exception) {
        // 後処理はありません。
    }
}
