package com.ats.server.domain.stock.repository

import com.ats.server.domain.stock.entity.StockDaily
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.math.BigDecimal
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

    // [매수 추천] 골든크로스(1) 발생 종목 100개 (RSI 오름차순: 과매도된 것부터 추천)
    fun findTop100ByBaseDateAndCrossTypeOrderByRsiAsc(
        baseDate: LocalDate,
        crossType: Int
    ): List<StockDaily>

    // [매도 추천] 데드크로스(-1) 발생 종목 100개 (RSI 내림차순: 과매수된 것부터 추천)
    fun findTop100ByBaseDateAndCrossTypeOrderByRsiDesc(
        baseDate: LocalDate,
        crossType: Int
    ): List<StockDaily>

    // [신규] 업데이트 대상 조회
    // 해당 날짜에 데이터가 존재하면서, 투자자 매매동향 컬럼 중 하나라도 NULL인 종목 코드 리스트 반환
    @Query("""
        SELECT s.stockCode 
        FROM StockDaily s 
        WHERE s.baseDate = :date 
          AND (s.individualBuy IS NULL 
               OR s.organBuy IS NULL 
               OR s.foreignerBuy IS NULL)
    """)
    fun findStockCodesForInvestorTrendUpdate(@Param("date") date: LocalDate): List<String>
}