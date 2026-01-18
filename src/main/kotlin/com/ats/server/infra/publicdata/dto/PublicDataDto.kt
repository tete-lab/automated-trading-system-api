package com.ats.server.infra.publicdata.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
data class PublicDataDailyRes(
    val response: PublicDataResponse
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class PublicDataResponse(
    val body: PublicDataBody
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class PublicDataBody(
    val items: PublicDataItems,
    val totalCount: Int,
    val pageNo: Int
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class PublicDataItems(
    val item: List<PublicDataItem> = emptyList() // 데이터가 없으면 빈 리스트
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class PublicDataItem(
    @JsonProperty("srtnCd") val srtnCd: String,   // 종목코드 (A005930)
    @JsonProperty("itmsNm") val itmsNm: String,   // 종목명
    @JsonProperty("mrktCtg") val mrktCtg: String, // 시장구분 (KOSPI)
    @JsonProperty("basDt") val basDt: String,     // 기준일자 (20240115)
    @JsonProperty("clpr") val clpr: String,       // 종가
    @JsonProperty("mkp") val mkp: String,         // 시가
    @JsonProperty("hipr") val hipr: String,       // 고가
    @JsonProperty("lopr") val lopr: String,       // 저가
    @JsonProperty("trqu") val trqu: String,       // 거래량
    @JsonProperty("trPrc") val trPrc: String,     // 거래대금
    @JsonProperty("mrktTotAmt") val mrktTotAmt: String // 시가총액
)