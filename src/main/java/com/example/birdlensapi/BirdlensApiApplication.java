package com.example.birdlensapi;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class BirdlensApiApplication {

    private static final Logger log = LoggerFactory.getLogger(BirdlensApiApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(BirdlensApiApplication.class, args);
    }

    @Bean
    public ApplicationRunner logStartupUrls(Environment env) {
        return args -> {
            String port = env.getProperty("server.port", "8088");
            log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            log.info("  🐦 Birdlens API is up and running!");
            log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            log.info("  Health Check  → http://localhost:{}/api/v1/health", port);
            log.info("  Swagger UI    → http://localhost:{}/swagger-ui/index.html", port);
            log.info("  API Docs      → http://localhost:{}/v3/api-docs", port);
            log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        };
    }
}