package com.loongair.tripplanner

import android.content.SharedPreferences
import com.loongair.tripplanner.data.local.AndroidKeyStoreCipher
import com.loongair.tripplanner.data.local.AuthPrefs
import com.loongair.tripplanner.data.local.StringCipher
import com.loongair.tripplanner.data.remote.DetectedCardInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
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
