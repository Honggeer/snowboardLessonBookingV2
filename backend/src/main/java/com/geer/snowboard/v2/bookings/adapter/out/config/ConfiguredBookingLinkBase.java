package com.geer.snowboard.v2.bookings.adapter.out.config;

import com.geer.snowboard.v2.bookings.application.port.out.BookingLinkBase;
import java.net.URI;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class ConfiguredBookingLinkBase implements BookingLinkBase {
    private final String value;

    public ConfiguredBookingLinkBase(Environment environment) {
        String configured = environment.getProperty("identity.public-url", "").replaceAll("/+$", "");
        URI uri = URI.create(configured);
        String scheme = uri.getScheme();
        if ((!"http".equals(scheme) && !"https".equals(scheme))
                || uri.getHost() == null || uri.getUserInfo() != null
                || uri.getQuery() != null || uri.getFragment() != null
                || (uri.getPath() != null && !uri.getPath().isEmpty())) {
            throw new IllegalArgumentException("Configure an absolute APP_PUBLIC_URL for booking mail");
        }
        this.value = configured;
    }

    @Override
    public String value() {
        return value;
    }
}
