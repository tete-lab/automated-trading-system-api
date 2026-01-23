package com.ats.server.domain.stock.controller

import com.ats.server.domain.stock.dto.StockDailyCreateReq
import com.ats.server.domain.stock.dto.StockDailyRes
import com.ats.server.domain.stock.dto.StockDailyUpdateReq
import com.ats.server.domain.stock.service.StockDailyService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
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


}