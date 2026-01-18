package com.ats.server.domain.apilog.repository

import com.ats.server.domain.apilog.entity.ApiLog
import org.springframework.data.jpa.repository.JpaRepository

interface ApiLogRepository : JpaRepository<ApiLog, Long> {
    // 최신 로그 순으로 조회
    fun findAllByOrderByLogIdDesc(): List<ApiLog>

    // 특정 API 이름으로 검색 (예: 에러 난 주문 API만 찾기)
    fun findAllByApiNameOrderByLogIdDesc(apiName: String): List<ApiLog>
}