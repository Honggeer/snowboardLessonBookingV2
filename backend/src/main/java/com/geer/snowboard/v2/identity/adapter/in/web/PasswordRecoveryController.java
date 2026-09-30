package com.geer.snowboard.v2.identity.adapter.in.web;

import com.geer.snowboard.v2.identity.application.port.in.PasswordRecoveryOperations;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@RequestMapping("/api/auth/password-recovery")
public class PasswordRecoveryController {
    private static final String GRANT = "identity.passwordResetGrant";
    private final PasswordRecoveryOperations recovery;
    private final ClientAddress addresses;
    private final CsrfTokenRepository csrfTokens;

    public PasswordRecoveryController(PasswordRecoveryOperations recovery, ClientAddress addresses,
                                      CsrfTokenRepository csrfTokens) {
        this.recovery = recovery;
        this.addresses = addresses;
        this.csrfTokens = csrfTokens;
    }

    public record EmailRequest(String email) {}
    public record CodeRequest(String email, String code) {}
    public record CompleteRequest(String newPassword, String confirmPassword) {}

    @PostMapping("/request")
    public Map<String, String> request(@RequestBody EmailRequest body, HttpServletRequest request) {
        var session = request.getSession(false);
        if (session != null) session.removeAttribute(GRANT);
        recovery.request(body.email(), addresses.from(request));
        return Map.of("message", "如果账号可找回，请查收验证码。");
    }

    @PostMapping("/verify")
    public Map<String, String> verify(@RequestBody CodeRequest body, HttpServletRequest request) {
        var previous = request.getSession(false);
        if (previous != null) previous.removeAttribute(GRANT);
        PasswordRecoveryOperations.Grant grant = recovery.verify(body.email(), body.code(), addresses.from(request));
        if (grant == null) throw new ResponseStatusException(HttpStatus.GONE, "验证码无效或已失效，请重新申请。");
        var session = request.getSession(true);
        request.changeSessionId();
        session.setAttribute(GRANT, grant);
        csrfTokens.saveToken(null, request, null);
        return Map.of("message", "邮箱验证成功，请设置新密码。");
    }

    @PostMapping("/complete")
    public Map<String, String> complete(@RequestBody CompleteRequest body, HttpServletRequest request) {
        var session = request.getSession(false);
        var grant = session == null ? null : session.getAttribute(GRANT);
        if (!recovery.complete(grant instanceof PasswordRecoveryOperations.Grant value ? value : null,
                body.newPassword(), body.confirmPassword())) {
            if (session != null) session.removeAttribute(GRANT);
            throw new ResponseStatusException(HttpStatus.GONE, "重设授权已失效，请重新申请验证码。");
        }
        if (session != null) session.invalidate();
        SecurityContextHolder.clearContext();
        return Map.of("message", "密码已更新，请重新登录。");
    }
}
