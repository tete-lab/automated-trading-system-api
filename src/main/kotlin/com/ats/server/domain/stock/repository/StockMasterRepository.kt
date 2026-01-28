package com.ats.server.domain.stock.repository

import com.ats.server.domain.stock.entity.StockMaster
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDate

interface StockMasterRepository : JpaRepository<StockMaster, String> {
    // 시장별 조회 기능 추가 (예: 코스피 종목만 보기)
    fun findAllByMarket(market: String): List<StockMaster>
    @Query("SELECT s.stockCode FROM StockMaster s")
    fun findAllStockCodes(): List<String>

    // [추가] 특정 날짜(date)에 Daily 데이터가 없는 종목 코드만 조회 (Left Join 방식 or Not Exists)
    // 설명: StockMaster(m)에는 있는데, StockDaily(d)에서 해당 날짜(targetDate)로 조회했을 때 없는 경우
    @Query("""
        SELECT m.stockCode 
        FROM StockMaster m 
        WHERE NOT EXISTS (
            SELECT 1 
            FROM StockDaily d 
            WHERE d.stockCode = m.stockCode 
              AND d.baseDate = :targetDate
        )
    """)
    fun findStockCodesByMissingDailyData(@Param("targetDate") targetDate: LocalDate): List<String>

    /**
     * [추가] 오늘 날짜(targetDate) 기준으로
     * 1. StockFundamental 데이터가 아예 없거나 (Join 실패 -> NULL)
     * 2. 데이터는 있는데 오늘 업데이트된 게 아닌 경우 (Date(updatedAt) != targetDate)
     * 인 종목 코드만 조회
     */
    @Query("""
        SELECT m.stockCode 
        FROM StockMaster m 
        LEFT JOIN StockFundamental f ON m.stockCode = f.stockCode 
        WHERE f.stockCode IS NULL 
           OR DATE(f.updatedAt) <> :targetDate
    """)
    fun findStockCodesByMissingFundamentalData(@Param("targetDate") targetDate: LocalDate): List<String>

}