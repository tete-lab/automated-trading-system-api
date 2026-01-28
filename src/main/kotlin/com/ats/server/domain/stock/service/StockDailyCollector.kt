package com.ats.server.domain.stock.service

import com.ats.server.domain.stock.repository.StockMasterRepository
import com.ats.server.domain.stock.service.StockDailyService
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.sync.Semaphore
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.client.HttpServerErrorException
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

    fun collectAll(targetDate: LocalDate, token: String) : Int {
        // [수정 전] 전체 종목 리스트
        // val codes = stockMasterRepository.findAllStockCodes()

        // [수정 후] 해당 날짜에 데이터가 없는 종목만 가져오기 (이렇게 하면 오류난 것들만 다시 호출할 수 있음)
        val codes = stockMasterRepository.findStockCodesByMissingDailyData(targetDate)
        val total = codes.size

        // 이미 다 수집된 경우 바로 종료
        if (total == 0) {
            log.info(">>> [Collector] $targetDate 일자 데이터는 이미 모두 수집되었습니다.")
            return 0
        }
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
                                val result = stockDailyService.fetchAndSaveDailyPrice(stockCode, targetDate, token)
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
        return successCount.get()
        log.info(">>> [완료] 소요시간: ${time / 1000}초, 성공: $successCount, 실패: $failCount")
    }


    private val kisLimitSemaphore = Semaphore(5)
    /**
     * [한국투자증권] 기간별 시세 병렬 수집 (Bulk)
     * - startDate == endDate 일 경우: 아직 수집 안 된 종목만 골라서 수집 (최적화)
     * - startDate != endDate 일 경우: 전체 종목 대상 수집 (Backfill)
     */
    fun collectAllPeriodFromKis(startDate: LocalDate, endDate: LocalDate, token: String): Int {
        // 1. 수집 대상 종목 선정
        val codes = if (startDate == endDate) {
            // [최적화] 시작과 끝이 같으면(예: 오늘자 수집), 해당 날짜 데이터가 없는 종목만 가져옴
            log.info(">>> [KIS Collector] $startDate 일자 미수집 종목만 선별하여 수집합니다.")
            stockMasterRepository.findStockCodesByMissingDailyData(endDate)
        } else {
            // [기간 수집] 특정 구간이므로 전체 종목을 대상으로 수행 (필요 시 로직 변경 가능)
            log.info(">>> [KIS Collector] $startDate ~ $endDate 기간 전체 종목 수집을 시작합니다.")
            stockMasterRepository.findAllStockCodes()
        }

        val total = codes.size
        if (total == 0) {
            log.info(">>> [KIS Collector] 대상 기간($startDate ~ $endDate)의 수집할 종목이 없습니다.")
            return 0
        }

        val successCount = AtomicInteger(0)
        val failCount = AtomicInteger(0)

        // [주의] KIS API는 초당 조회 제한(TPS)이 타이트하므로 세마포어를 키움(20)보다 보수적으로 잡는 게 안전합니다. (예: 5~10)
        // 여기서는 기존 설정인 kisLimitSemaphore(20)을 쓰되, 에러가 나면 줄여야 합니다.
        log.info(">>> [KIS Collector] 총 ${total}개 종목 병렬 수집 시작 (Max Concurrency: ${kisLimitSemaphore.availablePermits})")

        val time = measureTimeMillis {
            // 코루틴 스코프 (IO Dispatcher)
            runBlocking(Dispatchers.IO) {
                codes.forEach { stockCode ->
                    launch {
                        // 세마포어로 동시 실행 제어 (Rate Limiting)
                        kisLimitSemaphore.withPermit {
                            var retryCount = 0
                            val maxRetry = 3
                            var isSuccess = false

                            // [수정 3] 재시도 로직 추가 (초당 건수 초과 에러 대응)
                            while (retryCount < maxRetry && !isSuccess) {
                                try {
                                    val result = stockDailyService.fetchAndSavePeriodDailyPriceFromKis(
                                        stockCode = stockCode,
                                        startDate = startDate,
                                        endDate = endDate,
                                        token = token
                                    )

                                    if (result > 0) successCount.incrementAndGet()
                                    isSuccess = true

                                    // [수정 2] 성공했더라도 다음 요청을 위해 강제로 0.2초 쉼 (TPS 조절)
                                    delay(200)

                                } catch (e: HttpServerErrorException) {
                                    // 500 에러 중 "초당 거래건수" 관련 에러인지 확인
                                    val errorBody = e.responseBodyAsString
                                    if (errorBody.contains("msg1\":\"초당") || errorBody.contains("EGW00201")) {
                                        retryCount++
                                        log.warn(">>> [TPS 초과] $stockCode 잠시 대기 후 재시도 ($retryCount/$maxRetry)")

                                        // 초과 에러가 나면 1초 푹 쉬고 재시도
                                        delay(1000)
                                    } else {
                                        // 다른 500 에러면 바로 실패 처리 및 루프 탈출
                                        log.error("KIS 500 Error [$stockCode]: $errorBody")
                                        break
                                    }
                                } catch (e: Exception) {
                                    log.error("KIS Error [$stockCode]: ${e.message}")
                                    break
                                }
                            }

                            if (!isSuccess) {
                                failCount.incrementAndGet()
                            }
                        }
                    }
                }
            }
        }

        log.info(">>> [KIS 완료] 소요시간: ${time / 1000}초, 대상: $total, 성공: $successCount, 실패: $failCount")
        return successCount.get()
    }
}