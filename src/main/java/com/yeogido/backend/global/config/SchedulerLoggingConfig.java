package com.yeogido.backend.global.config;

import com.yeogido.backend.global.logging.SafeSchedulerErrorHandler;
import org.springframework.boot.task.ThreadPoolTaskSchedulerCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class SchedulerLoggingConfig {

    @Bean
    ThreadPoolTaskSchedulerCustomizer safeSchedulerErrorHandlerCustomizer() {
        return scheduler -> scheduler.setErrorHandler(new SafeSchedulerErrorHandler());
    }
}
