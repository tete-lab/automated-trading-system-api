package com.ats.server.infra.krx.client

import com.ats.server.domain.sysconfig.service.SysConfigService
import com.ats.server.infra.krx.dto.KrxKosdaqDailyRes
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate

@Component
class KrxKosdaqClient (
    @Value("\${api.krx.base-url}") private val baseUrl: String,
    private val sysConfigService: SysConfigService,
    private val restTemplate: RestTemplate
){


    val url = "$baseUrl/sto/stk_bydd_trd"

    private fun getApiKey(): String {
        val configRes = sysConfigService.getConfig("KRX_API_KEY")
        return configRes.configValue
            ?: throw RuntimeException("시스템 설정에 [KRX_KEY] 값이 비어있습니다.")
    }

    fun fetchKosdaqPrices(targetDate: String): KrxKosdaqDailyRes? {

        val apiKey = getApiKey()

        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
            set("AUTH_KEY", apiKey) // KRX 명세서 표준 인증 헤더 키
        }

        val body = mapOf("basDd" to targetDate)
        val entity = HttpEntity(body, headers)

        return try {
            restTemplate.postForObject(url, entity, KrxKosdaqDailyRes::class.java)
        } catch (e: Exception) {
            null
        }
    }
}