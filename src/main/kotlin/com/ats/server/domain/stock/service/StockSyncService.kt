package com.ats.server.domain.stock.service

import com.ats.server.domain.stock.dto.StockCreateReq
import com.ats.server.domain.stock.dto.StockFundamentalCreateReq
import com.ats.server.domain.stock.dto.StockFundamentalUpdateReq
import com.ats.server.domain.stock.repository.StockMasterRepository
import com.ats.server.infra.publicdata.client.PublicDataClient
import com.ats.server.infra.publicdata.dto.PublicDataItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicInteger

@Service
class StockSyncService(
    private val publicDataClient: PublicDataClient,
    private val stockMasterService: StockMasterService,
    private val stockFundamentalService: StockFundamentalService,
    private val stockMasterRepository: StockMasterRepository
) {

    private val log = LoggerFactory.getLogger(javaClass)
    private val semaphore = Semaphore(20) // DB 커넥션 풀 고려 (20~30)

    fun syncMasterByPublicData(): Int {
        // 1. 공공데이터 포털에서 최신 데이터 가져오기
        val items = findLatestPublicDataStocks()

        if (items.isEmpty()) {
            throw RuntimeException("최근 1주일간 공공데이터 시세 정보가 없습니다.")
        }

        // [최적화 핵심] 현재 DB에 있는 모든 종목 코드를 Set으로 가져옴 (DB 조회 1회)
        // Set은 contains 검색 속도가 O(1)이라서 매우 빠름
        val existingCodes = stockMasterRepository.findAllStockCodes().toSet()

        log.info(">>> 동기화 시작: ${items.size}개 종목 병렬 처리 (기존 종목: ${existingCodes.size}개)")

        val saveCount = AtomicInteger(0)

        // 2. 코루틴 병렬 처리 시작
        runBlocking(Dispatchers.IO) {
            items.forEach { item ->
                launch {
                    // 세마포어로 동시 진입 제한 (DB 부하 방지)
                    semaphore.withPermit {
                        try {
                            // "A005930" -> "005930"
                            val cleanCode = if (item.srtnCd.startsWith("A")) item.srtnCd.substring(1) else item.srtnCd

                            // [최적화] DB 쿼리(existsById) 없이 메모리에서 즉시 확인
                            if (cleanCode !in existingCodes) {
                                stockMasterService.createStock(
                                    StockCreateReq(
                                        stockCode = cleanCode,
                                        stockName = item.itmsNm,
                                        market = item.mrktCtg,
                                        sector = null,
                                        auditRisk = null
                                    )
                                )
                            }

                            // 시가총액 정보 업데이트 (이건 별도 함수 유지 추천)
                            updateMarketCap(cleanCode, item.mrktTotAmt)

                            saveCount.incrementAndGet()
                        } catch (e: Exception) {
                            log.error("Sync Error [${item.itmsNm}]: ${e.message}")
                        }
                    }
                }
            }
        }

        return saveCount.get()
    }

    // 날짜별 데이터 찾기 로직
    private fun findLatestPublicDataStocks(): List<PublicDataItem> {
        var targetDate = LocalDate.now()
        for (i in 0..7) {
            val dateStr = targetDate.minusDays(i.toLong()).format(DateTimeFormatter.ofPattern("yyyyMMdd"))
            val items = publicDataClient.getStockPriceInfo(dateStr, dateStr)
            if (items.isNotEmpty()) {
                log.info("최신 데이터 발견: $dateStr (총 ${items.size} 종목)")
                return items
            }
        }
        return emptyList()
    }

    // 시가총액 업데이트 로직 (코드가 길어서 따로 두는 게 좋음)
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
}