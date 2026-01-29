package com.ats.server.domain.stock.repository

import com.ats.server.domain.stock.entity.StockFinancialRatio
import com.ats.server.domain.stock.entity.StockFinancialRatioId
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface StockFinancialRatioRepository : JpaRepository<StockFinancialRatio, StockFinancialRatioId> {

    // 특정 종목의 모든 재무 비율 조회 (최신순 정렬)
    fun findAllByStockCodeOrderByStacYymmDesc(stockCode: String): List<StockFinancialRatio>

    // 특정 종목의 특정 년월 조회 (기본 findById가 있지만 편의상 추가 가능)
    fun findByStockCodeAndStacYymm(stockCode: String, stacYymm: String): StockFinancialRatio?
}