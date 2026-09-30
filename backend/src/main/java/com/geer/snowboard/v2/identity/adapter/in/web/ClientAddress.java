package com.geer.snowboard.v2.identity.adapter.in.web;

import jakarta.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.net.UnknownHostException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ClientAddress {
    private final String trustedProxyHost;
    public ClientAddress(@Value("${identity.trusted-proxy-host:}") String trustedProxyHost) {
        this.trustedProxyHost = trustedProxyHost;
    }

    public String from(HttpServletRequest request) {
        String remote = request.getRemoteAddr();
        if (trustedProxyHost.isBlank()) return remote;
        try {
            boolean trusted = false;
            for (InetAddress address : InetAddress.getAllByName(trustedProxyHost)) {
                if (address.getHostAddress().equals(remote)) { trusted = true; break; }
            }
            if (!trusted) return remote;
        } catch (UnknownHostException exception) {
            return remote;
        }
        String forwarded = request.getHeader("X-Real-IP");
        if (forwarded == null || forwarded.length() > 45
                || !forwarded.matches("[0-9a-fA-F:.]+")) return remote;
        return forwarded;
    }
}
