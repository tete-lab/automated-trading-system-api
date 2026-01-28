package com.ats.server.infra.kis.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import java.math.BigDecimal

@JsonIgnoreProperties(ignoreUnknown = true)
data class KisPeriodPriceResponse(
    @JsonProperty("rt_cd") val returnCode: String,
    @JsonProperty("msg1") val message: String,
    // [중요] 기간별 시세 API는 데이터 목록이 'output2'에 담겨 옵니다.
    @JsonProperty("output2") val output: List<KisPeriodPriceItem>?
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class KisPeriodPriceItem(
    @JsonProperty("stck_bsop_date") val date: String,      // 영업 일자
    @JsonProperty("stck_clpr") val closePrice: BigDecimal, // 종가
    @JsonProperty("stck_oprc") val openPrice: BigDecimal,  // 시가
    @JsonProperty("stck_hgpr") val highPrice: BigDecimal,  // 고가
    @JsonProperty("stck_lwpr") val lowPrice: BigDecimal,   // 저가
    @JsonProperty("acml_vol") val volume: Long,            // 거래량
    @JsonProperty("acml_tr_pbmn") val volumePrice: BigDecimal // 거래대금
    // 참고: 이 API도 수급(개인/외인/기관) 정보는 제공하지 않습니다.
)