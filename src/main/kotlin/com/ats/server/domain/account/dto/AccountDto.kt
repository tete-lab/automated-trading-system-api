package com.ats.server.domain.account.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min

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
    val secretKey: String?,

    @field:Schema(description = "매수 RSI 기준 (기본 30)", example = "30")
    @field:Min(0) @field:Max(100)
    val buyRsi: Int = 30,

    @field:Schema(description = "매도 RSI 기준 (기본 70)", example = "70")
    @field:Min(0) @field:Max(100)
    val sellRsi: Int = 70,

    @field:Schema(description = "모의투자 여부 (Y/N)", example = "N")
    val isVirtual: String = "N",

    @field:Schema(description = "계좌 사용 여부 (Y/N)", example = "Y")
    val isActive: String = "Y"

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