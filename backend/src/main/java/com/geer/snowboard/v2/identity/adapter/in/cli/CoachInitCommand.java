package com.geer.snowboard.v2.identity.adapter.in.cli;

import com.geer.snowboard.v2.identity.application.port.in.IdentityOperations;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "identity.coach-init", havingValue = "true")
public class CoachInitCommand implements ApplicationRunner {
    private final IdentityOperations identity;
    public CoachInitCommand(IdentityOperations identity) { this.identity = identity; }

    @Override public void run(ApplicationArguments args) throws Exception {
        var console = System.console();
        if (console != null) {
            String name = console.readLine("教练姓名: ");
            String email = console.readLine("教练邮箱: ");
            char[] password = console.readPassword("教练密码: ");
            try { identity.createCoach(name, email, new String(password)); }
            finally { java.util.Arrays.fill(password, '\0'); }
        } else {
            initialize(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        }
        System.out.println("教练待验证账号已创建。请配置邮件并完成邮箱验证。");
    }

    public void initialize(Reader input) throws IOException {
        BufferedReader reader = input instanceof BufferedReader buffered ? buffered : new BufferedReader(input);
        String name = reader.readLine();
        String email = reader.readLine();
        String password = reader.readLine();
        if (name == null || email == null || password == null) {
            throw new IllegalArgumentException("Expected name, email and password on three lines");
        }
        identity.createCoach(name.strip(), email.strip(), password);
    }
}
