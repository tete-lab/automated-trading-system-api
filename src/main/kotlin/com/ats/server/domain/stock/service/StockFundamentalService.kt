package com.ats.server.domain.stock.service

import com.ats.server.domain.stock.dto.StockFundamentalCreateReq
import com.ats.server.domain.stock.dto.StockFundamentalRes
import com.ats.server.domain.stock.dto.StockFundamentalUpdateReq
import com.ats.server.domain.stock.entity.StockFundamental
import com.ats.server.domain.stock.repository.StockFundamentalRepository
import com.ats.server.domain.stock.repository.StockMasterRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class StockFundamentalService(
    private val fundamentalRepository: StockFundamentalRepository,
    private val stockMasterRepository: StockMasterRepository // 종목 존재 여부 확인용
) {

    // 조회
    fun getFundamental(stockCode: String): StockFundamentalRes {
        val entity = fundamentalRepository.findById(stockCode)
            .orElseThrow { IllegalArgumentException("해당 종목의 분석 정보가 없습니다: $stockCode") }
        return toRes(entity)
    }

    // 등록
    @Transactional
    fun createFundamental(req: StockFundamentalCreateReq): String {
        // 1. 실제 종목 마스터에 있는 코드인지 확인
        if (!stockMasterRepository.existsById(req.stockCode)) {
            throw IllegalArgumentException("존재하지 않는 종목코드입니다: ${req.stockCode}")
        }
        // 2. 이미 분석 정보가 있는지 확인
        if (fundamentalRepository.existsById(req.stockCode)) {
            throw IllegalStateException("이미 분석 정보가 존재합니다. 수정 기능을 이용하세요.")
        }

        val entity = StockFundamental(
            stockCode = req.stockCode,
            marketCap = req.marketCap,
            avgVolume = req.avgVolume,
            per = req.per,
            pbr = req.pbr,
            psr = req.psr,
            eps = req.eps,
            bps = req.bps,
            roe = req.roe,
            revenueGrowth = req.revenueGrowth,
            divYield = req.divYield,
            divRate = req.divRate,
            divPayDate = req.divPayDate
        )
        fundamentalRepository.save(entity)
        return entity.stockCode
    }

    // 수정
    @Transactional
    fun updateFundamental(stockCode: String, req: StockFundamentalUpdateReq): String {
        val entity = fundamentalRepository.findById(stockCode)
            .orElseThrow { IllegalArgumentException("분석 정보가 존재하지 않습니다: $stockCode") }

        entity.update(
            req.marketCap, req.avgVolume, req.per, req.pbr, req.psr,
            req.eps, req.bps, req.roe, req.revenueGrowth,
            req.divYield, req.divRate, req.divPayDate
        )
        return entity.stockCode
    }

    // 변환 헬퍼
    private fun toRes(entity: StockFundamental) = StockFundamentalRes(
        stockCode = entity.stockCode,
        marketCap = entity.marketCap,
        avgVolume = entity.avgVolume,
        per = entity.per,
        pbr = entity.pbr,
        psr = entity.psr,
        eps = entity.eps,
        bps = entity.bps,
        roe = entity.roe,
        revenueGrowth = entity.revenueGrowth,
        divYield = entity.divYield,
        divRate = entity.divRate,
        divPayDate = entity.divPayDate,
        updatedAt = entity.updatedAt.toString()
    )
}