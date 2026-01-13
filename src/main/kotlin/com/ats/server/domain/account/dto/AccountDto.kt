package com.ats.server.domain.account.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull

data class AccountCreateReq(
    @field:Schema(description = "회원 ID", example = "1")
    @field:NotNull
    val memberId: Long,

    @field:Schema(description = "계좌번호", example = "5000-1234-55")
    @field:NotBlank
    val accountNum: String,

    @field:Schema(description = "계좌 별칭", example = "키움 모의투자")
    val accountName: String?,

    @field:Schema(description = "API Key")
    val apiKey: String?,
    @field:Schema(description = "Secret Key")
    val secretKey: String?
)

data class AccountRes(
    val accountId: Long,
    val accountNum: String,
    val accountName: String?,
    val isVirtual: String,
    val isActive: String,
    val buyRsi: Int,
    val sellRsi: Int
)