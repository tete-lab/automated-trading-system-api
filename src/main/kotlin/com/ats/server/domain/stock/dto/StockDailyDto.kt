package com.ats.server.domain.stock.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.math.BigDecimal
import java.time.LocalDate

data class StockDailyCreateReq(
    @field:Schema(description = "종목코드", example = "005930")
    @field:NotBlank
    val stockCode: String,

    @field:Schema(description = "기준 일자", example = "2024-01-15")
    @field:NotNull
    val baseDate: LocalDate,

    @field:Schema(description = "종가", example = "73000")
    @field:NotNull
    val closePrice: BigDecimal,

    val openPrice: BigDecimal?,
    val highPrice: BigDecimal?,
    val lowPrice: BigDecimal?,
    val volume: Long?,
    val volumePrice: BigDecimal?,

    val fluctuationRate: String?,
    val individualBuy: String?,
    val organBuy: String?,
    val foreignerBuy: String?,

    // 지표들은 생성 시점엔 없을 수도 있음
    val sma20: BigDecimal? = null,
    val sma50: BigDecimal? = null,
    val rsi: BigDecimal? = null,
    val macd: BigDecimal? = null,
    val signalLine: BigDecimal? = null,
    val crossType: Int? = null,
    val recommendYn: String = "N"
)

// 업데이트용 DTO (PK나 UniqueKey인 stockCode, baseDate 제외)
data class StockDailyUpdateReq(
    val closePrice: BigDecimal,
    val openPrice: BigDecimal?,
    val highPrice: BigDecimal?,
    val lowPrice: BigDecimal?,
    val volume: Long?,
    val volumePrice: BigDecimal?,

    val fluctuationRate: String?,
    val individualBuy: String?,
    val organBuy: String?,
    val foreignerBuy: String?,

    val sma20: BigDecimal?,
    val sma50: BigDecimal?,
    val ema9: BigDecimal?,
    val ema12: BigDecimal?,
    val ema26: BigDecimal?,
    val rsi: BigDecimal?,
    val macd: BigDecimal?,
    val signalLine: BigDecimal?,
    val crossType: Int?,
    val recommendYn: String
)

data class StockDailyRes(
    val stockDailyId: Long,
    val stockCode: String,
    val baseDate: LocalDate,
    val closePrice: BigDecimal,
    val openPrice: BigDecimal?,
    val highPrice: BigDecimal?,
    val lowPrice: BigDecimal?,
    val volume: Long?,
    val volumePrice: BigDecimal?,
    val fluctuationRate: String?,
    val individualBuy: String?,
    val organBuy: String?,
    val foreignerBuy: String?,
    val sma20: BigDecimal?,
    val sma50: BigDecimal?,
    val ema9: BigDecimal?,
    val ema12: BigDecimal?,
    val ema26: BigDecimal?,
    val rsi: BigDecimal?,
    val macd: BigDecimal?,
    val crossType: Int?,
    val recommendYn: String
)