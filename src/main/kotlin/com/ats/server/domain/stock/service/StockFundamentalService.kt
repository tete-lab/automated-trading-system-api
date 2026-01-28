package com.ats.server.domain.stock.service

import com.ats.server.domain.stock.dto.StockFundamentalCreateReq
import com.ats.server.domain.stock.dto.StockFundamentalRes
import com.ats.server.domain.stock.dto.StockFundamentalUpdateReq
import com.ats.server.domain.stock.entity.StockFundamental
import com.ats.server.domain.stock.repository.StockFundamentalRepository
import com.ats.server.domain.stock.repository.StockMasterRepository
import com.ats.server.infra.kiwoom.client.KiwoomClient
import com.ats.server.infra.kiwoom.dto.KiwoomFundamentalResponse
import com.fasterxml.jackson.databind.ObjectMapper
import kotlinx.coroutines.delay
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

@Service
@Transactional(readOnly = true)
class StockFundamentalService(
    private val fundamentalRepository: StockFundamentalRepository,
    private val stockMasterRepository: StockMasterRepository, // 종목 존재 여부 확인용
    private val kiwoomClient: KiwoomClient,
    private val objectMapper: ObjectMapper,
    private val stockFundamentalRepository: StockFundamentalRepository
) {
    // 로거 설정
    private val log = LoggerFactory.getLogger(javaClass)

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

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    suspend fun fetchAndSaveFundamental(stockCode: String, token: String): Int {
        var nextKey: String? = null
        var totalUpdateCount = 0

        // [Loop] 연속 데이터가 없을 때까지 반복
        do {
            try {
                // 1. API 호출 (nextKey 전달)
                val result = kiwoomClient.fetchStockFundamental(token, stockCode, nextKey)

                // 2. Body 파싱
                val dto = objectMapper.readValue(result.body, KiwoomFundamentalResponse::class.java)

                if (dto.returnCode != "0") {
                    log.warn("Kiwoom API Error [$stockCode]: ${dto.returnMsg}")
                    break // 에러 나면 루프 중단
                }

                // 3. 데이터 저장 (Upsert)
                saveToDb(stockCode, dto)
                totalUpdateCount++

                // 4. 다음 키 설정 (없으면 null이 되어 루프 종료)
                nextKey = if (result.hasNext) result.nextKey else null

                // [안전장치] 너무 빠른 연속 호출 방지 (필요 시)
                if (nextKey != null) delay(50)

            } catch (e: Exception) {
                log.error("Failed to fetch fundamental for $stockCode : ${e.message}")
                break
            }
        } while (nextKey != null) // nextKey가 있는 동안 계속 돕니다.

        return totalUpdateCount
    }

    private fun saveToDb(stockCode: String, dto: KiwoomFundamentalResponse) {
        val marketCap = toBigDecimal(dto.marketCap)
        val avgVolume = toBigDecimal(dto.trdeQty)
        val per = toBigDecimal(dto.per)
        val pbr = toBigDecimal(dto.pbr)
        val psr = toBigDecimal("0")
        val eps = toBigDecimal(dto.eps)
        val bps = toBigDecimal(dto.bps)
        val roe = toBigDecimal(dto.roe)
        val revenueGrowth = null
        val divYield = null
        val divRate = null
        val divPayDate = null

        val entity = stockFundamentalRepository.findById(stockCode).orElse(null)

        if (entity != null) {

            entity.update(marketCap, avgVolume, per, pbr, psr, eps, bps, roe, revenueGrowth, divYield, divRate, divPayDate)
        } else {
            val newEntity = StockFundamental(
                stockCode = stockCode,
                marketCap = marketCap,
                avgVolume = avgVolume,
                per = per,
                pbr = pbr,
                psr = psr,
                eps = eps,
                bps = bps,
                roe = roe,
                revenueGrowth = revenueGrowth,
                divYield = divYield,
                divRate = divRate,
                divPayDate = divPayDate
            )
            stockFundamentalRepository.save(newEntity)
        }
    }

    // 빈 문자열 안전하게 BigDecimal로 변환하는 유틸
    private fun toBigDecimal(value: String?): BigDecimal? {
        if (value.isNullOrBlank()) return null
        return try {
            // 콤마 제거 등 전처리 필요시 추가
            BigDecimal(value.trim())
        } catch (e: NumberFormatException) {
            null
        }
    }
}