package com.ats.server.infra.kis.client

import com.ats.server.domain.apilog.entity.ApiLog
import com.ats.server.domain.apilog.repository.ApiLogRepository
import com.ats.server.domain.token.dto.TokenRes
import com.ats.server.infra.kis.dto.KisApiResult
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.client.RestClientResponseException
import org.springframework.web.client.RestTemplate
import org.springframework.web.util.UriComponentsBuilder


@Component
class KisClient(
    private val apiLogRepository: ApiLogRepository,
    @Value("\${api.kis.base-url}") private val baseUrl: String,
    @Value("\${api.kis.mock-base-url}") private val mockUrl: String,
    @Value("\${api.main-api.is-mock:false}") private val isMock: Boolean,
    private val restTemplate: RestTemplate
) {
    // 로거 설정
    private val log = LoggerFactory.getLogger(javaClass)

    // isMock에 따라 URL 분기 처리
    private val targetUrl = if (isMock) mockUrl else baseUrl

    fun issueToken(appKey: String, secretKey: String): KisApiResult {
        val url = "$targetUrl/oauth2/tokenP"
        val body = mapOf(
            "grant_type" to "client_credentials",
            "appkey" to appKey,
            "appsecret" to secretKey
        )
        val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }

        return callKisApi(url, HttpMethod.POST, HttpEntity(body, headers), "접근토큰발급")
    }

    fun revokeToken(appKey: String, secretKey: String, token: String) {
        val url = "$targetUrl/oauth2/revokeP"
        val body = mapOf(
            "appkey" to appKey,
            "appsecret" to secretKey,
            "token" to token
        )
        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
            setBearerAuth(token)
        }

        callKisApi(url, HttpMethod.POST, HttpEntity(body, headers), "접근토큰폐기")
    }
    private fun callKisApi(
        url: String,
        method: HttpMethod,
        entity: HttpEntity<Map<String, String>>,
        apiName: String
    ): KisApiResult {
        val reqParamsString = entity.body.toString()
        val maskedReqParams = maskSensitiveData(reqParamsString)

        var statusCode = 0
        var responseBody: String? = null
        var errorMsg: String? = null

        val maxRetry = 3
        var retryCount = 0

        try {
            // [중요] getBody()만 하는 게 아니라 ResponseEntity 전체를 받음
            val responseEntity = restTemplate.exchange(url, method, entity, String::class.java)

            val body = responseEntity.body ?: ""
            val headers = responseEntity.headers
            // [🚨 디버깅용 로그 추가] 실제 서버가 뭐라고 응답했는지 확인
            log.info(">>> [$apiName] Raw Response Body: $body")

            // [헤더 파싱] cont-yn과 next-key 추출
            val contYn = headers.getFirst("cont-yn")
            val nextKey = headers.getFirst("next-key")

            val hasNext = "Y".equals(contYn, ignoreCase = true)

            return KisApiResult(body, hasNext, nextKey)

        } catch (e: RestClientResponseException) {
            // 수정 포인트 2: 4xx, 5xx 에러 발생 시 응답 본문(에러 메시지) 가져오기
            statusCode = e.statusCode.value()
            responseBody = e.responseBodyAsString
            errorMsg = "HTTP ERROR: ${e.statusCode} - ${e.responseBodyAsString}"
            log.error(">>> [$apiName] HTTP Error: ${e.statusCode} / Body: ${e.responseBodyAsString}")
            throw e
        } catch (e: Exception) {
            statusCode = 500
            errorMsg = "SYSTEM ERROR: ${e.message}"
            log.error(">>> [$apiName] System Error: ${e.message}")

            retryCount++
            if (retryCount >= maxRetry) {
                log.error(">>> [API FAIL] $apiName 3회 재시도 실패: ${e.message}")
                throw e
            }
            log.warn(">>> [API RETRY] $apiName ($retryCount/$maxRetry) - ${e.message}")
            Thread.sleep(500) // 0.5초 대기 후 재시도

            throw e
        } finally {
            // 성공이든 실패(예외)든 로그 저장
            // 속도 개선을 위해 일단 주석처리
            /*saveApiLog(
                apiName = apiName,
                url = url,
                method = method.name(),
                reqParams = maskedReqParams,
                // 토큰 발급일 땐 응답도 마스킹, 그 외엔 에러메시지 혹은 정상응답 저장
                resBody = if (apiName == "접근토큰발급" && errorMsg == null) "TOKEN_MASKED" else (errorMsg ?: responseBody),
                statusCode = statusCode
            )*/
        }
    }
    // [수정] REQUIRES_NEW를 사용하여 호출한 곳의 트랜잭션과 상관없이 별도의 쓰기 트랜잭션을 실행합니다.
    @Async // 비동기로 실행하여 메인 로직의 대기를 없앰
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun saveApiLog(apiName: String, url: String, method: String, reqParams: String?, resBody: String?, statusCode: Int) {
        try {
            val safeResBody = if ((resBody?.length ?: 0) > 60000) {
                resBody?.substring(0, 60000) + "...(truncated)"
            } else {
                resBody
            }

            apiLogRepository.save(
                ApiLog(
                    apiName = apiName,
                    url = url,
                    method = method,
                    reqParams = reqParams,
                    resBody = safeResBody,
                    statusCode = statusCode
                )
            )
        } catch (e: Exception) {
            log.info("WARN: API 로그 저장 실패 - ${e.message}")
        }
    }

    private fun maskSensitiveData(input: String): String {
        return input.replace(Regex("(appkey|secretkey|token)=[^,)]+"), "$1=****")
    }




    /**
     * [국내주식기간별시세(일_주_월_년)]
     * API ID: FHKST03010100
     * URL: /uapi/domestic-stock/v1/quotations/inquire-daily-itemchartprice
     */
    fun fetchPeriodPrice(
        token: String,
        appKey: String,
        appSecret: String,
        stockCode: String,
        startDate: String, // YYYYMMDD
        endDate: String,   // YYYYMMDD
        periodCode: String = "D", // D:일, W:주, M:월, Y:년
        orgAdjPrice: String = "1" // 1:수정주가반영
    ): KisApiResult {
        val path = "/uapi/domestic-stock/v1/quotations/inquire-daily-itemchartprice"
        val url = "$targetUrl$path"

        val uri = UriComponentsBuilder.fromHttpUrl(url)
            .queryParam("FID_COND_MRKT_DIV_CODE", "J") // J: 주식
            .queryParam("FID_INPUT_ISCD", stockCode)     // 종목코드
            .queryParam("FID_INPUT_DATE_1", startDate)   // 시작일
            .queryParam("FID_INPUT_DATE_2", endDate)     // 종료일
            .queryParam("FID_PERIOD_DIV_CODE", periodCode)
            .queryParam("FID_ORG_ADJ_PRC", orgAdjPrice)
            .build()
            .toUriString()

        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
            setBearerAuth(token)
            set("appkey", appKey)
            set("appsecret", appSecret)
            set("tr_id", "FHKST03010100") // [중요] 기간별시세 TR ID
            set("custtype", "P")
        }

        // GET 방식 호출
        return callKisApi(uri, HttpMethod.GET, HttpEntity(null, headers), "기간별시세요청")
    }

    /**
     * [국내주식 재무비율]
     * API ID: v1_국내주식-080
     * TR_ID: FHKST66430300
     * URL: /uapi/domestic-stock/v1/finance/financial-ratio
     */
    fun fetchFinancialRatio(
        token: String,
        appKey: String,
        appSecret: String,
        stockCode: String
    ): KisApiResult {
        // [안전장치] 이 API는 모의투자를 지원하지 않음
        if (targetUrl.contains("openapivts")) { // 혹은 isMock 변수 확인
            throw RuntimeException("⚠️ [재무비율 API]는 모의투자를 지원하지 않습니다. 실전투자(Real) 환경에서 실행해주세요.")
        }

        val path = "/uapi/domestic-stock/v1/finance/financial-ratio"
        val url = "$targetUrl$path"
        val divClassCode = "1"
        val uri = UriComponentsBuilder.fromHttpUrl(url)
            .queryParam("FID_COND_MRKT_DIV_CODE", "J") // J: 주식
            .queryParam("FID_INPUT_ISCD", stockCode)     // 종목코드
            // 만약 분기/연간 구분이 필요하다면 "FID_DIV_CLS_CODE" 파라미터 확인 필요 (문서상 기본값 사용 시 생략 가능)
            .queryParam("FID_DIV_CLS_CODE", divClassCode)
            .build()
            .toUriString()

        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
            setBearerAuth(token)
            set("tr_id", "FHKST66430300") // 재무비율 TR ID
            set("custtype", "P")          // 개인
            set("appkey", appKey)         // 필수
            set("appsecret", appSecret)   // 필수
        }

        return callKisApi(uri, HttpMethod.GET, HttpEntity(null, headers), "재무비율요청")
    }
}