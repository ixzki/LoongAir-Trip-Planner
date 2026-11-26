package com.loongair.tripplanner.data.remote

import com.loongair.tripplanner.data.local.AuthPrefs
import com.loongair.tripplanner.data.local.FlightRouteEntity
import com.loongair.tripplanner.data.local.UserSessionState
import com.loongair.tripplanner.util.ChinaClock
import com.loongair.tripplanner.util.suspendRunCatching
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * 长龙航空官方网关客户端（参考 eureka-wu/clairline MIT 协议封装）
 * 直连 https://www.loongair.cn/webhandler.ashx，纯本地端到端通信，无第三方中转。
 */
class LoongAirApi(
    private val authPrefs: AuthPrefs,
    private val rateLimiter: ApiRateLimiter = ApiRateLimiter(),
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(12, TimeUnit.SECONDS)
        .callTimeout(25, TimeUnit.SECONDS)
        .build()
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val pendingVerifyIds = ConcurrentHashMap<String, String>()
    private val refreshMutex = Mutex()
    private val keepaliveMutex = Mutex()

    private data class GatewayResponse(
        val httpCode: Int,
        val errorCode: Int,
        val message: String,
        val resultElement: JsonElement?,
        val setCookieSession: String?
    )

    private fun buildDeviceInfo(): String = "iPhone<iPhone16,2>,ios18.2"

    private fun nowFormatted(): String = ChinaClock.nowFormatted()

    private fun parseResultField(raw: JsonElement?): JsonElement? {
        if (raw == null || raw is JsonNull) return null
        if (raw is JsonObject || raw is JsonArray) return raw
        if (raw is JsonPrimitive && raw.isString) {
            val txt = raw.content.trim()
            if ((txt.startsWith("{") && txt.endsWith("}")) ||
                (txt.startsWith("[") && txt.endsWith("]"))
            ) {
                return runCatching { json.parseToJsonElement(txt) }.getOrNull() ?: raw
            }
        }
        return raw
    }

    private suspend fun callGateway(
        method: String,
        params: JsonObject,
        sessionToken: String = ""
    ): GatewayResponse = withContext(Dispatchers.IO) {
        val isAuthOrVerify = method.contains("VerifyCode") ||
            method.contains("Login") ||
            method.contains("refreshToken")
        val isHeavyFlightSearch = method == "secCardSearchFlights"
        var attempt = 0
        while (true) {
            rateLimiter.acquirePermit(
                isAuthOrVerify = isAuthOrVerify,
                isHeavyFlightSearch = isHeavyFlightSearch
            )
            val cleanToken = sessionToken.removePrefix("SESSION=").trim()
            val payload = buildJsonObject {
                put("AppID", "ClientService")
                put("MethodName", method)
                put("Parameters", params.toString())
            }
            val encoded = URLEncoder.encode(payload.toString(), "UTF-8")
            val body = "paras=$encoded"
                .toRequestBody("application/x-www-form-urlencoded; charset=UTF-8".toMediaType())

            val reqBuilder = Request.Builder()
                .url(GATEWAY_URL)
                .header("Host", "www.loongair.cn")
                .header("Accept", "*/*")
                .header("AppVersion", "5.1.1")
                .header("SubVersion", "1.0.239")
                .header("Accept-Language", "zh-CN,zh-Hans;q=0.9")
                .header("User-Agent", APP_USER_AGENT)
                .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                .post(body)

            if (cleanToken.isNotEmpty()) {
                // APP 通道 (SaleChannel=8) 仅以 Cookie: SESSION=... 鉴权，不附带冗余 Authorization 头
                reqBuilder.header("Cookie", "SESSION=$cleanToken")
            }

            val parsed = httpClient.newCall(reqBuilder.build()).execute().use { resp ->
                val setCookies = resp.headers("Set-Cookie")
                val cookieSess = setCookies.firstNotNullOfOrNull { line ->
                    Regex("SESSION=([^;]+)").find(line)?.groupValues?.getOrNull(1)
                }
                val respText = resp.body?.string().orEmpty()
                if (resp.code == 403) {
                    return@use GatewayResponse(403, 403, "登录会话已失效 (403)", null, cookieSess)
                }
                if (!resp.isSuccessful || respText.isBlank()) {
                    return@use GatewayResponse(resp.code, -1, "网络请求异常 (HTTP ${resp.code})", null, cookieSess)
                }
                val root = runCatching { json.parseToJsonElement(respText).jsonObject }.getOrNull()
                    ?: return@use GatewayResponse(resp.code, -1, "接口返回非 JSON 数据", null, cookieSess)
                val errCode = root["ErrorCode"]?.jsonPrimitive?.intOrNull ?: -1
                val message = root["message"]?.jsonPrimitive?.contentOrNull.orEmpty()
                val grant = (params["scgTid"] ?: params["secCardGrantTid"])?.primitiveStringOrNull().orEmpty()
                val msg = if (grant.isEmpty()) message else message.replace(grant, UserSessionState.maskGrantId(grant))
                val resultElem = parseResultField(root["Result"])
                GatewayResponse(resp.code, errCode, msg, resultElem, cookieSess)
            }

            if (!isAuthOrVerify && ApiRateLimiter.isThrottleSignal(parsed.httpCode, parsed.errorCode, parsed.message)) {
                val backoffMs = rateLimiter.recordThrottleAndComputeBackoffMs()
                if (attempt == 0) {
                    attempt++
                    delay(backoffMs)
                    continue
                }
                throw ApiRateLimitException(
                    parsed.message.ifBlank { "请求过于频繁触发长龙风控保护，已自动暂停，请稍后再试" }
                )
            }

            if (parsed.errorCode == 0) {
                rateLimiter.onSuccess()
            }
            return@withContext parsed
        }
        @Suppress("UNREACHABLE_CODE")
        error("Unreachable")
    }

    private suspend fun callAuthenticated(
        method: String,
        params: JsonObject,
        allowRetryRefresh: Boolean = true,
        generation: Long
    ): JsonElement {
        val session = authPrefs.current()
        if (!session.isLoggedIn || session.sessionToken.isBlank() || session.sessionGeneration != generation) {
            throw SessionExpiredException("请先在「我的」页面登录长龙会员后再查询实时余票")
        }
        val usedToken = session.sessionToken
        val resp = callGateway(method, params, usedToken)
        if (authPrefs.current().sessionGeneration != generation) throw SessionExpiredException("账号已切换，请重新查询")
        if ((resp.httpCode == 403 || resp.errorCode == 100001) && allowRetryRefresh) {
            if (tryRefreshSession(session)) {
                return callAuthenticated(method, params, allowRetryRefresh = false, generation = generation)
            }
            throw SessionExpiredException("长龙会员会话已过期，请在「我的」页重新验证码登录")
        }
        if (resp.errorCode != 0) {
            val baseMsg = resp.message.ifBlank { "长龙接口返回错误 (${resp.errorCode})" }
            if (baseMsg.contains("次卡") || baseMsg.contains("此卡") || baseMsg.contains("scgTid", ignoreCase = true)) {
                throw IllegalStateException(
                    "$baseMsg（请到「我的」重新同步卡包或查看诊断）"
                )
            }
            throw IllegalStateException(baseMsg)
        }
        // 回写服务端滚动下发的新 SESSION Cookie 并更新最近活跃时间，防止旧凭证过期
        authPrefs.touchSessionActivity(
            usedToken = usedToken,
            rotatedSession = resp.setCookieSession,
            updatedAt = nowFormatted(),
            expectedGeneration = generation
        )
        return resp.resultElement ?: JsonNull
    }

    suspend fun sendSmsCode(mobile: String, yidunValidate: String): SmsSendResult {
        val m = mobile.trim()
        if (!Regex("^1[3-9]\\d{9}$").matches(m)) {
            return SmsSendResult(false, message = "请输入正确的 11 位中国大陆手机号")
        }
        val v = yidunValidate.trim()
        if (v.isEmpty()) {
            return SmsSendResult(false, message = "请先完成网易易盾安全验证")
        }
        val checkParam = if (v.startsWith("CN")) v else "CN31_$v"
        val params = buildJsonObject {
            put("checkParam", checkParam)
            put("verifyCodeType", 9)
            put("mobile", m)
            put("userLoginLog", buildJsonObject {
                put("channel", "APP")
                put("deviceInfo", buildDeviceInfo())
                put("clientInfo", "App")
                put("appVersion", "1.0.239")
            })
            put("SaleChannel", 8)
            put("saleChannel", 8)
            put("language", "zh-cn")
        }
        return try {
            val resp = callGateway("/user/clientNoLogin/channel/getVerifyCode", params)
            if (resp.errorCode == 0) {
                val obj = resp.resultElement as? JsonObject
                val verifyId = obj?.get("verifyId")?.jsonPrimitive?.contentOrNull
                    ?: obj?.get("Base64SessionID")?.jsonPrimitive?.contentOrNull
                    ?: resp.setCookieSession
                    ?: ""
                if (verifyId.isNotBlank()) {
                    pendingVerifyIds[m] = verifyId
                }
                SmsSendResult(true, verifyId = verifyId, message = "短信验证码已发送，请注意查收")
            } else {
                SmsSendResult(false, message = "${resp.message.ifBlank { "发送失败" }} (${resp.errorCode})")
            }
        } catch (c: CancellationException) {
            throw c
        } catch (e: Exception) {
            SmsSendResult(false, message = "请求失败：${e.message}")
        }
    }

    suspend fun loginBySmsCode(mobile: String, smsCode: String, verifyId: String): LoginResult {
        val generation = authPrefs.current().sessionGeneration
        val m = mobile.trim()
        val c = smsCode.trim()
        if (!Regex("^1[3-9]\\d{9}$").matches(m) || c.length < 4) {
            return LoginResult(false, message = "请输入正确的手机号与短信验证码")
        }
        val effectiveVerifyId = verifyId.ifBlank { pendingVerifyIds[m].orEmpty() }
        val params = buildJsonObject {
            put("channelType", 4)
            put("mobile", m)
            put("code", c)
            put("verifyId", effectiveVerifyId)
            put("userLoginLog", buildJsonObject {
                put("channel", "APP")
                put("deviceInfo", buildDeviceInfo())
                put("clientInfo", "App")
                put("appVersion", "1.0.239")
            })
            put("SaleChannel", 8)
            put("saleChannel", 8)
            put("language", "zh-cn")
        }
        return try {
            val resp = callGateway("/user/clientNoLogin/channel/mobileCodeLogin", params)
            if (resp.errorCode != 0) {
                return LoginResult(false, message = "${resp.message.ifBlank { "验证码错误或已过期" }} (${resp.errorCode})")
            }
            val obj = resp.resultElement as? JsonObject ?: buildJsonObject { }
            val sessionId = obj["loongairToken"]?.jsonPrimitive?.contentOrNull
                ?: obj["Base64SessionID"]?.jsonPrimitive?.contentOrNull
                ?: resp.setCookieSession
                ?: ""
            if (sessionId.isBlank()) {
                return LoginResult(false, message = "登录成功但未返回 Session 凭据")
            }
            val refreshToken = obj["refreshToken"]?.jsonPrimitive?.contentOrNull
                ?: obj["token"]?.jsonPrimitive?.contentOrNull
                ?: ""
            val encUidFromLogin = obj["encryptedUserId"]?.jsonPrimitive?.contentOrNull.orEmpty()

            syncAndPersistProfile(
                sessionToken = sessionId,
                refreshToken = refreshToken,
                fallbackMobile = m,
                fallbackEncUid = encUidFromLogin,
                manualScgTid = "",
                expectedGeneration = generation
            )
        } catch (c: CancellationException) {
            throw c
        } catch (e: Exception) {
            LoginResult(false, message = "登录异常：${e.message}")
        }
    }

    suspend fun importManualCredentials(sessionToken: String, manualScgTid: String = ""): LoginResult {
        val generation = authPrefs.current().sessionGeneration
        val cleanToken = sessionToken.removePrefix("SESSION=").trim()
        if (cleanToken.length < 10) {
            return LoginResult(false, message = "请输入有效的 SESSION 凭证")
        }
        return try {
            syncAndPersistProfile(
                sessionToken = cleanToken,
                refreshToken = "",
                fallbackMobile = "",
                fallbackEncUid = "",
                manualScgTid = manualScgTid.trim(),
                expectedGeneration = generation
            )
        } catch (c: CancellationException) {
            throw c
        } catch (e: Exception) {
            LoginResult(false, message = "校验凭证失败：${e.message}")
        }
    }

    /**
     * 无需重新登录，直接刷新当前账号的卡包列表与畅飞卡授权 ID
     */
    suspend fun resyncUserCards(): CardSyncResult {
        val session = authPrefs.current()
        if (!session.isLoggedIn || session.sessionToken.isBlank()) {
            return CardSyncResult(false, "请先登录长龙会员")
        }
        var token = session.sessionToken
        var probe = discoverUnionCards(token)
        if (probe.authExpired && tryRefreshSession(session)) {
            val refreshed = authPrefs.current()
            if (refreshed.sessionGeneration != session.sessionGeneration) return CardSyncResult(false, "账号已切换，请重新同步")
            token = refreshed.sessionToken
            probe = discoverUnionCards(token)
        }
        if (probe.authExpired) {
            return CardSyncResult(false, "会话已过期，请重新验证码登录", diagnostic = probe.diagnostic)
        }
        val best = pickBestCard(probe.cards, currentTid = session.secCardGrantTid)
        val finalTid = best?.grantTid ?: session.secCardGrantTid
        val finalName = best?.cardName ?: session.cardName
        val saved = authPrefs.updateCards(
            expectedSession = session,
            cards = probe.cards,
            selectedTid = finalTid,
            selectedName = finalName,
            diagnostic = probe.diagnostic,
            updatedAt = nowFormatted()
        )
        if (!saved) return CardSyncResult(false, "账号或畅飞卡已切换，请重新同步")
        val msg = when {
            probe.cards.isEmpty() -> "未在卡包中检测到可用畅飞卡/次卡，请点「查看卡包诊断」排查字段结构"
            best != null -> "同步成功！检测到 ${probe.cards.size} 张卡，当前选用：${best.cardName}"
            else -> "同步完成，共检测到 ${probe.cards.size} 张卡"
        }
        return CardSyncResult(
            ok = true,
            message = msg,
            cards = probe.cards,
            selectedCard = best,
            diagnostic = probe.diagnostic
        )
    }

    private data class CardDiscoveryReport(
        val cards: List<DetectedCardInfo>,
        val psgNameFallback: String,
        val diagnostic: String,
        val authExpired: Boolean = false
    )

    private suspend fun discoverUnionCards(sessionToken: String): CardDiscoveryReport {
        val diagLines = mutableListOf<String>()
        val foundMap = linkedMapOf<String, DetectedCardInfo>()
        var psgNameFallback = ""

        val queries = listOf(
            "types=0,status=[0,1]" to buildJsonObject {
                put("cardTypes", 0)
                put("cardStatus", buildJsonArray { add(JsonPrimitive(0)); add(JsonPrimitive(1)) })
                put("SaleChannel", 8)
                put("saleChannel", 8)
                put("language", "zh-cn")
            },
            "types=0,status=[0]" to buildJsonObject {
                put("cardTypes", 0)
                put("cardStatus", buildJsonArray { add(JsonPrimitive(0)) })
                put("SaleChannel", 8)
                put("saleChannel", 8)
                put("language", "zh-cn")
            },
            "types=1,status=[0,1]" to buildJsonObject {
                put("cardTypes", 1)
                put("cardStatus", buildJsonArray { add(JsonPrimitive(0)); add(JsonPrimitive(1)) })
                put("SaleChannel", 8)
                put("saleChannel", 8)
                put("language", "zh-cn")
            },
            "types=2,status=[0,1]" to buildJsonObject {
                put("cardTypes", 2)
                put("cardStatus", buildJsonArray { add(JsonPrimitive(0)); add(JsonPrimitive(1)) })
                put("SaleChannel", 8)
                put("saleChannel", 8)
                put("language", "zh-cn")
            }
        )

        for ((idx, pair) in queries.withIndex()) {
            val (label, params) = pair
            // 如果前两次查询已经找到真实次卡 (scgTid)，跳过后续探测减少请求
            if (idx >= 2 && foundMap.values.any { it.isSecCard }) break

            val resp = suspendRunCatching { callGateway("cardToC_getUnionCardList", params, sessionToken) }.getOrNull()
            if (resp == null) {
                diagLines.add("[$label] 请求异常")
                continue
            }
            if (resp.httpCode == 403 || resp.errorCode == 100001) {
                return CardDiscoveryReport(emptyList(), "", "会话过期 (${resp.errorCode})", authExpired = true)
            }
            val rootObj = resp.resultElement as? JsonObject
            if (resp.errorCode != 0 || rootObj == null) {
                diagLines.add("[$label] err=${resp.errorCode} msg=${resp.message}")
                continue
            }

            val cardList = (rootObj["cardInfoList"] as? JsonArray)
                ?: (rootObj["secCardList"] as? JsonArray)
                ?: (rootObj["list"] as? JsonArray)
                ?: JsonArray(emptyList())

            diagLines.add("[$label] err=0 keys=${rootObj.keys.joinToString(",")} listSize=${cardList.size}")

            cardList.forEachIndexed { itemIdx, elem ->
                val itemObj = elem as? JsonObject ?: return@forEachIndexed
                val itemKeys = itemObj.keys.joinToString(",")
                val nestedDesc = mutableListOf<String>()
                extractCardsFromObject(
                    obj = itemObj,
                    path = "item[$itemIdx]",
                    outMap = foundMap,
                    nestedSummaries = nestedDesc,
                    onPsgName = { if (psgNameFallback.isBlank()) psgNameFallback = it }
                )
                diagLines.add("  #$itemIdx keys={$itemKeys} ${nestedDesc.joinToString(" | ")}")
            }
        }

        // 若 cardToC_getUnionCardList 仍未拿到任何真次卡，尝试单独探测次卡常用列表方法
        if (foundMap.values.none { it.isSecCard }) {
            val secFallbackMethods = listOf(
                "/coupon/client/getUserSecCardList",
                "getUserSecCardList",
                "cardToC_getSecCardList"
            )
            val simpleParams = buildJsonObject {
                put("status", 0)
                put("SaleChannel", 8)
                put("saleChannel", 8)
                put("language", "zh-cn")
            }
            for (method in secFallbackMethods) {
                val resp = suspendRunCatching { callGateway(method, simpleParams, sessionToken) }.getOrNull() ?: continue
                if (resp.httpCode == 403 || resp.errorCode == 100001) {
                    return CardDiscoveryReport(emptyList(), "", "会话过期", authExpired = true)
                }
                if (resp.errorCode == 0 && resp.resultElement != null) {
                    val nestedDesc = mutableListOf<String>()
                    extractCardsRecursively(resp.resultElement, method, foundMap, nestedDesc) {
                        if (psgNameFallback.isBlank()) psgNameFallback = it
                    }
                    diagLines.add("[$method] err=0 ${nestedDesc.joinToString(" | ")}")
                    if (foundMap.values.any { it.isSecCard }) break
                } else {
                    diagLines.add("[$method] err=${resp.errorCode} msg=${resp.message.take(40)}")
                }
            }
        }

        return CardDiscoveryReport(
            cards = foundMap.values.toList(),
            psgNameFallback = psgNameFallback,
            diagnostic = diagLines.joinToString("\n")
        )
    }

    private fun extractCardsRecursively(
        elem: JsonElement,
        path: String,
        outMap: MutableMap<String, DetectedCardInfo>,
        nestedSummaries: MutableList<String>,
        onPsgName: (String) -> Unit
    ) {
        when (elem) {
            is JsonObject -> extractCardsFromObject(elem, path, outMap, nestedSummaries, onPsgName)
            is JsonArray -> elem.forEachIndexed { idx, child ->
                extractCardsRecursively(child, "$path[$idx]", outMap, nestedSummaries, onPsgName)
            }
            else -> Unit
        }
    }

    private fun extractCardsFromObject(
        obj: JsonObject,
        path: String,
        outMap: MutableMap<String, DetectedCardInfo>,
        nestedSummaries: MutableList<String>,
        onPsgName: (String) -> Unit
    ) {
        val psg = obj["psgName"]?.primitiveStringOrNull()
        if (!psg.isNullOrBlank()) onPsgName(psg)

        val secIdKeys = listOf("scgTid", "secCardGrantTid", "secCardTid", "scGrantTid", "cardGrantTid", "grantTid")
        val fallbackIdKeys = listOf("ppgTid", "plusGrantTid")
        val nameKeys = listOf(
            "secCardName", "scProductName", "spProductName",
            "ppoPlusProductName", "productName", "cardName", "name", "title"
        )

        val cardName = nameKeys.firstNotNullOfOrNull { k ->
            obj[k]?.primitiveStringOrNull()?.takeIf { it.isNotBlank() }
        }
        val statusText = listOf("cardStatusName", "statusName", "status", "cardType", "productType")
            .mapNotNull { k -> obj[k]?.primitiveStringOrNull()?.let { "$k=$it" } }
            .joinToString(",")

        val secHit = secIdKeys.firstNotNullOfOrNull { k ->
            val v = obj[k]?.primitiveStringOrNull()?.takeIf { it.isNotBlank() && it != "0" }
            if (v != null) k to v else null
        }
        val fallbackHit = fallbackIdKeys.firstNotNullOfOrNull { k ->
            val v = obj[k]?.primitiveStringOrNull()?.takeIf { it.isNotBlank() && it != "0" }
            if (v != null) k to v else null
        }

        if (secHit != null) {
            val (field, tid) = secHit
            val info = DetectedCardInfo(
                grantTid = tid,
                cardName = cardName ?: "长龙畅飞卡/次卡 ($field)",
                idField = "$path.$field",
                isSecCard = true,
                statusHint = statusText
            )
            outMap[tid] = info
            nestedSummaries.add("$path[$field=${UserSessionState.maskGrantId(tid)}, name=${info.cardName}]")
        } else if (fallbackHit != null) {
            val (field, tid) = fallbackHit
            val existing = outMap[tid]
            if (existing == null || !existing.isSecCard) {
                val info = DetectedCardInfo(
                    grantTid = tid,
                    cardName = cardName ?: "权益/Plus卡 ($field)",
                    idField = "$path.$field",
                    isSecCard = false,
                    statusHint = statusText
                )
                outMap[tid] = info
                nestedSummaries.add("$path[$field=${UserSessionState.maskGrantId(tid)}, name=${info.cardName}]")
            }
        }

        // 继续检查所有嵌套子对象/子数组（例如 secCard / plusCard / cardDetail / secondaryCard）
        for ((k, v) in obj) {
            if (v is JsonObject && v.isNotEmpty()) {
                val safeKeys = v.keys.filterNot { isSensitiveKey(it) }.take(12).joinToString(",")
                if (nestedSummaries.size < 16) {
                    nestedSummaries.add("$k{$safeKeys}")
                }
                extractCardsFromObject(v, "$path.$k", outMap, nestedSummaries, onPsgName)
            } else if (v is JsonArray && v.isNotEmpty()) {
                v.forEachIndexed { idx, child ->
                    if (child is JsonObject) {
                        extractCardsFromObject(child, "$path.$k[$idx]", outMap, nestedSummaries, onPsgName)
                    }
                }
            }
        }
    }

    private fun isSensitiveKey(key: String): Boolean {
        val lower = key.lowercase()
        return lower.contains("foid") || lower.contains("cert") || lower.contains("idcard") ||
            lower.contains("mobile") || lower.contains("phone") || lower.contains("token") ||
            lower.contains("cookie") || lower.contains("psgname")
    }

    private fun JsonElement.primitiveStringOrNull(): String? {
        val p = this as? JsonPrimitive ?: return null
        if (this is JsonNull) return null
        return p.contentOrNull?.trim()
    }

    private fun pickBestCard(cards: List<DetectedCardInfo>, currentTid: String = ""): DetectedCardInfo? {
        if (cards.isEmpty()) return null
        // 如果之前用户已经选中了列表中的某张真次卡，优先保持
        cards.firstOrNull { it.grantTid == currentTid && it.isSecCard }?.let { return it }
        // 1. 优先选真次卡 (scgTid) 且名称含畅飞/随心飞/次卡
        cards.firstOrNull {
            it.isSecCard && (it.cardName.contains("畅飞") || it.cardName.contains("随心") || it.cardName.contains("次卡") || it.cardName.contains("惠飞"))
        }?.let { return it }
        // 2. 任意真次卡 (scgTid)
        cards.firstOrNull { it.isSecCard }?.let { return it }
        // 3. 退一步选名称含畅飞的卡
        cards.firstOrNull { it.cardName.contains("畅飞") || it.cardName.contains("次卡") }?.let { return it }
        // 4. 兜底第一张
        return cards.first()
    }

    private suspend fun syncAndPersistProfile(
        sessionToken: String,
        refreshToken: String,
        fallbackMobile: String,
        fallbackEncUid: String,
        manualScgTid: String,
        expectedGeneration: Long
    ): LoginResult {
        val defaultParams = buildJsonObject {
            put("SaleChannel", 8)
            put("saleChannel", 8)
            put("language", "zh-cn")
        }
        var cardName = "长龙畅飞卡"

        val detailResp = callGateway("sales_user_userDetailInfo", defaultParams, sessionToken)
        if (detailResp.errorCode == 100001 || detailResp.httpCode == 403) {
            return LoginResult(false, message = "该凭证已失效或未通过长龙校验")
        }
        val detail = detailResp.resultElement as? JsonObject
        val profileKeys = listOf("Name", "aliasName", "Mobile", "Foid", "encryptedUserId", "Level")
        if (detailResp.errorCode != 0 || detail == null ||
            (profileKeys.none { !detail[it]?.primitiveStringOrNull().isNullOrBlank() } && (detail["IDInfoList"] as? JsonArray).isNullOrEmpty())) {
            return LoginResult(false, message = "未能核验会员资料，请稍后重试")
        }
        var userName = detail["Name"]?.primitiveStringOrNull()
            ?: detail["aliasName"]?.primitiveStringOrNull() ?: "长龙会员"
        val mobile = detail["Mobile"]?.primitiveStringOrNull() ?: fallbackMobile
        val idInfo = (detail["IDInfoList"] as? JsonArray)?.firstOrNull() as? JsonObject
        val certNumber = detail["Foid"]?.primitiveStringOrNull().orEmpty()
            .ifBlank { idInfo?.get("foid")?.primitiveStringOrNull().orEmpty() }
        val encryptedUserId = detail["encryptedUserId"]?.primitiveStringOrNull() ?: fallbackEncUid
        val memberLevel = when (detail["Level"]?.primitiveStringOrNull()) {
            "2" -> "银卡会员"
            "3" -> "金卡会员"
            "4" -> "白金卡会员"
            else -> "乘云会员"
        }

        val effectiveToken = detailResp.setCookieSession
            ?.removePrefix("SESSION=")
            ?.trim()
            ?.takeIf { it.length > 8 }
            ?: sessionToken

        val cardReport = discoverUnionCards(effectiveToken)
        if (cardReport.authExpired) return LoginResult(false, message = "会话已过期，请重新登录")
        if (userName == "长龙会员" && cardReport.psgNameFallback.isNotBlank()) {
            userName = cardReport.psgNameFallback
        }
        val bestCard = pickBestCard(cardReport.cards, currentTid = manualScgTid)
        val scgTid = manualScgTid.ifBlank { bestCard?.grantTid.orEmpty() }
        if (bestCard != null) {
            cardName = bestCard.cardName
        }

        val saved = authPrefs.saveSession(
            token = effectiveToken,
            refreshToken = refreshToken,
            userName = userName,
            mobile = mobile,
            memberLevel = memberLevel,
            cardName = cardName,
            secCardGrantTid = scgTid,
            encryptedUserId = encryptedUserId,
            certNumber = certNumber,
            updatedAt = nowFormatted(),
            detectedCards = cardReport.cards,
            cardDiagnostic = cardReport.diagnostic,
            expectedGeneration = expectedGeneration
        )
        if (!saved) return LoginResult(false, message = "登录状态已变更，请重新登录")
        pendingVerifyIds.remove(mobile)

        val scgHint = when {
            scgTid.isBlank() -> "（未自动识别到畅飞卡 ID，请点击下方「查看卡包诊断」或手动填写）"
            cardReport.cards.size > 1 -> "（共识别 ${cardReport.cards.size} 张卡，可在下方切换）"
            else -> ""
        }
        return LoginResult(
            ok = true,
            userName = userName,
            cardName = cardName,
            detectedCardsCount = cardReport.cards.size,
            message = "欢迎回来，$userName！已绑定 $cardName$scgHint"
        )
    }

    /**
     * 轻量级会话保活：向长龙网关发送 sales_user_userDetailInfo 心跳，
     * 刷新服务端滑动 TTL 并回写新 Set-Cookie；遇到 403/100001 自动尝试 refreshToken 静默续期。
     * 网络波动或风控限流时返回 TransientError，绝不误判为登录失效。
     */
    suspend fun keepaliveSession(force: Boolean = false): KeepaliveResult = keepaliveMutex.withLock {
        val s = authPrefs.current()
        if (!s.isLoggedIn || s.sessionToken.isBlank()) {
            return@withLock KeepaliveResult.NotLoggedIn
        }
        val now = System.currentTimeMillis()
        if (!force && s.lastKeepaliveAtMillis > 0L && (now - s.lastKeepaliveAtMillis) < KEEPALIVE_MIN_INTERVAL_MS) {
            return@withLock KeepaliveResult.SkippedFresh
        }
        val usedToken = s.sessionToken
        val params = buildJsonObject {
            put("SaleChannel", 8)
            put("saleChannel", 8)
            put("language", "zh-cn")
        }
        val resp = try {
            callGateway("sales_user_userDetailInfo", params, usedToken)
        } catch (c: CancellationException) {
            throw c
        } catch (e: Exception) {
            return@withLock KeepaliveResult.TransientError(e.message ?: "network_error")
        }

        if (resp.errorCode == 0) {
            val rotated = authPrefs.touchSessionActivity(
                usedToken = usedToken,
                rotatedSession = resp.setCookieSession,
                updatedAt = nowFormatted(),
                nowMillis = System.currentTimeMillis(),
                expectedGeneration = s.sessionGeneration
            )
            return@withLock KeepaliveResult.Alive(tokenRotated = rotated)
        }

        if (resp.httpCode == 403 || resp.errorCode == 100001) {
            return@withLock if (tryRefreshSession(s)) {
                KeepaliveResult.Refreshed
            } else {
                KeepaliveResult.Expired
            }
        }

        KeepaliveResult.TransientError("errorCode=${resp.errorCode}")
    }

    private suspend fun tryRefreshSession(failedSession: UserSessionState): Boolean = refreshMutex.withLock {
        val s = authPrefs.current()
        // 并发请求同时遇到 403 时，若其他协程已刷新成功则直接复用新 Token，防止 refreshToken 二次轮换失效
        if (!s.isLoggedIn || s.sessionGeneration != failedSession.sessionGeneration) return@withLock false
        if (s.sessionToken != failedSession.sessionToken) {
            return@withLock true
        }
        if (s.refreshToken.isBlank()) return@withLock false
        val params = buildJsonObject {
            put("refreshToken", s.refreshToken)
            put("token", s.sessionToken)
            put("userId", s.mobile)
            put("encryptedUserId", s.encryptedUserId)
            put("userLoginLog", buildJsonObject {
                put("channel", "APP")
                put("deviceInfo", buildDeviceInfo())
                put("clientInfo", "App")
                put("appVersion", "1.0.239")
            })
            put("SaleChannel", 8)
            put("saleChannel", 8)
            put("language", "zh-cn")
        }
        val methods = listOf(
            "/user/clientNoLogin/channel/refreshToken",
            "/user/client/channel/refreshToken"
        )
        for (m in methods) {
            val resp = suspendRunCatching { callGateway(m, params, s.sessionToken) }.getOrNull() ?: continue
            if (resp.errorCode == 0) {
                val obj = resp.resultElement as? JsonObject
                val newToken = obj?.get("Base64SessionID")?.primitiveStringOrNull()
                    ?: obj?.get("loongairToken")?.primitiveStringOrNull()
                    ?: obj?.get("token")?.primitiveStringOrNull()
                    ?: resp.setCookieSession
                    ?: ""
                val newRt = obj?.get("refreshToken")?.primitiveStringOrNull() ?: s.refreshToken
                if (newToken.length > 8) {
                    return@withLock authPrefs.updateTokens(s, newToken, newRt, nowFormatted())
                }
            }
        }
        false
    }

    private fun requireSecCardGrantTid(session: UserSessionState): String {
        val tid = session.secCardGrantTid.trim()
        if (tid.isBlank()) {
            throw IllegalStateException("未获取到畅飞卡授权 ID (scgTid)，请到「我的」点击「重新同步卡包」并选择你的畅飞卡")
        }
        return tid
    }

    /**
     * 拉取航线未来 60 天放票日历（以北京时间 Asia/Shanghai 为基准）
     * 返回 Map<YYYY-MM-DD, status> (1=可兑, 0=已兑完, -1=未开放, -2=不适用, -3=已截止, -4=无航班)
     */
    suspend fun getCalendar(fromCity: String, toCity: String): Map<String, Int> {
        val s = authPrefs.current()
        val tid = requireSecCardGrantTid(s)
        val params = buildJsonObject {
            put("scgTid", tid)
            put("departureCode", CityIataMapper.toApiIata(fromCity))
            put("arriveCode", CityIataMapper.toApiIata(toCity))
            put("SaleChannel", 8)
            put("saleChannel", 8)
            put("language", "zh-cn")
        }
        val resObj = callAuthenticated("getSecCardRedeemedCalendar", params, generation = s.sessionGeneration) as? JsonObject
        val rawMap = resObj?.get("calenderMap") as? JsonObject ?: error("官方放票日历缺失，请稍后重试")
        val today = ChinaClock.today()
        val maxDate = today.plusDays(180)
        val todayStr = today.toString()
        val maxDateStr = maxDate.toString()
        return buildMap {
            for ((dateKey, value) in rawMap) {
                check(runCatching { LocalDate.parse(dateKey) }.isSuccess) { "官方放票日历日期格式异常" }
                if (dateKey in todayStr..maxDateStr) {
                    val code = (value as? JsonPrimitive)?.intOrNull ?: error("官方放票日历状态格式异常")
                    put(dateKey, code)
                }
            }
        }
    }

    /**
     * 查询指定航线、指定日期的具体班次和畅飞卡剩余张数
     */
    suspend fun searchFlights(
        fromCity: String,
        toCity: String,
        dateStr: String,
        knownRoutes: List<FlightRouteEntity>
    ): List<LiveFlightSeat> {
        val s = authPrefs.current()
        val tid = requireSecCardGrantTid(s)
        val date = LocalDate.parse(dateStr)
        val fromCode = CityIataMapper.toApiIata(fromCity)
        val toCode = CityIataMapper.toApiIata(toCity)
        val params = buildJsonObject {
            put("secCardGrantTid", tid)
            put("encryptedUserId", s.encryptedUserId)
            put("certNumber", s.certNumber)
            put("mobile", s.mobile)
            put("disabilityIDType", 1)
            put("ifNonCompany", false)
            put("tripType", 1)
            put("directionType", "")
            put("bookingScenario", 1)
            put("departureAirportCode", fromCode)
            put("arriveAirportCode", toCode)
            put("departureTime", "${dateStr}T00:00:00")
            put("SaleChannel", 8)
            put("saleChannel", 8)
            put("language", "zh-cn")
        }
        val resObj = callAuthenticated("secCardSearchFlights", params, generation = s.sessionGeneration) as? JsonObject
            ?: error("官方余票查询结果缺失，请稍后重试")
        val flightInfos = resObj["flightInfos"] as? JsonArray ?: error("官方航班列表缺失，请稍后重试")

        val result = mutableListOf<LiveFlightSeat>()
        for (item in flightInfos) {
            val fi = item as? JsonObject ?: error("官方航班记录格式异常")
            val depRaw = fi["departureTime"]?.primitiveStringOrNull().orEmpty()
            val arrRaw = fi["arriveTime"]?.primitiveStringOrNull().orEmpty()
            check(depRaw.length >= 16 && arrRaw.length >= 16) { "官方航班起降时间格式异常" }
            val depDatePart = depRaw.substring(0, 10)
            val arrDatePart = arrRaw.substring(0, 10)
            check(depDatePart == dateStr && (arrDatePart == dateStr || arrDatePart == date.plusDays(1).toString())) {
                "官方航班日期与查询不符"
            }
            val dep = depRaw.substring(11, 16)
            val arrHm = arrRaw.substring(11, 16)
            val depMin = parseMinutes(dep) ?: error("官方起飞时间格式异常")
            val arrMin = parseMinutes(arrHm) ?: error("官方到达时间格式异常")
            val isNextDay = arrDatePart != depDatePart || arrMin < depMin
            val arr = if (isNextDay) "$arrHm+1" else arrHm
            val durMins = (if (isNextDay) arrMin + 24 * 60 - depMin else arrMin - depMin).coerceAtLeast(0)
            val computedDuration = if (durMins > 0) "${durMins / 60}h${durMins % 60}m" else ""

            val isVia = fi["stopOver"]?.jsonPrimitive?.booleanOrNull == true ||
                (fi["stopOver"]?.jsonPrimitive?.intOrNull ?: 0) > 0
            val stopCity = listOf("stopCityName", "stopCity", "viaCityName", "viaCity")
                .firstNotNullOfOrNull { k -> fi[k]?.primitiveStringOrNull()?.takeIf { it.isNotBlank() } }
            val officialStopover = when {
                !stopCity.isNullOrBlank() -> "经停$stopCity"
                isVia -> "经停"
                else -> "直飞"
            }
            val officialAircraft = fi["aircraftType"]?.primitiveStringOrNull()
                ?.takeIf { it.isNotBlank() } ?: "A320"
            val floatPrice = fi["floatPrice"]?.jsonPrimitive?.intOrNull
                ?: resObj["floatPrice"]?.jsonPrimitive?.intOrNull
            val numberFields = listOf(
                "flightNo", "flightNumber", "fltNo", "flightNum",
                "carrierFlightNo", "marketFlightNo"
            ).mapNotNull { fi[it] }.filter { it != JsonNull && (it as? JsonPrimitive)?.content?.isBlank() != true }
            val flightNo = if (numberFields.isNotEmpty()) {
                numberFields.firstNotNullOfOrNull { field -> field.primitiveStringOrNull()?.let { decodeFlightNo(it, floatPrice) } }
                    ?: error("官方航班号无法识别，请稍后重试")
            } else {
                val scheduled = knownRoutes.filter {
                    it.fromIata == fromCode && it.toIata == toCode && it.depTime == dep && it.arrTime == arr &&
                        date.dayOfWeek.value.toString() in it.frequency
                }
                val numbers = scheduled.map { decodeFlightNo(it.flightNo) }.distinct()
                numbers.singleOrNull() ?: error("官方未提供航班号，现有时刻无法唯一确认")
            }

            val rawCabins = fi["cabinInfos"]
            check(rawCabins == null || rawCabins is JsonArray || rawCabins is JsonNull) { "官方舱位列表格式异常" }
            val cabins = rawCabins as? JsonArray
            if (cabins.isNullOrEmpty()) {
                result.add(
                    LiveFlightSeat(
                        flightNo = flightNo,
                        depTime = dep,
                        arrTime = arr,
                        duration = computedDuration,
                        aircraft = officialAircraft,
                        stopover = officialStopover,
                        redeemPrice = 0.0,
                        secCardLeft = 0,
                        normalLeft = 0,
                        cabinCode = "M"
                    )
                )
                continue
            }
            for (cItem in cabins) {
                val c = cItem as? JsonObject ?: error("官方舱位记录格式异常")
                result.add(
                    LiveFlightSeat(
                        flightNo = flightNo,
                        depTime = dep,
                        arrTime = arr,
                        duration = computedDuration,
                        aircraft = officialAircraft,
                        stopover = officialStopover,
                        redeemPrice = c["ticketPrice"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                        secCardLeft = c["secCardBaseLeftCount"]?.jsonPrimitive?.intOrNull ?: 0,
                        normalLeft = c["leftCount"]?.jsonPrimitive?.intOrNull ?: 0,
                        cabinCode = c["cabinCode"]?.primitiveStringOrNull() ?: "M"
                    )
                )
            }
        }
        return result.sortedBy { it.depTime }
    }

    /**
     * 拉取长龙官方国内出发城市与机场目录（首次登录后用于点选或拼音/三字码筛选首个同步城市）：
     * 优先尝试畅飞卡专属出发机场接口，再自动回退至长龙官方全局国内城市/机场目录接口
     * （/fare/clientNoLogin/getCityInfosByGroupHmt 与 GetAirportInfosByGroup）。
     */
    suspend fun getOfficialDepartureAirports(): List<OfficialAirportEndpoint> {
        val session = authPrefs.current()
        val tid = session.secCardGrantTid.trim()
        val baseParams = buildJsonObject {
            put("SaleChannel", 8)
            put("saleChannel", 8)
            put("language", "zh-cn")
        }
        val methods = buildList {
            if (tid.isNotBlank()) {
                val secParams = buildJsonObject {
                    put("scgTid", tid)
                    put("SaleChannel", 8)
                    put("saleChannel", 8)
                    put("language", "zh-cn")
                }
                add("/coupon/client/getSecCardDepartureAirport" to secParams)
                add("/coupon/client/getSecCardDepAirport" to secParams)
                add("/coupon/client/getSecCardDepartAirport" to secParams)
            }
            add("/fare/clientNoLogin/getCityInfosByGroupHmt" to baseParams)
            add("GetAirportInfosByGroup" to baseParams)
        }
        var lastError: Throwable? = null
        var confirmedEmpty = false
        for ((method, params) in methods) {
            val result = suspendRunCatching {
                val payload = if (session.isLoggedIn) {
                    callAuthenticated(method, params, generation = session.sessionGeneration)
                } else {
                    val gw = callGateway(method, params)
                    check(gw.errorCode == 0) { "官方机场目录查询失败" }
                    gw.resultElement ?: JsonNull
                }
                parseAirportEndpoints(payload)
            }
            val parsed = result.getOrNull()
            if (parsed == null) {
                lastError = result.exceptionOrNull()
                continue
            }
            if (parsed.isNotEmpty()) return parsed
            confirmedEmpty = true
        }
        if (confirmedEmpty) return emptyList()
        throw IllegalStateException("未能获取完整的官方出发机场目录", lastError)
    }

    /**
     * 拉取当前畅飞卡在某出发城市允许兑换的官方目的地城市与机场明细
     */
    suspend fun getOfficialArrivalAirports(fromCity: String): List<OfficialAirportEndpoint> {
        val session = authPrefs.current()
        val tid = requireSecCardGrantTid(session)
        val params = buildJsonObject {
            put("scgTid", tid)
            put("fromCode", CityIataMapper.toApiIata(fromCity))
            put("SaleChannel", 8)
            put("saleChannel", 8)
            put("language", "zh-cn")
        }
        val resElem = callAuthenticated("/coupon/client/getSecCardArriveAirport", params, generation = session.sessionGeneration)
        return parseAirportEndpoints(resElem)
    }

    internal fun parseAirportEndpoints(root: JsonElement): List<OfficialAirportEndpoint> {
        val byCode = linkedMapOf<String, OfficialAirportEndpoint>()
        fun malformed(): Nothing = throw IllegalStateException("官方机场目录数据不完整，请稍后重试")
        val collectionKeys = setOf("list", "data", "airports", "cities", "citys", "airportlist", "citylist", "rows", "items", "records")
        var parsedRows = 0
        fun isSkippedRegionKey(lower: String): Boolean =
            lower.startsWith("int") || lower.contains("international") || lower.contains("hmt")

        fun checkPaginationMarkers(obj: JsonObject) {
            val more = obj["hasMore"] ?: obj["hasNext"] ?: obj["hasNextPage"]
            val last = obj["isLastPage"] ?: obj["lastPage"]
            if (more?.primitiveStringOrNull() in setOf("true", "1") ||
                last?.primitiveStringOrNull() in setOf("false", "0") ||
                (obj["totalPages"]?.primitiveStringOrNull()?.toIntOrNull() ?: 1) > 1 ||
                (obj["pageNum"]?.primitiveStringOrNull()?.toIntOrNull() ?: 1) > 1
            ) {
                malformed()
            }
        }

        fun collect(elem: JsonElement, allowEmpty: Boolean) {
            when (elem) {
                is JsonArray -> {
                    if (elem.isEmpty() && !allowEmpty) malformed()
                    elem.forEach { collect(it, false) }
                }
                is JsonObject -> {
                    checkPaginationMarkers(elem)
                    val listed = elem.entries.filter {
                        val key = it.key.lowercase()
                        it.value is JsonArray && (key in collectionKeys || key in setOf("domairports", "domcitys", "domcities"))
                    }.singleOrNull()
                    val total = (elem["totalCount"] ?: elem["total"])?.primitiveStringOrNull()?.toIntOrNull()
                    if (listed != null && total != null && total != (listed.value as JsonArray).size) malformed()
                    // 仅采集国内机场/城市，排除国际与港澳台列表（畅飞卡仅适用于国内航线）
                    val domAirports = elem["domAirports"]
                    val domCitys = elem["domCitys"] ?: elem["domCities"]
                    if (domAirports != null || domCitys != null) {
                        if (domAirports != null) collect(domAirports, true)
                        if (domCitys != null) collect(domCitys, true)
                        return
                    }
                    val hasAirportShape = listOf(
                        "cityName", "CityName",
                        "airportCode", "AirportCode",
                        "cityCode", "CityCode",
                        "threeCode", "ThreeCode",
                        "airportName", "AirportName",
                        "AirportShortChineseName"
                    ).any { elem.containsKey(it) }

                    if (hasAirportShape) {
                        val rawCity = listOf("cityName", "CityName")
                            .firstNotNullOfOrNull { elem[it]?.primitiveStringOrNull()?.takeIf(String::isNotBlank) }
                            .orEmpty()
                        val code = listOf("airportCode", "AirportCode", "cityCode", "CityCode", "threeCode", "ThreeCode", "code", "Code")
                            .firstNotNullOfOrNull { elem[it]?.primitiveStringOrNull()?.takeIf(String::isNotBlank) }
                            .orEmpty()
                            .uppercase()
                        val airport = listOf(
                            "airportName", "AirportName",
                            "airportShortName", "AirportShortChineseName", "airportShortChineseName",
                            "name", "Name"
                        ).firstNotNullOfOrNull { elem[it]?.primitiveStringOrNull()?.takeIf(String::isNotBlank) }
                            .orEmpty()
                        val pinyin = listOf(
                            "pinyin", "Pinyin",
                            "cityPinYin", "CityPinYin",
                            "CityPhoneticize", "cityPhoneticize",
                            "shortPinYin", "ShortPinYin"
                        ).firstNotNullOfOrNull { elem[it]?.primitiveStringOrNull()?.takeIf(String::isNotBlank) }
                            .orEmpty()

                        val finalCode = code.ifBlank { if (rawCity.isNotBlank()) CityIataMapper.toApiIata(rawCity) else "" }
                        val resolvedName = CityIataMapper.disambiguateCityName(rawCity, finalCode, airport)
                        if (!CityIataMapper.isAsciiIataCode(finalCode) || resolvedName.isBlank()) malformed()
                        parsedRows++
                        val existing = byCode[finalCode]
                        val betterAirport = airport.ifBlank { existing?.airportName.orEmpty() }
                        val betterPinyin = pinyin.ifBlank { existing?.pinyin.orEmpty() }
                        if (existing == null ||
                            (existing.airportName.isBlank() && betterAirport.isNotBlank()) ||
                            (existing.pinyin.isBlank() && betterPinyin.isNotBlank())
                        ) {
                            byCode[finalCode] = OfficialAirportEndpoint(
                                cityName = resolvedName,
                                airportName = betterAirport,
                                airportCode = finalCode,
                                pinyin = betterPinyin
                            )
                        }
                    } else {
                        val knownEntries = elem.entries.filter { (k, _) ->
                            val lower = k.lowercase()
                            !isSkippedRegionKey(lower) && (lower in collectionKeys || k.matches(Regex("[A-Za-z]")))
                        }
                        if (knownEntries.isNotEmpty()) {
                            knownEntries.forEach { (_, child) -> collect(child, true) }
                            elem.entries.forEach { (k, child) ->
                                val lower = k.lowercase()
                                if (!isSkippedRegionKey(lower) && lower !in collectionKeys && !k.matches(Regex("[A-Za-z]")) && child is JsonObject) {
                                    checkPaginationMarkers(child)
                                }
                            }
                        } else {
                            var foundCollection = false
                            for ((k, child) in elem) {
                                val lower = k.lowercase()
                                if (isSkippedRegionKey(lower)) continue
                                if (child is JsonObject || child is JsonArray) {
                                    val before = parsedRows
                                    collect(child, false)
                                    if (parsedRows == before) malformed()
                                    foundCollection = true
                                }
                            }
                            if (!foundCollection) malformed()
                        }
                    }
                }
                else -> malformed()
            }
        }
        collect(root, true)

        // 若接口中仍有其他未知双机场城市（多个不同三字码对应同一 rawCity），自动以机场简称或三字码区分标注
        val grouped = byCode.values.groupBy { it.cityName }
        return byCode.values.map { ep ->
            if ((grouped[ep.cityName]?.size ?: 0) > 1) {
                val shortSuffix = ep.airportName
                    .removePrefix(ep.cityName)
                    .replace("国际机场", "")
                    .replace("机场", "")
                    .trim()
                val label = if (shortSuffix.isNotBlank()) "${ep.cityName}$shortSuffix" else "${ep.cityName}(${ep.airportCode})"
                ep.copy(cityName = label)
            } else ep
        }.onEach { CityIataMapper.registerDynamic(it.cityName, it.airportCode, it.pinyin) }.sortedBy { it.cityName }
    }

    /**
     * 拉取当前畅飞卡在某出发城市允许兑换的官方目的地城市列表
     */
    suspend fun getOfficialDestinations(fromCity: String): List<String> =
        getOfficialArrivalAirports(fromCity).map { it.cityName }

    private fun parseMinutes(hhmm: String): Int? {
        val parts = hhmm.take(5).split(":")
        if (parts.size != 2) return null
        val h = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        if (h !in 0..23 || m !in 0..59) return null
        return h * 60 + m
    }

    companion object {
        private const val GATEWAY_URL = "https://www.loongair.cn/webhandler.ashx"
        private const val KEEPALIVE_MIN_INTERVAL_MS = 15L * 60L * 1000L
        private const val APP_USER_AGENT =
            "Mozilla/5.0 (iPhone; CPU iPhone OS 18_7 like Mac OS X) " +
                "AppleWebKit/605.1.15 (KHTML, like Gecko) Mobile/15E148 " +
                "Provider/finogeeks (iPhone; miniprogram; FinChat; runtimeSdkVersion/2.51.11)"

        fun decodeFlightNo(raw: String, floatPrice: Int? = null): String? =
            FlightNoDecoder.decode(raw, floatPrice)
    }
}
