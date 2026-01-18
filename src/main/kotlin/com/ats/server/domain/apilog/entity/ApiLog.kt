package com.ats.server.domain.apilog.entity

import com.ats.server.global.entity.BaseEntity
import jakarta.persistence.*

@Entity
@Table(name = "api_log")
class ApiLog(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "log_id")
    val logId: Long? = null,

    @Column(name = "api_name", length = 50)
    var apiName: String?, // 예: "StockOrder", "GetPrice"

    @Column(name = "url", length = 200)
    var url: String?,

    @Column(name = "method", length = 10)
    var method: String?, // GET, POST

    @Column(name = "req_params", columnDefinition = "TEXT")
    var reqParams: String?,

    // 매우 긴 JSON 응답이 올 수 있으므로 LONGTEXT 사용
    @Column(name = "res_body", columnDefinition = "LONGTEXT")
    var resBody: String?,

    @Column(name = "status_code")
    var statusCode: Int?

) : BaseEntity() {

    // 로그는 보통 수정하지 않지만, 형식상 Update 메서드 제공
    fun update(
        apiName: String?, url: String?, method: String?,
        reqParams: String?, resBody: String?, statusCode: Int?
    ) {
        this.apiName = apiName
        this.url = url
        this.method = method
        this.reqParams = reqParams
        this.resBody = resBody
        this.statusCode = statusCode
    }
}