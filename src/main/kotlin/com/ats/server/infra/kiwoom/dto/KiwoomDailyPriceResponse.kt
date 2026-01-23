package com.ats.server.infra.kiwoom.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import java.math.BigDecimal

// 키움 API 응답 구조 매핑
data class KiwoomDailyPriceResponse(
    @JsonProperty("daly_stkpc") val dalyStkpc: List<KiwoomDailyItem>?,
    @JsonProperty("return_code") val returnCode: String,
    @JsonProperty("return_msg") val returnMsg: String,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class KiwoomDailyItem(
    @JsonProperty("date") val date: String,
    @JsonProperty("open_pric") val openPrice: BigDecimal? = BigDecimal.ZERO,
    @JsonProperty("high_pric") val highPrice: BigDecimal? = BigDecimal.ZERO,
    @JsonProperty("low_pric") val lowPrice: BigDecimal? = BigDecimal.ZERO,
    @JsonProperty("close_pric") val closePriceStr: String? = "0", // 기호(+, -) 포함 문자열
    @JsonProperty("trde_qty") val volume: Long? = 0L,
    @JsonProperty("amt_mn") val volumePrice: BigDecimal? = BigDecimal.ZERO,
    @JsonProperty("flu_rt") val fluctuationRate: String? = "0", //등락률
    @JsonProperty("ind") val individualBuy: String? = "0", // 개인매수
    @JsonProperty("orgn") val organBuy: String? = "0", // 기관매수
    @JsonProperty("for_qty") val foreignerBuy: String? = "0", // 외국인매수
) {
    // 기호를 제거하고 숫자로 변환하는 helper 프로퍼티
    val closePrice: BigDecimal
        get() = BigDecimal(closePriceStr?.replace("+", "")?.replace("-", "")?.ifBlank { "0" } ?: "0")
}