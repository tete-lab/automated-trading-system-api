package com.ats.server.domain.apilog.controller

import com.ats.server.domain.apilog.dto.ApiLogCreateReq
import com.ats.server.domain.apilog.dto.ApiLogRes
import com.ats.server.domain.apilog.service.ApiLogService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@Tag(name = "08. API 로그 (ApiLog)", description = "외부 시스템 통신 이력 관리")
@RestController
@RequestMapping("/api/v1/api-logs")
class ApiLogController(
    private val apiLogService: ApiLogService
) {

    @Operation(summary = "전체 로그 조회", description = "저장된 API 호출 로그를 최신순으로 조회합니다.")
    @GetMapping
    fun getAllLogs(): ResponseEntity<List<ApiLogRes>> {
        return ResponseEntity.ok(apiLogService.getAllLogs())
    }

    @Operation(summary = "API 이름별 조회", description = "특정 API 이름(예: 주식주문)으로 로그를 검색합니다.")
    @GetMapping("/search")
    fun searchLogs(@RequestParam apiName: String): ResponseEntity<List<ApiLogRes>> {
        return ResponseEntity.ok(apiLogService.getLogsByApiName(apiName))
    }

    @Operation(summary = "로그 상세 조회", description = "로그 ID로 상세 내용을 확인합니다.")
    @GetMapping("/{logId}")
    fun getLog(@PathVariable logId: Long): ResponseEntity<ApiLogRes> {
        return ResponseEntity.ok(apiLogService.getLog(logId))
    }

    @Operation(summary = "로그 수동 저장", description = "테스트 목적으로 로그를 직접 저장합니다.")
    @PostMapping
    fun createLog(@RequestBody req: ApiLogCreateReq): ResponseEntity<Long> {
        val createdId = apiLogService.createLog(req)
        return ResponseEntity.status(HttpStatus.CREATED).body(createdId)
    }

    @Operation(summary = "로그 삭제", description = "특정 로그를 삭제합니다.")
    @DeleteMapping("/{logId}")
    fun deleteLog(@PathVariable logId: Long): ResponseEntity<Void> {
        apiLogService.deleteLog(logId)
        return ResponseEntity.noContent().build()
    }
}