package com.ats.server.domain.stock.service

import com.ats.server.domain.stock.repository.StockMasterRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicInteger
import kotlin.system.measureTimeMillis

@Service
class StockFundamentalCollector(
    private val stockFundamentalService: StockFundamentalService,
    private val stockMasterRepository: StockMasterRepository
) {

    private val log = LoggerFactory.getLogger(javaClass)

    // [조절 필요] 에러가 계속 나면 20 -> 10, 5로 줄이세요.
    private val limitSemaphore = Semaphore(20) // 20에서 10으로 하향 조정 권장

    fun collectAll(token: String): Int {
        val today = LocalDate.now()

        // [수정 전]
        // val codes = stockMasterRepository.findAllStockCodes()

        // [수정 후] 오늘 아직 업데이트되지 않은 종목만 가져오기
        val codes = stockMasterRepository.findStockCodesByMissingFundamentalData(today)

        val total = codes.size

        // 이미 다 수집된 경우 바로 종료
        if (total == 0) {
            log.info(">>> [Fundamental Collector] 오늘 재무 데이터는 이미 모두 최신 상태입니다.")
            return 0
        }
        val successCount = AtomicInteger(0)
        val failCount = AtomicInteger(0)

        runBlocking(Dispatchers.IO) {
            codes.forEach { stockCode ->
                launch {
                    limitSemaphore.withPermit {
                        try {
                            val result = stockFundamentalService.fetchAndSaveFundamental(stockCode, token)
                            if (result > 0) successCount.incrementAndGet()
                            else failCount.incrementAndGet()

                            // [핵심 해결책] 처리 후 아주 잠깐 쉼 (Throttling)
                            // 20개 스레드가 각각 0.05초씩 쉬어주면 서버 부하가 확 줄어듭니다.
                            Thread.sleep(50)

                        } catch (e: Exception) {
                            failCount.incrementAndGet()
                        }
                    }
                }
            }
        }
        return successCount.get()
    }
}