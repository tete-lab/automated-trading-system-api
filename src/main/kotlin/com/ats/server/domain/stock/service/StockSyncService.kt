package com.ats.server.domain.stock.service

import com.ats.server.domain.stock.dto.StockCreateReq
import com.ats.server.domain.stock.dto.StockFundamentalCreateReq
import com.ats.server.domain.stock.dto.StockFundamentalUpdateReq
import com.ats.server.domain.stock.repository.StockDailyRepository
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
import org.springframework.transaction.annotation.Transactional // 주의: 클래스 레벨 Transactional은 제거하거나 readOnly로 하고, 개별 메서드에 맡기는 게 좋음
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicInteger

@Service
class StockSyncService(
    private val publicDataClient: PublicDataClient,
    private val stockMasterService: StockMasterService,
    private val stockDailyService: StockDailyService,
    private val stockFundamentalService: StockFundamentalService,
    private val stockDailyRepository: StockDailyRepository,
    private val stockMasterRepository: StockMasterRepository
) {

    private val log = LoggerFactory.getLogger(javaClass)

    // [설정] DB 커넥션 풀을 고려하여 동시 처리 개수 제한 (20~30 적당)
    private val semaphore = Semaphore(20)

    // =========================================================================
    // 1. Stock Master & 시가총액 동기화 (Source: 공공데이터포털)
    // =========================================================================
    // [중요] 전체 로직을 Transactional로 묶지 않습니다. (병렬 처리 시 트랜잭션 전파 문제 방지)
    fun syncMasterByPublicData(): Int {
        // 1. 데이터 가져오기 (여긴 네트워크 작업이므로 기존대로 수행)
        val items = findLatestPublicDataStocks()

        if (items.isEmpty()) {
            throw RuntimeException("최근 1주일간 공공데이터 시세 정보가 없습니다. (API 키, 휴장일, 또는 시간 확인)")
        }

        log.info(">>> 동기화 시작: ${items.size}개 종목 병렬 처리")

        // 2. 병렬 처리 준비
        // 멀티 스레드 환경에서 안전하게 숫자를 세기 위해 AtomicInteger 사용
        val saveCount = AtomicInteger(0)

        // 3. 코루틴 실행 (병렬)
        runBlocking(Dispatchers.IO) {
            items.forEach { item ->
                launch {
                    // 세마포어로 동시 DB 접근 제어
                    semaphore.withPermit {
                        try {
                            processStockItem(item)
                            saveCount.incrementAndGet() // 성공 시 카운트 증가
                        } catch (e: Exception) {
                            log.error("Sync Error [${item.itmsNm}]: ${e.message}")
                        }
                    }
                }
            }
        }

        return saveCount.get()
    }

    /**
     * 개별 종목 처리 로직 분리
     * (기존 for문 안의 내용을 함수로 뺌)
     */
    private fun processStockItem(item: PublicDataItem) {
        // "A005930" -> "005930"
        val cleanCode = if (item.srtnCd.startsWith("A")) item.srtnCd.substring(1) else item.srtnCd

        // 1) 마스터 정보 저장 (없을 경우 생성)
        // 주의: existsById와 createStock 사이에 아주 짧은 틈이 있지만, 마스터 데이터 특성상 충돌 확률 낮음
        if (!stockMasterRepository.existsById(cleanCode)) {
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

        // 2) 시가총액(Fundamental) 저장
        updateMarketCap(cleanCode, item.mrktTotAmt)
    }

    private fun findLatestPublicDataStocks(): List<PublicDataItem> {
        var targetDate = LocalDate.now()

        for (i in 0..7) {
            val dateStr = targetDate.minusDays(i.toLong()).format(DateTimeFormatter.ofPattern("yyyyMMdd"))

            // Client 내부에서 로깅 등 시간이 걸릴 수 있으므로, 필요하다면 여기도 Dispatchers.IO를 쓸 수 있지만
            // 호출 횟수가 7번 이내라 그냥 둬도 무방합니다.
            val items = publicDataClient.getStockPriceInfo(dateStr, dateStr)

            if (items.isNotEmpty()) {
                log.info("최신 데이터 발견: $dateStr (총 ${items.size} 종목)")
                return items
            }
        }
        return emptyList()
    }

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