package com.ats.server.domain.stock.controller

import com.ats.server.domain.stock.dto.StockFundamentalCreateReq
import com.ats.server.domain.stock.dto.StockFundamentalRes
import com.ats.server.domain.stock.dto.StockFundamentalUpdateReq
import com.ats.server.domain.stock.service.StockFundamentalService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@Tag(name = "05. 종목 분석 (Fundamental)", description = "PER, PBR, 배당 등 재무 지표 관리")
@RestController
@RequestMapping("/api/v1/stock-fundamentals")
class StockFundamentalController(
    private val fundamentalService: StockFundamentalService
) {

    @Operation(summary = "분석 정보 조회", description = "특정 종목의 재무 지표를 조회합니다.")
    @GetMapping("/{stockCode}")
    fun getFundamental(@PathVariable stockCode: String): ResponseEntity<StockFundamentalRes> {
        return ResponseEntity.ok(fundamentalService.getFundamental(stockCode))
    }

    @Operation(summary = "분석 정보 등록", description = "종목의 재무 지표를 신규 등록합니다.")
    @PostMapping
    fun createFundamental(@Valid @RequestBody req: StockFundamentalCreateReq): ResponseEntity<String> {
        val createdCode = fundamentalService.createFundamental(req)
        return ResponseEntity.status(HttpStatus.CREATED).body(createdCode)
    }

    @Operation(summary = "분석 정보 수정", description = "기존 재무 지표를 업데이트합니다.")
    @PutMapping("/{stockCode}")
    fun updateFundamental(
        @PathVariable stockCode: String,
        @RequestBody req: StockFundamentalUpdateReq
    ): ResponseEntity<String> {
        return ResponseEntity.ok(fundamentalService.updateFundamental(stockCode, req))
    }
}