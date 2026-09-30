package com.geer.snowboard.v2.identity.adapter.in.web;

import com.geer.snowboard.v2.identity.application.port.in.AccountView;
import com.geer.snowboard.v2.identity.application.port.in.IdentityOperations;
import com.geer.snowboard.v2.identity.application.port.in.RegisterCommand;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@RequestMapping("/api/auth")
public class IdentityController {
    private final IdentityOperations identity;
    private final SecurityContextRepository contexts;
    private final CsrfTokenRepository csrfTokens;
    private final Clock clock;
    private final ClientAddress addresses;

    public IdentityController(IdentityOperations identity, SecurityContextRepository contexts,
                              CsrfTokenRepository csrfTokens, Clock clock, ClientAddress addresses) {
        this.identity = identity;
        this.contexts = contexts;
        this.csrfTokens = csrfTokens;
        this.clock = clock;
        this.addresses = addresses;
    }

    @GetMapping("/csrf")
    public Map<String, String> csrf(CsrfToken token) {
        return Map.of("token", token.getToken(), "headerName", token.getHeaderName());
    }

    @PostMapping("/register")
    public Map<String, String> register(@RequestBody RegisterCommand command, HttpServletRequest request) {
        identity.register(command, addresses.from(request));
        return Map.of("message", "请检查邮箱；如果可以注册，验证链接会发送到该邮箱。");
    }

    public record TokenRequest(String token) {}
    @PostMapping("/email-verification")
    public Map<String, String> verify(@RequestBody TokenRequest body) {
        if (!identity.verify(body.token())) throw new ResponseStatusException(HttpStatus.GONE, "验证链接已失效");
        return Map.of("message", "邮箱已验证");
    }

    public record EmailRequest(String email) {}
    @PostMapping("/email-verification/resend")
    public Map<String, String> resend(@RequestBody EmailRequest body, HttpServletRequest request) {
        if (body.email() == null || body.email().isBlank()) throw new IllegalArgumentException("Invalid email");
        identity.resend(body.email(), addresses.from(request));
        return Map.of("message", "请检查邮箱；如果可以重发，我们会尽快处理。");
    }

    public record LoginRequest(String email, String password) {}
    @PostMapping("/login")
    public AccountView login(@RequestBody LoginRequest body, HttpServletRequest request, HttpServletResponse response) {
        AccountView account = identity.authenticate(body.email(), body.password(), addresses.from(request));
        if (account == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "邮箱或密码不正确");
        var session = request.getSession(true);
        request.changeSessionId();
        session.setAttribute("identity.authenticatedAt", clock.millis());
        var authentication = UsernamePasswordAuthenticationToken.authenticated(account.id(), null,
                List.of(new SimpleGrantedAuthority("ROLE_" + account.role())));
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        contexts.saveContext(context, request, response);
        csrfTokens.saveToken(null, request, response);
        return account;
    }

    @GetMapping("/me")
    public AccountView me() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        AccountView account = identity.findById(principal.toString());
        if (account == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        return account;
    }

    @PostMapping("/logout")
    public Map<String, String> logout(HttpServletRequest request) {
        var session = request.getSession(false);
        if (session != null) session.invalidate();
        SecurityContextHolder.clearContext();
        return Map.of("message", "已退出登录");
    }
}
