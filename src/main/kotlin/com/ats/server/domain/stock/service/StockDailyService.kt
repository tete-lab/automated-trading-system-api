package com.ats.server.domain.stock.service

import com.ats.server.domain.stock.dto.StockDailyCreateReq
import com.ats.server.domain.stock.dto.StockDailyRes
import com.ats.server.domain.stock.dto.StockDailyUpdateReq
import com.ats.server.domain.stock.entity.StockDaily
import com.ats.server.domain.stock.repository.StockDailyRepository
import com.ats.server.domain.stock.repository.StockMasterRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Service
@Transactional(readOnly = true)
class StockDailyService(
    private val stockDailyRepository: StockDailyRepository,
    private val stockMasterRepository: StockMasterRepository
) {

    // 기간 조회 (차트 데이터)
    fun getDailyList(stockCode: String, startDate: LocalDate, endDate: LocalDate): List<StockDailyRes> {
        val list = stockDailyRepository.findAllByStockCodeAndBaseDateBetweenOrderByBaseDateAsc(
            stockCode, startDate, endDate
        )
        return list.map { toRes(it) }
    }

    // 단건 등록
    @Transactional
    fun createDaily(req: StockDailyCreateReq): Long {
        // 1. 종목 존재 여부 확인
        if (!stockMasterRepository.existsById(req.stockCode)) {
            throw IllegalArgumentException("존재하지 않는 종목코드입니다: ${req.stockCode}")
        }
        // 2. 해당 날짜 데이터 중복 확인
        if (stockDailyRepository.existsByStockCodeAndBaseDate(req.stockCode, req.baseDate)) {
            throw IllegalStateException("이미 해당 날짜의 시세 데이터가 존재합니다. (${req.baseDate})")
        }

        val entity = StockDaily(
            stockCode = req.stockCode,
            baseDate = req.baseDate,
            closePrice = req.closePrice,
            openPrice = req.openPrice,
            highPrice = req.highPrice,
            lowPrice = req.lowPrice,
            volume = req.volume,
            volumePrice = req.volumePrice,
            sma20 = req.sma20,
            sma50 = req.sma50,
            rsi = req.rsi,
            macd = req.macd,
            signalLine = req.signalLine,
            crossType = req.crossType,
            recommendYn = req.recommendYn
        )
        return stockDailyRepository.save(entity).stockDailyId!!
    }

    // 수정 (날짜와 종목코드로 찾아서 업데이트)
    @Transactional
    fun updateDaily(stockCode: String, baseDate: LocalDate, req: StockDailyUpdateReq): Long {
        val entity = stockDailyRepository.findByStockCodeAndBaseDate(stockCode, baseDate)
            .orElseThrow { IllegalArgumentException("해당 날짜의 시세 데이터가 없습니다.") }

        entity.update(
            req.closePrice, req.openPrice, req.highPrice, req.lowPrice,
            req.volume, req.volumePrice,
            req.sma20, req.sma50, req.ema9, req.ema12, req.ema26,
            req.rsi, req.macd, req.signalLine, req.crossType, req.recommendYn
        )
        return entity.stockDailyId!!
    }

    // 변환 헬퍼
    private fun toRes(entity: StockDaily) = StockDailyRes(
        stockDailyId = entity.stockDailyId!!,
        stockCode = entity.stockCode,
        baseDate = entity.baseDate,
        closePrice = entity.closePrice,
        openPrice = entity.openPrice,
        highPrice = entity.highPrice,
        lowPrice = entity.lowPrice,
        volume = entity.volume,
        rsi = entity.rsi,
        macd = entity.macd,
        recommendYn = entity.recommendYn
    )
}