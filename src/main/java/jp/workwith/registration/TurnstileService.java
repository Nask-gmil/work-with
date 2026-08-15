package jp.workwith.registration;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Cloudflare SiteverifyでTurnstileトークンをサーバー側検証します。 */
@Service
public class TurnstileService {

    private static final String SITEVERIFY_URL =
            "https://challenges.cloudflare.com/turnstile/v0/siteverify";

    private final RestClient restClient;
    private final String secretKey;

    public TurnstileService(RestClient.Builder restClientBuilder,
            @Value("${turnstile.secret-key:}") String secretKey) {
        this.restClient = restClientBuilder.build();
        this.secretKey = secretKey;
    }

    public boolean verify(String token, String clientIp) {
        if (token == null || token.isBlank() || token.length() > 2048) {
            return false;
        }
        if (secretKey == null || secretKey.isBlank()) {
            throw new TurnstileUnavailableException();
        }

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("secret", secretKey);
        form.add("response", token);
        if (clientIp != null && !clientIp.equals("unknown")) {
            form.add("remoteip", clientIp);
        }

        try {
            SiteverifyResponse response = restClient.post()
                    .uri(SITEVERIFY_URL)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(SiteverifyResponse.class);
            return response != null && response.success();
        } catch (RestClientException exception) {
            throw new TurnstileUnavailableException(exception);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record SiteverifyResponse(boolean success) {
    }
}
