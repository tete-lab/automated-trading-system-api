package com.ats.server.domain.stock.controller

import com.ats.server.domain.stock.dto.StockDailyCreateReq
import com.ats.server.domain.stock.dto.StockDailyRes
import com.ats.server.domain.stock.dto.StockDailyUpdateReq
import com.ats.server.domain.stock.service.StockDailyService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import kotlinx.coroutines.*
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.LocalDate

@Tag(name = "06. 일자별 시세 (StockDaily)", description = "일봉 데이터 및 보조지표 관리")
@RestController
@RequestMapping("/api/v1/stock-daily")
class StockDailyController(
    private val stockDailyService: StockDailyService
) {

    @Operation(summary = "기간별 시세 조회", description = "특정 종목의 기간별 일봉 데이터를 조회합니다.")
    @GetMapping("/{stockCode}")
    fun getDailyList(
        @PathVariable stockCode: String,
        @Parameter(description = "시작일 (yyyy-MM-dd)", example = "2024-01-01")
        @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") startDate: LocalDate,
        @Parameter(description = "종료일 (yyyy-MM-dd)", example = "2024-01-31")
        @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") endDate: LocalDate
    ): ResponseEntity<List<StockDailyRes>> {
        return ResponseEntity.ok(stockDailyService.getDailyList(stockCode, startDate, endDate))
    }

    @Operation(summary = "일별 시세 등록", description = "하루치 시세 데이터를 등록합니다.")
    @PostMapping
    fun createDaily(@Valid @RequestBody req: StockDailyCreateReq): ResponseEntity<Long> {
        val createdId = stockDailyService.createDaily(req)
        return ResponseEntity.status(HttpStatus.CREATED).body(createdId)
    }

    @Operation(summary = "일별 시세 수정", description = "종목코드와 날짜를 기준으로 데이터를 수정합니다.")
    @PutMapping("/{stockCode}/{baseDate}")
    fun updateDaily(
        @PathVariable stockCode: String,
        @PathVariable @DateTimeFormat(pattern = "yyyy-MM-dd") baseDate: LocalDate,
        @RequestBody req: StockDailyUpdateReq
    ): ResponseEntity<Long> {
        return ResponseEntity.ok(stockDailyService.updateDaily(stockCode, baseDate, req))
    }

    @Operation(summary = "3. 지수 계산 요청 (기간 설정)", description = "시작일부터 종료일까지의 지수를 백그라운드에서 순차적으로 계산합니다.")
    @PostMapping("/calculate-indicators")
    fun calculateIndicators(
        @Parameter(description = "시작일 (yyyyMMdd)", example = "20260101")
        @RequestParam @DateTimeFormat(pattern = "yyyyMMdd") startDate: LocalDate,

        @Parameter(description = "종료일 (yyyyMMdd)", example = "20260131")
        @RequestParam @DateTimeFormat(pattern = "yyyyMMdd") endDate: LocalDate
    ): ResponseEntity<String> {

        // 1. 날짜 유효성 검증
        if (startDate.isAfter(endDate)) {
            return ResponseEntity.badRequest().body("시작일이 종료일보다 늦을 수 없습니다.")
        }

        // 2. 별도의 스코프에서 백그라운드 실행
        CoroutineScope(Dispatchers.IO).launch {
            try {
                println(">>> 백그라운드 기간 지표 계산 시작 ($startDate ~ $endDate) <<<")

                // 서비스의 기간 처리 함수 호출
                stockDailyService.calculateIndicatorsForPeriod(startDate, endDate)

                println(">>> 백그라운드 기간 지표 계산 전체 완료 ($startDate ~ $endDate) <<<")
            } catch (e: Exception) {
                println(">>> 지표 계산 중 에러 발생: ${e.message}")
                e.printStackTrace()
            }
        }

        // 3. 즉시 응답 반환
        return ResponseEntity.ok("기간 지표 계산 요청이 백그라운드에서 시작되었습니다. ($startDate ~ $endDate)")
    }


}