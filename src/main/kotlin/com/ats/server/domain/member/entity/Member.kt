package com.ats.server.domain.member.entity

import com.ats.server.global.entity.BaseEntity
import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "member")
class Member(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "member_id")
    val memberId: Long? = null,

    @Column(nullable = false, unique = true, length = 100)
    var email: String,

    @Column(nullable = false, length = 200)
    var password: String, // 실무에선 반드시 BCrypt 등으로 암호화해야 합니다.

    @Column(nullable = false, length = 50)
    var name: String,

    @Column(length = 20)
    var role: String = "USER",

    @Column(name = "login_fail_count")
    var loginFailCount: Int = 0,

    @Column(name = "last_login_at")
    var lastLoginAt: LocalDateTime? = null

) : BaseEntity()