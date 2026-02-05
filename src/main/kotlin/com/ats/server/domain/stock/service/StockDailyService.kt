package com.ats.server.domain.stock.service

import com.ats.server.domain.stock.dto.StockDailyCreateReq
import com.ats.server.domain.stock.dto.StockDailyRes
import com.ats.server.domain.stock.dto.StockDailyUpdateReq
import com.ats.server.domain.stock.entity.StockDaily
import com.ats.server.domain.stock.repository.StockDailyRepository
import com.ats.server.domain.stock.repository.StockMasterRepository
import com.ats.server.domain.token.dto.TokenFindReq
import com.ats.server.domain.token.dto.TokenRes
import com.ats.server.domain.token.service.TokenService
import com.ats.server.infra.kis.client.KisClient
import com.ats.server.infra.kis.dto.KisApiResult
import com.ats.server.infra.kis.dto.KisPeriodPriceResponse
import com.ats.server.infra.kiwoom.client.KiwoomClient
import com.ats.server.infra.kiwoom.dto.KiwoomDailyItem
import com.ats.server.infra.kiwoom.dto.KiwoomDailyPriceResponse
import com.ats.server.infra.krx.client.KrxKosdaqClient
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import kotlinx.coroutines.*
import java.time.format.DateTimeFormatter
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.transaction.annotation.Propagation
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.concurrent.atomic.AtomicInteger
import kotlin.system.measureTimeMillis
import com.ats.server.infra.kis.dto.KisInvestorTrendResponse // [Import] 분리된 DTO

@Service
@Transactional(readOnly = true)
class StockDailyService(
    private val stockDailyRepository: StockDailyRepository,
    private val stockMasterRepository: StockMasterRepository,
    private val tokenService: TokenService,
    private val kiwoomClient: KiwoomClient,
    private val objectMapper: ObjectMapper, // JSON 파싱용
    private val krxKosdaqClient: KrxKosdaqClient,
    private val kisClient: KisClient
) {
    // 로거 설정
    private val log = LoggerFactory.getLogger(javaClass)


    /**
     * [Helper] 토큰 가져오기 (메모리 캐싱 적용)
     * - cachedToken이 있으면 DB 안 가고 바로 반환
     * - 없으면 DB 조회 후 메모리에 저장
     */
     fun getApiToken(apiName: String): String {
//        if (cachedToken == null) {
            log.info(">>> 키움 API 토큰이 메모리에 없어 DB에서 발급받습니다.")
            val req = TokenFindReq(null, apiName)
            // DB 조회 or API 발급
            val res = tokenService.getValidToken(req)
            return res.token
//            cachedToken = res.token
//        }
//        return cachedToken!!
    }
    
    // 기간 조회 (차트 데이터)
    fun getDailyList(stockCode: String, startDate: LocalDate, endDate: LocalDate): List<StockDailyRes> {
        val list = stockDailyRepository.findAllByStockCodeAndBaseDateBetweenOrderByBaseDateAsc(
            stockCode, startDate, endDate
        )
        return list.map { toRes(it) }
    }

    // 단건 등록
    @Transactional
    fun createDaily(req: StockDailyCreateReq): Long {
        // 1. 종목 존재 여부 확인
        if (!stockMasterRepository.existsById(req.stockCode)) {
            throw IllegalArgumentException("존재하지 않는 종목코드입니다: ${req.stockCode}")
        }
        // 2. 해당 날짜 데이터 중복 확인
        if (stockDailyRepository.existsByStockCodeAndBaseDate(req.stockCode, req.baseDate)) {
            throw IllegalStateException("이미 해당 날짜의 시세 데이터가 존재합니다. (${req.baseDate})")
        }

        val entity = StockDaily(
            stockCode = req.stockCode,
            baseDate = req.baseDate,
            closePrice = req.closePrice,
            openPrice = req.openPrice,
            highPrice = req.highPrice,
            lowPrice = req.lowPrice,
            volume = req.volume,
            volumePrice = req.volumePrice,
            fluctuationRate = req.fluctuationRate,
            individualBuy = req.individualBuy,
            organBuy = req.organBuy,
            foreignerBuy = req.foreignerBuy,
            sma20 = req.sma20,
            sma50 = req.sma50,
            rsi = req.rsi,
            macd = req.macd,
            signalLine = req.signalLine,
            crossType = req.crossType,
            recommendYn = req.recommendYn
        )
        return stockDailyRepository.save(entity).stockDailyId!!
    }

    // 수정 (날짜와 종목코드로 찾아서 업데이트)
    @Transactional
    fun updateDaily(stockCode: String, baseDate: LocalDate, req: StockDailyUpdateReq): Long {
        val entity = stockDailyRepository.findByStockCodeAndBaseDate(stockCode, baseDate)
            .orElseThrow { IllegalArgumentException("해당 날짜의 시세 데이터가 없습니다.") }

        entity.update(
            req.closePrice, req.openPrice, req.highPrice, req.lowPrice,
            req.volume, req.volumePrice,
            req.fluctuationRate, req.individualBuy, req.organBuy, req.foreignerBuy,
            req.sma20, req.sma50, req.ema9, req.ema12, req.ema26,
            req.rsi, req.macd, req.signalLine, req.crossType, req.recommendYn
        )
        return entity.stockDailyId!!
    }

    // 변환 헬퍼
    private fun toRes(entity: StockDaily) = StockDailyRes(
        stockDailyId = entity.stockDailyId!!,
        stockCode = entity.stockCode,
        baseDate = entity.baseDate,
        closePrice = entity.closePrice,
        openPrice = entity.openPrice,
        highPrice = entity.highPrice,
        lowPrice = entity.lowPrice,
        volume = entity.volume,
        volumePrice = entity.volumePrice,
        fluctuationRate = entity.fluctuationRate,
        individualBuy = entity.individualBuy,
        organBuy = entity.organBuy,
        foreignerBuy = entity.foreignerBuy,
        sma20 = entity.sma20,
        sma50 = entity.sma50,
        ema9 = entity.ema9,
        ema12 = entity.ema12,
        ema26 = entity.ema26,
        rsi = entity.rsi,
        macd = entity.macd,
        recommendYn = entity.recommendYn,
        crossType = entity.crossType
    )


    /**
     * [키움증권 API] 일별 시세 수집 (연속 조회 + Bulk Upsert)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    suspend fun fetchAndSaveDailyPrice(stockCode: String, targetDate: LocalDate, token: String): Int {
        val formatter = DateTimeFormatter.ofPattern("yyyyMMdd")
        val strTarget = targetDate.format(formatter)

        // 1. [Pagination] 모든 페이지의 데이터를 메모리에 수집
        // 사용자님 DTO인 KiwoomDailyItem 사용
//        val accumulatedItems = mutableListOf<StockDaily>()
        val accumulatedItems = mutableListOf<KiwoomDailyItem>()
        var nextKey: String? = null
        var loopCount = 0
        val maxLoop = 20 // 안전장치: 최대 20페이지(약 300일치)까지만 조회 (필요 시 조절)

        do {
            try {
                // API 호출 (KiwoomClient가 Header 정보도 주는 KiwoomApiResult를 반환해야 함)
                val result = kiwoomClient.fetchDailyPrice(token, stockCode, strTarget, nextKey)

                // 응답 파싱
                val responseDto = objectMapper.readValue(result.body, KiwoomDailyPriceResponse::class.java)

                if (responseDto.returnCode != "0") {
                    log.warn("Kiwoom API Error [$stockCode]: ${responseDto.returnMsg}")
                    break
                }

                // 데이터 적재 (null safe)
                responseDto.dalyStkpc?.let {
                    accumulatedItems.addAll(it)
                }

                // 다음 키 갱신
                nextKey = if (result.hasNext) result.nextKey else null

                // [속도 제어] 연속 호출 시 서버 부하 방지
                if (nextKey != null) {
                    delay(50)
                    loopCount++
                }

            } catch (e: Exception) {
                log.error("Fetch Loop Error [$stockCode]: ${e.message}")
                break
            }
        } while (nextKey != null && loopCount < maxLoop)

        // 수집된 데이터가 없으면 종료
        if (accumulatedItems.isEmpty()) return 0

        log.info(">>> [$stockCode] 총 ${accumulatedItems.size}건 데이터 수집 완료. DB 병합 시작.")


        // 2. [최적화] Bulk Upsert 로직
        // 수집된 전체 데이터의 날짜 범위를 구함
        val dates = accumulatedItems.map { LocalDate.parse(it.date, formatter) }
        val minDate = dates.minOrNull() ?: return 0
        val maxDate = dates.maxOrNull() ?: return 0

        // DB에서 해당 범위의 기존 데이터를 한 번에 조회 (Map으로 변환)
        val existingMap = stockDailyRepository.findAllByStockCodeAndBaseDateBetweenOrderByBaseDateAsc(
            stockCode, minDate, maxDate
        ).associateBy { it.baseDate }

        val saveList = mutableListOf<StockDaily>()

        accumulatedItems.forEach { item ->
            val baseDate = LocalDate.parse(item.date, formatter)
            val existingEntity = existingMap[baseDate]

            // [데이터 변환] DTO의 String 타입 필드들을 DB 타입에 맞게 파싱
            // KiwoomDailyItem에 정의된 closePrice는 BigDecimal getter가 있으므로 바로 사용
            val volumeVal = item.volume ?: 0L
            val volumePriceVal = item.volumePrice
            val flucRateVal = item.fluctuationRate // "0.56" -> 0.56
            val indivBuyVal = item.individualBuy     // "+1,000" -> 1000
            val orgBuyVal = item.organBuy
            val forBuyVal = item.foreignerBuy

            if (existingEntity != null) {
                // [Update]
                existingEntity.update(
                    closePrice = item.closePrice, // DTO Helper 사용
                    openPrice = item.openPrice,
                    highPrice = item.highPrice,
                    lowPrice = item.lowPrice,
                    volume = volumeVal,
                    volumePrice = volumePriceVal,
                    fluctuationRate = flucRateVal, // Double? or String? Entity 타입에 맞춤
                    individualBuy = indivBuyVal,
                    organBuy = orgBuyVal,
                    foreignerBuy = forBuyVal,
                    // 기존 지표 유지
                    sma20 = existingEntity.sma20, sma50 = existingEntity.sma50,
                    ema9 = existingEntity.ema9, ema12 = existingEntity.ema12,
                    ema26 = existingEntity.ema26, rsi = existingEntity.rsi,
                    macd = existingEntity.macd, signalLine = existingEntity.signalLine,
                    crossType = existingEntity.crossType, recommendYn = existingEntity.recommendYn
                )
                saveList.add(existingEntity)
            } else {
                // [Insert]
                saveList.add(StockDaily(
                    stockCode = stockCode,
                    baseDate = baseDate,
                    closePrice = item.closePrice,
                    openPrice = item.openPrice,
                    highPrice = item.highPrice,
                    lowPrice = item.lowPrice,
                    volume = volumeVal,
                    volumePrice = volumePriceVal,
                    fluctuationRate = flucRateVal,
                    individualBuy = indivBuyVal,
                    organBuy = orgBuyVal,
                    foreignerBuy = forBuyVal
                ))
            }
        }

        // 3. 일괄 저장
        if (saveList.isNotEmpty()) {
            stockDailyRepository.saveAll(saveList)
        }

        log.info(">>> [SUCCESS] 종목: $stockCode, 처리 건수: ${saveList.size}")
        return saveList.size
    }

    // [Helper] 문자열(예: "+1,000", "-500", "")을 Long으로 안전하게 변환
    private fun parseLong(value: String?): Long {
        if (value.isNullOrBlank()) return 0L
        return try {
            value.replace(",", "").replace("+", "").toLong()
        } catch (e: Exception) {
            0L
        }
    }

    // [Helper] 문자열(예: "-0.56", "3.2")을 Double로 변환
    private fun parseDouble(value: String?): Double {
        if (value.isNullOrBlank()) return 0.0
        return try {
            value.replace("%", "").toDouble()
        } catch (e: Exception) {
            0.0
        }
    }

    /**
     * KRX 코스닥 일별 매매정보 수집 및 저장
     */
    @Transactional
    fun fetchAndSaveKrxKosdaqPrices(targetDate: LocalDate): Int {
        val strDate = targetDate.format(DateTimeFormatter.ofPattern("yyyyMMdd"))
        log.info(">>> KRX 코스닥 데이터 수집 시작: {}", strDate)

        // 1. KRX API 호출
        val response = krxKosdaqClient.fetchKosdaqPrices(strDate)
        val items = response?.output ?: return 0

        // 2. [최적화] 해당 날짜의 기존 데이터를 한 번에 가져와서 Map으로 관리 (중복 체크용)
        // 지수와 달리 종목은 개수가 많으므로 필요 시 해당 날짜 데이터 존재 여부만 체크할 수도 있습니다.

        val saveList = mutableListOf<StockDaily>()

        items.forEach { item ->
            val stockCode = item.shortCode // ISU_CD에서 추출한 6자리 코드

            // 3. 엔티티 매핑
            val entity = StockDaily(
                stockCode = stockCode,
                baseDate = targetDate,
                closePrice = item.toBigDecimal(item.closePrice),
                openPrice = item.toBigDecimal(item.openPrice),
                highPrice = item.toBigDecimal(item.highPrice),
                lowPrice = item.toBigDecimal(item.lowPrice),
                volume = item.toLong(item.volume),
                volumePrice = item.toBigDecimal(item.volumePrice),
                fluctuationRate = item.flucRt,
                foreignerBuy = null,
                individualBuy = null,
                organBuy = null
            )
            saveList.add(entity)
        }

        // 4. 벌크 저장 (성능 핵심)
        if (saveList.isNotEmpty()) {
            stockDailyRepository.saveAll(saveList)
        }

        log.info(">>> KRX 코스닥 수집 완료: {}건 저장됨", saveList.size)
        return saveList.size
    }

    // 기간별 지표 계산 (Controller에서 호출)
    suspend fun calculateIndicatorsForPeriod(startDate: LocalDate, endDate: LocalDate) {
        var currentDate = startDate

        // 시작일이 종료일보다 커질 때까지 반복
        while (!currentDate.isAfter(endDate)) {
            println("--- [진행중] $currentDate 지표 계산 ---")

            // 기존의 하루치 계산 함수 재사용
            calculateIndicators(currentDate)

            // 하루 증가
            currentDate = currentDate.plusDays(1)
        }
    }
    // Controller에서 이 함수를 runBlocking 혹은 CoroutineScope로 호출해야 함
    // 예: fun fetch(...) = runBlocking { service.calculateIndicators(date) }
    suspend fun calculateIndicators(targetDate: LocalDate) = coroutineScope {
        val targets = stockDailyRepository.findAllByBaseDate(targetDate)
        println("지표 계산 시작: 총 ${targets.size}개 종목") // 로그 확인용

        // 진행 상황 로깅용
        val counter = AtomicInteger(0)
        val total = targets.size

        val time = measureTimeMillis {
            // 1. 전체 종목을 100개씩 나눔 (DB 커넥션 풀 고갈 방지)
            targets.chunked(100).forEach { batch ->
                // 2. 각 배치를 병렬(async)로 처리
                val jobs = batch.map { stock ->

                    async(Dispatchers.IO) { // DB 작업이므로 IO Dispatcher 사용
                        try {
                            // 과거 데이터 조회
                            val historyDesc = stockDailyRepository.findPriceHistory(
                                stock.stockCode,
                                targetDate,
                                PageRequest.of(0, 100)
                            )
//                            log.info("[tgkim checking] " + stock.stockCode + " / " + stock.baseDate)
//                            log.info("size : " + historyDesc.size)
                            if (historyDesc.size >= 30) {
                                val history = historyDesc.reversed()
                                val closePrices = history.map { it.closePrice.toDouble() }

                                // 지수 계산
                                val sma20 = calculateSma(closePrices, 20)
                                val sma50 = calculateSma(closePrices, 50)
                                val ema9 = calculateEma(closePrices, 9)
                                val ema12 = calculateEma(closePrices, 12)
                                val ema26 = calculateEma(closePrices, 26)
                                val rsi = calculateRsi(closePrices, 14)

                                // [수정] MACD 함수가 이제 3가지 값을 반환함 (구조 분해 선언)
                                val (macd, signal, crossType) = calculateMacd(closePrices)

                                // Entity 업데이트
                                stock.apply {
                                    this.sma20 = sma20?.toBigDecimal()
                                    this.sma50 = sma50?.toBigDecimal()
                                    this.ema9 = ema9?.toBigDecimal()
                                    this.ema12 = ema12?.toBigDecimal()
                                    this.ema26 = ema26?.toBigDecimal()
                                    this.rsi = rsi?.toBigDecimal()

                                    this.macd = macd?.toBigDecimal()
                                    this.signalLine = signal?.toBigDecimal()

                                    // [추가] 크로스 타입 저장 (DB 컬럼명에 맞춰 수정하세요)
                                    // 예: this.macdCross = crossType
                                    // 만약 Entity에 필드가 없다면 추가해야 합니다.
                                    this.crossType = crossType
                                }

                                stockDailyRepository.save(stock)
                            }
                        } catch (e: Exception) {
                            println("Error calculating for ${stock.stockCode}: ${e.message}")
                        } finally {
                            counter.incrementAndGet()
                        }
                    }
                }
                // 현재 배치의 모든 작업이 끝날 때까지 대기
                jobs.awaitAll()
                println("배치(100개) 처리 완료..") // 진행 상황 모니터링
            }
        }

        println("지표 계산 완료. 소요 시간: ${time}ms")
    }

    // 1. SMA (단순 이동평균)
    private fun calculateSma(prices: List<Double>, period: Int): Double? {
        if (prices.size < period) return null
        // 마지막 날짜 기준 period 만큼 잘라서 평균
        return prices.takeLast(period).average()
    }

    // 2. EMA (지수 이동평균) - Python ewm(adjust=False) 로직
    private fun calculateEma(prices: List<Double>, period: Int): Double? {
        if (prices.size < period) return null

        val alpha = 2.0 / (period + 1.0)
        var ema = prices[0] // 초기값은 첫 데이터 (혹은 SMA로 시작하기도 함)

        // 전체 데이터를 순회하며 EMA 누적 계산
        for (i in 1 until prices.size) {
            ema = (prices[i] * alpha) + (ema * (1 - alpha))
        }
        return ema
    }

    // List 전체의 EMA 시리즈를 반환 (MACD 계산용)
    // List 전체의 EMA 시리즈를 반환 (MACD 계산 시 필요)
    private fun calculateEmaSeries(prices: List<Double>, period: Int): List<Double> {
        if (prices.isEmpty()) return emptyList()

        val alpha = 2.0 / (period + 1.0)
        val result = ArrayList<Double>()

        // 첫 데이터로 초기화 (SMA로 시작하는 경우도 있으나, 여기선 첫 값 사용)
        var ema = prices[0]
        result.add(ema)

        for (i in 1 until prices.size) {
            ema = (prices[i] * alpha) + (ema * (1 - alpha))
            result.add(ema)
        }
        return result
    }

    // 3. RSI (상대강도지수) - Python rolling mean 로직
    private fun calculateRsi(prices: List<Double>, period: Int): Double? {
        if (prices.size <= period) return null

        // 등락폭 계산
        val deltas = prices.zipWithNext { a, b -> b - a } // b가 오늘, a가 어제

        // 최근 period 기간의 상승폭/하락폭 평균 (SMA 방식)
        val relevantDeltas = deltas.takeLast(period)

        val gains = relevantDeltas.map { if (it > 0) it else 0.0 }.average()
        val losses = relevantDeltas.map { if (it < 0) -it else 0.0 }.average()

        if (losses == 0.0) return 100.0 // 하락이 없으면 RSI 100

        val rs = gains / losses
        return 100.0 - (100.0 / (1.0 + rs))
    }

    // 4. MACD & Signal
    /**
     * MACD, Signal, 그리고 Cross 여부를 계산
     * Return: Triple(Macd값, Signal값, CrossType)
     * CrossType: 1(Golden), -1(Dead), 0(None)
     */
    private fun calculateMacd(prices: List<Double>): Triple<Double?, Double?, Int> {
        // MACD를 구하기 위한 최소 데이터 개수 체크 (26일 + Signal용 1일 이상 권장)
        if (prices.size < 26) return Triple(null, null, 0)

        // 1. 전체 기간에 대한 EMA 시리즈 생성
        val ema12Series = calculateEmaSeries(prices, 12)
        val ema26Series = calculateEmaSeries(prices, 26)

        // 2. MACD Series = EMA12 - EMA26
        // (zip을 이용해 같은 인덱스끼리 뺌)
        val macdSeries = ema12Series.zip(ema26Series) { e12, e26 -> e12 - e26 }

        // 3. Signal Series = MACD Series의 9일 EMA
        val signalSeries = calculateEmaSeries(macdSeries, 9)

        // 데이터 개수 안전 장치
        if (macdSeries.isEmpty() || signalSeries.isEmpty()) {
            return Triple(null, null, 0)
        }

        // 4. 오늘(마지막) 값
        val currMacd = macdSeries.last()
        val currSignal = signalSeries.last()

        // 5. 크로스 체크 (데이터가 2개 이상일 때만 가능)
        var crossType = 0 // 0: 없음, 1: 골든, -1: 데드

        if (macdSeries.size >= 2 && signalSeries.size >= 2) {
            val prevMacd = macdSeries[macdSeries.lastIndex - 1]
            val prevSignal = signalSeries[signalSeries.lastIndex - 1]

            // 골든 크로스: 어제는 MACD가 시그널 아래, 오늘은 위
            if (prevMacd < prevSignal && currMacd > currSignal) {
                crossType = 1
            }
            // 데드 크로스: 어제는 MACD가 시그널 위, 오늘은 아래
            else if (prevMacd > prevSignal && currMacd < currSignal) {
                crossType = -1
            }
        }

        return Triple(currMacd, currSignal, crossType)
    }

    // 5. 골든크로스/데드크로스 판별
    // DB 컬럼이 cross_type (String 혹은 Decimal) 인지에 따라 리턴타입 조정 필요
    // 여기서는 문자열이나 코드로 반환한다고 가정 (예: 1=Golden, -1=Dead, 0=None)
    private fun determineCrossType(history: List<StockDaily>, currentMacd: Double?, currentSignal: Double?): BigDecimal? {
        if (currentMacd == null || currentSignal == null || history.size < 2) return null

        // 어제 날짜의 지표가 필요함.
        // history는 reversed된 상태(오래된 것 -> 최신)라고 가정했을 때:
        // prices 리스트를 다시 계산하거나, DB에 저장된 어제 값을 써야 함.
        // 정확성을 위해 방금 계산한 로직을 어제 기준으로도 한번 더 수행하는 것이 가장 정확함.
        // 하지만 성능상 여기서는 DB에 저장된 어제 값을 믿거나,
        // MACD Series의 끝에서 두번째 값을 가져오는 방식을 추천함.

        // 로직 단순화를 위해:
        // 오늘: MACD > Signal (정배열)
        // 어제: MACD < Signal (역배열 이었음)
        // => 골든 크로스

        // *주의: calculateMacd 함수 내부에서 macdSeries 전체를 구했으므로 그걸 활용하면 좋음.
        // 코드를 간결하게 유지하기 위해 여기서는 "현재 상태"만 우선 저장하거나
        // 정밀 계산이 필요하면 calculateMacd에서 (오늘, 어제) 값을 모두 리턴하도록 수정해야 함.

        // 약식 구현 (현재 상태 기준):
        // DB 테이블의 cross_type이 숫자인지 문자인지 확인 필요.
        // 질문의 테이블 스키마: `cross_type` decimal(10,2) -> 숫자로 가정 (1: Golden, 2: Dead 등)

        val diff = currentMacd - currentSignal

        // 단순히 오늘 상태만 기록한다면:
        // return if (diff > 0) BigDecimal("1") else BigDecimal("-1")

        // 크로스 '발생' 시점을 기록하려면 어제 데이터를 알아야 함.
        // 편의상 이 예제에서는 null 처리 혹은 추후 고도화 영역으로 둡니다.
        return null
    }

    // BigDecimal 변환 헬퍼 (null 처리 및 소수점 반올림)
    private fun Double.toBigDecimal(): BigDecimal {
        return BigDecimal.valueOf(this).setScale(2, RoundingMode.HALF_UP)
    }


    /**
     * [한국투자증권] 기간별 시세 수집 (Start ~ End)
     * - API: FHKST03010100
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    suspend fun fetchAndSavePeriodDailyPriceFromKis(
        stockCode: String,
        startDate: LocalDate,
        endDate: LocalDate,
        token: String
    ): Int {
        val formatter = DateTimeFormatter.ofPattern("yyyyMMdd")
        val strStart = startDate.format(formatter)
        val strEnd = endDate.format(formatter)

        val (appKey, appSecret) = tokenService.getAppKeys(null, "KIS")
        // 1. API 호출
        val result = kisClient.fetchPeriodPrice(token, appKey, appSecret, stockCode, strStart, strEnd)

        // 2. 파싱 (Output2를 사용하는 DTO)
        val response = objectMapper.readValue(result.body, KisPeriodPriceResponse::class.java)

        if (response.returnCode != "0") {
            log.warn("KIS API Error [$stockCode]: ${response.message}")
            return 0
        }

        val items = response.output ?: emptyList()
        if (items.isEmpty()) return 0

        // 3. Bulk Upsert 준비
        // API가 준 데이터의 실제 날짜 범위 파악 (보통 내림차순으로 옴)
        val dates = items.map { LocalDate.parse(it.date, formatter) }
        val realMinDate = dates.minOrNull() ?: startDate
        val realMaxDate = dates.maxOrNull() ?: endDate

        // DB에서 해당 구간 기존 데이터 조회
        val existingMap = stockDailyRepository.findAllByStockCodeAndBaseDateBetweenOrderByBaseDateAsc(
            stockCode, realMinDate, realMaxDate
        ).associateBy { it.baseDate }

        val saveList = mutableListOf<StockDaily>()

        items.forEach { item ->
            val baseDate = LocalDate.parse(item.date, formatter)
            val existingEntity = existingMap[baseDate]

            if (existingEntity != null) {
                // Update
                existingEntity.apply {
                    closePrice = item.closePrice
                    openPrice = item.openPrice
                    highPrice = item.highPrice
                    lowPrice = item.lowPrice
                    volume = item.volume
                    volumePrice = item.volumePrice
                }
                saveList.add(existingEntity)
            } else {
                // Insert
                saveList.add(StockDaily(
                    stockCode = stockCode,
                    baseDate = baseDate,
                    closePrice = item.closePrice,
                    openPrice = item.openPrice,
                    highPrice = item.highPrice,
                    lowPrice = item.lowPrice,
                    volume = item.volume,
                    volumePrice = item.volumePrice,
                    fluctuationRate = null, // 필요시 전일대비 계산 로직 추가 가능
                    individualBuy = null,
                    organBuy = null,
                    foreignerBuy = null
                ))
            }
        }

        if (saveList.isNotEmpty()) {
            stockDailyRepository.saveAll(saveList)
        }

        //log.info(">>> [KIS Period] $stockCode ($strStart~$strEnd) : ${saveList.size}건 저장 완료")
        return saveList.size
    }

    /**
     * 투자자별 매매동향 단건 수집 및 저장
     * return: 업데이트 성공 여부
     */
    @Transactional
    suspend fun fetchAndSaveInvestorTrend(stockCode: String, date: LocalDate, token: String): Boolean {
        // 1. 데이터 가져오기 (Optional 벗기기)
        val stockDaily = stockDailyRepository.findByStockCodeAndBaseDate(stockCode, date).orElse(null)
            ?: return false

        try {
            // 2. API 호출
            val response = callKisInvestorApi(stockCode, date, token)

            // 3. 날짜 매칭
            val targetData = response?.output?.find {
                it.stckBsopDate == date.format(DateTimeFormatter.ofPattern("yyyyMMdd"))
            }

            if (targetData != null) {
                // 4. 데이터 유효성 검사 (빈 값 체크)
                if (targetData.individualBuyQty.isNullOrBlank()) {
                    log.warn(">>> [Skip] 데이터가 비어있음: $stockCode ($date)")
                    return false
                }

                // 5. 값 변경
                stockDaily.apply {
                    this.fluctuationRate = targetData.fluctuationRate
                    this.individualBuy = targetData.individualBuyQty
                    this.foreignerBuy = targetData.foreignerBuyQty
                    this.organBuy = targetData.organBuyQty
                }

                // [🚨 핵심 수정] 변경 사항을 즉시 DB에 반영 (Flush & Commit)
                stockDailyRepository.save(stockDaily)

//                log.info(">>> [Success] $stockCode ($date) 투자자 데이터 업데이트 완료! (개인: ${targetData.individualBuyQty})")
                return true
            } else {
                log.warn("KIS data not found for $stockCode at $date")
            }

        } catch (e: Exception) {
            log.error("Failed to fetch investor trend for $stockCode: ${e.message}")
            throw e
        }
        return false
    }

    private fun callKisInvestorApi(stockCode: String, date: LocalDate, token: String): KisInvestorTrendResponse? {
        val dateStr = date.format(DateTimeFormatter.ofPattern("yyyyMMdd"))
        // Config 등에서 앱키 가져오는 로직 필요 (혹은 파라미터로 받기)
        val (appKey, appSecret) = tokenService.getAppKeys(null, "KIS")

        // [수정] KisClient 호출
        val result: KisApiResult = kisClient.fetchInvestorTrend(
            token = token,
            appKey = appKey,
            appSecret = appSecret,
            stockCode = stockCode,
            startDate = dateStr,
            endDate = dateStr
        )

        // String(JSON) -> DTO 변환 (ObjectMapper 사용)
        return objectMapper.readValue(result.body, KisInvestorTrendResponse::class.java)
    }

}