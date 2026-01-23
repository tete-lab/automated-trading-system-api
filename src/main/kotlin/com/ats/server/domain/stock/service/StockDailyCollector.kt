package com.ats.server.domain.stock.service

import com.ats.server.domain.stock.repository.StockMasterRepository
import com.ats.server.domain.stock.service.StockDailyService
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.sync.Semaphore
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDate

import java.util.concurrent.atomic.AtomicInteger
import kotlin.system.measureTimeMillis

@Service
class StockDailyCollector(
    private val stockDailyService: StockDailyService,
    private val stockMasterRepository: StockMasterRepository // 종목코드 가져오기용
) {
    private val log = LoggerFactory.getLogger(javaClass)

    // [설정] 키움 API 서버 부하를 고려한 동시 요청 수 (너무 높으면 IP 차단됨)
    // 10 ~ 20 정도가 적당해보임. 로그 보면서 조절 필요. (EOF가 잦으면 10 또는 5로 수정)
    private val limitSemaphore = Semaphore(20)

    fun collectAll(targetDate: LocalDate) {
        val codes = stockMasterRepository.findAllStockCodes() // 전체 종목 코드 리스트 (약 2500개)
        val total = codes.size
        val successCount = AtomicInteger(0)
        val failCount = AtomicInteger(0)

        log.info(">>> [Collector] 총 ${total}개 종목 병렬 수집 시작 (Max Concurrency: 20)")

        val time = measureTimeMillis {
            // 코루틴 스코프 실행 (Dispatcher.IO는 네트워크 요청에 최적화된 스레드풀 사용)
            runBlocking(Dispatchers.IO) {
                codes.forEach { stockCode ->
                    launch {
                        // 세마포어로 동시 실행 개수 제한 (Rate Limiting)
                        limitSemaphore.withPermit {
                            try {
                                val result = stockDailyService.fetchAndSaveDailyPrice(stockCode, targetDate)
                                if (result > 0) successCount.incrementAndGet()
                            } catch (e: Exception) {
                                log.error("Error fetching $stockCode: ${e.message}")
                                failCount.incrementAndGet()
                            }
                        }
                    }
                }
            }
        }

        log.info(">>> [완료] 소요시간: ${time / 1000}초, 성공: $successCount, 실패: $failCount")
    }
}