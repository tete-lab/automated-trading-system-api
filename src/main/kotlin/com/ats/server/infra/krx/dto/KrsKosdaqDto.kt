package com.ats.server.infra.krx.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import java.math.BigDecimal

@JsonIgnoreProperties(ignoreUnknown = true)
data class KrxKosdaqDailyRes(
    @JsonProperty("OutBlock_1")
    val output: List<KrxKosdaqItem>?
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class KrxKosdaqItem(
    @JsonProperty("BAS_DD") val basDd: String,        // 기준일자
    @JsonProperty("ISU_CD") val isuCd: String,        // 종목코드 (예: KR7000000000)
    @JsonProperty("ISU_NM") val isuNm: String,        // 종목명
    @JsonProperty("TDD_CLSPRC") val closePrice: String, // 종가
    @JsonProperty("TDD_OPNPRC") val openPrice: String,  // 시가
    @JsonProperty("TDD_HGPRC") val highPrice: String,   // 고가
    @JsonProperty("TDD_LWPRC") val lowPrice: String,    // 저가
    @JsonProperty("ACC_TRDVOL") val volume: String,     // 거래량
    @JsonProperty("ACC_TRDVAL") val volumePrice: String, // 거래대금
    @JsonProperty("FLUC_RT") val flucRt: String         // 등락률
) {
    // 숫자에 포함된 콤마(,) 제거 후 변환
    fun toBigDecimal(value: String): BigDecimal =
        BigDecimal(value.replace(",", "").ifBlank { "0" })

    fun toLong(value: String): Long =
        value.replace(",", "").ifBlank { "0" }.toLong()

    // KRX 종목코드는 표준코드(12자리)일 수 있으므로 단축코드(6자리) 추출 필요 시 사용
    val shortCode: String
        get() = if (isuCd.length == 12) isuCd.substring(3, 9) else isuCd
}