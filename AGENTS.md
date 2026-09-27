# Lichen History CLI

> 在 Minecraft 中实现类 bash 的 `history` 命令，管理**本模组自持的命令历史日志**。

## 许可证

- **代码**：以 GNU Lesser General Public License v3.0 或更新版本（LGPL-3.0-or-later）授权。
- **项目文档**：以 Creative Commons Attribution-ShareAlike 4.0（CC BY-SA 4.0）授权。
- **图标** `icon/icon.png`：**CC BY-NC-SA 4.0（禁止商业使用）**，与代码许可不同，见 `icon/README.md`。

---

## 1. 概述

面向服务的多平台 Minecraft 历史命令工具。模组在所有支持的平台上维护**自己独立的命令历史日志**，**从不读写或干涉原版**的 `command_history.txt` / `.console_history` / 上下键内存历史。因此不存在"接管/共存"冲突，也不会有 `/!` 原文残留问题。

### 支持平台

| 平台 | 产物 |
| --- | --- |
| Fabric / Quilt | **单一 jar** |
| NeoForge | **单一 jar**（26.2+） |
| Forge | **单一 jar**（26.2+） |
| Bukkit 系（Spigot/Paper/Purpur/Leaves/Leaf） | 单一插件 jar（Folia 兼容） |
| Velocity / BungeeCord(Waterfall) | 单一代理 jar |

**没有"客户端版 / 服务端版"两种构建**——每个加载器只产出一个 jar，
客户端 / 专用服务器 / 集成服务器的行为在**运行时按当前环境注入**：

| 运行环境 | 注入的行为 |
| --- | --- |
| 客户端 | 客户端命令、客户端 mixin（`sendCommand` + `sendChat` 拦截）、配置屏、客户端历史存储 |
| 专用服务器 | 服务端命令、玩家/控制台命令录入、服务端历史存储 |
| 集成服务器（`enable_integrated_history=true`） | 在客户端运行时额外启用服务端 mixin 与 `<存档>/data/lichenhistorycli` 历史存储 |

实现方式：
- **Fabric**：`fabric.mod.json` 中 client mixin 配置标 `environment: "client"`，包监听等两端共用的 mixin 标 `"*"`；
- **NeoForge/Forge**：公共 `@Mod` 类只注册通用事件，客户端事件由独立类在 `dist.isClient()` 时才注册
  （否则专用服务器会因解析客户端类型 `NoClassDefFoundError`）；
- **Bukkit / Velocity / BungeeCord**：事件监听本身即由运行环境决定触发与否。

> 依赖声明的 Minecraft 范围是 `[26.2,27)`（见 `gradle.properties` 的 `minecraft_version_range` 与各 `mods.toml` / `fabric.mod.json`）。
> 此前 AGENTS.md 声称"26.1~26.2 单 jar 通用"，但**只按 26.2 编译与测试过**，且 mods.toml 里根本没有依赖声明，故已统一为 26.2+。

---

## 2. 构建结构（单仓库多模块，仿 LuckPerms）

```
settings.gradle              # include :common :fabric :neoforge :forge :bukkit :velocity :bungee
build.gradle                 # 版本三态逻辑 + subprojects 统一（group 取自 mod_group_id）
gradle/libs.versions.toml    # 版本目录
common/
  HistoryStore               # 内存缓冲 + 文件读写 + 展开（!!/!n/!-n/!string）+ 落盘跟踪
  HistoryParser              # 子命令参数解析
  HistoryCommandHandler      # 平台无关命令分派（修一次全端生效）
  HistoryCliConfigIO         # JSON 配置 IO（Fabric 用）
  HistoryCliTomlConfigIO     # TOML 配置 IO + 按 key 合并写回（Forge/NeoForge 用）
  HistorySuggestions         # Brigadier tab 补全（仅 mod 平台用）
  PermissionNode             # 权限节点常量与判定（代理端 / Bukkit）
fabric/                      # loom + jar-in-jar 嵌入 common
neoforge/  forge/            # 双端命令 + CommandEvent 历史记录 + 配置屏
bukkit/                      # 服务端命令 + ServerCommandEvent + PlayerCommandPreprocessEvent，folia-supported
velocity/  bungee/           # 代理端命令 + 命令事件自记录
```

`common` 对 gson/brigadier 是 `compileOnly`（测试用 `testImplementation` 补齐）：
gson 在 Paper/Velocity/BungeeCord 运行时均由平台提供，brigadier 只有 mod 平台会用到。

### 版本三态

`build.gradle` 的 `resolveVersion()`：
- 本地构建 → `26-SNAPSHOT`
- GitHub Actions push → `26-dev.<sha 前 7 位>`
- GitHub Actions pull_request → `26-pr.<sha 前 7 位>`（与 push 区分）
- tag 发版 → `26-<标签名去前导 v>`

产物命名：`historycli-<加载器>-<版本>.jar`。每个 shadow 模块**只产出这一个 jar**——
普通 `jar` 任务被禁用（它与 `shadowJar` 默认文件名相同、会互相覆盖，可能发布出缺少 `common` 的空壳 jar），
所以 `build/libs` 里不会再出现第二个版本的产物。

### 字节码级别（分层）

`common` 与 `velocity`/`bungee` 用 `--release 21`，`fabric`/`neoforge`/`forge`/`bukkit` 用 `--release 25`：

| 模块 | release | 原因 |
| --- | --- | --- |
| common | 21 | 会被 shade 进下面两个插件，必须跟着降级 |
| velocity / bungee | 21 | Velocity 3.x 要求 Java 21、BungeeCord 约 17/21；用 25 编译会在真实代理上 `UnsupportedClassVersionError` |
| fabric / neoforge / forge / bukkit | 25 | 它们要读 Minecraft 26.2 / Paper 26.2 的 class major 69 类文件 |

验证方式：解压产物看 class 文件头的 major 版本（65=Java 21，69=Java 25）。

---

## 3. 数据模型：mod 自持独立日志

所有端的历史统一存本模组自己的文件，格式为**每行一条命令**：

| 端 | 路径 |
| --- | --- |
| 客户端 / 专用服务器（Fabric/NeoForge/Forge） | `<游戏目录>/local/historycli/command_history.log` |
| 集成服务器（单人/局域网，`enable_integrated_history=true`） | `<存档>/data/lichenhistorycli/command_history.log` |
| Bukkit | `<服务器目录>/local/historycli/command_history.log` |
| Velocity | `plugins/lichenhistorycli/command_history.log`（插件 id） |
| BungeeCord | `plugins/LichenHistoryCli/command_history.log`（插件名） |

- `local/` 为 Forge/NeoForge mod 数据惯例目录；NeoForge/Forge 走 `FMLPaths.GAMEDIR`，不依赖进程 CWD。
- 原版历史文件 / 上下键内存：**不读、不写、不干涉**。
- **日志是全局共享的**：服务端/代理端所有玩家与控制台共用一条，条目**不记录来源**，
  `ignoredups` 会跳过"连续两条完全相同"的命令（不同玩家连续敲同一条时第二条不入史）。

### 配置

| 平台 | 配置文件 |
| --- | --- |
| Fabric | `config/lichen-history-cli.json` |
| NeoForge / Forge | `config/lichen-history-cli.toml` |
| Bukkit / BungeeCord | `config.yml` |
| Velocity | `plugins/lichenhistorycli/config.properties` |

字段：
- `language`（Bukkit/代理端）
- `enable_integrated_history`（mod）
- `record_history`、`history_size`（0 = 不限，默认 500）
- **`record_forwarded_commands`（仅代理端，默认 `false`）**：是否记录"会被转发到后端服务器"的命令。
  默认只记录**发给代理本身**的命令（Velocity 按 `PostCommandInvocationEvent` 的 `FORWARDED` 结果区分，
  BungeeCord 按 `PluginManager.isExecutableCommand` 判定）。

配置屏（Fabric/NeoForge/Forge）与 `reload` 子命令都会**真正重新读取配置并应用到存储**；
写回时按 key 合并，用户的注释与自加键不会被抹掉。**配置损坏时 `reload` 回 `config_reload_failed`**
（不会回落成默认值还报"重载成功"）。

---

## 4. 命令体系

### 客户端（仅本机生效）

| 完整命令 | 普通命令 |
| --- | --- |
| `/historycliclient`（别名 `historyclic`、`historycli`） | `/historyclient`（别名 `historyc`、`history`） |

- 短名按"尽力注册"处理，只检查本地命令树，**服务端同名命令仍可能被客户端短名遮蔽**（Fabric 客户端命令优先级更高）。
- 子命令：列表、`<num>`、`-c/-w/-a/-r`、`-d`、`reload`（完整命令）；普通命令为 bash 标准子集（无 `reload`/`!`/`-d`）。
- `!` 系列（`!!`、`!n`、`!-n`、`!string`）：裸输入（不带 `/`）与 `/!!` 均可展开，只操作本 mod 日志。
  - Fabric/NeoForge：拦截 `sendChat` 与 `sendCommand`，两种输入都生效。
  - Forge：只有 `ClientChatEvent`（来自 `sendChat`），故**仅裸 `!!` 生效**，见"已知限制"。

### 服务端

| 完整命令 | 普通命令 |
| --- | --- |
| `/historycliserver`（别名 `historyclis`、`historycliser`） | `/historyserver`（别名 `historys`、`historyser`） |

- 权限：mod 端用 `Permissions.COMMANDS_GAMEMASTER`（原 op level 2）；Bukkit 端为 op 或 `historycli.*` 权限节点。
- **集成服务器**同样可注册（`enable_integrated_history=true`），并非"仅专用服务器"。
- 命令入史来源：
  - Fabric：玩家聊天命令（`handleChatCommand`/`handleSignedChatCommand`）+ 控制台（`handleConsoleInput`）。
  - NeoForge/Forge：`CommandEvent`（含集成服务器）。
  - Bukkit：`ServerCommandEvent`（控制台）+ `PlayerCommandPreprocessEvent`（玩家）。
- 以 `!` 开头的输入**不入史**（避免历史里留下字面量 `!!`）。各入口的展开支持情况：

  | 入口 | 展开？ |
  | --- | --- |
  | Fabric **控制台**裸 `!!` / `!5` | ✅ `DedicatedServerMixin` |
  | **Bukkit** 玩家 `/!!` 与控制台 `!!` | ✅ 且与命令形式走**同一道** `historycli.execute` 权限门 |
  | Fabric / NeoForge / Forge **玩家**敲 `/!!` | ❌ 原样交给原版 → "未知命令" |
  | NeoForge / Forge **控制台**裸 `!!` | ❌（见 §6 #1） |
  | 任意端 `/historycliserver !!` | ✅ 全部平台 |
  | 聊天框里多词的 `!` 输入（`!hello world`）、孤立 `!` | 不视为展开请求，**原样放行**（不能吞掉这类聊天） |

### 代理端

| 完整命令 | 普通命令 |
| --- | --- |
| `historycliproxy`（别名 `historyclipro`、`historyclip`） | `historyproxy`（别名 `historypro`、`historyp`） |

- 无 op，权限按子命令节点判定（`PermissionNode.isGranted`），支持 LuckPerms：
  - `historycli.list/clear/write/append/read/delete/execute/reload`
  - `historycli.*` 通配全部
  - `historycli.use` **仅放行只读的列表/帮助**（不含任何写操作与 `!` 执行）
- Velocity 与 BungeeCord 都已鉴权；BungeeCord 控制台（以及其它非玩家发送者）直接放行。
- **默认拒绝**：Velocity 玩家若未装权限插件（LuckPerms 等），`hasPermission` 一律为 false，
  两个代理命令都不可用——这是平台默认行为，需给玩家授予 `historycli.*` / `historycli.use`。
- **查询/展开一律走命令形式**：`historycliproxy !!`、`historyproxy`——
  **代理端没有聊天侧 `!!` 展开，控制台也不支持裸 `!!`/`!n`/`!-n`**。
  （BungeeCord 曾用 `ChatEvent.setMessage` 做聊天侧展开，但 1.19+ 客户端的
  `UpstreamBridge.handle(ClientCommand)` 会丢弃改写结果——展开无效还写了幽灵历史行，已移除。）
- **入史来源与口径**（默认只记发给代理的命令，见 `record_forwarded_commands`）：
  - **Velocity**：`PostCommandInvocationEvent`（执行后的结果回执）——
    `EXECUTED`/`SYNTAX_ERROR`/`EXCEPTION` 记录，`FORWARDED`（转发到后端）仅在开关打开时记录；
    另由 `CommandExecuteEvent` 补上「其它插件强制 forward/denied」这两种 Post 事件不会触发的场景。
    控制台同样走 `executeAsync` → **控制台命令会入史**。
  - **BungeeCord**：`ChatEvent` + `PluginManager.isExecutableCommand(命令名, sender)` 判定代理是否消费；
    **控制台命令无法入史**：该版本 API 没有 `CommandEvent`（事件清单里只有 Chat/TabComplete/Server* 等），
    插件侧不存在可拦截控制台输入的钩子。

### 历史展开（bash 语义）

`!!` 上一条、`!n` 第 n 条、`!-n` 倒数第 n 条、`!string` 最近以 string 开头的命令（大小写敏感）。

- 平台在进入处理器之前就把本次调用记入了历史，`HistoryStore.dropSelfInvocation` 会先把这条
  **本次调用本身**剔除，否则 `!!` 会解析到自己 → 执行 → 再进入处理器 → 无限递归。
- 展开结果若**仍然是 `!` 形式**（历史文件被外部编辑成字面量 `!!`），一律按"无匹配"拒绝，防止递归。
- 展开结果若是 `historyxxx !n` 形态（被权限拒绝的调用也会被记录，从而留在历史里），
  同样拒绝——它不以 `!` 开头，放行会派发回本命令再次展开；另有**深度上限 4** 兜底。
- **孤立的 `!` 一律按"无匹配"处理**（bash 的 event not found 语义）：
  否则聊天框里误敲一个感叹号就会重跑上一条（可能是 `/stop` 这类破坏性命令）。
- 展开后把**展开结果**记入历史（不再记录 `!!` 这样的请求本身），并以「去掉前导 `/`」的命令执行。
- 超长数字（`!99999999999999`）按"无匹配"处理，不抛 `NumberFormatException`。
- 代理端展开到**后端服务器的命令**无法由代理执行：Velocity/BungeeCord 会回报
  `historycli.msg.not_executable`，而不是静默无操作。

---

## 5. 构建与测试

```powershell
.\gradlew.ps1 build          # 全 7 模块
.\gradlew.ps1 :common:test   # 单测
```

- 需要 JDK 25 + Gradle（wrapper 9.6.1，已加 `distributionSha256Sum` 校验）。
- 运行目录：客户端 `run/Client`，服务端 `run/Server`（Fabric/NeoForge）。
- `common` 单测 **56 个、0 失败**，覆盖：
  - `HistoryStore`：落盘 / `dirtyCount`（`-r` 不丢未落盘行、`delete`/`trim` 后的不变量）、
    **`-w` 临时文件 + 原子替换**、`!!` 自引用剔除、数字溢出、孤立 `!` 拒绝、连续去重；
  - `HistoryCommandHandler`：reload 钩子（含失败回执）、IO 错误转译、递归防护
    （含 `historyxxx !n` 形态与深度上限）、畸形参数回报失败、存储未初始化；
  - `HistoryParser`（`HELP` 与 `PARSE_ERROR` 的区分）、`PermissionNode`（`use` 只读语义、通配）；
  - JSON/TOML 配置 IO：注释与自加键保留、**行尾注释的空格与缩进**、类型错值回落默认。

---

## 6. 实现状态与已知限制

| 里程碑 | 状态 |
| --- | --- |
| M0 骨架（7 模块 + CI + 版本三态） | 完成 |
| M1 Fabric 客户端核心 | 完成 |
| M2 Fabric 客户端 `!` 展开 | 完成 |
| M3 Fabric 服务端 | 完成 |
| M5 NeoForge + Forge 服务端 | 完成 |
| M6 Bukkit 系（Folia） | 完成 |
| M7 代理端（Velocity/Bungee） | 完成 |
| M4 复用重构（HistoryCommandHandler 单点管理） | 完成 |

已知限制：

1. **NeoForge/Forge 控制台直接输入 `!5` 不展开**（输入经 JLine 直达，不触发 `CommandEvent`）；
   用 `historycliserver !5` 可用。
2. ~~**Forge 客户端无法记录经 `/` 发出的命令**~~ → **已修复，待运行时复验**：
   Forge 26.x 的 `ClientChatEvent` 只由 `sendChat` 触发，`sendCommand` **不发任何事件**
   （其字节码头部是 `ClientCommandHandler.runCommand`，直接发包）——因此已给 Forge 模块补上 mixin：
   `lichenhistorycli.forge.mixins.json` + `ClientPacketListenerMixin`，
   生产靠 jar manifest 的 `MixinConfigs` 属性、开发环境靠 FG runs 的 `-mixin.config=` 参数发现
   （FML 的 `ModDirTransformerDiscoverer` 就是扫描以 `-mixin`/`--mixin` 开头的启动参数）。
   `compatibilityLevel` 只能写 **`JAVA_21`**——Forge 26.2 带的是 Mixin 0.8.7，
   其 `CompatibilityLevel` 枚举最高只到 `JAVA_21`，写 `JAVA_25` 会在加载时抛
   `MixinInitialisationError`。裸 `!!`（走 `sendChat`）仍然可用。
3. **以 `!` 开头的真实命令不会入史**（各平台统一）：无法区分"历史展开请求"与"以此为名的命令"。
4. **代理端展开只能执行代理端注册的命令**：展开到后端的命令会回报 `not_executable`。
5. **客户端短名可能遮蔽服务端同名命令**（Fabric 客户端命令在客户端命令树中优先）。
6. **Bukkit 记录点在 `EventPriority.LOW`**：若其它插件在其之后才取消该命令，该条仍会入史。
7. **崩溃不丢历史仅限代理端**：Velocity/BungeeCord 每分钟定时异步落盘；
   Bukkit/mod 端仅在 `onDisable`/停服/断线时写盘，进程被 kill 会丢失本次会话新增记录。
   （`-w` 已改为"写临时文件 + 原子替换"，落盘过程中崩溃不会再截断整个日志。）
8. **BungeeCord 控制台命令不入史**：该版本 API 未提供 `CommandEvent`，无钩子可用（见 §4）。
   同理，BungeeCord 侧的控制台 `!` 展开也不可用。
9. **转发到后端的命令默认不入史**（`record_forwarded_commands=false`）：
   代理历史只反映"发给代理本身的命令"；需要时打开该开关即可恢复全量记录。
10. **代理端没有聊天侧 `!!` 展开，控制台也不支持裸 `!!`/`!n`/`!-n`**：
   查询/展开一律用 `historycliproxy !!`、`historyproxy`（见 §4）。
11. **mod 服务端玩家敲 `/!!` 不展开**（只有 Fabric 控制台与 Bukkit 两端会展开），见 §4 的展开支持表。
12. **运行时实测状态**（编译 + 单测全部通过）：
    - ✅ **NeoForge 客户端已实测**（`gradlew :neoforge:runClient`）：日志证明
      `Mixing ClientPacketListenerMixin ... into ClientPacketListener`、两个 `@Inject` 均应用成功，
      敲 `/time set ...` 后 `/history` 能列出并落盘到 `local/historycli/command_history.log`。
    - ⬜ 其余端待实测：Fabric 客户端/服务端、Forge 客户端（新 mixin）、Bukkit/代理端。
    - **静态已核对**：全部 `@Inject` 目标方法都存在于对应 26.2 jar 中
      （`handleChatCommand`/`handleSignedChatCommand`/`handleConsoleInput`/`sendCommand`/`sendChat`），
      参数签名与注入方法一致——历史上（2026-08-31 的日志）旧 jar 用了不存在的 `handleCommand`，
      导致 `MixinApplyError` + **玩家无法进入服务器**（`Couldn't place player in world`），
      这类错误必须靠 `runClient`/`runServer` 才能拦住。
13. **`pack.mcmeta` 必须带 `min_format` / `max_format`**：MC 26.x 对高版本号的 pack
    （>64）强制要求这两个字段，缺了会报
    `Pack declares support for version newer than 81, but is missing mandatory fields min_format and max_format`
    并回退解析——直接影响装在 `assets/<modid>/lang/` 下的翻译文件。

---

## 7. 环境与目录

- 代码包名统一 `org.anjisuan608.historycli`；平台 ID `lichenhistorycli`；显示名 "Lichen History CLI"（不支持空格的平台用驼峰 `LichenHistoryCli`）。
- 语言文件统一在根 `lang/`，由各模块 `processResources` 打进相应位置
  （mod 端 → `assets/lichenhistorycli/lang/`，插件端 → jar 根）。
- `temp/`、`tmp/`、`eps/` 为本地参考代码与临时产物，已在 `.gitignore` 中忽略，不要提交。
