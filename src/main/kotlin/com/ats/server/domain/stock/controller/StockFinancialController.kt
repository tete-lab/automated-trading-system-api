package com.ats.server.interfaces.api

import com.ats.server.domain.stock.dto.StockFinancialRatioDto
import com.ats.server.domain.stock.service.StockFinancialRatioService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@Tag(name = "04. Stock Financial Ratio", description = "주식 재무 비율 API")
@RestController
@RequestMapping("/api/stock/financial-ratio")
class StockFinancialRatioController(
    private val service: StockFinancialRatioService
) {

    @Operation(summary = "재무 비율 조회", description = "특정 종목의 재무 비율 이력을 조회합니다.")
    @GetMapping("/{stockCode}")
    fun getFinancialRatios(@PathVariable stockCode: String): ResponseEntity<List<StockFinancialRatioDto>> {
        val list = service.getFinancialRatios(stockCode)
        return ResponseEntity.ok(list)
    }

    @Operation(summary = "재무 비율 단건 저장", description = "재무 비율 정보를 저장하거나 수정합니다.")
    @PostMapping
    fun saveFinancialRatio(@RequestBody dto: StockFinancialRatioDto): ResponseEntity<String> {
        service.saveRatio(dto)
        return ResponseEntity.ok("저장 완료")
    }
}