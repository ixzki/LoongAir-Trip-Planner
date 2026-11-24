package com.loongair.tripplanner.data.remote

import kotlinx.serialization.Serializable

/**
 * 仅在同步后用于地图投影与列表展示的城市元数据（从长龙官方同步的城市机场动态构建，登录前为空）
 */
data class CityMeta(
    val city: String,
    val code: String,
    val airport: String,
    val province: String = "",
    val region: String = "",
    val hub: String = "dest",
    val lng: Double = 114.0,
    val lat: Double = 34.0,
    val pinyin: String = "",
    val tags: List<String> = emptyList()
)

@Serializable
data class ChinaProvincePath(
    val name: String,
    val path: String
)

data class OfficialAirportEndpoint(
    val cityName: String,
    val airportName: String,
    val airportCode: String,
    val pinyin: String = ""
)

data class LiveFlightSeat(
    val flightNo: String,
    val depTime: String,
    val arrTime: String,
    val duration: String = "",
    val aircraft: String,
    val stopover: String,
    val redeemPrice: Double,
    val secCardLeft: Int,
    val normalLeft: Int,
    val cabinCode: String
)

data class SmsSendResult(
    val ok: Boolean,
    val verifyId: String = "",
    val message: String = ""
)

@Serializable
data class DetectedCardInfo(
    val grantTid: String,
    val cardName: String,
    val idField: String,
    val isSecCard: Boolean,
    val statusHint: String = ""
)

data class CardSyncResult(
    val ok: Boolean,
    val message: String,
    val cards: List<DetectedCardInfo> = emptyList(),
    val selectedCard: DetectedCardInfo? = null,
    val diagnostic: String = ""
)

data class LoginResult(
    val ok: Boolean,
    val message: String = "",
    val userName: String = "",
    val cardName: String = "",
    val detectedCardsCount: Int = 0
)

/**
 * 机场三字码与纯地理坐标投影工具：
 * 仅用于接口参数编解码及将「已同步的官方航线」投影至中国地图轮廓，绝不作为预置城市/航线目录对外展示。
 */
object CityIataMapper {
    private val cityToApiIata: Map<String, String> = mapOf(
        "阿勒泰" to "AAT", "阿拉尔" to "ACF", "阿克苏" to "AKU", "巴中" to "BZX",
        "广州" to "CAN", "郑州" to "CGO", "长春" to "CGQ", "重庆" to "CKG",
        "长沙" to "CSX", "成都双流" to "CTU", "成都天府" to "TFU", "成都" to "TFU",
        "大连" to "DLC", "东营" to "DOY", "张家界" to "DYG", "达州" to "DZH",
        "恩施" to "ENH", "海口" to "HAK", "邯郸" to "HDG", "呼和浩特" to "HET",
        "杭州" to "HGH", "海拉尔" to "HLD", "衡阳" to "HNY", "哈尔滨" to "HRB",
        "菏泽" to "HZA", "银川" to "INC", "西双版纳" to "JHG", "泉州" to "JJN",
        "嘉兴" to "JNH", "鸡西" to "JXA", "喀什" to "KHG", "昆明" to "KMG",
        "赣州" to "KOW", "克拉玛依" to "KRY", "贵阳" to "KWE", "兰州" to "LHW",
        "丽江" to "LJG", "芒市" to "LUM", "临沂" to "LYI", "泸州" to "LZO",
        "宁波" to "NGB", "北京首都" to "PEK", "北京大兴" to "PKX", "北京" to "PEK",
        "上海浦东" to "PVG", "上海虹桥" to "SHA", "上海" to "PVG",
        "沈阳" to "SHE", "荆州" to "SHS", "三亚" to "SYX", "深圳" to "SZX",
        "青岛" to "TAO", "通化" to "TNH", "图木舒克" to "TWC", "太原" to "TYN",
        "乌鲁木齐" to "URC", "潍坊" to "WEF", "威海" to "WEH",
        "遵义茅台" to "WMT", "茅台" to "WMT", "遵义新舟" to "ZYI", "遵义" to "ZYI",
        "温州" to "WNZ", "武汉" to "WUH", "无锡" to "WUX", "襄阳" to "XFN",
        "西安" to "XIY", "西宁" to "XNN", "徐州" to "XUZ", "烟台" to "YNT",
        "珠海" to "ZUH", "包头" to "BAV", "南京" to "NKG", "南宁" to "NNG",
        "常州" to "CZX", "柳州" to "LZH", "济南" to "TNA", "福州" to "FOC",
        "赤峰" to "CIF", "延吉" to "YNJ", "长白山" to "NBS", "厦门" to "XMN",
        "天津" to "TSN", "合肥" to "HFE", "南昌" to "KHN", "石家庄" to "SJW",
        "拉萨" to "LXA", "桂林" to "KWL", "大理" to "DLU", "揭阳" to "SWA",
        "湛江" to "ZHA", "绵阳" to "MIG"
    )

    /**
     * 国内双机场城市的规范分开标注（避免把天府/双流、首都/大兴、浦东/虹桥、新舟/茅台混为同一节点）
     */
    private val dualAirportDisplayNames: Map<String, String> = mapOf(
        "TFU" to "成都天府",
        "CTU" to "成都双流",
        "PEK" to "北京首都",
        "PKX" to "北京大兴",
        "PVG" to "上海浦东",
        "SHA" to "上海虹桥",
        "ZYI" to "遵义新舟",
        "WMT" to "遵义茅台"
    )

    private val iataToCity: Map<String, String> = buildMap {
        cityToApiIata.forEach { (city, code) ->
            if (code !in dualAirportDisplayNames) put(code, city)
        }
        dualAirportDisplayNames.forEach { (code, name) -> put(code, name) }
        put("JXS", "嘉兴")
        put("AAL", "阿拉尔")
        put("TWS", "图木舒克")
        put("DAX", "达州")
    }

    fun dualAirportLabelForCode(airportCode: String): String? =
        dualAirportDisplayNames[airportCode.trim().uppercase()]

    /**
     * 当同一城市存在多个机场时，结合三字码与机场名生成区分标注（优先标准双机场简称）
     */
    fun disambiguateCityName(rawCity: String, airportCode: String, airportName: String = ""): String {
        val code = airportCode.trim().uppercase()
        dualAirportDisplayNames[code]?.let { return it }
        val cleanCity = rawCity.trim()
        if (cleanCity.isBlank()) return toCityName(code)
        // 若机场名中已明确区分（如「天府」「双流」「大兴」「虹桥」「茅台」等）
        val lowerAirport = airportName.trim()
        return when {
            "天府" in lowerAirport -> "成都天府"
            "双流" in lowerAirport -> "成都双流"
            "大兴" in lowerAirport -> "北京大兴"
            "首都" in lowerAirport -> "北京首都"
            "浦东" in lowerAirport -> "上海浦东"
            "虹桥" in lowerAirport -> "上海虹桥"
            "茅台" in lowerAirport -> "遵义茅台"
            "新舟" in lowerAirport -> "遵义新舟"
            else -> cleanCity
        }
    }

    private val dynamicCityToIata = mutableMapOf<String, String>()
    private val dynamicIataToCity = mutableMapOf<String, String>()
    private val dynamicPinyinToCity = mutableMapOf<String, String>()

    // 常见城市拼音解析表（仅用于将用户键盘输入的英文拼音翻译为城市名/三字码，绝不作为预置目录展示）
    private val pinyinToCity: Map<String, String> = mapOf(
        "hangzhou" to "杭州", "hz" to "杭州", "ningbo" to "宁波", "nb" to "宁波",
        "wenzhou" to "温州", "wz" to "温州", "guangzhou" to "广州", "gz" to "广州",
        "chengdu" to "成都", "cd" to "成都", "yinchuan" to "银川", "yc" to "银川",
        "haerbin" to "哈尔滨", "harbin" to "哈尔滨", "heb" to "哈尔滨",
        "changchun" to "长春", "cc" to "长春", "xian" to "西安", "xa" to "西安",
        "wuhan" to "武汉", "wh" to "武汉", "xuzhou" to "徐州", "xz" to "徐州",
        "shenzhen" to "深圳", "sz" to "深圳", "beijing" to "北京", "bj" to "北京",
        "shanghai" to "上海", "sh" to "上海", "chongqing" to "重庆", "cq" to "重庆",
        "kunming" to "昆明", "km" to "昆明", "sanya" to "三亚", "sy" to "三亚",
        "haikou" to "海口", "hk" to "海口", "zhengzhou" to "郑州", "zz" to "郑州",
        "zhuhai" to "珠海", "zh" to "珠海", "shenyang" to "沈阳", "dalian" to "大连",
        "qingdao" to "青岛", "qd" to "青岛", "lijiang" to "丽江", "lj" to "丽江",
        "xishuangbanna" to "西双版纳", "banna" to "西双版纳", "xsbn" to "西双版纳",
        "wulumuqi" to "乌鲁木齐", "urumqi" to "乌鲁木齐", "wlmq" to "乌鲁木齐",
        "kashi" to "喀什", "aletai" to "阿勒泰", "akesu" to "阿克苏",
        "guiyang" to "贵阳", "gy" to "贵阳", "lanzhou" to "兰州", "lz" to "兰州",
        "xining" to "西宁", "xn" to "西宁", "taiyuan" to "太原", "ty" to "太原",
        "changsha" to "长沙", "cs" to "长沙", "nanning" to "南宁", "nn" to "南宁",
        "nanjing" to "南京", "nj" to "南京", "jinan" to "济南", "jn" to "济南",
        "fuzhou" to "福州", "fz" to "福州", "xiamen" to "厦门", "xm" to "厦门",
        "quanzhou" to "泉州", "qz" to "泉州", "yantai" to "烟台", "weihai" to "威海",
        "weifang" to "潍坊", "heze" to "菏泽", "linyi" to "临沂", "enshi" to "恩施",
        "zhangjiajie" to "张家界", "zjj" to "张家界", "zunyi" to "遵义",
        "huhehaote" to "呼和浩特", "hhht" to "呼和浩特", "hailaer" to "海拉尔",
        "jiaxing" to "嘉兴", "jx" to "嘉兴", "wuxi" to "无锡", "changzhou" to "常州",
        "xiangyang" to "襄阳", "jingzhou" to "荆州", "ganzhou" to "赣州",
        "hengyang" to "衡阳", "liuzhou" to "柳州", "dali" to "大理", "guilin" to "桂林",
        "hefei" to "合肥", "nanchang" to "南昌", "tianjin" to "天津", "shijiazhuang" to "石家庄"
    )

    fun registerDynamic(cityName: String, airportCode: String, pinyin: String = "") {
        val c = cityName.trim()
        val code = airportCode.trim().uppercase()
        if (c.isNotEmpty() && code.length == 3) {
            dynamicCityToIata[c] = code
            dynamicIataToCity[code] = c
        }
        val py = pinyin.trim().lowercase().replace(" ", "")
        if (c.isNotEmpty() && py.isNotEmpty()) {
            dynamicPinyinToCity[py] = c
        }
    }

    fun toApiIata(cityOrCode: String): String {
        val clean = cityOrCode.trim()
        if (clean.length == 3 && clean.all { it.isLetter() }) return clean.uppercase()
        val resolved = toCityName(clean)
        return dynamicCityToIata[resolved] ?: cityToApiIata[resolved] ?: clean.uppercase()
    }

    fun toCityName(cityOrCode: String): String {
        val clean = cityOrCode.trim()
        val upper = clean.uppercase()
        val lower = clean.lowercase().replace(" ", "")
        return dynamicIataToCity[upper]
            ?: iataToCity[upper]
            ?: dynamicPinyinToCity[lower]
            ?: pinyinToCity[lower]
            ?: clean
    }

    /** 仅用于中国地图轮廓坐标投影（经度, 纬度） */
    private val geoCoords: Map<String, Pair<Double, Double>> = mapOf(
        "杭州" to (120.43 to 30.23), "银川" to (106.39 to 38.32), "长春" to (125.68 to 43.99),
        "宁波" to (121.46 to 29.82), "哈尔滨" to (126.25 to 45.62), "温州" to (120.85 to 27.91),
        "广州" to (113.30 to 23.39), "成都天府" to (104.44 to 30.32), "成都双流" to (103.95 to 30.58),
        "成都" to (104.44 to 30.32), "武汉" to (114.21 to 30.78),
        "徐州" to (117.55 to 34.05), "西双版纳" to (100.76 to 21.97), "丽江" to (100.24 to 26.68),
        "阿勒泰" to (88.08 to 47.75), "喀什" to (75.98 to 39.54), "克拉玛依" to (84.95 to 45.46),
        "阿克苏" to (80.29 to 41.26), "阿拉尔" to (81.33 to 40.54), "图木舒克" to (79.13 to 39.87),
        "乌鲁木齐" to (87.47 to 43.90), "三亚" to (109.41 to 18.30), "海口" to (110.46 to 19.93),
        "珠海" to (113.37 to 22.01), "深圳" to (113.81 to 22.64), "太原" to (112.63 to 37.75),
        "西安" to (108.75 to 34.44), "郑州" to (113.84 to 34.52),
        "北京首都" to (116.59 to 40.08), "北京大兴" to (116.41 to 39.51), "北京" to (116.59 to 40.08),
        "重庆" to (106.64 to 29.72), "嘉兴" to (120.68 to 30.71), "恩施" to (109.49 to 30.32),
        "遵义新舟" to (107.25 to 27.81), "遵义茅台" to (106.44 to 27.70), "遵义" to (107.25 to 27.81),
        "贵阳" to (106.80 to 26.54), "西宁" to (102.04 to 36.53),
        "兰州" to (103.62 to 36.51), "昆明" to (102.93 to 25.10), "芒市" to (98.53 to 24.40),
        "张家界" to (110.45 to 29.10), "沈阳" to (123.48 to 41.64), "大连" to (121.54 to 38.96),
        "青岛" to (120.39 to 36.27), "威海" to (122.24 to 37.19), "烟台" to (120.98 to 37.66),
        "潍坊" to (119.12 to 36.65), "临沂" to (118.41 to 35.05), "菏泽" to (115.54 to 35.31),
        "济南" to (117.22 to 36.86), "呼和浩特" to (111.82 to 40.86), "海拉尔" to (119.83 to 49.20),
        "包头" to (110.00 to 40.56), "赤峰" to (118.84 to 42.16), "通化" to (125.70 to 42.04),
        "鸡西" to (131.19 to 45.29), "荆州" to (112.46 to 30.33), "襄阳" to (112.29 to 32.15),
        "长沙" to (113.22 to 28.19), "衡阳" to (112.62 to 26.72), "南宁" to (108.17 to 22.61),
        "柳州" to (109.40 to 24.21), "赣州" to (114.78 to 25.85), "泉州" to (118.59 to 24.80),
        "福州" to (119.66 to 25.93), "常州" to (119.78 to 31.91), "无锡" to (120.43 to 31.50),
        "南京" to (118.86 to 31.74), "上海浦东" to (121.80 to 31.14), "上海虹桥" to (121.34 to 31.20),
        "上海" to (121.80 to 31.14), "邯郸" to (114.43 to 36.53),
        "东营" to (118.79 to 37.51), "泸州" to (105.47 to 28.98), "达州" to (107.43 to 31.14),
        "巴中" to (106.65 to 31.79), "茅台" to (106.44 to 27.70), "延吉" to (129.45 to 42.88),
        "长白山" to (127.61 to 42.07), "厦门" to (118.13 to 24.54), "天津" to (117.35 to 39.12),
        "合肥" to (117.00 to 31.98), "南昌" to (115.90 to 28.86), "石家庄" to (114.70 to 38.28),
        "拉萨" to (90.91 to 29.30), "桂林" to (110.04 to 25.22), "大理" to (100.32 to 25.65),
        "揭阳" to (116.50 to 23.55), "湛江" to (110.36 to 21.21), "绵阳" to (104.74 to 31.43)
    )

    fun geoCoordsFor(cityName: String): Pair<Double, Double>? = geoCoords[cityName.trim()]
}
