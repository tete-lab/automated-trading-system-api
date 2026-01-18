package com.ats.server.domain.stock.controller

import com.ats.server.domain.stock.dto.StockCreateReq
import com.ats.server.domain.stock.dto.StockRes
import com.ats.server.domain.stock.dto.StockUpdateReq
import com.ats.server.domain.stock.service.StockMasterService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@Tag(name = "04. 종목 (Stock)", description = "주식 종목 마스터 관리")
@RestController
@RequestMapping("/api/v1/stocks")
class StockMasterController(
    private val stockMasterService: StockMasterService
) {

    @Operation(summary = "전체 종목 조회", description = "등록된 모든 주식 종목을 조회합니다.")
    @GetMapping
    fun getAllStocks(): ResponseEntity<List<StockRes>> {
        return ResponseEntity.ok(stockMasterService.getAllStocks())
    }

    @Operation(summary = "시장별 종목 조회", description = "KOSPI 또는 KOSDAQ 종목만 조회합니다.")
    @GetMapping("/market/{market}")
    fun getStocksByMarket(@PathVariable market: String): ResponseEntity<List<StockRes>> {
        return ResponseEntity.ok(stockMasterService.getStocksByMarket(market))
    }

    @Operation(summary = "종목 단건 조회", description = "종목코드(PK)로 정보를 조회합니다.")
    @GetMapping("/{code}")
    fun getStock(@PathVariable code: String): ResponseEntity<StockRes> {
        return ResponseEntity.ok(stockMasterService.getStock(code))
    }

    @Operation(summary = "종목 등록", description = "새로운 주식 종목을 등록합니다.")
    @PostMapping
    fun createStock(@Valid @RequestBody req: StockCreateReq): ResponseEntity<String> {
        val createdCode = stockMasterService.createStock(req)
        return ResponseEntity.status(HttpStatus.CREATED).body(createdCode)
    }

    @Operation(summary = "종목 수정", description = "종목 정보를 수정합니다.")
    @PutMapping("/{code}")
    fun updateStock(
        @PathVariable code: String,
        @RequestBody req: StockUpdateReq
    ): ResponseEntity<String> {
        return ResponseEntity.ok(stockMasterService.updateStock(code, req))
    }

    @Operation(summary = "종목 삭제", description = "특정 종목을 삭제합니다.")
    @DeleteMapping("/{code}")
    fun deleteStock(@PathVariable code: String): ResponseEntity<Void> {
        stockMasterService.deleteStock(code)
        return ResponseEntity.noContent().build()
    }
}