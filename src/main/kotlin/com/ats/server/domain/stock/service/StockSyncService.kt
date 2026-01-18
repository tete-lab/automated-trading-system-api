package com.ats.server.domain.stock.service

import com.ats.server.domain.stock.dto.StockCreateReq
import com.ats.server.domain.stock.dto.StockDailyCreateReq
import com.ats.server.domain.stock.dto.StockFundamentalCreateReq
import com.ats.server.domain.stock.dto.StockFundamentalUpdateReq
import com.ats.server.domain.stock.repository.StockDailyRepository
import com.ats.server.domain.stock.repository.StockMasterRepository
import com.ats.server.infra.publicdata.client.PublicDataClient
import com.ats.server.infra.publicdata.dto.PublicDataItem
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Service
class StockSyncService(
    private val publicDataClient: PublicDataClient,
    private val stockMasterService: StockMasterService,
    private val stockDailyService: StockDailyService,
    private val stockFundamentalService: StockFundamentalService,
    private val stockDailyRepository: StockDailyRepository,
    private val stockMasterRepository: StockMasterRepository
) {

    // =========================================================================
    // 1. Stock Master & 시가총액 동기화 (Source: 공공데이터포털)
    // =========================================================================
    fun syncMasterByPublicData(): Int {
        // [수정됨] 최근 영업일 데이터 찾기 로직 간소화
        val items = findLatestPublicDataStocks()

        if (items.isEmpty()) {
            throw RuntimeException("최근 1주일간 공공데이터 시세 정보가 없습니다. (API 키, 휴장일, 또는 시간 확인)")
        }

        var saveCount = 0
        for (item in items) {
            try {
                // "A005930" -> "005930"
                val cleanCode = if (item.srtnCd.startsWith("A")) item.srtnCd.substring(1) else item.srtnCd

                // 1) 마스터 정보 저장 (없을 경우 생성)
                if (!stockMasterRepository.existsById(cleanCode)) {
                    stockMasterService.createStock(
                        StockCreateReq(
                            stockCode = cleanCode,
                            stockName = item.itmsNm,
                            market = item.mrktCtg,
                            sector = null, // 공공데이터 미제공
                            auditRisk = null
                        )
                    )
                }

                // 2) 시가총액(Fundamental) 저장
                updateMarketCap(cleanCode, item.mrktTotAmt)

                saveCount++
            } catch (e: Exception) {
                // 로그만 찍고 계속 진행
                println("Sync Error [${item.itmsNm}]: ${e.message}")
            }
        }
        return saveCount
    }

    /**
     * [핵심 변경]
     * 이제 publicDataClient.getStockPriceInfo 하나로 해결합니다.
     * 날짜를 하루씩 줄여가며 호출해보고, 데이터가 나오면 그 리스트(전체 페이지 포함)를 반환합니다.
     */
    private fun findLatestPublicDataStocks(): List<PublicDataItem> {
        var targetDate = LocalDate.now()

        // 오늘 데이터가 아직 안 나왔을 수 있으니(장 중이거나 아침), 최대 7일 전까지 뒤져봅니다.
        for (i in 0..7) {
            val dateStr = targetDate.minusDays(i.toLong()).format(DateTimeFormatter.ofPattern("yyyyMMdd"))

            // [중요] 시작일=dateStr, 종료일=dateStr 로 호출하면 -> "해당 날짜의 전 종목"을 가져옵니다.
            // Client 내부에서 while 루프로 모든 페이지를 긁어오므로 여기서 페이징 신경 쓸 필요 X
            val items = publicDataClient.getStockPriceInfo(dateStr, dateStr)

            if (items.isNotEmpty()) {
                println("최신 데이터 발견: $dateStr (총 ${items.size} 종목)")
                return items
            }
        }
        return emptyList()
    }

    // 시가총액 저장 로직 (이전과 동일)
    private fun updateMarketCap(code: String, marketCapStr: String) {
        val marketCap = try { BigDecimal(marketCapStr) } catch (e: Exception) { BigDecimal.ZERO }

        try {
            stockFundamentalService.updateFundamental(
                code,
                StockFundamentalUpdateReq(
                    marketCap = marketCap,
                    avgVolume = null, per = null, pbr = null, psr = null,
                    eps = null, bps = null, roe = null, revenueGrowth = null,
                    divYield = null, divRate = null, divPayDate = null
                )
            )
        } catch (e: Exception) {
            stockFundamentalService.createFundamental(
                StockFundamentalCreateReq(
                    stockCode = code,
                    marketCap = marketCap,
                    avgVolume = null, per = null, pbr = null, psr = null,
                    eps = null, bps = null, roe = null, revenueGrowth = null,
                    divYield = null, divRate = null, divPayDate = null
                )
            )
        }
    }


    // =========================================================================
    // 2. Stock Daily 동기화 (Source: 공공데이터포털)
    // =========================================================================
    @Transactional
    fun syncDailyByPublicData(stockCode: String, startDate: LocalDate, endDate: LocalDate) {
        val startStr = startDate.format(DateTimeFormatter.ofPattern("yyyyMMdd"))
        val endStr = endDate.format(DateTimeFormatter.ofPattern("yyyyMMdd"))

        // [수정됨] Client가 자동 페이징을 해주므로 그냥 호출하면 기간 내 모든 데이터가 옴
        val rawItems = publicDataClient.getStockPriceInfo(startStr, endStr)

        // 해당 종목만 필터링 (공공데이터는 보통 'A'+코드 사용)
        val items = rawItems
            .filter { it.srtnCd == "A$stockCode" || it.srtnCd == stockCode }
            .sortedBy { it.basDt }

        if (items.isEmpty()) {
            println("[$stockCode] 해당 기간 데이터 없음")
            return
        }

        // 기존 데이터 삭제
        val existingList = stockDailyRepository.findAllByStockCodeAndBaseDateBetweenOrderByBaseDateAsc(stockCode, startDate, endDate)
        stockDailyRepository.deleteAll(existingList)
        stockDailyRepository.flush()

        // 저장
        for (item in items) {
            stockDailyService.createDaily(
                StockDailyCreateReq(
                    stockCode = stockCode,
                    baseDate = LocalDate.parse(item.basDt, DateTimeFormatter.ofPattern("yyyyMMdd")),
                    closePrice = item.clpr.toBigDecimal(),
                    openPrice = item.mkp.toBigDecimal(),
                    highPrice = item.hipr.toBigDecimal(),
                    lowPrice = item.lopr.toBigDecimal(), // DTO 필드명 lopr
                    volume = item.trqu.toLong(),
                    volumePrice = item.trPrc.toBigDecimal()
                )
            )
        }
    }
}