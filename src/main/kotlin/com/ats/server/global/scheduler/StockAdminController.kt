package com.ats.server.global.scheduler

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import kotlin.coroutines.cancellation.CancellationException

@Tag(name = "999. 관리자 수동 매치", description = "관리자 수동 배치")
@RestController
@RequestMapping("/api/admin/sync")
class StockAdminController(
    private val stockDataScheduler: StockDataScheduler // 스케줄러 주입
) {

    // 백그라운드 작업을 위한 별도의 코루틴 스코프 생성
    // (SupervisorJob을 써야 에러가 나도 애플리케이션이 죽지 않음)
    private val backgroundScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    @Operation(summary = "수동 데이터 수집 실행", description = "스케줄러 로직을 백그라운드에서 즉시 실행합니다.")
    @PostMapping("/run-manual")
    fun runManualSync(): ResponseEntity<String> {
        // [핵심] suspend 함수가 아니므로 기다리지 않음
        // 별도의 스코프에서 'launch'로 실행하여 메인 요청 흐름과 분리
        backgroundScope.launch {
            try {
                stockDataScheduler.runDailyStockCollection()
            } catch (e: CancellationException) {
                // 혹시라도 취소되면 로그 남기기
                println(">>> 수동 작업이 취소되었습니다.")
            } catch (e: Exception) {
                println(">>> 수동 작업 중 에러 발생: ${e.message}")
            }
        }

        // 작업이 끝날 때까지 기다리지 않고 "바로" 응답을 줌
        return ResponseEntity.ok("🚀 수동 수집 프로세스가 백그라운드에서 시작되었습니다. (완료 여부는 텔레그램/로그 확인)")
    }

    @Operation(summary = "수동 데이터 계산 실행", description = "스케줄러 로직을 백그라운드에서 즉시 실행합니다.")
    @PostMapping("/run-manual-calculation")
    fun runManualCalculation(): ResponseEntity<String> {
        // [핵심] suspend 함수가 아니므로 기다리지 않음
        // 별도의 스코프에서 'launch'로 실행하여 메인 요청 흐름과 분리
        backgroundScope.launch {
            try {
                stockDataScheduler.runDailyStockCalculateCollection()
            } catch (e: CancellationException) {
                // 혹시라도 취소되면 로그 남기기
                println(">>> 수동 작업이 취소되었습니다.")
            } catch (e: Exception) {
                println(">>> 수동 작업 중 에러 발생: ${e.message}")
            }
        }

        // 작업이 끝날 때까지 기다리지 않고 "바로" 응답을 줌
        return ResponseEntity.ok("🚀 수동 수집 프로세스가 백그라운드에서 시작되었습니다. (완료 여부는 텔레그램/로그 확인)")
    }

    @Operation(summary = "지표로 종목 추천 이메일 발송", description = "지표로 종목 추천 이메일 발송합니다.")
    @PostMapping("/mail-manual")
    fun mailManualSync(): ResponseEntity<String?>? {
        // [핵심] suspend 함수가 아니므로 기다리지 않음
        // 별도의 스코프에서 'launch'로 실행하여 메인 요청 흐름과 분리

            try {
                stockDataScheduler.sendDailyRecommendationEmail()
            } catch (e: CancellationException) {
                // 혹시라도 취소되면 로그 남기기
                println(">>> 수동 메일 발송 실패.")
            } catch (e: Exception) {
                println(">>> 수동 매일 중 에러 발생: ${e.message}")
            }


        // 작업이 끝날 때까지 기다리지 않고 "바로" 응답을 줌
        return ResponseEntity.ok("수동 으로 메일을 보냈습니다.")
    }
}