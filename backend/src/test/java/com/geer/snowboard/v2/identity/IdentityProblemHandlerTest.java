package com.geer.snowboard.v2.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.geer.snowboard.v2.identity.adapter.in.web.IdentityProblemHandler;
import com.geer.snowboard.v2.identity.application.port.in.RateLimited;
import java.util.Arrays;
import java.lang.reflect.Method;
import org.springframework.dao.DataAccessException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;

class IdentityProblemHandlerTest {
    @Test
    void rateLimitContractStillMapsToHttp429() throws NoSuchMethodException {
        var method = IdentityProblemHandler.class.getMethod("limited");
        var annotation = method.getAnnotation(ExceptionHandler.class);
        assertTrue(Arrays.asList(annotation.value()).contains(RateLimited.class));
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, new IdentityProblemHandler().limited().getStatusCode());
    }

    @Test
    @SuppressWarnings("unchecked")
    void databaseOutageReturnsGenericServiceUnavailable() throws Exception {
        Method method = IdentityProblemHandler.class.getMethod("databaseUnavailable", DataAccessException.class);
        var response = (ResponseEntity<java.util.Map<String, Object>>) method.invoke(new IdentityProblemHandler(),
                new DataAccessResourceFailureException("sensitive database address"));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertEquals("服务暂时不可用，请稍后重试", response.getBody().get("detail"));
        assertTrue(!response.getBody().toString().contains("sensitive database address"));
    }
}
