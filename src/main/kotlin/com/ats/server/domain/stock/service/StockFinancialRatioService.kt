package com.ats.server.domain.stock.service

import com.ats.server.domain.stock.dto.StockFinancialRatioDto
import com.ats.server.domain.stock.entity.StockFinancialRatio
import com.ats.server.domain.stock.entity.StockFinancialRatioId
import com.ats.server.domain.stock.repository.StockFinancialRatioRepository
import com.ats.server.domain.token.service.TokenService
import com.ats.server.infra.kis.client.KisClient
import com.ats.server.infra.kis.dto.KisFinancialRatioResponse
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class StockFinancialRatioService(
    private val kisClient: KisClient,
    private val repository: StockFinancialRatioRepository,
    private val objectMapper: ObjectMapper,
    private val tokenService: TokenService
    ) {
    private val log = LoggerFactory.getLogger(javaClass)
    /**
     * 특정 종목의 재무 비율 목록 조회
     */
    fun getFinancialRatios(stockCode: String): List<StockFinancialRatioDto> {
        return repository.findAllByStockCodeOrderByStacYymmDesc(stockCode).map { entity ->
            StockFinancialRatioDto(
                stockCode = entity.stockCode,
                stacYymm = entity.stacYymm,
                grs = entity.grs,
                bsopPrfiInrt = entity.bsopPrfiInrt,
                ntinInrt = entity.ntinInrt,
                roeVal = entity.roeVal,
                eps = entity.eps,
                sps = entity.sps,
                bps = entity.bps,
                rsrvRate = entity.rsrvRate,
                lbltRate = entity.lbltRate
            )
        }
    }

    /**
     * 단건 저장 (Upsert)
     */
    @Transactional
    fun saveRatio(dto: StockFinancialRatioDto) {
        val id = StockFinancialRatioId(dto.stockCode, dto.stacYymm)

        // 기존 데이터 조회
        val existingEntity = repository.findById(id).orElse(null)

        if (existingEntity != null) {
            // Update
            existingEntity.update(
                grs = dto.grs,
                bsopPrfiInrt = dto.bsopPrfiInrt,
                ntinInrt = dto.ntinInrt,
                roeVal = dto.roeVal,
                eps = dto.eps,
                sps = dto.sps,
                bps = dto.bps,
                rsrvRate = dto.rsrvRate,
                lbltRate = dto.lbltRate
            )
        } else {
            // Insert
            repository.save(
                StockFinancialRatio(
                    stockCode = dto.stockCode,
                    stacYymm = dto.stacYymm,
                    grs = dto.grs,
                    bsopPrfiInrt = dto.bsopPrfiInrt,
                    ntinInrt = dto.ntinInrt,
                    roeVal = dto.roeVal,
                    eps = dto.eps,
                    sps = dto.sps,
                    bps = dto.bps,
                    rsrvRate = dto.rsrvRate,
                    lbltRate = dto.lbltRate
                )
            )
        }
    }

    /**
     * 다건 저장 (Bulk Upsert)
     */
    @Transactional
    fun saveAll(dtos: List<StockFinancialRatioDto>): Int {
        dtos.forEach { saveRatio(it) } // 간단한 구현 (성능 필요 시 Bulk Insert 쿼리 고려)
        return dtos.size
    }

    /**
     * 특정 종목의 재무비율 데이터를 KIS에서 가져와 저장
     */
    @Transactional
    fun syncFinancialRatio(stockCode: String, token: String): Int {

        val (appKey, appSecret) = tokenService.getAppKeys(null, "KIS")

        // 1. API 호출
        val result = kisClient.fetchFinancialRatio(token, appKey, appSecret, stockCode)

        // 2. 파싱
        val response = objectMapper.readValue(result.body, KisFinancialRatioResponse::class.java)

        if (response.returnCode != "0") {
            log.warn("KIS Financial API Error [$stockCode]: ${response.message}")
            return 0
        }

        val items = response.output ?: emptyList()
        if (items.isEmpty()) return 0

        val saveList = mutableListOf<StockFinancialRatio>()

        // 3. 엔티티 매핑
        items.forEach { item ->
            // PK 확인
            val id = StockFinancialRatioId(stockCode, item.stacYymm)

            // 기존 데이터 확인 (JPA findById)
            val existing = repository.findById(id).orElse(null)

            if (existing != null) {
                // Update
                existing.update(
                    grs = item.grs,
                    bsopPrfiInrt = item.bsopPrfiInrt,
                    ntinInrt = item.ntinInrt,
                    roeVal = item.roeVal,
                    eps = item.eps,
                    sps = item.sps,
                    bps = item.bps,
                    rsrvRate = item.rsrvRate,
                    lbltRate = item.lbltRate
                )
                saveList.add(existing)
            } else {
                // Insert
                saveList.add(
                    StockFinancialRatio(
                        stockCode = stockCode,
                        stacYymm = item.stacYymm,
                        grs = item.grs,
                        bsopPrfiInrt = item.bsopPrfiInrt,
                        ntinInrt = item.ntinInrt,
                        roeVal = item.roeVal,
                        eps = item.eps,
                        sps = item.sps,
                        bps = item.bps,
                        rsrvRate = item.rsrvRate,
                        lbltRate = item.lbltRate
                    )
                )
            }
        }

        // 4. 저장
        if (saveList.isNotEmpty()) {
            repository.saveAll(saveList)
        }

        //log.info(">>> [KIS Financial] $stockCode : ${saveList.size}건 저장 완료")
        return saveList.size
    }
}