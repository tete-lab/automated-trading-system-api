package com.ats.server.domain.member.controller

import com.ats.server.domain.member.dto.MemberJoinReq
import com.ats.server.domain.member.dto.MemberRes
import com.ats.server.domain.member.service.MemberService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@Tag(name = "02. 회원 (Member)", description = "회원 가입 및 조회")
@RestController
@RequestMapping("/api/v1/members")
class MemberController(
    private val memberService: MemberService
) {
    @Operation(summary = "회원 가입", description = "신규 회원을 등록합니다.")
    @PostMapping
    fun join(@Valid @RequestBody req: MemberJoinReq): ResponseEntity<Long> {
        return ResponseEntity.status(HttpStatus.CREATED).body(memberService.join(req))
    }

    @Operation(summary = "회원 조회", description = "Member ID로 회원 정보를 조회합니다.")
    @GetMapping("/{memberId}")
    fun getMember(@PathVariable memberId: Long): ResponseEntity<MemberRes> {
        return ResponseEntity.ok(memberService.getMember(memberId))
    }
}