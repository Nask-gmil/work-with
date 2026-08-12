package jp.workwith.realtime;

import java.security.Principal;
import java.util.Map;

import org.springframework.http.server.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

import jp.workwith.user.UserSession;

@Component
public class AuthenticatedHandshakeHandler extends DefaultHandshakeHandler {
    @Override
    protected Principal determineUser(
            ServerHttpRequest request,
            WebSocketHandler webSocketHandler,
            Map<String, Object> attributes) {
        Object userId = attributes.get(UserSession.LOGIN_USER_ID);
        return userId instanceof Number number
                ? () -> Long.toString(number.longValue())
                : null;
    }
}
