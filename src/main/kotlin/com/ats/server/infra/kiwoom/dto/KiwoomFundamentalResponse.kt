package com.ats.server.infra.kiwoom.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
data class KiwoomFundamentalResponse(
    @JsonProperty("return_code")
    val returnCode: String?,
    @JsonProperty("return_msg")
    val returnMsg: String?,

    @JsonProperty("stk_cd")
    val stockCode: String?,

    // 시가총액 (API는 억원 단위일 수 있으나 예시상 전체 금액 혹은 백만단위 확인 필요, 보통 문자열로 옴)
    @JsonProperty("mac")
    val marketCap: String?,
    @JsonProperty("trde_qty")
    val trdeQty: String?,
    // PER, PBR, EPS, BPS, ROE (빈 문자열 ""로 오는 경우 처리 필요)
    @JsonProperty("per")
    val per: String?,
    @JsonProperty("pbr")
    val pbr: String?,
    @JsonProperty("eps")
    val eps: String?,
    @JsonProperty("bps")
    val bps: String?,
    @JsonProperty("roe")
    val roe: String?,

    // EV (Enterprise Value) - 테이블엔 없지만 참고용
    @JsonProperty("ev")
    val ev: String?

    // 참고: API 명세서에 매출성장률, 배당수익률 등은 명시적으로 보이지 않아
    // 기본 정보(ka10001)에서 제공하는 것만 매핑합니다.
)