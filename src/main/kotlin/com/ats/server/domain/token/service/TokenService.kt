package com.ats.server.domain.token.service

import com.ats.server.domain.sysconfig.repository.SysConfigRepository
import com.ats.server.domain.account.repository.MemberAccountRepository
import com.ats.server.domain.token.dto.TokenFindReq
import com.ats.server.domain.token.dto.TokenRes
import com.ats.server.domain.token.entity.Token
import com.ats.server.domain.token.repository.TokenRepository
import com.ats.server.infra.kiwoom.client.KiwoomClient
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Service
class TokenService(
    private val tokenRepository: TokenRepository,
    private val sysConfigRepository: SysConfigRepository,
    private val memberAccountRepository: MemberAccountRepository,
    private val kiwoomClient: KiwoomClient,
    private val objectMapper: ObjectMapper
) {
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")

    /**
     * 유효한 토큰 가져오기 (만료 시 자동 갱신)
     * Input: TokenFindReq (memberId 포함)
     * Output: TokenRes (Token 정보)
     */
    @Transactional
    fun getValidToken(req: TokenFindReq): TokenRes {
        // [수정됨] 입력값 전처리: "string"이나 공백이 들어오면 null(시스템 토큰)로 처리
        val memberId: String? = if (req.memberId.isNullOrBlank() || req.memberId == "string") {
            null
        } else {
            req.memberId
        }
        val lastToken = tokenRepository.findFirstByMemberIdOrderByCreatedAtDesc(memberId)

        // 1. 유효한 토큰이 있는지 확인 (만료 1시간 전까지 유효)
        if (lastToken != null && lastToken.expiredDt.isAfter(LocalDateTime.now().plusHours(1))) {
            return TokenRes.from(lastToken)
        }

        // 2. 만료 임박/경과 시 기존 토큰 폐기 (선택 사항이나 권장됨)
        if (lastToken != null) {
            revokeToken(lastToken)
        }

        // 3. 새 토큰 발급
        return issueNewToken(memberId)
    }

    private fun issueNewToken(memberId: String?): TokenRes {
        val (appKey, secretKey) = getAppKeys(memberId)

        // Client 호출
        val responseJson = kiwoomClient.issueToken(appKey, secretKey)

        // 파싱
        val rootNode = objectMapper.readTree(responseJson)
        val accessToken = rootNode.path("token").asText()
        val expiredStr = rootNode.path("expires_dt").asText()
        val tokenType = rootNode.path("token_type").asText("Bearer")
        val expiredDt = LocalDateTime.parse(expiredStr, dateFormatter)

        // DB 저장
        val newTokenEntity = Token(
            token = accessToken,
            tokenType = tokenType,
            expiredDt = expiredDt,
            memberId = memberId
        )
        tokenRepository.save(newTokenEntity)

        // DTO 반환
        return TokenRes(
            token = accessToken,
            tokenType = tokenType,
            expiredDt = expiredDt
        )
    }

    fun revokeToken(tokenEntity: Token) {
        try {
            val (appKey, secretKey) = getAppKeys(tokenEntity.memberId)
            // Client 호출
            kiwoomClient.revokeToken(appKey, secretKey, tokenEntity.token)
        } catch (e: Exception) {
            // 폐기 실패해도 DB 삭제는 진행하거나 로그만 남김
            println("토큰 폐기 API 호출 실패 (이미 만료되었을 수 있음): ${e.message}")
        }

        // DB 삭제
        tokenRepository.delete(tokenEntity)
    }

    private fun getAppKeys(memberId: String?): Pair<String, String> {
        return (if (memberId == null) {
            val appKey = sysConfigRepository.findById("MAIN_KIWOOM_APP_KEY").orElseThrow().configValue
            val secretKey = sysConfigRepository.findById("MAIN_KIWOOM_APP_SECRET").orElseThrow().configValue
            Pair(appKey, secretKey)
        } else {
            val appKey = memberAccountRepository.findById(memberId.toLong()).orElseThrow().apiKey
            var secretKey = memberAccountRepository.findById(memberId.toLong()).orElseThrow().secretKey
        }) as Pair<String, String>
    }
}