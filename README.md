# 长龙畅飞规划 (LoongAir Trip Planner · Android)

一款专为长龙航空畅飞卡旅客打造的**纯本地原生 Android 行程规划助手**，主打**全国随心飞航网可视化**、**多日期/多时段智能往返配对**、**近 40 天放票日历与多舱位余票核验**以及**精确抢票闹钟提醒**。

> - 长龙官方网关通信协议（`webhandler.ashx`）与航班号解码参考开源项目 [eureka-wu/clairline](https://github.com/eureka-wu/clairline)（MIT License）。
> - 界面字阶、弹簧物理动效、阴影层级与双主题视觉语言致敬开源项目 [zzNaLogiczz/chher-public](https://github.com/zzNaLogiczz/chher-public)。

---

## 核心特性

1. **内置全网核验航线库 · 失效航线自动剔除管理**
   - 内置经长龙官方接口全量核验的 **75 座国内通航城市、410 条畅飞卡航线与 445 条真实航班时刻**（`assets/bundled_changfei_network.json`），新用户安装即开箱浏览全网地图与规划往返，免去首次数百次请求失败风险。
   - **生命周期与失效自动剔除**：数据库区分内置基准（`bundled`）与实时校验（`official`）并维护失效墓碑表（`invalidated_routes`）；登录同步或查日历时若检测到某条航线已换季停航或当前卡片不适用，自动永久剔除且重启不复活，支持在设置中随时一键恢复内置基准。
   - 自动识别国内双机场城市并独立标注（`成都天府 TFU` / `成都双流 CTU`、`北京首都 PEK` / `北京大兴 PKX`、`上海浦东 PVG` / `上海虹桥 SHA`、`遵义新舟 ZYI` / `遵义茅台 WMT`）。
   - 双模式（`一三五日` / `二四六`）可兑日优先采样与按需 7 天全周期精确校准，真实还原逐趟航班执飞星期。

2. **近 40 天放票日历详情 & 多星期/任意日往返快搜**
   - 点击任意航线卡片进入 **近 40 天月历详情页**，支持点选任意日期实时查询多舱位聚合的**畅飞卡剩余张数**、普通余票与现金参考价，并一键跳转往返搜索。
   - **自定义往返模板**支持单选、**多选去程星期几**及 **「任意」** 出发日，自动计算目的地最长可停留时长，并支持随时可停止的防风控批量日历核验。

3. **行程计划与精确抢票提醒闹钟**
   - 选定去返航班保存至「行程提醒」，内置基于 **北京时间（`Asia/Shanghai`）** 推算的智能预设（提前 30 天 `09:55` 去/返程开抢提醒、提前 4 天 `20:00` 兑换截止提醒、次日早刷回流票提醒）及任意自定义时间。
   - 基于 `AlarmManager.setExactAndAllowWhileIdle` 实现息屏唤醒，支持系统重启、应用覆盖升级及系统时间/时区变更自动重排闹钟，并可一键导出至手机系统日历。

4. **端侧安全与防风控保护**
   - 所有会话凭据、手机号、证件号、`scgTid` 及诊断记录均通过 `AndroidKeyStore` 硬件级 **AES-256-GCM** 加密存储于本机，并关闭系统云备份导出，不经过任何第三方服务器。
   - 内置全局网关限流器（`ApiRateLimiter`）与 Token 刷新互斥锁（`Mutex`），遇到瞬时限流自动指数退避。

---

## 本地编译与测试

需要 **JDK 17** 与 **Android SDK (API 35)**：

```powershell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-17.0.21.10002-hotspot"
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"

# 运行单元测试、Lint 静态检查、Debug 与 R8 混淆压缩 Release 构建
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug assembleRelease
```

生成产物路径：
- Debug APK：`app/build/outputs/apk/debug/app-debug.apk`
- Release APK（已启用 R8 混淆与资源压缩）：`app/build/outputs/apk/release/app-release.apk`
