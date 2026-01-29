package com.ats.server.infra.kis.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
data class KisFinancialRatioResponse(
    @JsonProperty("rt_cd") val returnCode: String,
    @JsonProperty("msg1") val message: String,
    @JsonProperty("output") val output: List<KisFinancialRatioItem>?
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class KisFinancialRatioItem(
    @JsonProperty("stac_yymm") val stacYymm: String,           // 결산년월 (202312)
    @JsonProperty("grs") val grs: String?,                     // 매출액 증가율
    @JsonProperty("bsop_prfi_inrt") val bsopPrfiInrt: String?, // 영업이익 증가율
    @JsonProperty("ntin_inrt") val ntinInrt: String?,          // 순이익 증가율
    @JsonProperty("roe_val") val roeVal: String?,              // ROE
    @JsonProperty("eps") val eps: String?,                     // EPS
    @JsonProperty("sps") val sps: String?,                     // SPS
    @JsonProperty("bps") val bps: String?,                     // BPS
    @JsonProperty("rsrv_rate") val rsrvRate: String?,          // 유보율
    @JsonProperty("lblt_rate") val lbltRate: String?           // 부채비율
)