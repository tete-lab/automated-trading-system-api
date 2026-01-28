package com.ats.server.domain.token.service

import com.ats.server.domain.sysconfig.repository.SysConfigRepository
import com.ats.server.domain.account.repository.MemberAccountRepository
import com.ats.server.domain.token.dto.TokenFindReq
import com.ats.server.domain.token.dto.TokenRes
import com.ats.server.domain.token.entity.Token
import com.ats.server.domain.token.repository.TokenRepository
import com.ats.server.infra.kis.client.KisClient
import com.ats.server.infra.kiwoom.client.KiwoomClient
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter


@Service
class TokenService(
    private val tokenRepository: TokenRepository,
    private val sysConfigRepository: SysConfigRepository,
    private val memberAccountRepository: MemberAccountRepository,
    private val kiwoomClient: KiwoomClient,
    private val objectMapper: ObjectMapper,
    @Value("\${api.main-api.is-mock:false}") private val isMock: Boolean,
    private val kisClient: KisClient
) {

    // 로거 설정
    private val log = LoggerFactory.getLogger(javaClass)

    private val dateFormatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")

    /**
     * 유효한 토큰 가져오기 (만료 시 자동 갱신)
     * Input: TokenFindReq (memberId 포함)
     * Output: TokenRes (Token 정보)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun getValidToken(req: TokenFindReq): TokenRes {
        // [수정됨] 입력값 전처리: "string"이나 공백이 들어오면 null(시스템 토큰)로 처리
        val memberId: String? = if (req.memberId.isNullOrBlank() || req.memberId == "string") {
            null
        } else {
            req.memberId
        }
        val apiName: String? = req.apiName
        val lastToken = tokenRepository.findFirstByMemberIdOrApiNameOrderByCreatedAtDesc(memberId,apiName)

        // 1. 유효한 토큰이 있는지 확인 (만료 1시간 전까지 유효)
        if (lastToken != null && lastToken.expiredDt.isAfter(LocalDateTime.now().plusHours(1))) {
            return TokenRes.from(lastToken)
        }

        // 2. 만료 임박/경과 시 기존 토큰 폐기 (선택 사항이나 권장됨)
        if (lastToken != null) {
            revokeToken(lastToken)
        }

        // 3. 새 토큰 발급
        return issueNewToken(memberId, apiName)
    }

    private fun issueNewToken(memberId: String?, apiName: String?): TokenRes {
        val (appKey, secretKey) = getAppKeys(memberId, apiName)

        var accessToken = ""
        var expiredDt: LocalDateTime? = null
        var tokenType = "Bearer"

        // 날짜 포맷터 (한투는 "yyyy-MM-dd HH:mm:ss" 형식으로 옴)
        val kisDateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

        // 2. 증권사별 API 호출 및 파싱 (필드명이 달라서 여기서 파싱해야 함)
        when (apiName) {
            "KIWOOM" -> {
                // [키움증권]
                val result = kiwoomClient.issueToken(appKey, secretKey)
                val node = objectMapper.readTree(result.body) // JSON 문자열 파싱

                // 키움 응답 필드명에 맞춰 추출 (예시)
                accessToken = node.path("token").asText()
                val expiredStr = node.path("expires_dt").asText()

                // 키움 날짜 포맷에 맞춰 파싱 (기존 dateFormatter 사용)
                expiredDt = LocalDateTime.parse(expiredStr, dateFormatter)
            }

            "KIS" -> { // 한국투자증권
                // [한국투자증권]
                val result = kisClient.issueToken(appKey, secretKey)
                val node = objectMapper.readTree(result.body) // result.toString() 아님! .body 써야 함

                // 한투 응답 필드명 (명세서 기준)
                accessToken = node.path("access_token").asText()
                val expiredStr = node.path("access_token_token_expired").asText() // "2026-01-01 12:00:00"

                tokenType = node.path("token_type").asText("Bearer")

                // 한투 전용 포맷터로 파싱
                expiredDt = LocalDateTime.parse(expiredStr, kisDateFormatter)
            }

            else -> throw IllegalArgumentException("지원하지 않는 증권사입니다: $apiName")
        }

        // DB 저장
        val newTokenEntity = Token(
            token = accessToken,
            tokenType = tokenType,
            expiredDt = expiredDt,
            memberId = memberId,
            apiName = apiName
        )
        tokenRepository.save(newTokenEntity)

        // DTO 반환
        return TokenRes(
            token = accessToken,
            tokenType = tokenType,
            expiredDt = expiredDt,
            apiName = apiName
        )
    }

    fun revokeToken(tokenEntity: Token) {
        try {
            val (appKey, secretKey) = getAppKeys(tokenEntity.memberId, tokenEntity.apiName)
            // Client 호출
            kiwoomClient.revokeToken(appKey, secretKey, tokenEntity.token)
        } catch (e: Exception) {
            // 폐기 실패해도 DB 삭제는 진행하거나 로그만 남김
            log.info("토큰 폐기 API 호출 실패 (이미 만료되었을 수 있음): ${e.message}")
        }

        // DB 삭제
        tokenRepository.delete(tokenEntity)
    }

    fun getAppKeys(memberId: String?,apiName: String?): Pair<String, String> {
        return (if (memberId == null) {
            var appKeyValue = ""
            var appSecretValue = ""
            if(apiName.equals("KIWOOM")){
                appKeyValue =  "MAIN_KIWOOM_APP_KEY"
                appSecretValue = "MAIN_KIWOOM_APP_SECRET"
                if(isMock) {
                    appKeyValue = "MOCK_KIWOOM_APP_KEY"
                    appSecretValue = "MOCK_KIWOOM_APP_SECRET"
                }
            }else{
                appKeyValue =  "MAIN_KIS_APP_KEY"
                appSecretValue = "MAIN_KIS_APP_SECRET"
                if(isMock) {
                    appKeyValue = "MOCK_KIS_APP_KEY"
                    appSecretValue = "MOCK_KIS_APP_SECRET"
                }
            }

            val appKey = sysConfigRepository.findById(appKeyValue).orElseThrow().configValue
            val secretKey = sysConfigRepository.findById(appSecretValue).orElseThrow().configValue
            Pair(appKey, secretKey)
        } else {
            val appKey = memberAccountRepository.findById(memberId.toLong()).orElseThrow().apiKey
            var secretKey = memberAccountRepository.findById(memberId.toLong()).orElseThrow().secretKey
        }) as Pair<String, String>
    }
}