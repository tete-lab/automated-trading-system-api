package com.ats.server.domain.stock.repository

import com.ats.server.domain.stock.entity.StockFundamental
import org.springframework.data.jpa.repository.JpaRepository

interface StockFundamentalRepository : JpaRepository<StockFundamental, String>