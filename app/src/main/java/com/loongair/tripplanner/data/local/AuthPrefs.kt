package com.loongair.tripplanner.data.local

import android.content.Context
import android.content.SharedPreferences
import com.loongair.tripplanner.data.remote.DetectedCardInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

data class UserSessionState(
    val isLoggedIn: Boolean = false,
    val userName: String = "未登录",
    val mobile: String = "",
    val memberLevel: String = "乘云会员",
    val cardName: String = "长龙畅飞卡",
    val sessionToken: String = "",
    val refreshToken: String = "",
    val secCardGrantTid: String = "",
    val encryptedUserId: String = "",
    val certNumber: String = "",
    val updatedAt: String = "",
    val homeCity: String = "",
    val themeStyle: String = "crimson",
    val detectedCards: List<DetectedCardInfo> = emptyList(),
    val cardDiagnostic: String = ""
) {
    val maskedMobile: String
        get() = if (mobile.length == 11) "${mobile.take(3)}****${mobile.takeLast(4)}" else mobile
}

class AuthPrefs(
    private val prefs: SharedPreferences,
    private val cipher: StringCipher = AndroidKeyStoreCipher()
) {
    constructor(context: Context) : this(
        prefs = context.applicationContext.getSharedPreferences(PREFS_FILE_NAME, Context.MODE_PRIVATE),
        cipher = AndroidKeyStoreCipher()
    )

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    init {
        migrateLegacyPlaintextIfNeeded()
    }

    private val _state = MutableStateFlow(readState())
    val state: StateFlow<UserSessionState> = _state.asStateFlow()

    fun current(): UserSessionState = _state.value

    private fun getSecret(key: String): String {
        val raw = prefs.getString(key, "").orEmpty()
        return if (raw.isEmpty()) "" else cipher.decrypt(raw)
    }

    private fun SharedPreferences.Editor.putSecret(key: String, value: String): SharedPreferences.Editor {
        val enc = if (value.isEmpty()) "" else cipher.encrypt(value)
        return putString(key, enc)
    }

    /**
     * 平滑升级迁移：发现旧版本未加密的敏感字段时，自动使用 Keystore 重加密覆盖，不丢失现有会话。
     */
    private fun migrateLegacyPlaintextIfNeeded() {
        val editor = prefs.edit()
        var migrated = false
        for (key in SENSITIVE_KEYS) {
            val raw = prefs.getString(key, "").orEmpty()
            if (raw.isNotEmpty() && !cipher.isEncrypted(raw)) {
                val enc = cipher.encrypt(raw)
                if (enc.isNotEmpty()) {
                    editor.putString(key, enc)
                    migrated = true
                }
            }
        }
        if (migrated) {
            editor.apply()
        }
    }

    private fun readState(): UserSessionState {
        val token = getSecret(KEY_TOKEN)
        val cardsJson = getSecret(KEY_CARDS_JSON)
        val cards = if (cardsJson.isNotBlank()) {
            runCatching { json.decodeFromString<List<DetectedCardInfo>>(cardsJson) }.getOrDefault(emptyList())
        } else {
            emptyList()
        }
        return UserSessionState(
            isLoggedIn = token.length > 8,
            userName = prefs.getString(KEY_USER_NAME, "未登录").orEmpty(),
            mobile = getSecret(KEY_MOBILE),
            memberLevel = prefs.getString(KEY_MEMBER_LEVEL, "乘云会员").orEmpty(),
            cardName = prefs.getString(KEY_CARD_NAME, "长龙畅飞卡").orEmpty(),
            sessionToken = token,
            refreshToken = getSecret(KEY_REFRESH_TOKEN),
            secCardGrantTid = getSecret(KEY_SCG_TID),
            encryptedUserId = getSecret(KEY_ENC_UID),
            certNumber = getSecret(KEY_CERT_NO),
            updatedAt = prefs.getString(KEY_UPDATED_AT, "").orEmpty(),
            homeCity = prefs.getString(KEY_HOME_CITY, "").orEmpty(),
            themeStyle = prefs.getString(KEY_THEME_STYLE, "crimson").orEmpty().ifBlank { "crimson" },
            detectedCards = cards,
            cardDiagnostic = prefs.getString(KEY_CARD_DIAG, "").orEmpty()
        )
    }

    @Synchronized
    fun saveSession(
        token: String,
        refreshToken: String,
        userName: String,
        mobile: String,
        memberLevel: String,
        cardName: String,
        secCardGrantTid: String,
        encryptedUserId: String,
        certNumber: String,
        updatedAt: String,
        detectedCards: List<DetectedCardInfo> = emptyList(),
        cardDiagnostic: String = ""
    ) {
        val cleanToken = token.removePrefix("SESSION=").trim()
        prefs.edit()
            .putSecret(KEY_TOKEN, cleanToken)
            .putSecret(KEY_REFRESH_TOKEN, refreshToken)
            .putString(KEY_USER_NAME, userName.ifBlank { "长龙会员" })
            .putSecret(KEY_MOBILE, mobile)
            .putString(KEY_MEMBER_LEVEL, memberLevel.ifBlank { "乘云会员" })
            .putString(KEY_CARD_NAME, cardName.ifBlank { "长龙畅飞卡" })
            .putSecret(KEY_SCG_TID, secCardGrantTid)
            .putSecret(KEY_ENC_UID, encryptedUserId)
            .putSecret(KEY_CERT_NO, certNumber)
            .putString(KEY_UPDATED_AT, updatedAt)
            .putSecret(KEY_CARDS_JSON, json.encodeToString(detectedCards))
            .putString(KEY_CARD_DIAG, cardDiagnostic)
            .apply()
        _state.value = readState()
    }

    @Synchronized
    fun updateCards(
        cards: List<DetectedCardInfo>,
        selectedTid: String,
        selectedName: String,
        diagnostic: String,
        updatedAt: String
    ) {
        prefs.edit()
            .putSecret(KEY_CARDS_JSON, json.encodeToString(cards))
            .putSecret(KEY_SCG_TID, selectedTid)
            .putString(KEY_CARD_NAME, selectedName.ifBlank { current().cardName })
            .putString(KEY_CARD_DIAG, diagnostic)
            .putString(KEY_UPDATED_AT, updatedAt)
            .apply()
        _state.value = readState()
    }

    @Synchronized
    fun selectActiveCard(grantTid: String, cardName: String) {
        prefs.edit()
            .putSecret(KEY_SCG_TID, grantTid.trim())
            .putString(KEY_CARD_NAME, cardName.ifBlank { "长龙畅飞卡" })
            .apply()
        _state.value = readState()
    }

    @Synchronized
    fun updateTokens(newToken: String, newRefreshToken: String, updatedAt: String) {
        val cleanToken = newToken.removePrefix("SESSION=").trim()
        prefs.edit()
            .putSecret(KEY_TOKEN, cleanToken)
            .putSecret(KEY_REFRESH_TOKEN, newRefreshToken)
            .putString(KEY_UPDATED_AT, updatedAt)
            .apply()
        _state.value = readState()
    }

    @Synchronized
    fun setHomeCity(city: String) {
        prefs.edit().putString(KEY_HOME_CITY, city.trim()).apply()
        _state.value = readState()
    }

    @Synchronized
    fun setThemeStyle(styleKey: String) {
        prefs.edit().putString(KEY_THEME_STYLE, styleKey).apply()
        _state.value = readState()
    }

    @Synchronized
    fun clearSession() {
        val keepHomeCity = current().homeCity
        val keepTheme = current().themeStyle
        prefs.edit().clear()
            .putString(KEY_HOME_CITY, keepHomeCity)
            .putString(KEY_THEME_STYLE, keepTheme)
            .apply()
        _state.value = readState()
    }

    companion object {
        const val PREFS_FILE_NAME = "loongair_auth_prefs"
        private const val KEY_TOKEN = "session_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_MOBILE = "mobile"
        private const val KEY_MEMBER_LEVEL = "member_level"
        private const val KEY_CARD_NAME = "card_name"
        private const val KEY_SCG_TID = "sec_card_grant_tid"
        private const val KEY_ENC_UID = "encrypted_user_id"
        private const val KEY_CERT_NO = "cert_number"
        private const val KEY_UPDATED_AT = "updated_at"
        private const val KEY_HOME_CITY = "home_city"
        private const val KEY_THEME_STYLE = "theme_style"
        private const val KEY_CARDS_JSON = "detected_cards_json"
        private const val KEY_CARD_DIAG = "card_diagnostic"

        private val SENSITIVE_KEYS = listOf(
            KEY_TOKEN,
            KEY_REFRESH_TOKEN,
            KEY_MOBILE,
            KEY_SCG_TID,
            KEY_ENC_UID,
            KEY_CERT_NO,
            KEY_CARDS_JSON
        )
    }
}
