package com.ats.server.domain.stock.service

import com.ats.server.domain.stock.repository.StockDailyRepository
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
    private val stockFinancialRatioService: StockFinancialRatioService,
    private val stockMasterRepository: StockMasterRepository, // 종목코드 가져오기용
    private val stockDailyRepository: StockDailyRepository
) {
    private val log = LoggerFactory.getLogger(javaClass)

    // [설정] 키움 API 서버 부하를 고려한 동시 요청 수 (너무 높으면 IP 차단됨)
    // 10 ~ 20 정도가 적당해보임. 로그 보면서 조절 필요. (EOF가 잦으면 10 또는 5로 수정)
    private val limitSemaphore = Semaphore(10)

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

        log.info(">>> [Collector] 총 ${total}개 종목 병렬 수집 시작 (Max Concurrency: 10)")

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
        // 여기서는 기존 설정인 kisLimitSemaphore(5)을 쓰되, 에러가 나면 줄여야 합니다.
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


    private val kisLimitSemaphoreForRatio = Semaphore(4)
    /**
     * [한국투자증권] 재무비율 병렬 수집 (Bulk)
     * - 전 종목을 대상으로 재무비율(연간/분기) 데이터를 수집합니다.
     * - API 특성상 기간 지정 없이 리스트를 받아오므로 날짜 파라미터는 사용하지 않습니다.
     */
    fun collectAllFinancialRatioFromKis(token: String, divClassCode: String = "1"): Int {
        // 1. 수집 대상 종목 선정 (전체 종목)
        log.info(">>> [KIS Financial Collector] 전체 종목 재무비율 수집을 시작합니다.")
        val codes = stockMasterRepository.findAllStockCodes()

        val total = codes.size
        if (total == 0) {
            log.info(">>> [KIS Financial Collector] 수집할 종목이 없습니다.")
            return 0
        }

        val successCount = AtomicInteger(0)
        val failCount = AtomicInteger(0)

        // [세마포어] 일별시세와 공유하거나 별도로 설정 (여기선 공유한다고 가정)
        log.info(">>> [KIS Financial Collector] 총 ${total}개 종목 병렬 수집 시작 (Max Concurrency: ${kisLimitSemaphore.availablePermits})")

        val time = measureTimeMillis {
            runBlocking(Dispatchers.IO) {
                codes.forEach { stockCode ->
                    launch {
                        // 세마포어로 동시 실행 제어 (Rate Limiting)
                        kisLimitSemaphoreForRatio.withPermit {
                            var retryCount = 0
                            val maxRetry = 3
                            var isSuccess = false

                            // [재시도 로직] TPS 초과(EGW00201) 대응
                            while (retryCount < maxRetry && !isSuccess) {
                                try {
                                    // Service 호출
                                    val result = stockFinancialRatioService.syncFinancialRatio(
                                        stockCode = stockCode,
                                        token = token
                                        // 필요하다면 divClassCode(0:분기, 1:연간) 추가 전달
                                    )

                                    if (result > 0) successCount.incrementAndGet()
                                    isSuccess = true

                                    // [수정 2] 초당 4건 유지를 위한 1초 대기
                                    // 4개의 스레드가 각각 1초씩 쉬므로, 전체적으로 1초에 4건이 처리됨
                                    delay(1000)

                                } catch (e: HttpServerErrorException) {
                                    delay(2000) // 2초 대기

                                    // 500 에러 중 "초당 거래건수" 관련 에러인지 확인
                                    val errorBody = e.responseBodyAsString
                                    if (errorBody.contains("msg1\":\"초당") || errorBody.contains("EGW00201")) {
                                        retryCount++
                                        log.warn(">>> [TPS 초과] $stockCode 재무비율 대기 후 재시도 ($retryCount/$maxRetry)")
                                    } else {
                                        log.error("KIS Financial 500 Error [$stockCode]: $errorBody")
                                        break
                                    }
                                } catch (e: Exception) {
                                    delay(2000) // 2초 대기

                                    log.error("KIS Financial Error [$stockCode]: ${e.message}")
                                    break
                                }
                            }

                            if (!isSuccess) {
                                log.error(">>> [FAIL] $stockCode 최종 실패")
                                failCount.incrementAndGet()
                            }
                        }
                    }
                }
            }
        }

        log.info(">>> [KIS Financial 완료] 소요시간: ${time / 1000}초, 대상: $total, 성공: $successCount, 실패: $failCount")
        return successCount.get()
    }

    /**
     * [한국투자증권] 투자자별 매매동향 병렬 수집 (Bulk)
     * - 대상: 해당 날짜에 stock_daily 데이터가 존재하지만, 매매동향(개인/기관/외국인)이 NULL인 종목
     */
    fun collectInvestorTrendAll(date: LocalDate, token: String): Int {
        // 1. 수집 대상 종목 선정 (DB 쿼리)
        val codes = stockDailyRepository.findStockCodesForInvestorTrendUpdate(date)

        val total = codes.size
        if (total == 0) {
            log.info(">>> [KIS Investor Collector] 날짜($date)에 업데이트할 대상 종목이 없습니다.")
            return 0
        }

        log.info(">>> [KIS Investor Collector] 날짜($date) 총 ${total}개 종목 매매동향 업데이트 시작")

        val successCount = AtomicInteger(0)
        val failCount = AtomicInteger(0)

        val time = measureTimeMillis {
            runBlocking(Dispatchers.IO) {
                codes.forEach { stockCode ->
                    launch {
                        kisLimitSemaphore.withPermit {
                            var retryCount = 0
                            val maxRetry = 3
                            var isSuccess = false

                            while (retryCount < maxRetry && !isSuccess) {
                                try {
                                    val result = stockDailyService.fetchAndSaveInvestorTrend(
                                        stockCode = stockCode,
                                        date = date,
                                        token = token
                                    )

                                    if (result) successCount.incrementAndGet()
                                    isSuccess = true

                                    // API 호출 간격 조절
                                    delay(200)

                                } catch (e: HttpServerErrorException) {
                                    // TPS 초과 등 서버 에러 처리
                                    if (e.responseBodyAsString.contains("초당") || e.responseBodyAsString.contains("EGW00201")) {
                                        retryCount++
                                        delay(1000)
                                    } else {
                                        log.error("KIS 500 Error [$stockCode]: ${e.responseBodyAsString}")
                                        break
                                    }
                                } catch (e: Exception) {
                                    log.error("KIS Error [$stockCode]: ${e.message}")
                                    break
                                }
                            }
                            if (!isSuccess) failCount.incrementAndGet()
                        }
                    }
                }
            }
        }

        log.info(">>> [KIS Investor Collector] 완료 - 소요시간: ${time / 1000}초, 대상: $total, 성공: $successCount, 실패: $failCount")
        return successCount.get()
    }
}