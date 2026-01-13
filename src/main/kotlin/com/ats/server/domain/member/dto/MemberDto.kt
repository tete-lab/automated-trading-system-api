package com.ats.server.domain.member.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank

data class MemberJoinReq(
    @field:Schema(description = "이메일", example = "dev@tetelab.dev")
    @field:Email(message = "이메일 형식이 올바르지 않습니다.")
    val email: String,

    @field:Schema(description = "비밀번호", example = "password1234")
    @field:NotBlank
    val password: String,

    @field:Schema(description = "사용자 이름", example = "테테랩")
    @field:NotBlank
    val name: String
)

data class MemberRes(
    val memberId: Long,
    val email: String,
    val name: String,
    val role: String,
    val createdAt: String
)