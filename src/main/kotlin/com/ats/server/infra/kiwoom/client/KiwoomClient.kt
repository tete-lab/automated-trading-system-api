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

@Component
class KiwoomClient(
    private val apiLogRepository: ApiLogRepository,
    @Value("\${api.kiwoom.base-url}") private val baseUrl: String,
    @Value("\${api.kiwoom.is-mock:false}") private val isMock: Boolean
) {
    private val restTemplate = RestTemplate()

    // isMock에 따라 URL 분기 처리
    private val targetUrl = if (isMock) "\${api.kiwoom.mock-base-url}" else baseUrl

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
            throw e
        } finally {
            // 성공이든 실패(예외)든 로그 저장
            saveApiLog(
                apiName = apiName,
                url = url,
                method = method.name(),
                reqParams = maskedReqParams,
                // 토큰 발급일 땐 응답도 마스킹, 그 외엔 에러메시지 혹은 정상응답 저장
                resBody = if (apiName == "접근토큰발급" && errorMsg == null) "TOKEN_MASKED" else (errorMsg ?: responseBody),
                statusCode = statusCode
            )
        }
    }

    private fun saveApiLog(apiName: String, url: String, method: String, reqParams: String?, resBody: String?, statusCode: Int) {
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
            println("WARN: API 로그 저장 실패 - ${e.message}")
        }
    }

    private fun maskSensitiveData(input: String): String {
        return input.replace(Regex("(appkey|secretkey|token)=[^,)]+"), "$1=****")
    }
}