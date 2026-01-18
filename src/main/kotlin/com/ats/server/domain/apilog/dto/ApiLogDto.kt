package com.ats.server.domain.apilog.dto

import io.swagger.v3.oas.annotations.media.Schema

data class ApiLogCreateReq(
    @field:Schema(description = "API 이름", example = "키움_주식주문")
    val apiName: String?,

    @field:Schema(description = "호출 URL", example = "https://openapi.kiwoom.com/order")
    val url: String?,

    @field:Schema(description = "HTTP Method", example = "POST")
    val method: String?,

    @field:Schema(description = "요청 파라미터 (JSON String)", example = "{\"code\": \"005930\", \"qty\": 10}")
    val reqParams: String?,

    @field:Schema(description = "응답 본문 (JSON String)", example = "{\"result\": \"OK\", \"orderNo\": \"12345\"}")
    val resBody: String?,

    @field:Schema(description = "HTTP 상태 코드", example = "200")
    val statusCode: Int?
)

data class ApiLogRes(
    val logId: Long,
    val apiName: String?,
    val url: String?,
    val method: String?,
    val reqParams: String?,
    val resBody: String?,
    val statusCode: Int?,
    val createdAt: String?
)