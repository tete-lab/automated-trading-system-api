package com.ats.server.global.scheduler

import com.ats.server.domain.stock.service.StockDailyCollector
import com.ats.server.domain.stock.service.StockDailyService
import com.ats.server.domain.stock.service.StockEmailService
import com.ats.server.global.notification.TelegramService
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.slf4j.LoggerFactory
import java.time.LocalDate

@Component
class StockDataScheduler(
    private val stockDailyCollector: StockDailyCollector,
    private val stockDailyService: StockDailyService, // 토큰 발급용 서비스
    private val telegramService: TelegramService, // [추가] 서비스 주입
    private val stockEmailService: StockEmailService
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 매일 평일(월-금) 오후 4시 30분에 주가 수집 및 지표 계산 실행
     */
    @Scheduled(cron = "0 30 16 * * MON-FRI") // 1차: 16:30
    @Scheduled(cron = "0 0 17 * * MON-FRI")  // 2차: 17:00
    @Scheduled(cron = "0 20 17 * * MON-FRI")  // 3차: 17:30
    suspend fun runDailyStockCollection() {
        val startMsg = ">>> [Scheduler] 일일 주가 및 재무비율 수집 시작"
        log.info(startMsg)
        telegramService.sendMessage(startMsg)
        try{
            // 1. 최신 실전 토큰 가져오기 (또는 발급)
            val token = stockDailyService.getApiToken("KIS")

            // 2. 일자별 주가 수집 (오늘 날짜)
            val today = LocalDate.now()
            val count = stockDailyCollector.collectAllPeriodFromKis(today, today, token)

            val summaryMsg = """
                ✅ 수집 완료
                - 일자: $today
                - 주가 수집: ${count}건                
            """.trimIndent()

            log.info("총 ${count}건의 데이터가 수집/갱신되었습니다.")

            // [텔레그램] 결과 요약 전송
            telegramService.sendMessage(summaryMsg)

        } catch (e: Exception) {
            log.error(">>> [Scheduler] 수집 중 치명적 오류 발생: ${e.message}")
            val errorMsg = "🚨 [Scheduler] 수집 중 치명적 오류 발생: ${e.message}"
            log.error(errorMsg)
            // [텔레그램] 에러 발생 시 즉시 알림
            telegramService.sendMessage(errorMsg)
        } finally {
            val finalMsg = ">>> [Scheduler] 수집 프로세스 종료"
            log.info(finalMsg)
            telegramService.sendMessage(finalMsg)
        }
    }

    /**
     * 매일 평일(월-금) 오후 5시 30분에 stock_daily의 값으로 지표 계산
     */
    @Scheduled(cron = "0 30 17 * * MON-FRI")
    suspend fun runDailyStockCalculateCollection() {
        val startMsg = ">>> [Scheduler] 일일 주가 지표(MACD 등) 계산 시작"
        log.info(startMsg)
        telegramService.sendMessage(startMsg)
        try{
            val today = LocalDate.now()

            telegramService.sendMessage("⏳ 지표(MACD 등) 계산 시작...")
            stockDailyService.calculateIndicatorsForPeriod(today, today)

        } catch (e: Exception) {
            log.error(">>> [Scheduler] 수집 중 치명적 오류 발생: ${e.message}")
            val errorMsg = "🚨 [Scheduler] 수집 중 치명적 오류 발생: ${e.message}"
            log.error(errorMsg)
            // [텔레그램] 에러 발생 시 즉시 알림
            telegramService.sendMessage(errorMsg)
        } finally {
            val finalMsg = "✅ 지표 계산 완료"
            log.info(finalMsg)
            telegramService.sendMessage(finalMsg)
        }
    }
    /**
     * 2. 추천 종목 이메일 발송 (별도 분리)
     * 시간: 매일 평일 18:00
     */
    @Scheduled(cron = "0 0 18 * * MON-FRI")
    fun sendDailyRecommendationEmail() {
        log.info(">>> [Scheduler] 이메일 리포트 발송 시작")
        telegramService.sendMessage("📧 [Scheduler] 추천 종목 이메일 발송을 시작합니다.")

        try {
            val today = LocalDate.now()

            // 이메일 발송 로직 호출 (받는 사람 이메일 지정)
            stockEmailService.sendDailyRecommendationEmail(today, "ktgstar@gmail.com")

            log.info(">>> [Scheduler] 이메일 발송 완료")
            telegramService.sendMessage("✅ [Scheduler] 이메일 발송이 완료되었습니다.")

        } catch (e: Exception) {
            val errorMsg = "🚨 [Scheduler] 이메일 발송 실패: ${e.message}"
            log.error(errorMsg)
            telegramService.sendMessage(errorMsg)
        }
    }
}