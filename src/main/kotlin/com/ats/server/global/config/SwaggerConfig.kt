package com.ats.server.global.config

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class SwaggerConfig {
    @Bean
    fun openApi(): OpenAPI {
        return OpenAPI()
            .info(Info()
                .title("ATS 주식 자동매매 시스템 API")
                .description("주식 자동매매 서버 API 명세서입니다.")
                .version("v1.0.0"))
    }
}