package com.ats.server.domain.stock.repository

import com.ats.server.domain.stock.entity.StockDaily
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDate
import java.util.Optional

interface StockDailyRepository : JpaRepository<StockDaily, Long> {
    // 중복 체크용 (종목+날짜)
    fun existsByStockCodeAndBaseDate(stockCode: String, baseDate: LocalDate): Boolean

    // 특정 날짜의 데이터 조회 (수정 시 사용)
    fun findByStockCodeAndBaseDate(stockCode: String, baseDate: LocalDate): Optional<StockDaily>

    // 차트용 기간 조회 (예: 2024-01-01 ~ 2024-01-31 데이터)
    fun findAllByStockCodeAndBaseDateBetweenOrderByBaseDateAsc(
        stockCode: String,
        startDate: LocalDate,
        endDate: LocalDate
    ): List<StockDaily>
}