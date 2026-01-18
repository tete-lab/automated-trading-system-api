package com.ats.server.domain.token.dto

import com.ats.server.domain.token.entity.Token
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

data class TokenFindReq(
    @field:Schema(description = "회원아이디(NULL이면 SYS_CONFIG)")
    val memberId: String? = null
)

data class TokenRes(
    @field:Schema(description = "Access Token")
    val token: String,

    @field:Schema(description = "Token Type (Bearer 등)")
    val tokenType: String,

    @field:Schema(description = "만료일시")
    val expiredDt: LocalDateTime
) {
    // Entity -> DTO 변환 편의 메서드 (Companion Object)
    companion object {
        fun from(entity: Token): TokenRes {
            return TokenRes(
                token = entity.token,
                tokenType = entity.tokenType,
                expiredDt = entity.expiredDt
            )
        }
    }
}