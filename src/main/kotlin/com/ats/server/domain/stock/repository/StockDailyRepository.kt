package com.ats.server.domain.stock.repository

import com.ats.server.domain.stock.entity.StockDaily
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
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

    // 특정 날짜의 모든 종목 데이터를 가져옴 (계산 대상)
    fun findAllByBaseDate(baseDate: LocalDate): List<StockDaily>

    // 특정 종목의 과거 데이터를 최신순으로 가져옴 (지수 계산용, 넉넉하게 100개 정도 가져옴)
    // 50일 이동평균 + MACD 계산 등을 위해 충분한 데이터 필요
    @Query("SELECT s FROM StockDaily s WHERE s.stockCode = :stockCode AND s.baseDate <= :baseDate ORDER BY s.baseDate DESC")
    fun findPriceHistory(@Param("stockCode") stockCode: String,
                         @Param("baseDate") baseDate: LocalDate,
                         pageable: Pageable
    ): List<StockDaily>
}