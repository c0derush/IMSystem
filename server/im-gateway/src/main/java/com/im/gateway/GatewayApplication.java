package com.im.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = {
        "com.im.gateway",
        "com.im.user",
        "com.im.message",
        "com.im.session",
        "com.im.push",
        "com.im.group"
})
@EnableJpaRepositories(basePackages = {
        "com.im.user.repository",
        "com.im.message.repository",
        "com.im.group.repository"
})
@EntityScan(basePackages = {
        "com.im.user.entity",
        "com.im.message.entity",
        "com.im.group.entity"
})
public class GatewayApplication {
    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
