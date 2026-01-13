package com.ats.server.domain.account.entity

import com.ats.server.domain.member.entity.Member
import com.ats.server.global.entity.BaseEntity
import jakarta.persistence.*

@Entity
@Table(name = "member_account")
class MemberAccount(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "member_account_id")
    val memberAccountId: Long? = null,

    // N:1 관계 설정 (Lazy Loading 권장)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    val member: Member,

    @Column(name = "account_num", nullable = false, length = 20)
    var accountNum: String,

    @Column(name = "account_name", length = 50)
    var accountName: String?,

    @Column(name = "buy_rsi")
    var buyRsi: Int = 30,

    @Column(name = "sell_rsi")
    var sellRsi: Int = 70,

    @Column(name = "api_key", length = 200)
    var apiKey: String? = null,

    @Column(name = "secret_key", length = 200)
    var secretKey: String? = null,

    @Column(name = "is_virtual", length = 1)
    var isVirtual: String = "N", // Y/N

    @Column(name = "is_active", length = 1)
    var isActive: String = "Y"   // Y/N

) : BaseEntity()