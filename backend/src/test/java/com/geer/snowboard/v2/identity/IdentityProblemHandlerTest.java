package com.geer.snowboard.v2.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.geer.snowboard.v2.identity.adapter.in.web.IdentityProblemHandler;
import com.geer.snowboard.v2.identity.application.port.in.RateLimited;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;

class IdentityProblemHandlerTest {
    @Test
    void rateLimitContractStillMapsToHttp429() throws NoSuchMethodException {
        var method = IdentityProblemHandler.class.getMethod("limited");
        var annotation = method.getAnnotation(ExceptionHandler.class);
        assertTrue(Arrays.asList(annotation.value()).contains(RateLimited.class));
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, new IdentityProblemHandler().limited().getStatusCode());
    }
}
