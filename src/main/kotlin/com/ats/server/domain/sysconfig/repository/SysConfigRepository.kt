package com.ats.server.domain.sysconfig.repository

import com.ats.server.domain.sysconfig.entity.SysConfig
import org.springframework.data.jpa.repository.JpaRepository

// PK 타입이 String 이므로 JpaRepository<SysConfig, String>
interface SysConfigRepository : JpaRepository<SysConfig, String>