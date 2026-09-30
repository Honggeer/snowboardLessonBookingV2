package com.geer.snowboard.v2;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;

@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class SnowboardV2Application {

    public static void main(String[] args) {
        SpringApplication.run(SnowboardV2Application.class, args);
    }
}
