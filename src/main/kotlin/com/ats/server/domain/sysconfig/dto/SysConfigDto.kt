package com.ats.server.domain.sysconfig.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank

// 요청 DTO (Create)
data class SysConfigCreateReq(
    @field:Schema(description = "설정 코드 (PK)", example = "MAX_BUY_LIMIT")
    @field:NotBlank(message = "설정 코드는 필수입니다.")
    val configCode: String,

    @field:Schema(description = "설정 값", example = "1000000")
    val configValue: String?,

    @field:Schema(description = "설명", example = "1회 최대 매수 금액")
    val descTxt: String?
)

// 요청 DTO (Update) - PK는 PathVariable로 받으므로 여기선 제외
data class SysConfigUpdateReq(
    @field:Schema(description = "설정 값", example = "2000000")
    val configValue: String?,

    @field:Schema(description = "설명", example = "1회 최대 매수 금액 상향")
    val descTxt: String?
)

// 응답 DTO
data class SysConfigRes(
    val configCode: String,
    val configValue: String?,
    val descTxt: String?,
    val updatedAt: String?
)