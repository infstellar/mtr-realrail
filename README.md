# 更好的列车限速及信号判定 · MTR RealRail

> MTR 附属模组 v1.5.0，支持 Minecraft **1.20.1** + **Forge 47.x** + **MTR 4.0.5**，以及 Minecraft **1.21.1** + **NeoForge 21.1.265** + **MTR 4.1.0-beta.2**。

在 MTR 原版基础上补齐"限速判定 / 信号隔离 / 动态占用 / 路径与发车调度 / 站台智能调度"的一整套运行控制能力。
NeoForge 1.21.1 版本把调度服务与执行桥一并包含在 RealRail JAR 内。

---

## 调度组件

NeoForge 1.21.1 的 `mtrrealrail-1.5.0+1.21.1-neoforge.jar` 已包含调度与执行代码，客户端和服务端只需安装 RealRail 与匹配版本的 MTR。首次启动会生成 `config/tsc-dispatch.yml`；当前存档的调度状态保存在 `world/mtrrealrail/dispatch/`。Forge 1.20.1 版本仍按原有方式使用外部调度服务和执行桥。

---

## 功能

| # | 功能 | 说明 |
|---|---|---|
| 1 | **列车限速判定** | 轨道连接器处按"**车尾完全通过**才变更限速"判定（`TAIL`，默认；可切回 MTR 原版 `HEAD`），刷子可逐节点切换 |
| 2 | **多信号系统隔离** | 只对**共享同一信号颜色**的列车做闭塞/让行判断，不同线路互不干扰 |
| 3 | **动态轨道占用** | 用列车实时位置/速度计算米级占用区间与安全间距，同信号系统内允许"移动闭塞"式跟车放行 |
| 4 | **自动最优路径规划** | 依据实时占用与前车关系打分，必要时触发 MTR 重新生成车厂路径 |
| 5 | **动态发车与避让** | 停站满阈值后按实时交通决定等待让行（有硬上限与冷却，绝不压死站台） |
| 6 | **车站站台智能调度** | 车站界面开关（≥2 站台才显示）+ 线路界面"线路智能调度站台"多选 GUI；运行时把列车改停到**允许且空闲**的站台 |
| 7 | **车厂在线路中间**（v1.5.0） | 车厂 → 优化线路配置开关：按最近路线驶向最近车站投入运行；时刻表进入"无车发出的时段"自动回场；开关切换自动**清除车辆 + 刷新路线** |
| 8 | **运行时改停站台**（v1.5.0） | 经 `set_platform` 指令让执行桥为运行中的列车**重新生成进路**，绕开 MTR `immutablePath` 不可替换的限制 |

界面入口：
- 铁路仪表板 → **车厂** → 「优化线路配置」（动态闭塞 / 路径规划 / 发车避让 / **车厂在线路中间**）
- 铁路仪表板 → **车站** → 右下角「站台智能调度: 开/关」（仅 ≥2 站台的车站）
- 铁路仪表板 → **线路** → 「线路智能调度站台」（按车站多选允许停靠站台，底部显示调度链状态）

---

## 安装

1. 使用 Forge 1.20.1 时，安装 MTR 4.0.5 Forge 版和 `mtrrealrail-1.5.0+1.20.1.jar`。仓库的 `dist/` 也提供同版本的 Forge 发布包。
2. 使用 NeoForge 1.21.1 时，安装 MTR 4.1.0-beta.2 NeoForge 版和 `mtrrealrail-1.5.0+1.21.1-neoforge.jar`。
3. 客户端与服务端都需要安装对应版本。NeoForge 1.21.1 不需要另外安装调度服务或执行桥。

---

## 配置

`config/mtrrealrail.properties`（首次启动自动生成；修改后需重启）：

```properties
# 限速判定
default_node_mode=TAIL            # TAIL（车尾通过才变）/ HEAD（MTR 原版）
signal_isolation=true             # 多信号系统隔离

# 车厂级默认值（每个车厂可在 GUI 中单独覆盖）
optimize_dynamic_block_release=true
optimize_safety_margin_meters=30
optimize_route_enabled=true
optimize_path_lookahead_meters=400
optimize_departure_guard=true
optimize_dwell_threshold_seconds=10
optimize_conflict_window_seconds=5
optimize_max_hold_seconds=15

# 调度功能开关
tsc_dispatch_enabled=true
tsc_dispatch_required=true        # true = 执行链不可用时等待，不启用本地选台
execution_chain_required=true     # 要求调度服务与执行桥都已就绪
```

NeoForge 调度参数、执行桥端口和管理页端口由 `config/tsc-dispatch.yml` 管理；该文件首次启动时自动生成。执行桥默认只绑定 `127.0.0.1`，非本机访问时必须配置令牌。

存档级配置（随世界保存）：

```
world/mtrrealrail/platforms.json   # 车站开关 + 线路 × 车站 → 允许停靠站台
world/mtrrealrail/nodes.json       # 每个轨道连接器的限速判定方式
world/mtrrealrail/optimize.json    # 每个车厂的优化配置（含「车厂在线路中间」）
```

---

## 构建

Forge 1.20.1 使用 **JDK 17**，在 `mtr-realrail/` 目录运行：

```bash
bash ./gradlew build
```

产物为 `build/libs/mtrrealrail-1.5.0+1.20.1.jar`。构建会从 Modrinth 下载 MTR 4.0.5 并提取编译依赖。

NeoForge 1.21.1 使用 **JDK 21**，在 `mtr-realrail/` 目录运行：

```bash
./neoforge/gradlew -p neoforge build
```

产物为 `neoforge/build/libs/mtrrealrail-1.5.0+1.21.1-neoforge.jar`。NeoForge JAR 内含调度服务与执行桥；Forge 构建继续保留旧版外部调度连接方式。

> Windows 下若遇到 `Could not initialize native services`，把 `GRADLE_USER_HOME` 指到可写目录即可。
> 自检：服务端加 `-Dmtrrealrail.verifyMixins=true`，启动时会强制加载全部 Mixin 目标类，注入失败直接报错。

---

## 兼容性

- Forge 目标为 Minecraft 1.20.1 / Forge 47.4.23 / MTR 4.0.5。NeoForge 目标为 Minecraft 1.21.1 / NeoForge 21.1.265 / MTR 4.1.0-beta.2。
- 只依赖 `org.mtr.core` 公开成员与少数 MTR 自有方法名（`init2`、`writePlatformCache` 等）；MTR 大版本改名时主 Mixin 配置会直接报错提示。
- Forge 客户端界面 Mixin 的 `required` 为 `false`。NeoForge 在目标界面注入失败时会报告具体类和方法。
- 与其它 MTR 附属共存良好；配合两件套时由它们负责规划与执行，本模组提供策略、界面与状态展示。

## 目录结构

```
build.gradle / settings.gradle / gradle.properties   Gradle 工程
src/main/java/mtr/realrail/                          主模组（限速、信号隔离、调度、网络、界面）
src/main/java/mtr/realrail/mixin/                    Mixin（Vehicle / Rail / PathData / 界面注入）
src/main/java/mtr/realrail/traffic/                  动态占用、发车避让、站台调度、车厂中间调度
src/main/java/mtr/realrail/dispatch/                 调度链客户端（tsc-dispatch / 执行桥）
docs/                                                设计、实现与联调记录
dist/                                                发布 jar
```

## 更新日志

### v1.5.0
- 新增：车厂「优化线路配置」开关 **车厂在线路中间**（最近车站派车 / 无班次回场 / 切换自动清车刷新路线）。
- 新增：运行时改停站台 —— 经 `set_platform` 由执行桥重新生成进路，解除"路径不可中途改写"限制。
- 修复：车厂配置表头被当作数据行导致整份配置读取失败。
- 修复：列车被无限期压在站台（车门不关）—— 前后车判定与决策表重写。
- 修复：车站界面 Mixin 含非 private static 方法导致整体不生效；界面重初始化死循环。
- 新增：车站站台智能调度（车站开关 + 线路允许集合多选 GUI + 动态分配）。
- 新增：动态轨道占用 / 自动最优路径规划 / 动态发车避让 + 车厂优化配置 GUI（v1.1.0）。

## 许可

MIT，见 [LICENSE](LICENSE)。
