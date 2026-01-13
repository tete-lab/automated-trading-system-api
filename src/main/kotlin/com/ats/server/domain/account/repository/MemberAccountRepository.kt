package com.ats.server.domain.account.repository

import com.ats.server.domain.account.entity.MemberAccount
import org.springframework.data.jpa.repository.JpaRepository

interface MemberAccountRepository : JpaRepository<MemberAccount, Long> {
    // 특정 회원의 계좌 목록 조회
    fun findAllByMemberMemberId(memberId: Long): List<MemberAccount>
}