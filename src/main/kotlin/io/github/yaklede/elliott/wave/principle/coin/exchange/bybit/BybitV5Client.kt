package io.github.yaklede.elliott.wave.principle.coin.exchange.bybit

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.core.type.TypeReference
import io.github.yaklede.elliott.wave.principle.coin.config.BybitProperties
import io.github.yaklede.elliott.wave.principle.coin.marketdata.Candle
import io.github.yaklede.elliott.wave.principle.coin.marketdata.IntervalUtil
import java.math.BigDecimal
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.reactor.awaitSingle
import org.slf4j.LoggerFactory
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException

@Component
class BybitV5Client(
    private val webClient: WebClient,
    private val properties: BybitProperties,
    private val signer: BybitSigner,
    private val timeSync: BybitTimeSync,
    private val rateLimiter: BybitRateLimiter,
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val maxRetries = 3

    suspend fun marketTime(): Long {
        val response = executeWithRetry {
            get<BybitTimeResult>("/v5/market/time", emptyMap(), authenticated = false)
        }
        return response.time ?: response.result?.timeSecond?.toLong()?.times(1000L)
            ?: throw BybitApiException("Bybit server time missing in response")
    }

    suspend fun getKlines(
        category: String,
        symbol: String,
        interval: String,
        start: Long?,
        end: Long?,
        limit: Int?,
    ): List<Candle> {
        val params = linkedMapOf(
            "category" to category,
            "symbol" to symbol.uppercase(Locale.getDefault()),
            "interval" to interval,
        )
        start?.let { params["start"] = it.toString() }
        end?.let { params["end"] = it.toString() }
        limit?.let { params["limit"] = it.toString() }

        val response = executeWithRetry {
            get<BybitKlineResult>("/v5/market/kline", params, authenticated = false)
        }

        val list = response.result?.list ?: emptyList()
        val candles = list.mapNotNull { entry ->
            if (entry.size < 6) return@mapNotNull null
            Candle(
                timeOpenMs = entry[0].toLong(),
                open = entry[1].toBigDecimalOrNull() ?: return@mapNotNull null,
                high = entry[2].toBigDecimalOrNull() ?: return@mapNotNull null,
                low = entry[3].toBigDecimalOrNull() ?: return@mapNotNull null,
                close = entry[4].toBigDecimalOrNull() ?: return@mapNotNull null,
                volume = entry[5].toBigDecimalOrNull() ?: return@mapNotNull null,
            )
        }
        return candles.sortedBy { it.timeOpenMs }
    }

    suspend fun getKlinesPaged(
        category: String,
        symbol: String,
        interval: String,
        start: Long,
        end: Long,
        limit: Int = 1000,
    ): List<Candle> {
        val intervalMs = IntervalUtil.intervalToMillis(interval)
        if (intervalMs <= 0) return emptyList()
        val alignedStart = start - (start % intervalMs)
        val alignedEnd = end - (end % intervalMs)
        if (alignedEnd < alignedStart) return emptyList()

        val result = mutableListOf<Candle>()
        var cursorEnd = alignedEnd
        while (cursorEnd >= alignedStart) {
            val windowStart = (cursorEnd - intervalMs * (limit - 1)).coerceAtLeast(alignedStart)
            val batch = getKlines(category, symbol, interval, windowStart, cursorEnd, limit)
            if (batch.isEmpty()) break
            result.addAll(batch)
            val oldest = batch.first().timeOpenMs
            val nextEnd = oldest - intervalMs
            if (nextEnd >= cursorEnd) break
            cursorEnd = nextEnd
        }
        return result.distinctBy { it.timeOpenMs }.sortedBy { it.timeOpenMs }
    }

    suspend fun getInstrumentInfo(category: String, symbol: String): BybitInstrumentInfo? {
        val params = linkedMapOf(
            "category" to category,
            "symbol" to symbol.uppercase(Locale.getDefault()),
        )
        val response = executeWithRetry {
            get<BybitInstrumentInfoResult>("/v5/market/instruments-info", params, authenticated = false)
        }
        return response.result?.list?.firstOrNull()
    }

    suspend fun placeOrderSpotMarket(
        symbol: String,
        side: String,
        qty: BigDecimal,
        orderLinkId: String,
    ): BybitOrderResult {
        if (properties.apiKey.isBlank() || properties.apiSecret.isBlank()) {
            throw BybitApiException("Bybit apiKey/apiSecret required for live trading")
        }
        val body = linkedMapOf(
            "category" to "spot",
            "symbol" to symbol.uppercase(Locale.getDefault()),
            "side" to side,
            "orderType" to "Market",
            "qty" to qty.stripTrailingZeros().toPlainString(),
            "orderLinkId" to orderLinkId,
            "marketUnit" to "baseCoin",
        )
        val response = executeWithRetry {
            post<BybitOrderResult>("/v5/order/create", body)
        }
        return response.result ?: throw BybitApiException("Bybit order result missing")
    }

    private suspend inline fun <reified T> get(
        path: String,
        params: Map<String, String>,
        authenticated: Boolean,
    ): BybitResponse<T> {
        val queryString = buildQueryString(params)
        val uri = if (queryString.isBlank()) path else "$path?$queryString"
        val request = webClient.get().uri(uri)
        val finalized = if (authenticated) {
            val headers = authHeadersForGet(queryString)
            request.headers { it.addAll(headers) }
        } else {
            request
        }
        val body = finalized.exchangeToMono { response ->
            logRateLimitHeaders(response.headers().asHttpHeaders())
            response.bodyToMono(String::class.java)
        }.awaitSingle()
        val typeRef = object : TypeReference<BybitResponse<T>>() {}
        return objectMapper.readValue(body, typeRef)
    }

    private suspend inline fun <reified T> post(
        path: String,
        body: Map<String, String>,
    ): BybitResponse<T> {
        val jsonBody = objectMapper.writeValueAsString(body)
        val headers = authHeadersForPost(jsonBody)
        return webClient.post()
            .uri(path)
            .contentType(MediaType.APPLICATION_JSON)
            .headers { it.addAll(headers) }
            .bodyValue(jsonBody)
            .exchangeToMono { response ->
                logRateLimitHeaders(response.headers().asHttpHeaders())
                response.bodyToMono(object : ParameterizedTypeReference<BybitResponse<T>>() {})
            }
            .awaitSingle()
    }

    private suspend fun <T> executeWithRetry(block: suspend () -> BybitResponse<T>): BybitResponse<T> {
        var attempt = 0
        var delayMs = 200L
        while (true) {
            try {
                rateLimiter.acquire()
                val response = block()
                if (response.retCode != 0) {
                    val message = "Bybit error ${response.retCode}: ${response.retMsg}"
                    if (response.retCode == 10002) {
                        throw BybitApiException("$message (timestamp invalid; check time sync)", response.retCode)
                    }
                    if (response.retCode == 10006) {
                        throw BybitApiException("$message (rate limit)", response.retCode)
                    }
                    throw BybitApiException(message, response.retCode)
                }
                return response
            } catch (ex: BybitApiException) {
                if (ex.retCode == 10006 && attempt < maxRetries) {
                    delay(delayMs)
                    delayMs = (delayMs * 2).coerceAtMost(Duration.ofSeconds(5).toMillis())
                    attempt += 1
                    continue
                }
                throw ex
            } catch (ex: WebClientResponseException) {
                val shouldRetry = ex.statusCode.value() == 429 || ex.statusCode.value() == 403
                if (shouldRetry && attempt < maxRetries) {
                    delay(delayMs)
                    delayMs = (delayMs * 2).coerceAtMost(Duration.ofSeconds(5).toMillis())
                    attempt += 1
                    continue
                }
                throw ex
            }
        }
    }

    private suspend fun authHeadersForPost(payload: String): HttpHeaders {
        val timestamp = timeSync.currentTimestampMs().toString()
        val recvWindow = properties.recvWindowMs.toString()
        val apiKey = properties.apiKey
        val signature = signer.signPost(properties.apiSecret, timestamp, apiKey, recvWindow, payload)
        return HttpHeaders().apply {
            set("X-BAPI-API-KEY", apiKey)
            set("X-BAPI-TIMESTAMP", timestamp)
            set("X-BAPI-RECV-WINDOW", recvWindow)
            set("X-BAPI-SIGN", signature)
        }
    }

    private suspend fun authHeadersForGet(queryString: String): HttpHeaders {
        val timestamp = timeSync.currentTimestampMs().toString()
        val recvWindow = properties.recvWindowMs.toString()
        val apiKey = properties.apiKey
        val signature = signer.signGet(properties.apiSecret, timestamp, apiKey, recvWindow, queryString)
        return HttpHeaders().apply {
            set("X-BAPI-API-KEY", apiKey)
            set("X-BAPI-TIMESTAMP", timestamp)
            set("X-BAPI-RECV-WINDOW", recvWindow)
            set("X-BAPI-SIGN", signature)
        }
    }

    private fun buildQueryString(params: Map<String, String>): String {
        if (params.isEmpty()) return ""
        return params.entries.joinToString("&") { (key, value) ->
            val encoded = URLEncoder.encode(value, StandardCharsets.UTF_8)
            "$key=$encoded"
        }
    }

    private fun logRateLimitHeaders(headers: HttpHeaders) {
        val remaining = headers.getFirst("X-Bapi-Limit-Status")
        val limit = headers.getFirst("X-Bapi-Limit")
        val reset = headers.getFirst("X-Bapi-Limit-Reset-Timestamp")
        if (remaining != null || limit != null || reset != null) {
            log.debug("Bybit rate limit: remaining={}, limit={}, reset={}", remaining, limit, reset)
        }
    }
}

private fun String.toBigDecimalOrNull(): BigDecimal? =
    try {
        BigDecimal(this)
    } catch (ex: NumberFormatException) {
        null
    }
