package com.ats.server.infra.kiwoom.client

import com.ats.server.domain.apilog.entity.ApiLog
import com.ats.server.domain.apilog.repository.ApiLogRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClientResponseException
import org.springframework.web.client.RestTemplate
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.annotation.Propagation
@Component
class KiwoomClient(
    private val apiLogRepository: ApiLogRepository,
    @Value("\${api.kiwoom.base-url}") private val baseUrl: String,
    @Value("\${api.kiwoom.mock-base-url}") private val mockUrl: String,
    @Value("\${api.kiwoom.is-mock:false}") private val isMock: Boolean,
    private val restTemplate: RestTemplate
) {

    // 로거 설정
    private val log = LoggerFactory.getLogger(javaClass)

    // isMock에 따라 URL 분기 처리
    private val targetUrl = if (isMock) mockUrl else baseUrl

    fun issueToken(appKey: String, secretKey: String): String {
        val url = "$targetUrl/oauth2/token"
        val body = mapOf(
            "grant_type" to "client_credentials",
            "appkey" to appKey,
            "secretkey" to secretKey
        )
        val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }

        return callKiwoomApi(url, HttpMethod.POST, HttpEntity(body, headers), "접근토큰발급")
    }

    fun revokeToken(appKey: String, secretKey: String, token: String) {
        val url = "$targetUrl/oauth2/revoke"
        val body = mapOf(
            "appkey" to appKey,
            "secretkey" to secretKey,
            "token" to token
        )
        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
            setBearerAuth(token)
        }

        callKiwoomApi(url, HttpMethod.POST, HttpEntity(body, headers), "접근토큰폐기")
    }

    private fun callKiwoomApi(
        url: String,
        method: HttpMethod,
        entity: HttpEntity<Map<String, String>>,
        apiName: String
    ): String {
        val reqParamsString = entity.body.toString()
        val maskedReqParams = maskSensitiveData(reqParamsString)

        var statusCode = 0
        var responseBody: String? = null
        var errorMsg: String? = null

        val maxRetry = 3
        var retryCount = 0

        try {
            // 수정 포인트 1: String.class -> String::class.java
            val responseEntity = restTemplate.exchange(url, method, entity, String::class.java)

            statusCode = responseEntity.statusCode.value()
            responseBody = responseEntity.body ?: ""
            return responseBody

        } catch (e: RestClientResponseException) {
            // 수정 포인트 2: 4xx, 5xx 에러 발생 시 응답 본문(에러 메시지) 가져오기
            statusCode = e.statusCode.value()
            responseBody = e.responseBodyAsString
            errorMsg = "HTTP ERROR: ${e.statusCode} - ${e.responseBodyAsString}"
            throw e
        } catch (e: Exception) {
            statusCode = 500
            errorMsg = "SYSTEM ERROR: ${e.message}"

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
     * ka10005: 주식일주월시분요청 (POST 방식)
     * 문서 27p 기준: /api/dostk/mrkcond
     */
    fun fetchDailyPrice(token: String, stockCode: String, strTarget: String): String {
        val path = "/api/dostk/mrkcond"
        val url = "$targetUrl$path"

        // [수정] POST 방식이므로 Body에 파라미터를 담습니다.
        val body = mapOf(
            "stk_cd" to stockCode,
            "qry_dt" to strTarget,
            "indc_tp" to "0" //0:수량, 1:금액(백만원) 문서엔 이렇게 써있는데 상관없는듯;
        )

        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
            setBearerAuth(token)
            // set("authorization","Bearer" + token)
            set("api-id", "ka10086") // [중요] tr명
            set("cont-yn", "N")         // 연속조회여부
            set("next-key", "")         // 연속조회키

        }

        // 기존에 만드신 callKiwoomApi(url, HttpMethod.POST, ...)를 그대로 활용
        return callKiwoomApi(url, HttpMethod.POST, HttpEntity(body, headers), "일별주가요청")
    }
}