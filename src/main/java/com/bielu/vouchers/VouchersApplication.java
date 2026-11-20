package com.bielu.vouchers;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.time.Clock;

@SpringBootApplication
public class VouchersApplication {

    public static void main(String[] args) {
        SpringApplication.run(VouchersApplication.class, args);
    }

    /** Injected instead of calling Instant.now() directly, so time is controllable in tests. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
