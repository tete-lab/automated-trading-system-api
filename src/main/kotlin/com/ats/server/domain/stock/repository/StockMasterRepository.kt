package com.ats.server.domain.stock.repository

import com.ats.server.domain.stock.entity.StockMaster
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface StockMasterRepository : JpaRepository<StockMaster, String> {
    // 시장별 조회 기능 추가 (예: 코스피 종목만 보기)
    fun findAllByMarket(market: String): List<StockMaster>
    @Query("SELECT s.stockCode FROM StockMaster s")
    fun findAllStockCodes(): List<String>}