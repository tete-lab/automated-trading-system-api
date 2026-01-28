package com.ats.server.domain.token.entity

import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.time.LocalDateTime

@Entity
@Table(name = "token")
class Token(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "token_id")
    val id: Long? = null,

    @Column(name = "token", length = 1000, nullable = false) // 토큰 길이가 길 수 있어 넉넉히 잡음
    var token: String,

    @Column(name = "token_type", length = 200, nullable = false)
    var tokenType: String,

    @Column(name = "expired_dt")
    var expiredDt: LocalDateTime,

    @Column(name = "api_name", length = 50)
    val apiName: String? = null,

    @Column(name = "member_id", length = 50)
    val memberId: String? = null // null이면 시스템(배치용) 토큰
) {
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    var createdAt: LocalDateTime? = null

    @UpdateTimestamp
    @Column(name = "updated_at")
    var updatedAt: LocalDateTime? = null
}
