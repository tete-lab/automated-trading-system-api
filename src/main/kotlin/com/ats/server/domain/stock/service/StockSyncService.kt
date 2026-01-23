package com.ats.server.domain.stock.service

import com.ats.server.domain.stock.dto.StockCreateReq
import com.ats.server.domain.stock.dto.StockDailyCreateReq
import com.ats.server.domain.stock.dto.StockFundamentalCreateReq
import com.ats.server.domain.stock.dto.StockFundamentalUpdateReq
import com.ats.server.domain.stock.repository.StockDailyRepository
import com.ats.server.domain.stock.repository.StockMasterRepository
import com.ats.server.infra.publicdata.client.PublicDataClient
import com.ats.server.infra.publicdata.dto.PublicDataItem
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import org.slf4j.LoggerFactory

@Service
class StockSyncService(
    private val publicDataClient: PublicDataClient,
    private val stockMasterService: StockMasterService,
    private val stockDailyService: StockDailyService,
    private val stockFundamentalService: StockFundamentalService,
    private val stockDailyRepository: StockDailyRepository,
    private val stockMasterRepository: StockMasterRepository
) {

    // 로거 설정
    private val log = LoggerFactory.getLogger(javaClass)
    // =========================================================================
    // 1. Stock Master & 시가총액 동기화 (Source: 공공데이터포털)
    // =========================================================================
    fun syncMasterByPublicData(): Int {
        // [수정됨] 최근 영업일 데이터 찾기 로직 간소화
        val items = findLatestPublicDataStocks()

        if (items.isEmpty()) {
            throw RuntimeException("최근 1주일간 공공데이터 시세 정보가 없습니다. (API 키, 휴장일, 또는 시간 확인)")
        }

        var saveCount = 0
        for (item in items) {
            try {
                // "A005930" -> "005930"
                val cleanCode = if (item.srtnCd.startsWith("A")) item.srtnCd.substring(1) else item.srtnCd

                // 1) 마스터 정보 저장 (없을 경우 생성)
                if (!stockMasterRepository.existsById(cleanCode)) {
                    stockMasterService.createStock(
                        StockCreateReq(
                            stockCode = cleanCode,
                            stockName = item.itmsNm,
                            market = item.mrktCtg,
                            sector = null, // 공공데이터 미제공
                            auditRisk = null
                        )
                    )
                }

                // 2) 시가총액(Fundamental) 저장
                updateMarketCap(cleanCode, item.mrktTotAmt)

                saveCount++
            } catch (e: Exception) {
                // 로그만 찍고 계속 진행
                log.info("Sync Error [${item.itmsNm}]: ${e.message}")
            }
        }
        return saveCount
    }

    /**
     * [핵심 변경]
     * 이제 publicDataClient.getStockPriceInfo 하나로 해결합니다.
     * 날짜를 하루씩 줄여가며 호출해보고, 데이터가 나오면 그 리스트(전체 페이지 포함)를 반환합니다.
     */
    private fun findLatestPublicDataStocks(): List<PublicDataItem> {
        var targetDate = LocalDate.now()

        // 오늘 데이터가 아직 안 나왔을 수 있으니(장 중이거나 아침), 최대 7일 전까지 뒤져봅니다.
        for (i in 0..7) {
            val dateStr = targetDate.minusDays(i.toLong()).format(DateTimeFormatter.ofPattern("yyyyMMdd"))

            // [중요] 시작일=dateStr, 종료일=dateStr 로 호출하면 -> "해당 날짜의 전 종목"을 가져옵니다.
            // Client 내부에서 while 루프로 모든 페이지를 긁어오므로 여기서 페이징 신경 쓸 필요 X
            val items = publicDataClient.getStockPriceInfo(dateStr, dateStr)

            if (items.isNotEmpty()) {
                log.info("최신 데이터 발견: $dateStr (총 ${items.size} 종목)")
                return items
            }
        }
        return emptyList()
    }

    // 시가총액 저장 로직 (이전과 동일)
    private fun updateMarketCap(code: String, marketCapStr: String) {
        val marketCap = try { BigDecimal(marketCapStr) } catch (e: Exception) { BigDecimal.ZERO }

        try {
            stockFundamentalService.updateFundamental(
                code,
                StockFundamentalUpdateReq(
                    marketCap = marketCap,
                    avgVolume = null, per = null, pbr = null, psr = null,
                    eps = null, bps = null, roe = null, revenueGrowth = null,
                    divYield = null, divRate = null, divPayDate = null
                )
            )
        } catch (e: Exception) {
            stockFundamentalService.createFundamental(
                StockFundamentalCreateReq(
                    stockCode = code,
                    marketCap = marketCap,
                    avgVolume = null, per = null, pbr = null, psr = null,
                    eps = null, bps = null, roe = null, revenueGrowth = null,
                    divYield = null, divRate = null, divPayDate = null
                )
            )
        }
    }


}