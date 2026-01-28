package com.ats.server.infra.kis.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import java.math.BigDecimal

@JsonIgnoreProperties(ignoreUnknown = true)
data class KisDailyPriceResponse(
    @JsonProperty("rt_cd") val returnCode: String, // 0: 성공
    @JsonProperty("msg1") val message: String,
    @JsonProperty("output") val output: List<KisDailyPriceItem>?
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class KisDailyPriceItem(
    @JsonProperty("stck_bsop_date") val date: String,      // 주식 영업 일자 (YYYYMMDD)
    @JsonProperty("stck_clpr") val closePrice: BigDecimal, // 주식 종가
    @JsonProperty("stck_oprc") val openPrice: BigDecimal,  // 주식 시가
    @JsonProperty("stck_hgpr") val highPrice: BigDecimal,  // 주식 최고가
    @JsonProperty("stck_lwpr") val lowPrice: BigDecimal,   // 주식 최저가
    @JsonProperty("acml_vol") val volume: Long,            // 누적 거래량
    @JsonProperty("acml_tr_pbmn") val volumePrice: BigDecimal, // 누적 거래 대금

    // [참고] KIS 일별 시세 API(FHKST01010400)는 수급(개인/외인/기관) 정보를 제공하지 않음
    // 등락률은 계산되거나 별도 필드(prdy_ctrt)가 있을 수 있음
    @JsonProperty("prdy_ctrt") val fluctuationRate: BigDecimal? = BigDecimal.ZERO // 전일 대비율
)