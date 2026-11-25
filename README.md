# 畅飞行 (Changfei Trip Planner · Android)

一款专为随心飞畅飞卡旅客打造的**纯本地原生 Android 行程规划助手**，主打**全国随心飞航网交互可视化**、**多日期/多时段智能往返配对**、**近 40 天放票日历与官方适用窗校验**以及**主题化抢票闹钟提醒**。

> - 长龙官方网关通信协议（`webhandler.ashx`）与航班号解码参考开源项目 [eureka-wu/clairline](https://github.com/eureka-wu/clairline)（MIT License）。
> - 界面字阶、弹簧物理动效、阴影层级与「朱砂 / 薄暮」双主题视觉语言致敬开源项目 [zzNaLogiczz/chher-public](https://github.com/zzNaLogiczz/chher-public)。

---

## 核心特性

1. **内置全网核验航线库 · 失效航线自动剔除管理**
   - 内置经官方接口全量核验的 **75 座国内通航城市、410 条畅飞卡航线与 445 条航班时刻**（[`app/src/main/assets/bundled_changfei_network.json`](app/src/main/assets/bundled_changfei_network.json)），开箱即可浏览全网地图与规划往返，避免首次大量同步请求失败。
   - 区分内置基准（`bundled`）与实时校验（`official`），并维护失效墓碑表（`invalidated_routes`）；检测到航线停航或当前卡不适用时自动永久剔除且重启不复活，支持在设置中一键恢复内置基准。
   - 自动区分双机场城市（`成都天府 TFU` / `成都双流 CTU`、`北京首都 PEK` / `北京大兴 PKX`、`上海浦东 PVG` / `上海虹桥 SHA`、`遵义新舟 ZYI` / `遵义茅台 WMT`）。

2. **全国航网交互地图 & 枢纽筛选**
   - 概览模式自动对往返重合航线去重，以轻盈二次贝塞尔弧线绘制全网航迹，支持双指缩放、拖拽漫游与基于屏幕碰撞检测的防重叠城市标注。
   - 下方枢纽胶囊按**通航目的地数量**从高到低排序，详情列表优先展示当前**常驻城市**出发与到达航线。

3. **2026–2027 官方适用窗口 · 近 40 天日历 & 多星期快搜**
   - 内置 2026–2027 官方 5 段适用航班日期与 125 个间隔屏蔽日历（春运 / 五一 / 暑运 / 中秋国庆），在月历、选日器、往返搜索与行程计划中统一标注窗口外「不可用」与 3 天内「已截止」状态。
   - 点击航线卡片进入 **近 40 天放票日历详情页**，实时查询多舱位聚合余票与班期校准；支持单选/多选星期及「任意」日期模板往返快搜。

4. **全屏动态启动页 & 双主题通知闹钟**
   - 基于 `AlarmManager.setExactAndAllowWhileIdle` 实现息屏唤醒与重启/换日自动恢复闹钟，支持导出至系统日历。
   - 自定义高优先级横幅与展开式通知卡片跟随「朱砂 / 薄暮」主题与深浅色模式自适应，全屏启动页随本地航网就绪平滑过渡。

5. **端侧安全与防风控**
   - 会话凭据、`scgTid`、卡包与个人信息全部经 `AndroidKeyStore` 硬件级 **AES-256-GCM** 加密存储于本机，不上传任何第三方服务器；内置全局请求限流与 Token 刷新互斥保护。

---

## 本地编译与测试

需要 **JDK 17** 与 **Android SDK (API 35)**：

```powershell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-17.0.21.10002-hotspot"
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"

# 运行单元测试 (28 cases)、Lint 静态检查、Debug 与 R8 混淆压缩 Release 构建
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug assembleRelease
```

生成产物路径：
- Debug APK：`app/build/outputs/apk/debug/app-debug.apk`
- Release APK（已启用 R8 混淆与资源压缩）：`app/build/outputs/apk/release/app-release.apk`
