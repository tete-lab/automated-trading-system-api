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
import java.time.format.DateTimeFormatter
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.transaction.annotation.Propagation


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

    // [1] 토큰을 메모리에 저장할 전역 변수 (싱글톤이므로 유지됨)
    private var cachedToken: String? = null
    /**
     * [Helper] 토큰 가져오기 (메모리 캐싱 적용)
     * - cachedToken이 있으면 DB 안 가고 바로 반환
     * - 없으면 DB 조회 후 메모리에 저장
     */
    private fun getApiToken(): String {
        if (cachedToken == null) {
            log.info(">>> 키움 API 토큰이 메모리에 없어 DB에서 발급받습니다.")
            val req = TokenFindReq()
            // DB 조회 or API 발급
            val res = tokenService.getValidToken(req)
            cachedToken = res.token
        }
        return cachedToken!!
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
    fun fetchAndSaveDailyPrice(stockCode: String, targetDate: LocalDate): Int {

        // 1. 토큰 발급 (캐싱 로직은 Client 내부 혹은 여기서 처리)
        val token = getApiToken()
//        val req = TokenFindReq() // memberId는 기본값(null)으로 설정됨d
//        val res = tokenService.getValidToken(req)
//        val token = res.token

        // 2. 날짜 포맷 변환 (LocalDate -> yyyyMMdd)
        val formatter = DateTimeFormatter.ofPattern("yyyyMMdd")
        val strTarget = targetDate.format(formatter)

        // 3. API 호출
        val jsonResponse = kiwoomClient.fetchDailyPrice(token, stockCode, strTarget)
        // 4. 응답 파싱
        val responseDto = objectMapper.readValue(jsonResponse, KiwoomDailyPriceResponse::class.java)

        log.info("fetchDailyPrice returnCode : [" + responseDto.returnCode + "], returnMsg : " + responseDto.returnMsg )
        val items = responseDto.dalyStkpc ?: return 0

        // 5. [최적화] 해당 종목의 기존 데이터들(30일치 등)을 한 번에 조회하여 Map으로 변환
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
}