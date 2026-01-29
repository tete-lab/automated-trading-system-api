package com.ats.server.domain.stock.dto

import com.fasterxml.jackson.annotation.JsonProperty

data class StockFinancialRatioDto(
    @JsonProperty("stock_code") val stockCode: String,
    @JsonProperty("stac_yymm") val stacYymm: String,
    val grs: String?,
    @JsonProperty("bsop_prfi_inrt") val bsopPrfiInrt: String?,
    @JsonProperty("ntin_inrt") val ntinInrt: String?,
    @JsonProperty("roe_val") val roeVal: String?,
    val eps: String?,
    val sps: String?,
    val bps: String?,
    @JsonProperty("rsrv_rate") val rsrvRate: String?,
    @JsonProperty("lblt_rate") val lbltRate: String?
)