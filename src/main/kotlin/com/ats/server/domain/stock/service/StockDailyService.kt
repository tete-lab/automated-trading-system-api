package com.ats.server.domain.stock.service

import com.ats.server.domain.stock.dto.StockDailyCreateReq
import com.ats.server.domain.stock.dto.StockDailyRes
import com.ats.server.domain.stock.dto.StockDailyUpdateReq
import com.ats.server.domain.stock.entity.StockDaily
import com.ats.server.domain.stock.repository.StockDailyRepository
import com.ats.server.domain.stock.repository.StockMasterRepository
import com.ats.server.domain.token.dto.TokenFindReq
import com.ats.server.domain.token.service.TokenService
import com.ats.server.infra.kiwoom.client.KiwoomClient
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


@Service
@Transactional(readOnly = true)
class StockDailyService(
    private val stockDailyRepository: StockDailyRepository,
    private val stockMasterRepository: StockMasterRepository,
    private val tokenService: TokenService,
    private val kiwoomClient: KiwoomClient,
    private val objectMapper: ObjectMapper, // JSON 파싱용
    private val krxKosdaqClient: KrxKosdaqClient
) {
    // 로거 설정
    private val log = LoggerFactory.getLogger(javaClass)


    /**
     * [Helper] 토큰 가져오기 (메모리 캐싱 적용)
     * - cachedToken이 있으면 DB 안 가고 바로 반환
     * - 없으면 DB 조회 후 메모리에 저장
     */
     fun getApiToken(): String {
//        if (cachedToken == null) {
            log.info(">>> 키움 API 토큰이 메모리에 없어 DB에서 발급받습니다.")
            val req = TokenFindReq()
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
     * [추가] 키움증권 API를 통해 일별 시세를 가져와서 DB에 저장/갱신 (Upsert)
     */
    // [수정] 새로운 트랜잭션을 강제로 생성하여 Read-Only 설정을 무시하도록 함
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun fetchAndSaveDailyPrice(stockCode: String, targetDate: LocalDate, token: String): Int {



        // 2. 날짜 포맷 변환 (LocalDate -> yyyyMMdd)
        val formatter = DateTimeFormatter.ofPattern("yyyyMMdd")
        val strTarget = targetDate.format(formatter)

        // 3. API 호출
        val jsonResponse = kiwoomClient.fetchDailyPrice(token, stockCode, strTarget)
        // 4. 응답 파싱
        val responseDto = objectMapper.readValue(jsonResponse, KiwoomDailyPriceResponse::class.java)

        log.info("fetchDailyPrice returnCode : [" + responseDto.returnCode + "], returnMsg : " + responseDto.returnMsg )
        val items = responseDto.dalyStkpc ?: return 0

        // 5. [최적화] 해당 종목의 기존 데이터들(20일치 등)을 한 번에 조회하여 Map으로 변환
        val dates = items.map { LocalDate.parse(it.date, formatter) }
        val minDate = dates.minOrNull() ?: return 0
        val maxDate = dates.maxOrNull() ?: return 0

        val existingMap = stockDailyRepository.findAllByStockCodeAndBaseDateBetweenOrderByBaseDateAsc(
            stockCode, minDate, maxDate
        ).associateBy { it.baseDate }

        // 3. 메모리 내에서 비교 및 엔티티 준비
        val saveList = mutableListOf<StockDaily>()

        items.forEach { item ->
            val baseDate = LocalDate.parse(item.date, formatter)
            val existingEntity = existingMap[baseDate]

            if (existingEntity != null) {
                // [Update] 메모리에 있는 엔티티 수정
                existingEntity.update(
                    closePrice = item.closePrice,
                    openPrice = item.openPrice,
                    highPrice = item.highPrice,
                    lowPrice = item.lowPrice,
                    volume = item.volume,
                    volumePrice = item.volumePrice,
                    fluctuationRate = item.fluctuationRate,
                    individualBuy = item.individualBuy,
                    organBuy = item.organBuy,
                    foreignerBuy = item.foreignerBuy,
                    // 기존 지표 유지
                    sma20 = existingEntity.sma20, sma50 = existingEntity.sma50,
                    ema9 = existingEntity.ema9, ema12 = existingEntity.ema12,
                    ema26 = existingEntity.ema26, rsi = existingEntity.rsi,
                    macd = existingEntity.macd, signalLine = existingEntity.signalLine,
                    crossType = existingEntity.crossType, recommendYn = existingEntity.recommendYn
                )
                saveList.add(existingEntity)
            } else {
                // [Insert] 새 엔티티 생성
                saveList.add(StockDaily(
                    stockCode = stockCode,
                    baseDate = baseDate,
                    closePrice = item.closePrice,
                    openPrice = item.openPrice,
                    highPrice = item.highPrice,
                    lowPrice = item.lowPrice,
                    volume = item.volume,
                    volumePrice = item.volumePrice,
                    fluctuationRate = item.fluctuationRate,
                    individualBuy = item.individualBuy,
                    organBuy = item.organBuy,
                    foreignerBuy = item.foreignerBuy
                ))
            }
        }

        // 4. [최적화] saveAll을 통해 벌크 저장 (DB 쓰기 횟수 단축)
        stockDailyRepository.saveAll(saveList)

        log.info(">>> [SUCCESS] 종목: $stockCode, 처리 건수: ${saveList.size}")
        return saveList.size

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
//                                log.info("closePrices : " + closePrices)
                                // 지수 계산 로직 (기존 함수 재사용)
                                val sma20 = calculateSma(closePrices, 20)
//                                log.info("sma20 : " + sma20)

                                val sma50 = calculateSma(closePrices, 50)
                                val ema9 = calculateEma(closePrices, 9)
                                val ema12 = calculateEma(closePrices, 12)
                                val ema26 = calculateEma(closePrices, 26)
                                val rsi = calculateRsi(closePrices, 14)
                                val (macd, signal) = calculateMacd(closePrices)
                                // val crossType = determineCrossType(...)

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
                                }

                                // ★ 병렬 환경에서는 명시적 save 권장 (변경감지 트랜잭션 범위 애매할 수 있음)
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
    private fun calculateEmaSeries(prices: List<Double>, period: Int): List<Double> {
        if (prices.isEmpty()) return emptyList()

        val alpha = 2.0 / (period + 1.0)
        val result = ArrayList<Double>()
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
    private fun calculateMacd(prices: List<Double>): Pair<Double?, Double?> {
        if (prices.size < 26) return Pair(null, null)

        // 전체 기간에 대한 EMA 시리즈 생성
        val ema12Series = calculateEmaSeries(prices, 12)
        val ema26Series = calculateEmaSeries(prices, 26)

        // MACD Series = EMA12 - EMA26
        val macdSeries = ema12Series.zip(ema26Series) { e12, e26 -> e12 - e26 }

        // 현재(마지막) MACD 값
        val currentMacd = macdSeries.last()

        // Signal Line = MACD Series의 9일 EMA
        // Signal 계산을 위해 MACD Series 자체를 EMA 함수에 넣음
        val currentSignal = calculateEma(macdSeries, 9)

        return Pair(currentMacd, currentSignal)
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

}