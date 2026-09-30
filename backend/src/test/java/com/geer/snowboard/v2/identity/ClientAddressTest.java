package com.geer.snowboard.v2.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.geer.snowboard.v2.identity.adapter.in.web.ClientAddress;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class ClientAddressTest {
    @Test void ignoresForgedHeadersFromDirectClients() {
        MockHttpServletRequest direct = new MockHttpServletRequest();
        direct.setRemoteAddr("198.51.100.10");
        direct.addHeader("X-Real-IP", "203.0.113.20");
        assertThat(new ClientAddress("localhost").from(direct)).isEqualTo("198.51.100.10");
    }

    @Test void acceptsOnlyLiteralAddressSetByTrustedProxy() {
        MockHttpServletRequest proxied = new MockHttpServletRequest();
        proxied.setRemoteAddr("127.0.0.1");
        proxied.addHeader("X-Real-IP", "203.0.113.20");
        assertThat(new ClientAddress("localhost").from(proxied)).isEqualTo("203.0.113.20");
    }
}
