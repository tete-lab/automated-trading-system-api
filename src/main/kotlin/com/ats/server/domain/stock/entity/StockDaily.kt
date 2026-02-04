package com.ats.server.domain.stock.entity

import com.ats.server.global.entity.BaseEntity
import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

@Entity
@Table(
    name = "stock_daily",
    uniqueConstraints = [UniqueConstraint(columnNames = ["stock_code", "base_date"])]
)
class StockDaily(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "stock_daily_id")
    val stockDailyId: Long? = null,

    @Column(name = "stock_code", nullable = false, length = 20)
    val stockCode: String,

    @Column(name = "base_date", nullable = false)
    val baseDate: LocalDate,

    // [기본 시세]
    @Column(name = "close_price", nullable = false, precision = 15, scale = 2)
    var closePrice: BigDecimal,

    @Column(name = "open_price", precision = 15, scale = 2)
    var openPrice: BigDecimal?,

    @Column(name = "high_price", precision = 15, scale = 2)
    var highPrice: BigDecimal?,

    @Column(name = "low_price", precision = 15, scale = 2)
    var lowPrice: BigDecimal?,

    @Column(name = "volume")
    var volume: Long?,

    @Column(name = "volume_price", precision = 15, scale = 2)
    var volumePrice: BigDecimal?,

    @Column(name = "fluctuation_rate")
    var fluctuationRate: String?,
    @Column(name = "individual_buy")
    var individualBuy: String?,
    @Column(name = "organ_buy")
    var organBuy: String?,
    @Column(name = "foreigner_buy")
    var foreignerBuy: String?,

    // [보조 지표]
    @Column(name = "sma_20", precision = 15, scale = 2)
    var sma20: BigDecimal? = null,

    @Column(name = "sma_50", precision = 15, scale = 2)
    var sma50: BigDecimal? = null,

    @Column(name = "ema_9", precision = 15, scale = 2)
    var ema9: BigDecimal? = null,

    @Column(name = "ema_12", precision = 15, scale = 2)
    var ema12: BigDecimal? = null,

    @Column(name = "ema_26", precision = 15, scale = 2)
    var ema26: BigDecimal? = null,

    @Column(name = "rsi", precision = 10, scale = 2)
    var rsi: BigDecimal? = null,

    @Column(name = "macd", precision = 10, scale = 2)
    var macd: BigDecimal? = null,

    @Column(name = "signal_line", precision = 10, scale = 2)
    var signalLine: BigDecimal? = null,

    @Column(name = "cross_type", precision = 10, scale = 2)
    var crossType: Int? = 0, // 예: 1.0(Golden), -1.0(Dead)

    @Column(name = "recommend_yn", length = 1)
    var recommendYn: String = "Y"

) : BaseEntity() {

    // 시세 및 지표 업데이트 메서드
    fun update(
        closePrice: BigDecimal, openPrice: BigDecimal?, highPrice: BigDecimal?, lowPrice: BigDecimal?,
        volume: Long?, volumePrice: BigDecimal?,
        fluctuationRate: String?, individualBuy: String?, organBuy: String?, foreignerBuy: String?,
        sma20: BigDecimal?, sma50: BigDecimal?,
        ema9: BigDecimal?, ema12: BigDecimal?, ema26: BigDecimal?,
        rsi: BigDecimal?, macd: BigDecimal?, signalLine: BigDecimal?,
        crossType: Int?, recommendYn: String
    ) {
        this.closePrice = closePrice
        this.openPrice = openPrice
        this.highPrice = highPrice
        this.lowPrice = lowPrice
        this.volume = volume
        this.volumePrice = volumePrice
        this.fluctuationRate = fluctuationRate
        this.individualBuy = individualBuy
        this.organBuy = organBuy
        this.foreignerBuy = foreignerBuy

        this.sma20 = sma20
        this.sma50 = sma50
        this.ema9 = ema9
        this.ema12 = ema12
        this.ema26 = ema26
        this.rsi = rsi
        this.macd = macd
        this.signalLine = signalLine
        this.crossType = crossType
        this.recommendYn = recommendYn
        this.updatedAt = LocalDateTime.now()
    }
}