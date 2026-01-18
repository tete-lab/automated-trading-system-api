package com.ats.server.infra.publicdata.client

import com.ats.server.domain.apilog.dto.ApiLogCreateReq
import com.ats.server.domain.apilog.service.ApiLogService
import com.ats.server.domain.sysconfig.service.SysConfigService
import com.ats.server.infra.publicdata.dto.PublicDataDailyRes
import com.ats.server.infra.publicdata.dto.PublicDataItem
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.util.UriComponentsBuilder

@Component
class PublicDataClient(
    private val sysConfigService: SysConfigService,
    private val objectMapper: ObjectMapper,
    private val apiLogService: ApiLogService, // [1] 로그 서비스 주입
    @Value("\${api.public-data.base-url}") private val baseUrl: String
) {
    private val restClient = RestClient.create()

    private fun getDecodingKey(): String {
        val configRes = sysConfigService.getConfig("PUBLIC_DATA_DECODING_KEY")
        return configRes.configValue
            ?: throw RuntimeException("시스템 설정에 [PUBLIC_DATA_DECODING_KEY] 값이 비어있습니다.")
    }

    /**
     * [수정됨] marketCategory 파라미터 추가 (Nullable)
     * marketCategory: "KOSPI" 또는 "KOSDAQ"
     */
    fun getStockPriceInfo(
        startDate: String,
        endDate: String,
        marketCategory: String? = null // [추가] 시장 구분
    ): List<PublicDataItem> {
        val serviceKey = getDecodingKey()
        val allItems = mutableListOf<PublicDataItem>()

        var pageNo = 1
        val numOfRows = 1000

        while (true) {
            val builder = UriComponentsBuilder.fromHttpUrl("$baseUrl/getStockPriceInfo")
                .queryParam("serviceKey", serviceKey)
                .queryParam("resultType", "json")
                .queryParam("bgndBasDt", startDate)
                .queryParam("endBasDt", endDate)
                .queryParam("numOfRows", numOfRows)
                .queryParam("pageNo", pageNo)

            // [추가] 시장 구분이 있으면 파라미터 추가
            if (marketCategory != null) {
                builder.queryParam("mrktCtg", marketCategory)
            }

            val uri = builder.build().toUri()

            // ... (로그 URL 마스킹 처리 등 기존 로직 유지) ...
            val logUrl = uri.toString().replace(Regex("serviceKey=[^&]+"), "serviceKey=MASKED")
            var statusCode = 0
            var responseBody: String? = null

            try {
                // ... (통신 및 파싱 로직 기존과 동일) ...
                val responseEntity = restClient.get()
                    .uri(uri)
                    .retrieve()
                    .toEntity(String::class.java)

                statusCode = responseEntity.statusCode.value()
                responseBody = responseEntity.body

                if (responseBody == null) break

                val res = objectMapper.readValue(responseBody, PublicDataDailyRes::class.java)
                val body = res.response.body ?: break // body가 null이면 중단
                val items = body.items.item

                // [중요] 데이터가 없으면 루프 즉시 종료
                if (items.isEmpty()) break

                allItems.addAll(items)

                // 전체 개수 도달 시 종료
                if (allItems.size >= body.totalCount) {
                    println("[$marketCategory] 데이터 수집 완료: ${allItems.size}건")
                    break
                }

                // 안전장치 (주식만 가져오면 3페이지를 넘을 수 없음)
                if (pageNo > 10) break
                pageNo++

            } catch (e: Exception) {
                // ... (에러 처리 및 로그 저장 로직 기존과 동일) ...
                break
            } finally {
                saveLog("PublicData_GetPrice_$marketCategory", logUrl, "page=$pageNo", responseBody, statusCode)
            }
        }

        return allItems
    }

    // [4] 로그 저장 헬퍼 메서드
    private fun saveLog(apiName: String, url: String, reqParams: String, resBody: String?, statusCode: Int) {
        try {
            // DB 컬럼 사이즈 제한(TEXT 등)을 고려하여 내용이 너무 길면 자름 (약 6만자)
            val safeResBody = if ((resBody?.length ?: 0) > 60000) {
                resBody?.substring(0, 60000) + "...(truncated)"
            } else {
                resBody
            }

            apiLogService.createLog(
                ApiLogCreateReq(
                    apiName = apiName,
                    url = url,
                    method = "GET",
                    reqParams = reqParams,
                    resBody = safeResBody,
                    statusCode = statusCode
                )
            )
        } catch (e: Exception) {
            // 로그 저장이 실패한다고 메인 로직이 멈추면 안 됨
            println("WARN: API 로그 저장 실패 - ${e.message}")
        }
    }
}