package com.ats.server.domain.sysconfig.controller

import com.ats.server.domain.sysconfig.dto.SysConfigCreateReq
import com.ats.server.domain.sysconfig.dto.SysConfigRes
import com.ats.server.domain.sysconfig.dto.SysConfigUpdateReq
import com.ats.server.domain.sysconfig.service.SysConfigService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@Tag(name = "01. 시스템 설정 (SysConfig)", description = "시스템 전역 변수 관리 API")
@RestController
@RequestMapping("/api/v1/sys-config")
class SysConfigController(
    private val sysConfigService: SysConfigService
) {

    @Operation(summary = "전체 설정 조회", description = "시스템의 모든 설정을 리스트로 반환합니다.")
    @GetMapping
    fun getAllConfigs(): ResponseEntity<List<SysConfigRes>> {
        return ResponseEntity.ok(sysConfigService.getAllConfigs())
    }

    @Operation(summary = "단일 설정 조회", description = "설정 코드(PK)로 특정 설정을 조회합니다.")
    @GetMapping("/{code}")
    fun getConfig(@PathVariable code: String): ResponseEntity<SysConfigRes> {
        return ResponseEntity.ok(sysConfigService.getConfig(code))
    }

    @Operation(summary = "설정 등록", description = "새로운 시스템 설정을 등록합니다.")
    @PostMapping
    fun createConfig(@Valid @RequestBody req: SysConfigCreateReq): ResponseEntity<String> {
        val createdCode = sysConfigService.createConfig(req)
        return ResponseEntity.status(HttpStatus.CREATED).body(createdCode)
    }

    @Operation(summary = "설정 수정", description = "기존 설정의 값이나 설명을 수정합니다.")
    @PutMapping("/{code}")
    fun updateConfig(
        @PathVariable code: String,
        @RequestBody req: SysConfigUpdateReq
    ): ResponseEntity<String> {
        val updatedCode = sysConfigService.updateConfig(code, req)
        return ResponseEntity.ok(updatedCode)
    }

    @Operation(summary = "설정 삭제", description = "특정 설정을 삭제합니다.")
    @DeleteMapping("/{code}")
    fun deleteConfig(@PathVariable code: String): ResponseEntity<Void> {
        sysConfigService.deleteConfig(code)
        return ResponseEntity.noContent().build()
    }
}