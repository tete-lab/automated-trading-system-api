package com.ats.server.infra.kis.dto // 패키지 경로는 프로젝트 상황에 맞게 조정

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * [KIS] 투자자별 매매동향 API 응답 DTO
 * TR_ID: FHKST01010900 (국내주식 투자자별 매매동향 - 일별) 등
 */
data class KisInvestorTrendResponse(
    @JsonProperty("rt_cd")
    val rtCd: String?, // 여기도 혹시 모르니 Nullable

    @JsonProperty("msg1")
    val msg1: String?,

    @JsonProperty("output")
    val output: List<KisInvestorTrendOutput>?
)

data class KisInvestorTrendOutput(
    @JsonProperty("stck_bsop_date")
    val stckBsopDate: String?, // String -> String?

    @JsonProperty("prdy_ctrt")
    val fluctuationRate: String?, // String -> String? (에러 발생 지점)

    @JsonProperty("prsn_ntby_qty")
    val individualBuyQty: String?, // String -> String?

    @JsonProperty("frgn_ntby_qty")
    val foreignerBuyQty: String?, // String -> String?

    @JsonProperty("orgn_ntby_qty")
    val organBuyQty: String?      // String -> String?
)