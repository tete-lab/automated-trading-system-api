package com.ats.server.domain.stock.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import java.math.BigDecimal
import java.time.LocalDate

data class StockFundamentalCreateReq(
    @field:Schema(description = "종목코드", example = "005930")
    @field:NotBlank
    val stockCode: String,

    @field:Schema(description = "시가총액", example = "450000000000000")
    val marketCap: BigDecimal?,
    @field:Schema(description = "평균 거래량", example = "15000000")
    val avgVolume: Long?,

    @field:Schema(description = "PER", example = "15.5")
    val per: BigDecimal?,
    @field:Schema(description = "PBR", example = "1.2")
    val pbr: BigDecimal?,
    @field:Schema(description = "PSR", example = "3.5")
    val psr: BigDecimal?,

    @field:Schema(description = "EPS (주당순이익)", example = "4500")
    val eps: BigDecimal?,
    @field:Schema(description = "BPS (주당순자산)", example = "60000")
    val bps: BigDecimal?,

    @field:Schema(description = "ROE", example = "12.5")
    val roe: BigDecimal?,
    @field:Schema(description = "매출 성장률", example = "5.0")
    val revenueGrowth: BigDecimal?,

    @field:Schema(description = "배당 수익률(%)", example = "2.5")
    val divYield: BigDecimal?,
    @field:Schema(description = "주당 배당금(원)", example = "1400")
    val divRate: BigDecimal?,
    @field:Schema(description = "배당 지급일", example = "2024-04-20")
    val divPayDate: LocalDate?
)

// 업데이트용 DTO (PK인 stockCode 제외)
data class StockFundamentalUpdateReq(
    val marketCap: BigDecimal?,
    val avgVolume: Long?,
    val per: BigDecimal?,
    val pbr: BigDecimal?,
    val psr: BigDecimal?,
    val eps: BigDecimal?,
    val bps: BigDecimal?,
    val roe: BigDecimal?,
    val revenueGrowth: BigDecimal?,
    val divYield: BigDecimal?,
    val divRate: BigDecimal?,
    val divPayDate: LocalDate?
)

data class StockFundamentalRes(
    val stockCode: String,
    val marketCap: BigDecimal?,
    val avgVolume: Long?,
    val per: BigDecimal?,
    val pbr: BigDecimal?,
    val psr: BigDecimal?,
    val eps: BigDecimal?,
    val bps: BigDecimal?,
    val roe: BigDecimal?,
    val revenueGrowth: BigDecimal?,
    val divYield: BigDecimal?,
    val divRate: BigDecimal?,
    val divPayDate: LocalDate?,
    val updatedAt: String?
)