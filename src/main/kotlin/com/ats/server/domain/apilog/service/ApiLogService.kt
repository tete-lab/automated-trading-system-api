package com.ats.server.domain.apilog.service

import com.ats.server.domain.apilog.dto.ApiLogCreateReq
import com.ats.server.domain.apilog.dto.ApiLogRes
import com.ats.server.domain.apilog.entity.ApiLog
import com.ats.server.domain.apilog.repository.ApiLogRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class ApiLogService(
    private val apiLogRepository: ApiLogRepository
) {

    // 전체 로그 조회 (최신순)
    fun getAllLogs(): List<ApiLogRes> {
        return apiLogRepository.findAllByOrderByLogIdDesc().map { toRes(it) }
    }

    // 특정 API 이름으로 로그 검색
    fun getLogsByApiName(apiName: String): List<ApiLogRes> {
        return apiLogRepository.findAllByApiNameOrderByLogIdDesc(apiName).map { toRes(it) }
    }

    // 로그 단건 조회
    fun getLog(logId: Long): ApiLogRes {
        val entity = apiLogRepository.findById(logId)
            .orElseThrow { IllegalArgumentException("해당 로그가 존재하지 않습니다: $logId") }
        return toRes(entity)
    }

    // 로그 저장 (보통 시스템 내부에서 호출됨)
    @Transactional
    fun createLog(req: ApiLogCreateReq): Long {
        val apiLog = ApiLog(
            apiName = req.apiName,
            url = req.url,
            method = req.method,
            reqParams = req.reqParams,
            resBody = req.resBody,
            statusCode = req.statusCode
        )
        return apiLogRepository.save(apiLog).logId!!
    }

    // 로그 삭제 (오래된 로그 정리 등)
    @Transactional
    fun deleteLog(logId: Long) {
        if (!apiLogRepository.existsById(logId)) {
            throw IllegalArgumentException("삭제할 로그가 없습니다.")
        }
        apiLogRepository.deleteById(logId)
    }

    private fun toRes(entity: ApiLog) = ApiLogRes(
        logId = entity.logId!!,
        apiName = entity.apiName,
        url = entity.url,
        method = entity.method,
        reqParams = entity.reqParams,
        resBody = entity.resBody,
        statusCode = entity.statusCode,
        createdAt = entity.createdAt.toString()
    )
}