package com.ats.server.domain.stock.controller

import com.ats.server.domain.stock.service.StockSyncService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.LocalDate

@Tag(name = "99. 데이터 동기화 (Public Data)", description = "공공데이터포털 기반 합법적 데이터 수집")
@RestController
@RequestMapping("/api/v1/sync")
class StockSyncController(
    private val stockSyncService: StockSyncService
) {

    //이 api는 공공데이터 포털에서의 정보를 토대로 stock_master와 fundametal 데이터의 기본값만 가져옴
    @Operation(summary = "1. 종목 마스터 & 시가총액 동기화", description = "전 종목의 목록과 현재 시가총액 정보를 업데이트합니다.")
    @PostMapping("/master")
    fun syncMaster(): ResponseEntity<String> {
        val count = stockSyncService.syncMasterByPublicData()
        return ResponseEntity.ok("종목 마스터 및 시가총액 동기화 완료 (처리 건수: $count)")
    }

    @Operation(summary = "2. 일자별 시세 동기화", description = "특정 종목의 기간별 시세(OHLCV)를 가져옵니다.")
    @PostMapping("/daily/{stockCode}")
    fun syncDaily(
        @Parameter(description = "종목코드 (예: 005930)", example = "005930")
        @PathVariable stockCode: String,

        @Parameter(description = "시작일", example = "2024-01-01")
        @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") startDate: LocalDate,

        @Parameter(description = "종료일", example = "2024-01-31")
        @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") endDate: LocalDate
    ): ResponseEntity<String> {
        stockSyncService.syncDailyByPublicData(stockCode, startDate, endDate)
        return ResponseEntity.ok("[$stockCode] 시세 데이터 동기화 완료")
    }
}