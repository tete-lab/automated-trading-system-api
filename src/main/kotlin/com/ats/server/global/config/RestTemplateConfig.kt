package com.ats.server.global.config // 패키지는 상황에 맞게

import org.apache.hc.client5.http.impl.classic.HttpClients
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory
import org.springframework.web.client.RestTemplate
import java.time.Duration

@Configuration
class RestTemplateConfig {

    @Bean
    fun restTemplate(): RestTemplate {
        // 1. 커넥션 풀 설정
        val connectionManager = PoolingHttpClientConnectionManager().apply {
            maxTotal = 200           // 전체 최대 커넥션 수
            defaultMaxPerRoute = 50  // 호스트(키움) 당 최대 커넥션 수
        }

        // 2. HttpClient 생성 (타임아웃 설정 포함)
        val httpClient = HttpClients.custom()
            .setConnectionManager(connectionManager)
            .build()

        // 3. Factory 설정
        val factory = HttpComponentsClientHttpRequestFactory(httpClient)
        // [중요] 30초나 기다리지 말고 3~5초 안에 응답 없으면 바로 에러 뱉고 재시도하는 게 낫습니다.
        factory.setConnectTimeout(3000)
        factory.setConnectionRequestTimeout(3000)

        return RestTemplate(factory)
    }
}