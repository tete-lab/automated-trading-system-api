package com.ats.server.domain.stock.entity

import com.ats.server.global.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "stock_master")
class StockMaster(
    @Id
    @Column(name = "stock_code", length = 20)
    val stockCode: String, // PK가 종목코드 ("005930")

    @Column(name = "stock_name", nullable = false, length = 100)
    var stockName: String,

    @Column(name = "market", nullable = false, length = 10)
    var market: String, // KOSPI, KOSDAQ

    @Column(name = "sector", length = 50)
    var sector: String? = null,

    @Column(name = "audit_risk", length = 20)
    var auditRisk: String? = null

) : BaseEntity() {

    // 수정 메서드 (Dirty Checking 용)
    fun update(stockName: String, market: String, sector: String?, auditRisk: String?) {
        this.stockName = stockName
        this.market = market
        this.sector = sector
        this.auditRisk = auditRisk
    }
}