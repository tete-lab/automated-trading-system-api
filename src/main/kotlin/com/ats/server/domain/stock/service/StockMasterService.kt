package com.ats.server.domain.stock.service

import com.ats.server.domain.stock.dto.StockCreateReq
import com.ats.server.domain.stock.dto.StockRes
import com.ats.server.domain.stock.dto.StockUpdateReq
import com.ats.server.domain.stock.entity.StockMaster
import com.ats.server.domain.stock.repository.StockMasterRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class StockMasterService(
    private val stockMasterRepository: StockMasterRepository
) {

    // 전체 조회
    fun getAllStocks(): List<StockRes> {
        return stockMasterRepository.findAll().map { toRes(it) }
    }

    // 시장별 조회 (KOSPI, KOSDAQ)
    fun getStocksByMarket(market: String): List<StockRes> {
        return stockMasterRepository.findAllByMarket(market).map { toRes(it) }
    }

    // 단건 조회
    fun getStock(code: String): StockRes {
        val entity = findByIdOrThrow(code)
        return toRes(entity)
    }

    // 등록
    @Transactional
    fun createStock(req: StockCreateReq): String {
        if (stockMasterRepository.existsById(req.stockCode)) {
            throw IllegalStateException("이미 존재하는 종목코드입니다: ${req.stockCode}")
        }

        val stock = StockMaster(
            stockCode = req.stockCode,
            stockName = req.stockName,
            market = req.market,
            sector = req.sector,
            auditRisk = req.auditRisk
        )
        stockMasterRepository.save(stock)
        return stock.stockCode
    }

    // 수정
    @Transactional
    fun updateStock(code: String, req: StockUpdateReq): String {
        val stock = findByIdOrThrow(code)
        stock.update(req.stockName, req.market, req.sector, req.auditRisk)
        return stock.stockCode
    }

    // 삭제
    @Transactional
    fun deleteStock(code: String) {
        if (!stockMasterRepository.existsById(code)) {
            throw IllegalArgumentException("삭제할 종목이 존재하지 않습니다.")
        }
        stockMasterRepository.deleteById(code)
    }

    // 헬퍼 메서드: Entity -> Res 변환
    private fun toRes(entity: StockMaster) = StockRes(
        stockCode = entity.stockCode,
        stockName = entity.stockName,
        market = entity.market,
        sector = entity.sector,
        auditRisk = entity.auditRisk,
        updatedAt = entity.updatedAt.toString()
    )

    // 헬퍼 메서드: 조회 로직
    private fun findByIdOrThrow(code: String): StockMaster {
        return stockMasterRepository.findById(code)
            .orElseThrow { IllegalArgumentException("해당 종목을 찾을 수 없습니다: $code") }
    }
}