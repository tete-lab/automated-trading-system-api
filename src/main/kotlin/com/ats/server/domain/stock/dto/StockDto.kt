package com.ats.server.domain.stock.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank

data class StockCreateReq(
    @field:Schema(description = "종목코드 (PK)", example = "005930")
    @field:NotBlank
    val stockCode: String,

    @field:Schema(description = "종목명", example = "삼성전자")
    @field:NotBlank
    val stockName: String,

    @field:Schema(description = "시장 구분 (KOSPI/KOSDAQ)", example = "KOSPI")
    @field:NotBlank
    val market: String,

    @field:Schema(description = "업종", example = "전기전자")
    val sector: String?,

    @field:Schema(description = "감사 리스크 (NULL이면 정상)", example = "투자주의")
    val auditRisk: String?
)

data class StockUpdateReq(
    @field:Schema(description = "종목명", example = "삼성전자")
    val stockName: String,

    @field:Schema(description = "시장 구분", example = "KOSPI")
    val market: String,

    @field:Schema(description = "업종", example = "전기전자")
    val sector: String?,

    @field:Schema(description = "감사 리스크")
    val auditRisk: String?
)

data class StockRes(
    val stockCode: String,
    val stockName: String,
    val market: String,
    val sector: String?,
    val auditRisk: String?,
    val updatedAt: String?
)