package jp.workwith.config;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** ブラウザへ公開してよい設定だけを返します。Turnstile secret keyは扱いません。 */
@RestController
public class PublicConfigController {

    private final String turnstileSiteKey;

    public PublicConfigController(@Value("${turnstile.site-key:}") String turnstileSiteKey) {
        this.turnstileSiteKey = turnstileSiteKey;
    }

    @GetMapping("/api/public-config")
    public Map<String, String> publicConfig() {
        return Map.of("turnstileSiteKey", turnstileSiteKey);
    }
}
