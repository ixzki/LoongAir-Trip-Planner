package com.loongair.tripplanner

import android.content.SharedPreferences
import com.loongair.tripplanner.data.local.AndroidKeyStoreCipher
import com.loongair.tripplanner.data.local.AuthPrefs
import com.loongair.tripplanner.data.local.FlightRouteEntity
import com.loongair.tripplanner.data.local.StringCipher
import com.loongair.tripplanner.data.remote.DetectedCardInfo
import com.loongair.tripplanner.data.remote.LoongAirApi
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class AuthPrefsEncryptionAndMigrationTest {

    private class JvmAesGcmCipher : StringCipher {
        private val secretKey: SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

        override fun isEncrypted(stored: String): Boolean = stored.startsWith(AndroidKeyStoreCipher.PREFIX)

        override fun encrypt(plain: String): String {
            if (plain.isEmpty()) return ""
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val iv = cipher.iv
            val enc = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
            val combined = ByteArray(1 + iv.size + enc.size)
            combined[0] = iv.size.toByte()
            System.arraycopy(iv, 0, combined, 1, iv.size)
            System.arraycopy(enc, 0, combined, 1 + iv.size, enc.size)
            return AndroidKeyStoreCipher.PREFIX + Base64.getEncoder().encodeToString(combined)
        }

        override fun decrypt(stored: String): String {
            if (stored.isEmpty()) return ""
            if (!isEncrypted(stored)) return stored
            val raw = Base64.getDecoder().decode(stored.removePrefix(AndroidKeyStoreCipher.PREFIX))
            val ivLen = raw[0].toInt() and 0xFF
            val iv = raw.copyOfRange(1, 1 + ivLen)
            val body = raw.copyOfRange(1 + ivLen, raw.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
            return String(cipher.doFinal(body), Charsets.UTF_8)
        }
    }

    private class InMemorySharedPreferences(initial: Map<String, Any?> = emptyMap()) : SharedPreferences {
        val data = LinkedHashMap<String, Any?>(initial)

        override fun getAll(): MutableMap<String, *> = HashMap(data)
        override fun getString(key: String?, defValue: String?): String? = (data[key] as? String) ?: defValue
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? = defValues
        override fun getInt(key: String?, defValue: Int): Int = (data[key] as? Int) ?: defValue
        override fun getLong(key: String?, defValue: Long): Long = (data[key] as? Long) ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float = (data[key] as? Float) ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = (data[key] as? Boolean) ?: defValue
        override fun contains(key: String?): Boolean = data.containsKey(key)

        override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
            private val pending = LinkedHashMap<String, Any?>()
            private var clearFlag = false

            override fun putString(key: String?, value: String?): SharedPreferences.Editor = apply {
                if (key != null) pending[key] = value
            }
            override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor = this
            override fun putInt(key: String?, value: Int): SharedPreferences.Editor = apply { if (key != null) pending[key] = value }
            override fun putLong(key: String?, value: Long): SharedPreferences.Editor = apply { if (key != null) pending[key] = value }
            override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = apply { if (key != null) pending[key] = value }
            override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = apply { if (key != null) pending[key] = value }
            override fun remove(key: String?): SharedPreferences.Editor = apply { if (key != null) pending[key] = null }
            override fun clear(): SharedPreferences.Editor = apply { clearFlag = true }
            override fun commit(): Boolean { apply(); return true }
            override fun apply() {
                if (clearFlag) data.clear()
                for ((k, v) in pending) {
                    if (v == null) data.remove(k) else data[k] = v
                }
            }
        }

        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
    }

    private fun AuthPrefs.login(token: String) = saveSession(
        token, "test-refresh", "测试", "", "", "", "sample-card-grant", "", "", "now"
    )

    private fun apiWithReplies(
        prefs: AuthPrefs, vararg replies: Pair<Int, String>, beforeReply: (Int) -> Unit = {}
    ): LoongAirApi {
        val responses = replies.iterator()
        var index = 0
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            beforeReply(index++)
            val (code, body) = responses.next()
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(code).message("fixture").body(body.toResponseBody()).build()
        }.build()
        return LoongAirApi(prefs, httpClient = client)
    }

    @Test
    fun lateRefreshAndCardSyncCannotRestoreAnEarlierLogin() {
        val prefs = AuthPrefs(InMemorySharedPreferences(), JvmAesGcmCipher())
        prefs.login("first-session")
        val original = prefs.current()
        prefs.clearSession()
        assertFalse(prefs.updateTokens(original, "late-session", "late-refresh", "later"))
        assertFalse(prefs.current().isLoggedIn)
        prefs.login("first-session") // 即使重新登录碰巧得到同一 token，也属于新会话。
        assertFalse(prefs.updateTokens(original, "late-session", "late-refresh", "later"))
        assertFalse(prefs.updateCards(original, emptyList(), "old-grant", "旧卡", "", "later"))
        assertFalse(prefs.touchSessionActivity("first-session", "rotated-late", "later", expectedGeneration = original.sessionGeneration))
        assertEquals("first-session", prefs.current().sessionToken)
        assertEquals("sample-card-grant", prefs.current().secCardGrantTid)
    }

    @Test
    fun encryptionFailureBeforeApplyPreservesTheWholePreviousSession() {
        val backing = InMemorySharedPreferences()
        val delegate = JvmAesGcmCipher()
        val prefs = AuthPrefs(backing, object : StringCipher by delegate {
            override fun encrypt(plain: String): String =
                if (plain == "failed-refresh") "" else delegate.encrypt(plain)
        })
        prefs.login("first-session")
        val before = prefs.current()
        val stored = backing.getAll()
        assertThrows(IllegalStateException::class.java) {
            prefs.updateTokens(before, "newer-session", "failed-refresh", "later")
        }
        assertEquals(stored, backing.getAll())
        assertEquals(before, prefs.current())
    }

    @Test
    fun failedProfileAndExpiredCardResponseDoNotReplaceExistingCredentials() = runBlocking {
        val prefs = AuthPrefs(InMemorySharedPreferences(), JvmAesGcmCipher())
        prefs.login("original-session")
        val before = prefs.current()
        val badResponses = listOf(500 to "", 200 to "unexpected", 200 to """{"ErrorCode":0,"Result":{}}""",
            200 to """{"ErrorCode":0,"Result":{"message":"unexpected"}}""")
        for (response in badResponses) {
            assertFalse(apiWithReplies(prefs, response).importManualCredentials("unverified-session").ok)
            assertEquals(before, prefs.current())
        }
        val api = apiWithReplies(prefs, 200 to """{"ErrorCode":0,"Result":{"Name":"测试"}}""", 403 to "")
        assertFalse(api.importManualCredentials("expired-session").ok)
        assertEquals(before, prefs.current())
    }

    @Test
    fun aRefreshResponseArrivingAfterAnotherLoginCannotReplaceItsCredentials() = runBlocking {
        val prefs = AuthPrefs(InMemorySharedPreferences(), JvmAesGcmCipher())
        prefs.login("original-session")
        val api = apiWithReplies(prefs, 403 to "", 200 to """{"ErrorCode":0,"Result":{"token":"late-session"}}""") { index ->
            if (index == 1) {
                prefs.clearSession()
                prefs.login("replacement-session")
            }
        }
        api.keepaliveSession(force = true)
        assertEquals("replacement-session", prefs.current().sessionToken)
    }

    @Test
    fun rejectedQueriesDoNotExposeTheFullGrantOrInventAnEmptyInventory() = runBlocking {
        val prefs = AuthPrefs(InMemorySharedPreferences(), JvmAesGcmCipher())
        prefs.login("original-session")
        val api = apiWithReplies(prefs, 200 to """{"ErrorCode":12,"message":"此卡 sample-card-grant 不适用"}""",
            200 to """{"ErrorCode":0}""", 200 to """{"ErrorCode":0,"Result":{"unexpected":[]}}""")
        val message = runCatching { api.getCalendar("杭州", "宁波") }.exceptionOrNull()?.message.orEmpty()
        assertTrue("不适用" in message)
        assertFalse("sample-card-grant" in message)
        assertTrue(runCatching { api.getCalendar("杭州", "宁波") }.exceptionOrNull() is IllegalStateException)
        assertTrue(runCatching { api.searchFlights("杭州", "宁波", "2026-12-07", emptyList()) }.exceptionOrNull() is IllegalStateException)
    }

    @Test
    fun malformedOrPartialDirectoryCannotBeTreatedAsACompleteList() {
        val api = apiWithReplies(AuthPrefs(InMemorySharedPreferences(), JvmAesGcmCipher()))
        val row = """{"CityName":"杭州","AirportCode":"HGH"}"""
        listOf("[]", """{"domAirports":[]}""", """{"list":[]}""").forEach {
            assertTrue(api.parseAirportEndpoints(Json.parseToJsonElement(it)).isEmpty())
        }
        val grouped = """{"domCities":{"H":[$row]},"international":[{}]}"""
        assertEquals("HGH", api.parseAirportEndpoints(Json.parseToJsonElement(grouped)).single().airportCode)
        val withSiblingMeta = """{"list":[$row],"pageInfo":{"pageNum":1}}"""
        assertEquals("HGH", api.parseAirportEndpoints(Json.parseToJsonElement(withSiblingMeta)).single().airportCode)
        val withHotEmpty = """{"A":[$row],"hotCities":[]}"""
        assertEquals("HGH", api.parseAirportEndpoints(Json.parseToJsonElement(withHotEmpty)).single().airportCode)
        for (bad in listOf("null", "{}", """{"unknown":[]}""", """{"list":null}""", """[$row,{}]""",
            """{"list":[$row],"total":2}""", """{"list":[$row],"hasMore":true}""",
            """{"list":[$row],"pageInfo":{"hasMore":true}}""", """{"metadata":{"list":[]}}""",
            """{"domAirports":[],"domCities":[{"CityName":"未知城市"}]}""")) {
            assertThrows(IllegalStateException::class.java) { api.parseAirportEndpoints(Json.parseToJsonElement(bad)) }
        }
    }

    @Test
    fun flightNumbersUseTheOfficialResultOrOneExactScheduledFlight() = runBlocking {
        val prefs = AuthPrefs(InMemorySharedPreferences(), JvmAesGcmCipher())
        prefs.login("sample-session")
        val route = FlightRouteEntity(fromCity = "杭州", toCity = "宁波", fromIata = "HGH", toIata = "NGB",
            depTime = "08:00", arrTime = "09:00", frequency = "1", flightNo = "GJ1001", aircraft = "old",
            duration = "1h", stopover = "经停", season = "official")
        suspend fun lookup(number: String?, vararg routes: FlightRouteEntity): String {
            val numberField = number?.let { ",\"flightNo\":\"$it\"" }.orEmpty()
            val json = """{"ErrorCode":0,"Result":{"flightInfos":[{"departureTime":"2026-12-07T08:00","arriveTime":"2026-12-07T09:00"$numberField}]}}"""
            return apiWithReplies(prefs, 200 to json).searchFlights("杭州", "宁波", "2026-12-07", routes.toList()).single().flightNo
        }
        assertEquals("GJ1002", lookup("GJ1002", route))
        assertEquals("GJ1001", lookup(null, route))
        assertTrue(runCatching { lookup("unreadable", route) }.exceptionOrNull() is IllegalStateException)
        assertTrue(runCatching { lookup(null, route, route.copy(flightNo = "GJ1002")) }.exceptionOrNull() is IllegalStateException)
        assertTrue(runCatching { lookup(null, route.copy(frequency = "2")) }.exceptionOrNull() is IllegalStateException)
        assertTrue(runCatching { lookup(null, route.copy(depTime = "08:01")) }.exceptionOrNull() is IllegalStateException)
    }

    @Test
    fun authPrefs_encryptsSensitiveFieldsAndMigratesLegacyPlaintextSeamlessly() {
        // 1. 模拟旧版明文 SharedPreferences
        val rawPrefs = InMemorySharedPreferences(
            mapOf(
                "session_token" to "PLAIN_SESSION_TOKEN_12345",
                "refresh_token" to "PLAIN_REFRESH_999",
                "mobile" to "13800138000",
                "cert_number" to "330106199001011234",
                "sec_card_grant_tid" to "SCG_88888",
                "card_diagnostic" to "[diag] legacy text",
                "home_city" to "杭州",
                "theme_style" to "crimson"
            )
        )
        val cipher = JvmAesGcmCipher()
        val authPrefs = AuthPrefs(rawPrefs, cipher)

        // 升级后内存状态保持已登录且准确还原
        val migratedState = authPrefs.current()
        assertTrue(migratedState.isLoggedIn)
        assertEquals("PLAIN_SESSION_TOKEN_12345", migratedState.sessionToken)
        assertEquals("138****8000", migratedState.maskedMobile)
        assertEquals("330106199001011234", migratedState.certNumber)
        assertEquals("[diag] legacy text", migratedState.cardDiagnostic)
        assertEquals("98****10", com.loongair.tripplanner.data.local.UserSessionState.maskGrantId("9876543210"))
        assertEquals("(空)", com.loongair.tripplanner.data.local.UserSessionState.maskGrantId(""))

        // 底层存储已被自动重加密为 enc:v1: 密文，不再包含明文敏感串
        val storedToken = rawPrefs.getString("session_token", "")!!
        val storedCert = rawPrefs.getString("cert_number", "")!!
        val storedDiag = rawPrefs.getString("card_diagnostic", "")!!
        assertTrue(storedToken.startsWith("enc:v1:"))
        assertTrue(storedCert.startsWith("enc:v1:"))
        assertTrue(storedDiag.startsWith("enc:v1:"))
        assertNotEquals("PLAIN_SESSION_TOKEN_12345", storedToken)
        assertFalse(storedCert.contains("330106"))

        // 2. 新写入完整会话 + 卡包 JSON 后重启读取一致
        authPrefs.saveSession(
            token = "SESSION=NEW_SECRET_TOKEN_ABCDEFG",
            refreshToken = "NEW_RT_777",
            userName = "测试旅客",
            mobile = "13912345678",
            memberLevel = "金卡",
            cardName = "畅飞卡2026",
            secCardGrantTid = "TID_2026",
            encryptedUserId = "ENC_UID_1",
            certNumber = "110101199505056789",
            updatedAt = "2026-11-25 00:30:00",
            detectedCards = listOf(DetectedCardInfo("TID_2026", "畅飞卡2026", "scgTid", true))
        )
        val reloaded = AuthPrefs(rawPrefs, cipher).current()
        assertEquals("NEW_SECRET_TOKEN_ABCDEFG", reloaded.sessionToken)
        assertEquals("TID_2026", reloaded.secCardGrantTid)
        assertEquals(1, reloaded.detectedCards.size)

        // 3. 退出登录清空敏感信息但保留常驻城市与主题
        authPrefs.setHomeCity("杭州")
        authPrefs.setThemeStyle("mist")
        authPrefs.clearSession()
        val afterLogout = authPrefs.current()
        assertFalse(afterLogout.isLoggedIn)
        assertEquals("", afterLogout.sessionToken)
        assertEquals("杭州", afterLogout.homeCity)
        assertEquals("mist", afterLogout.themeStyle)
    }

    @Test
    fun touchSessionActivity_persistsRotatedSessionAndGuardsStaleToken() {
        val rawPrefs = InMemorySharedPreferences()
        val cipher = JvmAesGcmCipher()
        val authPrefs = AuthPrefs(rawPrefs, cipher)

        authPrefs.saveSession(
            token = "SESSION_INIT_123456",
            refreshToken = "RT_KEEP_ME",
            userName = "测试旅客",
            mobile = "13912345678",
            memberLevel = "金卡",
            cardName = "畅飞卡2026",
            secCardGrantTid = "TID_2026",
            encryptedUserId = "ENC_UID_1",
            certNumber = "",
            updatedAt = "2026-11-25 14:00:00"
        )

        // 1. 服务端滚动返回新 SESSION Cookie 时安全落盘，不破坏 refreshToken 与卡包字段
        val rotated = authPrefs.touchSessionActivity(
            usedToken = "SESSION_INIT_123456",
            rotatedSession = "SESSION=SESSION_ROTATED_999999",
            updatedAt = "2026-11-25 14:20:00",
            nowMillis = 1_700_000_000_000L
        )
        assertTrue(rotated)
        val afterRotate = authPrefs.current()
        assertEquals("SESSION_ROTATED_999999", afterRotate.sessionToken)
        assertEquals("RT_KEEP_ME", afterRotate.refreshToken)
        assertEquals("TID_2026", afterRotate.secCardGrantTid)
        assertEquals("2026-11-25 14:20:00", afterRotate.updatedAt)
        assertEquals(1_700_000_000_000L, afterRotate.lastKeepaliveAtMillis)

        // 2. 并发旧请求携带过期 usedToken 返回时，不得覆盖已更新的新 SESSION
        val staleResult = authPrefs.touchSessionActivity(
            usedToken = "SESSION_INIT_123456",
            rotatedSession = "SESSION_STALE_000000",
            updatedAt = "2026-11-25 14:21:00",
            nowMillis = 1_700_000_060_000L
        )
        assertFalse(staleResult)
        assertEquals("SESSION_ROTATED_999999", authPrefs.current().sessionToken)
        assertEquals(1_700_000_000_000L, authPrefs.current().lastKeepaliveAtMillis)

        // 3. 无 Cookie 轮换的普通成功请求仅刷新最近保活时间戳
        val touched = authPrefs.touchSessionActivity(
            usedToken = "SESSION_ROTATED_999999",
            rotatedSession = null,
            updatedAt = "2026-11-25 14:35:00",
            nowMillis = 1_700_000_900_000L
        )
        assertFalse(touched)
        assertEquals("SESSION_ROTATED_999999", authPrefs.current().sessionToken)
        assertEquals("2026-11-25 14:35:00", authPrefs.current().updatedAt)
        assertEquals(1_700_000_900_000L, authPrefs.current().lastKeepaliveAtMillis)
    }
}
