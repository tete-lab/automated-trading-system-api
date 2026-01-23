package com.ats.server.domain.stock.controller

import com.ats.server.domain.stock.service.StockDailyCollector
import com.ats.server.domain.stock.service.StockDailyService
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
    private val stockSyncService: StockSyncService,
    private val stockDailyService: StockDailyService,
    private val stockDailyCollector: StockDailyCollector
) {

    //이 api는 공공데이터 포털에서의 정보를 토대로 stock_master와 fundametal 데이터의 기본값만 가져옴
    @Operation(summary = "1. 종목 마스터 & 시가총액 동기화", description = "전 종목의 목록과 현재 시가총액 정보를 업데이트합니다.")
    @PostMapping("/master")
    fun syncMaster(): ResponseEntity<String> {
        val count = stockSyncService.syncMasterByPublicData()
        return ResponseEntity.ok("종목 마스터 및 시가총액 동기화 완료 (처리 건수: $count)")
    }

    //일자를 입력하면 30일전 데이터부터 가져옴.
    @Operation(summary = "2. 외부(키움) 일자별 주가 수집", description = "키움증권 API를 통해 특정 일자의 시세를 수집하여 DB에 저장합니다.(일자 입력하면 30일전 데이터까지 가져옴)")
    @PostMapping("/fetch/{stockCode}")
    fun fetchDailyFromKiwoom(
        @PathVariable stockCode: String,
        @Parameter(description = "타겟일 (yyyyMMdd)", example = "20260101")
        @RequestParam @DateTimeFormat(pattern = "yyyyMMdd") targetDate: LocalDate
    ): ResponseEntity<String> {
        val count = stockDailyService.fetchAndSaveDailyPrice(stockCode, targetDate)
        return ResponseEntity.ok("총 ${count}건의 데이터가 수집/갱신되었습니다.")
    }

    //일자를 입력하면 30일전 데이터부터 가져옴.
    @Operation(summary = "3. 외부(키움) 일자별 주가 수집(전체 종목)", description = "키움증권 API를 통해 특정 일자의 시세를 수집하여 DB에 저장합니다.(일자 입력하면 30일전 데이터까지 가져옴)")
    @PostMapping("/fetch-all")
    fun fetchDailyAllFromKiwoom(
        @Parameter(description = "타겟일 (yyyyMMdd)", example = "20260101")
        @RequestParam @DateTimeFormat(pattern = "yyyyMMdd") targetDate: LocalDate
    ): ResponseEntity<String> {
        val count = stockDailyCollector.collectAll(targetDate)
        return ResponseEntity.ok("총 ${count}건의 데이터가 수집/갱신되었습니다.")
    }

    @PostMapping("/fetch/krx-kosdaq")
    fun fetchDailyKrxKosdaq(
        @Parameter(description = "4. 외부(KRX) 일자별 주가 수집", example = "2026-01-19")
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        targetDate: LocalDate
    ): ResponseEntity<String> {
        val count = stockDailyService.fetchAndSaveKrxKosdaqPrices(targetDate)
        return ResponseEntity.ok("성공적으로 ${count}건의 코스닥 시세 데이터를 수집했습니다.")
    }
}