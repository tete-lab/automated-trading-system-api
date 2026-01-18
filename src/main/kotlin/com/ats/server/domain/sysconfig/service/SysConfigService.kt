package com.ats.server.domain.sysconfig.service

import com.ats.server.domain.sysconfig.dto.SysConfigCreateReq
import com.ats.server.domain.sysconfig.dto.SysConfigRes
import com.ats.server.domain.sysconfig.dto.SysConfigUpdateReq
import com.ats.server.domain.sysconfig.entity.SysConfig
import com.ats.server.domain.sysconfig.repository.SysConfigRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true) // 기본적으로 읽기 전용 (성능 최적화)
class SysConfigService(
    private val sysConfigRepository: SysConfigRepository
) {

    // 전체 조회
    fun getAllConfigs(): List<SysConfigRes> {
        return sysConfigRepository.findAll().map {
            SysConfigRes(it.configCode, it.configValue, it.descTxt, it.updatedAt.toString())
        }
    }

    // 단건 조회
    fun getConfig(code: String): SysConfigRes {
        val entity = sysConfigRepository.findById(code)
            .orElseThrow { IllegalArgumentException("해당 설정 코드가 존재하지 않습니다: $code") }

        return SysConfigRes(entity.configCode, entity.configValue, entity.descTxt, entity.updatedAt.toString())
    }


    // 생성
    @Transactional // 쓰기 작업 허용
    fun createConfig(req: SysConfigCreateReq): String {
        if (sysConfigRepository.existsById(req.configCode)) {
            throw IllegalStateException("이미 존재하는 설정 코드입니다: ${req.configCode}")
        }

        val sysConfig = SysConfig(
            configCode = req.configCode,
            configValue = req.configValue,
            descTxt = req.descTxt
        )
        sysConfigRepository.save(sysConfig)
        return sysConfig.configCode
    }

    // 수정
    @Transactional
    fun updateConfig(code: String, req: SysConfigUpdateReq): String {
        val entity = sysConfigRepository.findById(code)
            .orElseThrow { IllegalArgumentException("해당 설정 코드가 존재하지 않습니다: $code") }

        // Dirty Checking (JPA 변경 감지) 이용
        entity.update(req.configValue, req.descTxt)

        return entity.configCode
    }

    // 삭제
    @Transactional
    fun deleteConfig(code: String) {
        if (!sysConfigRepository.existsById(code)) {
            throw IllegalArgumentException("삭제할 설정이 존재하지 않습니다.")
        }
        sysConfigRepository.deleteById(code)
    }


}