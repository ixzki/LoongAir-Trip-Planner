package com.loongair.tripplanner.data.local

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

interface StringCipher {
    fun isEncrypted(stored: String): Boolean
    fun encrypt(plain: String): String
    fun decrypt(stored: String): String
}

/**
 * 基于 AndroidKeyStore 的硬件级 AES-256-GCM 字段加密器：
 * - 无需引入已弃用的 security-crypto 依赖；
 * - 对旧版明文存储值自动平滑兼容并原地迁移加密，升级不掉登录态；
 * - 若系统重装导致 Keystore 密钥失效，安全返回空串触发重新登录而不崩溃。
 */
class AndroidKeyStoreCipher(
    private val keyAlias: String = DEFAULT_KEY_ALIAS
) : StringCipher {

    override fun isEncrypted(stored: String): Boolean = stored.startsWith(PREFIX)

    override fun encrypt(plain: String): String {
        if (plain.isEmpty()) return ""
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
            val iv = cipher.iv
            val cipherBytes = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
            val combined = ByteArray(1 + iv.size + cipherBytes.size)
            combined[0] = iv.size.toByte()
            System.arraycopy(iv, 0, combined, 1, iv.size)
            System.arraycopy(cipherBytes, 0, combined, 1 + iv.size, cipherBytes.size)
            PREFIX + Base64.getEncoder().encodeToString(combined)
        } catch (_: Exception) {
            ""
        }
    }

    override fun decrypt(stored: String): String {
        if (stored.isEmpty()) return ""
        if (!isEncrypted(stored)) {
            // 兼容旧版本明文字段，由 AuthPrefs 在初始化时无缝重加密落盘
            return stored
        }
        return try {
            val raw = Base64.getDecoder().decode(stored.removePrefix(PREFIX))
            if (raw.size < 2) return ""
            val ivLen = raw[0].toInt() and 0xFF
            if (ivLen <= 0 || raw.size <= 1 + ivLen) return ""
            val iv = raw.copyOfRange(1, 1 + ivLen)
            val cipherBytes = raw.copyOfRange(1 + ivLen, raw.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
            String(cipher.doFinal(cipherBytes), Charsets.UTF_8)
        } catch (_: Exception) {
            ""
        }
    }

    @Synchronized
    private fun getOrCreateSecretKey(): SecretKey {
        val ks = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        val existing = (ks.getEntry(keyAlias, null) as? KeyStore.SecretKeyEntry)?.secretKey
        if (existing != null) return existing

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
        val spec = KeyGenParameterSpec.Builder(
            keyAlias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    companion object {
        const val PREFIX = "enc:v1:"
        private const val DEFAULT_KEY_ALIAS = "loongair_auth_aes_gcm_v1"
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128
    }
}
