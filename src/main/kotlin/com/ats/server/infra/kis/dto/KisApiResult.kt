package com.ats.server.infra.kis.dto

/**
 * 한국투자증권 API 호출 결과를 담는 Wrapper 클래스
 * - Body뿐만 아니라 Header에 있는 연속 조회 키(Next Key)를 서비스 계층으로 전달하기 위함
 */
data class KisApiResult(
    val body: String,       // 응답 JSON 문자열
    val hasNext: Boolean,   // 연속 조회 가능 여부 (Header "cont-yn" == "Y")
    val nextKey: String?    // 다음 조회를 위한 키 값 (Header "next-key")
)