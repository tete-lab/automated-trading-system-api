package com.ats.server.domain.sysconfig.entity

import com.ats.server.global.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "sys_config") // DB 테이블명 매핑 [cite: 1]
class SysConfig(
    @Id
    @Column(name = "config_code", length = 50)
    val configCode: String, // PK가 String임에 주의

    @Column(name = "config_value", columnDefinition = "TEXT")
    var configValue: String?,

    @Column(name = "desc_txt", length = 200)
    var descTxt: String?
) : BaseEntity() {

    // 수정 편의 메서드 (비즈니스 로직)
    fun update(value: String?, description: String?) {
        this.configValue = value
        this.descTxt = description
        this.updatedBy = "ADMIN" // 예시: 실제론 로그인 유저명
    }
}