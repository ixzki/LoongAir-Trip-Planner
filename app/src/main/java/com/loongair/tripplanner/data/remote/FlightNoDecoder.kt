package com.loongair.tripplanner.data.remote

/**
 * 长龙官方网关混淆航班号解码器（如 "168&248&250&306&344&425" -> "GJ8859"）：
 * 1. 若已是标准明文航班号（GJ8859），直接返回；
 * 2. 长龙前端算法 dp(e, t)：charCode[r] = (e[r] - 7 - t) / (2 + r)，优先用 floatPrice 解码；
 * 3. 若接口未返回 floatPrice 或修复历史缓存，自动按长龙 GJ 前缀及通用航司前缀精确反解 t；
 * 4. 兜底尝试 didi(e) 算法：(e[r] - 7) / 2；绝不返回带 "&" 的乱码串。
 */
object FlightNoDecoder {
    private val VALID_FLIGHT_NO_REGEX = Regex("^[A-Z0-9]{2}\\d{3,4}[A-Z]?$")

    fun decode(raw: String, floatPrice: Int? = null): String? {
        val clean = raw.trim().uppercase()
        if (clean.isEmpty()) return null
        if (VALID_FLIGHT_NO_REGEX.matches(clean)) return clean
        if (!clean.contains("&")) return null

        val nums = clean.split("&").map { it.trim().toIntOrNull() ?: return null }
        if (nums.size !in 4..8) return null

        fun tryDp(t: Int): String? {
            val sb = StringBuilder(nums.size)
            for (r in nums.indices) {
                val numerator = nums[r] - 7 - t
                val denom = 2 + r
                if (numerator <= 0 || numerator % denom != 0) return null
                val code = numerator / denom
                if (code !in 48..90) return null
                sb.append(code.toChar())
            }
            val candidate = sb.toString().uppercase()
            return candidate.takeIf { VALID_FLIGHT_NO_REGEX.matches(it) }
        }

        if (floatPrice != null) {
            tryDp(floatPrice)?.let { return it }
        }

        val gjT = nums[0] - 7 - 2 * 'G'.code
        if (nums.size >= 2 && (nums[1] - 7 - gjT) == 3 * 'J'.code) {
            tryDp(gjT)?.let { return it }
        }

        val minT = nums[0] - 7 - 2 * 'Z'.code
        val maxT = nums[0] - 7 - 2 * '0'.code
        for (t in minT..maxT) {
            if (t == gjT || t == floatPrice) continue
            tryDp(t)?.let { return it }
        }

        val didiSb = StringBuilder(nums.size)
        var didiOk = true
        for (v in nums) {
            val n = v - 7
            if (n <= 0 || n % 2 != 0) {
                didiOk = false
                break
            }
            val code = n / 2
            if (code !in 48..90) {
                didiOk = false
                break
            }
            didiSb.append(code.toChar())
        }
        if (didiOk) {
            val cand = didiSb.toString().uppercase()
            if (VALID_FLIGHT_NO_REGEX.matches(cand)) return cand
        }

        return null
    }
}
