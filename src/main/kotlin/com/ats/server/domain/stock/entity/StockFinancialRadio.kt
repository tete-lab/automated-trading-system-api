package com.ats.server.domain.stock.entity

import jakarta.persistence.*
import java.io.Serializable
import com.ats.server.global.entity.BaseEntity // 기존에 사용하시던 BaseEntity가 있다면 상속

// [1] 복합키 클래스 (Serializable 구현 필수)
data class StockFinancialRatioId(
    var stockCode: String = "",
    var stacYymm: String = ""
) : Serializable

// [2] Entity 정의
@Entity
@Table(name = "stock_financial_ratio")
@IdClass(StockFinancialRatioId::class) // 복합키 클래스 지정
class StockFinancialRatio(
    @Id
    @Column(name = "stock_code", length = 20, nullable = false)
    var stockCode: String,

    @Id
    @Column(name = "stac_yymm", length = 6, nullable = false)
    var stacYymm: String, // 결산년월 (YYYYMM)

    @Column(name = "grs", length = 12)
    var grs: String? = null, // 매출액 증가율

    @Column(name = "bsop_prfi_inrt", length = 12)
    var bsopPrfiInrt: String? = null, // 영업이익 증가율

    @Column(name = "ntin_inrt", length = 12)
    var ntinInrt: String? = null, // 순이익 증가율

    @Column(name = "roe_val", length = 13)
    var roeVal: String? = null, // ROE

    @Column(name = "eps", length = 11)
    var eps: String? = null, // EPS

    @Column(name = "sps", length = 18)
    var sps: String? = null, // SPS

    @Column(name = "bps", length = 11)
    var bps: String? = null, // BPS

    @Column(name = "rsrv_rate", length = 20)
    var rsrvRate: String? = null, // 유보율

    @Column(name = "lblt_rate", length = 20)
    var lbltRate: String? = null // 부채비율

) {
    // 업데이트 편의 메서드
    fun update(
        grs: String?, bsopPrfiInrt: String?, ntinInrt: String?,
        roeVal: String?, eps: String?, sps: String?,
        bps: String?, rsrvRate: String?, lbltRate: String?
    ) {
        this.grs = grs
        this.bsopPrfiInrt = bsopPrfiInrt
        this.ntinInrt = ntinInrt
        this.roeVal = roeVal
        this.eps = eps
        this.sps = sps
        this.bps = bps
        this.rsrvRate = rsrvRate
        this.lbltRate = lbltRate
    }
}