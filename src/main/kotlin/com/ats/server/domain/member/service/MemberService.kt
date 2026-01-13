package com.ats.server.domain.member.service

import com.ats.server.domain.member.dto.MemberJoinReq
import com.ats.server.domain.member.dto.MemberRes
import com.ats.server.domain.member.entity.Member
import com.ats.server.domain.member.repository.MemberRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class MemberService(
    private val memberRepository: MemberRepository
) {
    // 회원 가입
    @Transactional
    fun join(req: MemberJoinReq): Long {
        if (memberRepository.existsByEmail(req.email)) {
            throw IllegalArgumentException("이미 가입된 이메일입니다.")
        }

        // TODO: 비밀번호 암호화 필요 (PasswordEncoder)
        val member = Member(
            email = req.email,
            password = req.password, // 여기선 평문 저장하지만, 실제론 암호화 필수
            name = req.name
        )

        return memberRepository.save(member).memberId!!
    }

    // 회원 조회
    fun getMember(memberId: Long): MemberRes {
        val member = memberRepository.findById(memberId)
            .orElseThrow { IllegalArgumentException("회원이 존재하지 않습니다.") }

        return MemberRes(
            memberId = member.memberId!!,
            email = member.email,
            name = member.name,
            role = member.role,
            createdAt = member.createdAt.toString()
        )
    }
}