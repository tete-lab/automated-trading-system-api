package com.ats.server.domain.token.repository

import com.ats.server.domain.token.entity.Token
import org.springframework.data.jpa.repository.JpaRepository

interface TokenRepository : JpaRepository<Token, Long> {
    // 최신 토큰 순으로 정렬하여 첫 번째 값 조회
    fun findFirstByMemberIdOrderByCreatedAtDesc(memberId: String?): Token?
    fun findFirstByMemberIdOrApiNameOrderByCreatedAtDesc(memberId: String?, apiName: String?): Token?
}
