# 长龙畅飞规划 (LoongAir Trip Planner · Android)

一款专为长龙航空畅飞卡旅客打造的**纯本地原生 Android App**，主打**指定日期/时段往返行程快搜**、**实时余票核验**与**精确抢票闹钟提醒**。

> 长龙官方网关协议（`webhandler.ashx`）与基础航网排班种子（`routes_seed.json`、`cities_meta.json`）参考开源项目 [eureka-wu/clairline](https://github.com/eureka-wu/clairline)（MIT License）。

## 核心功能

1. **常驻杭州 · 秒搜指定日期/时段往返行程**
   - 默认常驻出发城市为 **杭州 (HGH)**（可在「账号与设置」中修改为任意通航城市）。
   - 内置 71 座城市 / 490+ 班次离线排班种子，未登录即可按去程日期+起飞时段、返程日期+起飞时段自动配对全国所有可往返目的地，并计算可停留时长。
   - 支持 **自定义往返模板**（预置「周五晚→周日」「周六早→周日」「下周五晚→下周日」「周四晚→周日」，支持任意新增、编辑、删除自定义模板）与一键「上周 / 下周」切换。
2. **直连长龙官方核验放票日历与余票**
   - 在「账号与设置」页通过 **手机号 + 官方网易易盾安全验证 + 短信验证码** 本地直登（也支持手动导入 `SESSION` / `scgTid`）。
   - 支持单目的地实时查询去程与返程各班次的 **畅飞卡剩余张数**、普通余票与兑换价，并支持防风控限速批量核验放票日历（随时可停止）。
   - 支持拉取你名下畅飞卡从常驻城市出发的官方可达城市列表。
3. **安排行程计划 + 精确抢票提醒闹钟**
   - 选定去程与返程航班加入「行程与提醒」，支持一键设定：
     - **去程开抢前 5 分钟**（自动按提前 30 天 09:55 推算）
     - **返程开抢前 5 分钟**
     - **兑换截止前提醒**（起飞前 4 天 20:00）
     - **完全自定义精确日期时间**
   - 基于 Android `AlarmManager.setExactAndAllowWhileIdle` + 高优先级通知 + 重启自动恢复（`BOOT_COMPLETED`），并支持 **一键写入手机系统日历** 作为双保险。

## 本地编译命令

```powershell
$env:JAVA_HOME = "$env:USERPROFILE\.local\jdks\jdk-17.0.21.1+2"
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
.\gradlew.bat testDebugUnitTest assembleDebug
```

编译生成的 APK 位于：
`app/build/outputs/apk/debug/app-debug.apk`
