package com.ats.server.domain.account.controller

import com.ats.server.domain.account.dto.AccountCreateReq
import com.ats.server.domain.account.dto.AccountRes
import com.ats.server.domain.account.service.AccountService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@Tag(name = "03. 계좌 (Account)", description = "주식 계좌 및 API 키 관리")
@RestController
@RequestMapping("/api/v1/accounts")
class AccountController(
    private val accountService: AccountService
) {

    @Operation(summary = "계좌 등록", description = "회원의 증권 계좌 정보를 등록합니다.")
    @PostMapping
    fun createAccount(@Valid @RequestBody req: AccountCreateReq): ResponseEntity<Long> {
        return ResponseEntity.status(HttpStatus.CREATED).body(accountService.createAccount(req))
    }

    @Operation(summary = "내 계좌 목록", description = "특정 회원의 모든 계좌를 조회합니다.")
    @GetMapping("/member/{memberId}")
    fun getMyAccounts(@PathVariable memberId: Long): ResponseEntity<List<AccountRes>> {
        return ResponseEntity.ok(accountService.getMyAccounts(memberId))
    }
}