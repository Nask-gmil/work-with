package jp.workwith.registration;

import java.net.InetAddress;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.stereotype.Component;

/** Renderの信頼できるプロキシヘッダーからRate Limit用のクライアントIPを解決します。 */
@Component
public class ClientIpResolver {

    public String resolve(HttpServletRequest request) {
        String cloudflareIp = validIp(request.getHeader("CF-Connecting-IP"));
        if (cloudflareIp != null) {
            return cloudflareIp;
        }

        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null) {
            String[] hops = forwardedFor.split(",");
            String lastHop = hops[hops.length - 1].trim();
            String forwardedIp = validIp(lastHop);
            if (forwardedIp != null) {
                return forwardedIp;
            }
        }

        String remoteAddress = validIp(request.getRemoteAddr());
        return remoteAddress == null ? "unknown" : remoteAddress;
    }

    private String validIp(String candidate) {
        if (candidate == null || candidate.isBlank() || candidate.length() > 45) {
            return null;
        }
        String trimmed = candidate.trim();
        boolean looksLikeIpv4 = trimmed.matches("[0-9.]+");
        boolean looksLikeIpv6 = trimmed.contains(":") && trimmed.matches("[0-9A-Fa-f:.]+");
        if (!looksLikeIpv4 && !looksLikeIpv6) {
            return null;
        }
        try {
            return InetAddress.getByName(trimmed).getHostAddress();
        } catch (Exception exception) {
            return null;
        }
    }
}
