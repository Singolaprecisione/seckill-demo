package com.seckill.config;

import com.google.common.util.concurrent.RateLimiter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RateLimiterConfig {

    @Bean
    public RateLimiter seckillRateLimiter() {
        return RateLimiter.create(1000.0);
    }
}
