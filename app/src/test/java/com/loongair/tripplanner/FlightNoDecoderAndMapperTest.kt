package com.loongair.tripplanner

import com.loongair.tripplanner.data.remote.CityIataMapper
import com.loongair.tripplanner.data.remote.FlightNoDecoder
import com.loongair.tripplanner.data.remote.LoongAirApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FlightNoDecoderAndMapperTest {

    @Test
    fun decodeFlightNo_handlesLoongAirObfuscatedPayloadsAndPlaintext() {
        // 真实抓包样例：无 floatPrice 时自动通过 GJ 前缀反解 t
        assertEquals("GJ8859", FlightNoDecoder.decode("168&248&250&306&344&425"))
        assertEquals("GJ8700", FlightNoDecoder.decode("195&275&277&328&341&389"))
        assertEquals("GJ8859", LoongAirApi.decodeFlightNo("168&248&250&306&344&425", floatPrice = 19))

        // 明文航班号直接归一化返回
        assertEquals("GJ6031", FlightNoDecoder.decode(" gj6031 "))
        assertEquals("GJ8959A", FlightNoDecoder.decode("GJ8959A"))

        // didi(e) 兜底算法：(e[r] - 7) / 2 -> "GJ8124"
        val didiEncoded = "GJ8124".map { it.code * 2 + 7 }.joinToString("&")
        assertEquals("GJ8124", FlightNoDecoder.decode(didiEncoded))

        // 非法或损坏串返回 null，绝不透传带 & 的乱码
        assertNull(FlightNoDecoder.decode(""))
        assertNull(FlightNoDecoder.decode("12&abc&99"))
        assertNull(FlightNoDecoder.decode("1&2&3&4&5&6"))
    }

    @Test
    fun cityIataMapper_separatesDualAirportsAndResolvesPinyin() {
        assertEquals("成都天府", CityIataMapper.disambiguateCityName("成都", "TFU", "成都天府国际机场"))
        assertEquals("成都双流", CityIataMapper.disambiguateCityName("成都", "CTU", "成都双流国际机场"))
        assertEquals("北京首都", CityIataMapper.disambiguateCityName("北京", "PEK", "北京首都国际机场"))
        assertEquals("北京大兴", CityIataMapper.disambiguateCityName("北京", "PKX", "北京大兴国际机场"))
        assertEquals("上海浦东", CityIataMapper.disambiguateCityName("上海", "PVG", "上海浦东国际机场"))
        assertEquals("上海虹桥", CityIataMapper.disambiguateCityName("上海", "SHA", "上海虹桥国际机场"))
        assertEquals("遵义新舟", CityIataMapper.disambiguateCityName("遵义", "ZYI", "遵义新舟机场"))
        assertEquals("遵义茅台", CityIataMapper.disambiguateCityName("遵义", "WMT", "遵义茅台机场"))

        assertEquals("CTU", CityIataMapper.toApiIata("成都双流"))
        assertEquals("TFU", CityIataMapper.toApiIata("成都天府"))
        assertEquals("杭州", CityIataMapper.toCityName("hangzhou"))
        assertEquals("HGH", CityIataMapper.toApiIata("hz"))

        CityIataMapper.registerDynamic("义乌", "YIW", "yiwu")
        assertEquals("义乌", CityIataMapper.toCityName("yiwu"))
        assertEquals("YIW", CityIataMapper.toApiIata("义乌"))
    }
}
