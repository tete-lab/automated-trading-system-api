package com.ats.server.domain.stock.service

import com.ats.server.domain.stock.entity.StockDaily
import com.ats.server.domain.stock.repository.StockDailyRepository
import com.ats.server.domain.stock.repository.StockMasterRepository // [추가]
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
    private val stockMasterRepository: StockMasterRepository, // [추가] 종목명 조회를 위해 주입
    private val javaMailSender: JavaMailSender
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional(readOnly = true)
    fun sendDailyRecommendationEmail(targetDate: LocalDate, toEmail: String) {
        // 1. 데이터 조회 (추천 로직)
        // crossType은 Int (1: 골든, -1: 데드)
        val buyList = stockDailyRepository.findTop100ByBaseDateAndCrossTypeOrderByRsiAsc(targetDate, 1)
        val sellList = stockDailyRepository.findTop100ByBaseDateAndCrossTypeOrderByRsiDesc(targetDate, -1)

        if (buyList.isEmpty() && sellList.isEmpty()) {
            log.info("추천 종목이 없어 이메일을 발송하지 않습니다.")
            return
        }

        // [추가] 2. 종목명 매핑을 위한 Map 생성 (코드 -> 이름)
        // findAll()을 통해 모든 종목 마스터 정보를 가져와서 Map으로 변환
        val stockNameMap = stockMasterRepository.findAll()
            .associate { it.stockCode to it.stockName }

        // 3. 이메일 제목 및 본문 구성 (nameMap 전달)
        val subject = "[$targetDate] 매수/매도 추천 종목 리포트 (Golden/Dead Cross)"
        val content = buildEmailContent(targetDate, buyList, sellList, stockNameMap)

        // 4. 이메일 발송
        try {
            val message: MimeMessage = javaMailSender.createMimeMessage()
            val helper = MimeMessageHelper(message, true, "UTF-8")

            helper.setTo(toEmail)
            helper.setSubject(subject)
            helper.setText(content, true)

            javaMailSender.send(message)
            log.info("추천 종목 이메일 발송 성공 (To: $toEmail)")
        } catch (e: Exception) {
            log.error("이메일 발송 실패: ${e.message}")
        }
    }

    // HTML 본문 생성 헬퍼
    private fun buildEmailContent(
        date: LocalDate,
        buyList: List<StockDaily>,
        sellList: List<StockDaily>,
        nameMap: Map<String, String> // [추가]
    ): String {
        return """
            <html>
            <head>
                <style>
                    table { border-collapse: collapse; width: 100%; font-size: 12px; }
                    th, td { border: 1px solid #ddd; padding: 8px; text-align: center; }
                    th { background-color: #f2f2f2; }
                    h2 { color: #333; font-size: 18px; margin-top: 20px; }
                    .buy { color: #d32f2f; } /* 빨간색 */
                    .sell { color: #1976d2; } /* 파란색 */
                    .stock-name { font-weight: bold; font-size: 13px; color: #333; }
                    .stock-code { font-size: 11px; color: #888; }
                </style>
            </head>
            <body>
                <h1>📈 $date 기술적 분석 추천 종목</h1>
                
                <h2 class="buy">🚀 매수 추천 (Golden Cross + 과매도) - 상위 ${buyList.size}건</h2>
                ${createTable(buyList, nameMap)}
                
                <h2 class="sell">💧 매도 추천 (Dead Cross + 과매수) - 상위 ${sellList.size}건</h2>
                ${createTable(sellList, nameMap)}
            </body>
            </html>
        """.trimIndent()
    }

    private fun createTable(list: List<StockDaily>, nameMap: Map<String, String>): String {
        if (list.isEmpty()) return "<p>해당하는 종목이 없습니다.</p>"

        val sb = StringBuilder()
        sb.append("<table>")
        sb.append("<tr><th width='30%'>종목명 (코드)</th><th>현재가</th><th>RSI</th><th>MACD</th><th>Signal</th></tr>")

        list.forEach { item ->
            // 가격 포맷팅 (콤마 추가)
            val price = try {
                item.closePrice.toString().toDouble().toLong()
            } catch (e: Exception) { 0L }
            val formattedPrice = String.format("%,d", price)

            // [추가] 이름 가져오기 (없으면 '이름미상' 표시)
            val stockName = nameMap[item.stockCode] ?: "이름미상"

            sb.append("<tr>")
            // [수정] 종목명과 코드를 같이 표시
            sb.append("""
                <td style="text-align: left; padding-left: 10px;">
                    <div class="stock-name">$stockName</div>
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