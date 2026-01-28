package com.ats.server.domain.token.controller

import com.ats.server.domain.token.dto.TokenFindReq
import com.ats.server.domain.token.dto.TokenRes
import com.ats.server.domain.token.service.TokenService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "98. 증권사 API Token 발급", description = "증권사 API 토큰 발급")
@RestController
@RequestMapping("/api/v1/token")
class TokenController(
    private val tokenService: TokenService
) {

    @Operation(
        summary = "1. 증권사 API 토큰 발급 (시스템/사용자)",
        description = "토큰 유효성을 확인하고 필요 시 재발급합니다. Request Body에 memberId가 있으면 해당 사용자 토큰을, 없으면 시스템 토큰을 처리합니다."
    )
    @PostMapping("/validToken")
    fun validToken(@RequestBody req: TokenFindReq): ResponseEntity<TokenRes> {
        // @RequestBody를 통해 전달받은 req(memberId 포함/미포함)를 서비스로 전달
        val tokenRes = tokenService.getValidToken(req)

        return ResponseEntity.ok(tokenRes)
    }
}