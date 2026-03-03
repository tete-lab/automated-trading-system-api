package com.ats.server.domain.stock.service

import com.ats.server.domain.stock.entity.StockDaily
import com.ats.server.domain.stock.entity.StockMaster // [필요 시 Import 추가]
import com.ats.server.domain.stock.repository.StockDailyRepository
import com.ats.server.domain.stock.repository.StockMasterRepository
import com.ats.server.global.notification.TelegramService
import jakarta.mail.internet.MimeMessage
import org.slf4j.LoggerFactory
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.MimeMessageHelper
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Service
class StockEmailService(
    private val stockDailyRepository: StockDailyRepository,
    private val stockMasterRepository: StockMasterRepository,
    private val javaMailSender: JavaMailSender,
    private val telegramService: TelegramService
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional(readOnly = true)
    fun sendDailyRecommendationEmail(targetDate: LocalDate, toEmails: List<String>) {
        // 1. 데이터 조회
        val buyList = stockDailyRepository.findTop100ByBaseDateAndCrossTypeOrderByRsiAsc(targetDate, 1)
        val sellList = stockDailyRepository.findTop100ByBaseDateAndCrossTypeOrderByRsiDesc(targetDate, -1)

        if (buyList.isEmpty() && sellList.isEmpty()) {
            val teleMsg = "🚨 [Scheduler] 추천 종목이 없어 이메일을 발송하지 않습니다."
            telegramService.sendMessage(teleMsg)
            log.info(teleMsg)
            return
        }

        // [수정] 2. 종목 정보 매핑을 위한 Map 생성 (코드 -> StockMaster 객체)
        // 기존에는 이름만 가져왔으나, 시장(market) 정보도 필요하므로 객체 자체를 맵핑합니다.
        val stockMap = stockMasterRepository.findAll()
            .associateBy { it.stockCode }

        // 3. 이메일 제목 및 본문 구성 (stockMap 전달)
        val subject = "[$targetDate] 매수/매도 추천 종목 리포트 (Golden/Dead Cross)"
        val content = buildEmailContent(targetDate, buyList, sellList, stockMap)

        // 4. 이메일 발송
        toEmails.forEach { email ->
            try {
                val message: MimeMessage = javaMailSender.createMimeMessage()
                val helper = MimeMessageHelper(message, true, "UTF-8")

                helper.setTo(email) // 한 명씩 설정
                helper.setSubject(subject)
                helper.setText(content, true)

                javaMailSender.send(message)
                log.info("추천 종목 이메일 발송 성공 (To: $email)")
            } catch (e: Exception) {
                log.error("이메일 발송 실패 (To: $email): ${e.message}")
            }
        }
    }

    // HTML 본문 생성 헬퍼
    private fun buildEmailContent(
        date: LocalDate,
        buyList: List<StockDaily>,
        sellList: List<StockDaily>,
        stockMap: Map<String, StockMaster> // [수정] 파라미터 타입 변경 (String -> StockMaster)
    ): String {
        return """
            <html>
            <head>
                <style>
                    table { border-collapse: collapse; width: 100%; font-size: 12px; }
                    th, td { border: 1px solid #ddd; padding: 8px; text-align: center; }
                    th { background-color: #f2f2f2; }
                    h2 { color: #333; font-size: 18px; margin-top: 20px; }
                    .buy { color: #d32f2f; }
                    .sell { color: #1976d2; }
                    .stock-name { font-weight: bold; font-size: 13px; color: #333; }
                    .stock-code { font-size: 11px; color: #888; }
                    .market-tag { font-size: 10px; color: #555; margin-left: 4px; padding: 2px 4px; border-radius: 3px; background-color: #eee; }
                </style>
            </head>
            <body>
                <h1>📈 $date 기술적 분석 추천 종목</h1>
                <p style="font-size: 11px; color: #666;">* 노란색 배경은 KOSPI 종목입니다.</p>
                
                <h2 class="buy">🚀 매수 추천 (Golden Cross + 과매도) - 상위 ${buyList.size}건</h2>
                ${createTable(buyList, stockMap)}
                
                <h2 class="sell">💧 매도 추천 (Dead Cross + 과매수) - 상위 ${sellList.size}건</h2>
                ${createTable(sellList, stockMap)}
            </body>
            </html>
        """.trimIndent()
    }

    private fun createTable(list: List<StockDaily>, stockMap: Map<String, StockMaster>): String {
        if (list.isEmpty()) return "<p>해당하는 종목이 없습니다.</p>"

        val sb = StringBuilder()
        sb.append("<table>")
        sb.append("<tr><th width='30%'>종목명 (코드)</th><th>현재가</th><th>RSI</th><th>MACD</th><th>Signal</th></tr>")

        list.forEach { item ->
            val price = try {
                item.closePrice.toString().toDouble().toLong()
            } catch (e: Exception) { 0L }
            val formattedPrice = String.format("%,d", price)

            // [수정] StockMaster 객체 조회 및 정보 추출
            val stockInfo = stockMap[item.stockCode]
            val stockName = stockInfo?.stockName ?: "이름미상"
            val market = stockInfo?.market ?: "" // market 컬럼 (KOSPI, KOSDAQ 등)

            // [추가] KOSPI인 경우 배경색 노란색(#fff9c4: 연한 노랑) 지정
            val rowStyle = if (market == "KOSPI") "style='background-color: #fff9c4;'" else ""

            // tr 태그에 style 적용
            sb.append("<tr $rowStyle>")

            sb.append("""
                <td style="text-align: left; padding-left: 10px;">
                    <div class="stock-name">
                        $stockName 
                        </div>
                    <div class="stock-code">${item.stockCode}</div>
                </td>
            """.trimIndent())

            sb.append("<td><b>$formattedPrice</b></td>")
            sb.append("<td>${item.rsi ?: "-"}</td>")
            sb.append("<td>${item.macd ?: "-"}</td>")
            sb.append("<td>${item.signalLine ?: "-"}</td>")
            sb.append("</tr>")
        }
        sb.append("</table>")
        return sb.toString()
    }
}