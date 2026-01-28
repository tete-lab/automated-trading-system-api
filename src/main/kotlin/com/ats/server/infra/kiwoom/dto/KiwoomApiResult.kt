package com.ats.server.infra.kiwoom.dto

data class KiwoomApiResult(
    val body: String,      // JSON 응답 본문
    val hasNext: Boolean,  // cont-yn == "Y" 인지 여부
    val nextKey: String?   // next-key 값
)