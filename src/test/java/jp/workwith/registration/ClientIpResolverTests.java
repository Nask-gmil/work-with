package jp.workwith.registration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class ClientIpResolverTests {

    private final ClientIpResolver resolver = new ClientIpResolver();

    @Test
    void usesLastAddressFromMultipleForwardedForHops() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "192.0.2.10, 198.51.100.20, 203.0.113.30");

        assertThat(resolver.resolve(request)).isEqualTo("203.0.113.30");
    }

    @Test
    void ignoresClientSuppliedSpoofedAddressBeforeProxyAppendedAddress() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "192.0.2.123, 198.51.100.45");

        assertThat(resolver.resolve(request)).isEqualTo("198.51.100.45");
        assertThat(resolver.resolve(request)).isNotEqualTo("192.0.2.123");
    }

    @Test
    void stillPrefersCloudflareConnectingIp() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("CF-Connecting-IP", "203.0.113.80");
        request.addHeader("X-Forwarded-For", "192.0.2.10, 198.51.100.20");

        assertThat(resolver.resolve(request)).isEqualTo("203.0.113.80");
    }
}
