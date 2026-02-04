package com.ats.server.global.notification

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate

@Service
class TelegramService(
    @Value("\${telegram.bot.token}") private val token: String,
    @Value("\${telegram.bot.chat-id}") private val chatId: String
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val restTemplate = RestTemplate()

    // 비동기로 보내거나, 단순 호출 (여기선 단순 호출로 구현)
    fun sendMessage(text: String) {
        try {
            val url = "https://api.telegram.org/bot$token/sendMessage"

            // 전송할 데이터 (JSON 형식 아님, 단순 Map으로 요청)
            val params = mapOf(
                "chat_id" to chatId,
                "text" to text
            )

            // 텔레그램 API 호출
            restTemplate.postForObject(url, params, String::class.java)

        } catch (e: Exception) {
            // 텔레그램 전송 실패가 메인 로직을 방해하면 안 되므로 로그만 남김
            log.error("텔레그램 전송 실패: ${e.message}")
        }
    }
}