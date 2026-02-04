package com.ats.server.domain.stock.controller

import com.ats.server.domain.stock.service.StockDailyCollector
import com.ats.server.domain.stock.service.StockDailyService
import com.ats.server.domain.stock.service.StockFundamentalCollector
import com.ats.server.domain.stock.service.StockFundamentalService
import com.ats.server.domain.stock.service.StockFinancialRatioService
import com.ats.server.domain.stock.service.StockSyncService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.LocalDate

@Tag(name = "99. 데이터 동기화 (Sync)", description = "외부 API 기반 합법적 데이터 수집, 단 키움 API KEY는 IP로 제한 걸려있음")
@RestController
@RequestMapping("/api/v1/sync")
class StockSyncController(
    private val stockSyncService: StockSyncService,
    private val stockDailyService: StockDailyService,
    private val stockDailyCollector: StockDailyCollector,
    private val stockFundamentalCollector: StockFundamentalCollector,
    private val stockFundamentalService: StockFundamentalService,
    private val stockFinancialRatioService: StockFinancialRatioService
) {

    //이 api는 공공데이터 포털에서의 정보를 토대로 stock_master와 fundametal 데이터의 기본값만 가져옴
    //이 부분은 엑셀 업로드로 한번에 올리는 방식이 나을듯 함, 수정 예정
    @Operation(summary = "[공공데이터포털] 종목 마스터 & 시가총액 동기화", description = "전 종목의 목록과 현재 시가총액 정보를 업데이트합니다.")
    @PostMapping("/fetch/public-data/master")
    fun syncMaster(): ResponseEntity<String> {
        val count = stockSyncService.syncMasterByPublicData()
        return ResponseEntity.ok("종목 마스터 및 시가총액 동기화 완료 (처리 건수: $count)")
    }

    //일자를 입력하면 20일전 데이터부터 가져옴.
    @Operation(summary = "[키움] 일자별 주가 수집", description = "키움증권 API를 통해 특정 일자의 시세를 수집하여 DB에 저장합니다.(일자 입력하면 20일전 데이터까지 가져옴)")
    @PostMapping("/fetch/kiwoom/{stockCode}")
    suspend fun fetchDailyFromKiwoom(
        @PathVariable stockCode: String,
        @Parameter(description = "타겟일 (yyyyMMdd)", example = "20260101")
        @RequestParam @DateTimeFormat(pattern = "yyyyMMdd") targetDate: LocalDate
    ): ResponseEntity<String> {
        // 1. 토큰 발급 (캐싱 로직은 Client 내부 혹은 여기서 처리)
        val token = stockDailyService.getApiToken("KIWOOM")
        val count = stockDailyService.fetchAndSaveDailyPrice(stockCode, targetDate, token)
        return ResponseEntity.ok("총 ${count}건의 데이터가 수집/갱신되었습니다.")
    }

    //일자를 입력하면 20일전 데이터부터 가져옴.
    @Operation(summary = "[키움] 일자별 주가 수집(전체 종목)", description = "키움증권 API를 통해 특정 일자의 시세를 수집하여 DB에 저장합니다.(일자 입력하면 20일전 데이터까지 가져옴)")
    @PostMapping("/fetch/kiwoom/daily-all")
    fun fetchDailyAllFromKiwoom(
        @Parameter(description = "타겟일 (yyyyMMdd)", example = "20260101")
        @RequestParam @DateTimeFormat(pattern = "yyyyMMdd") targetDate: LocalDate
    ): ResponseEntity<String> {
        // 1. 토큰 발급 (캐싱 로직은 Client 내부 혹은 여기서 처리)
        val token = stockDailyService.getApiToken("KIWOOM")
        //데이터 수집
        val count = stockDailyCollector.collectAll(targetDate,token)

        return ResponseEntity.ok("총 ${count}건의 데이터가 수집/갱신되었습니다.")
    }

    @Operation(summary = "[KRX] 일자별 주가 수집 (코스피 종목데이터를 가져오지 못함, 미사용처리)")
    @PostMapping("/fetch/krx/daily-kosdaq")
    fun fetchDailyKrxKosdaq(
        @Parameter(description = "일자", example = "2026-01-19")
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        targetDate: LocalDate
    ): ResponseEntity<String> {
        //테스트 할 때 주석 해제
        //val count = stockDailyService.fetchAndSaveKrxKosdaqPrices(targetDate)
        val count = 0
        return ResponseEntity.ok("성공적으로 ${count}건의 코스닥 시세 데이터를 수집했습니다.")
    }

    @Operation(summary = "[키움] 주식 기본정보(재무) 수집(전체종목)", description = "키움증권 API(ka10001)를 통해 PER, EPS 등을 수집하여 갱신합니다.")
    @PostMapping("/fetch/kiwoom/fundamental-all")
    fun fetchFundamentalAll(): ResponseEntity<String> {
        // 1. 토큰 발급 (기존 서비스 활용)
        val token = stockDailyService.getApiToken("KIWOOM") // getApiToken 메서드가 public이어야 함

        // 2. 수집 실행
        val count = stockFundamentalCollector.collectAll(token)

        return ResponseEntity.ok("총 ${count}건의 재무 데이터가 수집/갱신되었습니다.")
    }


    @Operation(summary = "[키움] 주식 기본정보(재무) 수집", description = "키움증권 API(ka10001)를 통해 PER, EPS 등을 수집하여 갱신합니다.")
    @PostMapping("/fetch/kiwoom/fundamental/{stockCode}")
    suspend fun fetchDailyFromKiwoom(
        @PathVariable stockCode: String
    ): ResponseEntity<String> {
        // 1. 토큰 발급 (캐싱 로직은 Client 내부 혹은 여기서 처리)
        val token = stockDailyService.getApiToken("KIWOOM")
        val count = stockFundamentalService.fetchAndSaveFundamental(stockCode, token)

        return ResponseEntity.ok("총 ${count}건의 데이터가 수집/갱신되었습니다.")
    }



    @Operation(summary = "[한투] 일자(기간)별 주가 수집", description = "한국투자증권 API를 통해 시작일~종료일의 시세를 수집하여 DB에 저장합니다.")
    @PostMapping("/fetch/kis/daily-period")
    suspend fun fetchDailyPeriod(
        @Parameter(description = "종목코드", example = "005930")
        @RequestParam stockCode: String,

        @Parameter(description = "시작일 (yyyyMMdd)", example = "20260101")
        @RequestParam @DateTimeFormat(pattern = "yyyyMMdd") startDate: LocalDate,

        @Parameter(description = "종료일 (yyyyMMdd)", example = "20260131")
        @RequestParam @DateTimeFormat(pattern = "yyyyMMdd") endDate: LocalDate
    ): ResponseEntity<String> {

        // 토큰 발급 (기존 Service 활용)
        val token = stockDailyService.getApiToken("KIS") // getApiToken이 public이어야 함

        // 한투 서비스 호출
        val count = stockDailyService.fetchAndSavePeriodDailyPriceFromKis(stockCode, startDate, endDate, token)

        return ResponseEntity.ok("종목($stockCode) 기간($startDate ~ $endDate) : 총 ${count}건 수집 완료")
    }

    @Operation(summary = "[한투] 일자(기간)별 주가 수집(전체 종목)", description = "한국투자증권 API를 통해 시작일~종료일의 시세를 수집하여 DB에 저장합니다.")
    @PostMapping("/fetch/kis/daily-all")
    fun fetchDailyAllFromKis(
        @Parameter(description = "시작일 (yyyyMMdd)", example = "20260101")
        @RequestParam @DateTimeFormat(pattern = "yyyyMMdd") startDate: LocalDate,
        @Parameter(description = "종료일 (yyyyMMdd)", example = "20260101")
        @RequestParam @DateTimeFormat(pattern = "yyyyMMdd") endDate: LocalDate
    ): ResponseEntity<String> {
        // 1. 토큰 발급 (캐싱 로직은 Client 내부 혹은 여기서 처리)
        val token = stockDailyService.getApiToken("KIS")
        //데이터 수집
        val count = stockDailyCollector.collectAllPeriodFromKis(startDate,endDate, token)

        return ResponseEntity.ok("총 ${count}건의 데이터가 수집/갱신되었습니다.")
    }

    @Operation(summary = "[한투] 재무비율 수집 (단건)", description = "특정 종목의 재무비율 정보를 KIS API에서 수집하여 DB에 저장합니다.")
    @PostMapping("/fetch/kis/financial-ratio/{stockCode}")
    fun syncFinancialRatioOne(
        @Parameter(description = "종목코드", example = "005930")
        @PathVariable stockCode: String
    ): ResponseEntity<String> {

        // 1. 토큰 및 키 정보 조회 (Helper 메서드 활용)
        val token = stockDailyService.getApiToken("KIS")

        // 2. 서비스 호출
        val count = stockFinancialRatioService.syncFinancialRatio(stockCode, token)

        return ResponseEntity.ok("종목($stockCode) 재무비율 동기화 완료: ${count}건 저장됨")
    }

    @Operation(summary = "[한투] 재무비율 수집 (전체 종목)", description = "특정 종목의 재무비율 정보를 KIS API에서 수집하여 DB에 저장합니다.")
    @PostMapping("/fetch/kis/financial-ratio/all")
    fun syncFinancialRatioAll(
    ): ResponseEntity<String> {
        // 1. 토큰 발급 (캐싱 로직은 Client 내부 혹은 여기서 처리)
        val token = stockDailyService.getApiToken("KIS")
        //데이터 수집
        val count = stockDailyCollector.collectAllFinancialRatioFromKis(token)

        return ResponseEntity.ok("총 ${count}건의 데이터가 수집/갱신되었습니다.")
    }
}