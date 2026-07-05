package com.yeogido.backend.global.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI openAPI() {

        Info info = new Info()
                .title("여기도 API")
                .version("v1.0.0")
                .description("여기도 백엔드 API 문서");

        return new OpenAPI()
                .info(info);
    }

}