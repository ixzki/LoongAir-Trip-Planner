package com.loongair.tripplanner.data.local

import android.content.Context
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

class AuthPrefs(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("loongair_auth_prefs", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val _state = MutableStateFlow(readState())
    val state: StateFlow<UserSessionState> = _state.asStateFlow()

    fun current(): UserSessionState = _state.value

    private fun readState(): UserSessionState {
        val token = prefs.getString(KEY_TOKEN, "").orEmpty()
        val cardsJson = prefs.getString(KEY_CARDS_JSON, "").orEmpty()
        val cards = if (cardsJson.isNotBlank()) {
            runCatching { json.decodeFromString<List<DetectedCardInfo>>(cardsJson) }.getOrDefault(emptyList())
        } else {
            emptyList()
        }
        return UserSessionState(
            isLoggedIn = token.length > 8,
            userName = prefs.getString(KEY_USER_NAME, "未登录").orEmpty(),
            mobile = prefs.getString(KEY_MOBILE, "").orEmpty(),
            memberLevel = prefs.getString(KEY_MEMBER_LEVEL, "乘云会员").orEmpty(),
            cardName = prefs.getString(KEY_CARD_NAME, "长龙畅飞卡").orEmpty(),
            sessionToken = token,
            refreshToken = prefs.getString(KEY_REFRESH_TOKEN, "").orEmpty(),
            secCardGrantTid = prefs.getString(KEY_SCG_TID, "").orEmpty(),
            encryptedUserId = prefs.getString(KEY_ENC_UID, "").orEmpty(),
            certNumber = prefs.getString(KEY_CERT_NO, "").orEmpty(),
            updatedAt = prefs.getString(KEY_UPDATED_AT, "").orEmpty(),
            homeCity = prefs.getString(KEY_HOME_CITY, "").orEmpty(),
            themeStyle = prefs.getString(KEY_THEME_STYLE, "crimson").orEmpty().ifBlank { "crimson" },
            detectedCards = cards,
            cardDiagnostic = prefs.getString(KEY_CARD_DIAG, "").orEmpty()
        )
    }

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
            .putString(KEY_TOKEN, cleanToken)
            .putString(KEY_REFRESH_TOKEN, refreshToken)
            .putString(KEY_USER_NAME, userName.ifBlank { "长龙会员" })
            .putString(KEY_MOBILE, mobile)
            .putString(KEY_MEMBER_LEVEL, memberLevel.ifBlank { "乘云会员" })
            .putString(KEY_CARD_NAME, cardName.ifBlank { "长龙畅飞卡" })
            .putString(KEY_SCG_TID, secCardGrantTid)
            .putString(KEY_ENC_UID, encryptedUserId)
            .putString(KEY_CERT_NO, certNumber)
            .putString(KEY_UPDATED_AT, updatedAt)
            .putString(KEY_CARDS_JSON, json.encodeToString(detectedCards))
            .putString(KEY_CARD_DIAG, cardDiagnostic)
            .apply()
        _state.value = readState()
    }

    fun updateCards(
        cards: List<DetectedCardInfo>,
        selectedTid: String,
        selectedName: String,
        diagnostic: String,
        updatedAt: String
    ) {
        prefs.edit()
            .putString(KEY_CARDS_JSON, json.encodeToString(cards))
            .putString(KEY_SCG_TID, selectedTid)
            .putString(KEY_CARD_NAME, selectedName.ifBlank { current().cardName })
            .putString(KEY_CARD_DIAG, diagnostic)
            .putString(KEY_UPDATED_AT, updatedAt)
            .apply()
        _state.value = readState()
    }

    fun selectActiveCard(grantTid: String, cardName: String) {
        prefs.edit()
            .putString(KEY_SCG_TID, grantTid.trim())
            .putString(KEY_CARD_NAME, cardName.ifBlank { "长龙畅飞卡" })
            .apply()
        _state.value = readState()
    }

    fun updateTokens(newToken: String, newRefreshToken: String, updatedAt: String) {
        val cleanToken = newToken.removePrefix("SESSION=").trim()
        prefs.edit()
            .putString(KEY_TOKEN, cleanToken)
            .putString(KEY_REFRESH_TOKEN, newRefreshToken)
            .putString(KEY_UPDATED_AT, updatedAt)
            .apply()
        _state.value = readState()
    }

    fun setHomeCity(city: String) {
        prefs.edit().putString(KEY_HOME_CITY, city.trim()).apply()
        _state.value = readState()
    }

    fun setThemeStyle(styleKey: String) {
        prefs.edit().putString(KEY_THEME_STYLE, styleKey).apply()
        _state.value = readState()
    }

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
    }
}
