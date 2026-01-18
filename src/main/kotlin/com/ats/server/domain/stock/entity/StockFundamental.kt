package com.ats.server.domain.stock.entity

import com.ats.server.global.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.LocalDate

@Entity
@Table(name = "stock_fundamental")
class StockFundamental(
    @Id
    @Column(name = "stock_code", length = 20)
    val stockCode: String, // StockMaster와 1:1 공유 (PK이자 FK 개념)

    // [규모 및 유동성]
    @Column(name = "market_cap", precision = 25, scale = 0)
    var marketCap: BigDecimal?, // 시가총액

    @Column(name = "avg_volume")
    var avgVolume: Long?, // 평균 거래량

    // [가치 평가 지표]
    @Column(name = "per", precision = 10, scale = 2)
    var per: BigDecimal?,

    @Column(name = "pbr", precision = 10, scale = 2)
    var pbr: BigDecimal?,

    @Column(name = "psr", precision = 10, scale = 2)
    var psr: BigDecimal?,

    // [주당 가치 지표]
    @Column(name = "eps", precision = 15, scale = 2)
    var eps: BigDecimal?,

    @Column(name = "bps", precision = 15, scale = 2)
    var bps: BigDecimal?,

    // [수익성 및 성장성]
    @Column(name = "roe", precision = 10, scale = 2)
    var roe: BigDecimal?,

    @Column(name = "revenue_growth", precision = 10, scale = 2)
    var revenueGrowth: BigDecimal?,

    // [배당]
    @Column(name = "div_yield", precision = 5, scale = 2)
    var divYield: BigDecimal?,

    @Column(name = "div_rate", precision = 15, scale = 2)
    var divRate: BigDecimal?,

    @Column(name = "div_pay_date")
    var divPayDate: LocalDate?

) : BaseEntity() {

    // 전체 업데이트 메서드 (Yahoo Finance 등에서 긁어왔을 때 한 번에 갱신)
    fun update(
        marketCap: BigDecimal?, avgVolume: Long?,
        per: BigDecimal?, pbr: BigDecimal?, psr: BigDecimal?,
        eps: BigDecimal?, bps: BigDecimal?,
        roe: BigDecimal?, revenueGrowth: BigDecimal?,
        divYield: BigDecimal?, divRate: BigDecimal?, divPayDate: LocalDate?
    ) {
        this.marketCap = marketCap
        this.avgVolume = avgVolume
        this.per = per
        this.pbr = pbr
        this.psr = psr
        this.eps = eps
        this.bps = bps
        this.roe = roe
        this.revenueGrowth = revenueGrowth
        this.divYield = divYield
        this.divRate = divRate
        this.divPayDate = divPayDate
    }
}