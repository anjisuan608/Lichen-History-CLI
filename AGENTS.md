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
| Bukkit 系 **bukkit 版**（CraftBukkit / Spigot，也能跑在 Paper 系上） | 单一插件 jar，**MC 26.1.x ~ 26.3** |
| Paper 系 **paper 版**（Paper / Purpur / Leaves / Leaf + Folia） | 单一插件 jar，**MC 26.1.x ~ 26.3** |
| Sponge **sponge 版**（SpongeVanilla / SpongeForge） | 单一插件 jar，**Sponge API ≥ 12（MC 1.21.1 ~ 26.3；26.x 为 RC/实验性）** |
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
>
> **只有 Bukkit 系覆盖到 26.1**：插件没有 mod 那样的版本依赖声明，靠 `plugin.yml` 的 `api-version`
> 与"按最低版本编译"来保证，见 §2 的 Bukkit/Paper 版本兼容。

---

## 2. 构建结构（单仓库多模块，架构参考开源项目 LuckPerms）

```
settings.gradle              # include :common :fabric :neoforge :forge :bukkit :paper :velocity :bungee
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
paper/                       # **复用 bukkit 的源码**（sourceSets 指过去，不复制第二份）
                             #   + Paper/Folia 专属：paper-plugin.yml 原生加载、Brigadier 命令、
                             #     Adventure 可点击列表行、AsyncScheduler 每分钟异步落盘
sponge/                      # Sponge 适配：sponge_plugins.json（java_plain loader）、
                             #   Command.Raw 命令、ExecuteCommandEvent.Pre 录制、异步落盘
velocity/  bungee/           # 代理端命令 + 命令事件自记录
```

`common` 对 gson/brigadier 是 `compileOnly`（测试用 `testImplementation` 补齐）：
gson 在 Paper/Velocity/BungeeCord 运行时均由平台提供，brigadier 只有 mod 平台会用到。

### 版本三态

`build.gradle` 的 `resolveVersion()`：
- 本地构建 → `26-SNAPSHOT`
- GitHub Actions push → `26-dev-<sha 前 7 位>`（分隔符是 `-` 不是 `.`）
- GitHub Actions pull_request → `26-pr-<sha 前 7 位>`（与 push 区分，分隔符同样是 `-`）
- tag 发版 → `26-<标签名去前导 v>`

### 构建版本 vs 最低版本（`gradle.properties`）

**编译用的版本**与**发布给用户的下限**是分开的两个键（对应 NeoForge MDK 的 `*_min` 设计）：

| 构建版本（编译/dev 用） | 最低版本（写进元数据，低于它拒载） |
| --- | --- |
| `minecraft_version=26.2`（**被 forge 用作构件坐标，不能动**） | `minecraft_version_range=[26.1,27)`（mod 平台元数据，Forge/NeoForge 共用） |
| **`minecraft_version_fabric=26.1.2`**（Fabric 单独的编译目标） | `minecraft_version_range_fabric=>=26.1 <27`（Fabric 语法是 `>=X <Y`，见下方 Fabric 小节） |
| `neo_version=26.2.0.88` | `neo_version_min=26.1.0.0` → `neoforge.mods.toml` 的 `versionRange` |
| `forge_version=65.1.3` | `forge_version_min=62.0.0` → `mods.toml` 的 `versionRange` |
| `fabric_loader_version=0.19.3` | `fabric_loader_version_min=0.19.3` → `fabric.mod.json` 的 `depends.fabricloader` |

初始下限 = 构建版本（只在这些版本上验证过）；日后在更老版本上实测通过，**单独下调 `*_min` 即可**，
不必动构建版本。每个 `*_min` 都同时登记为 `processResources` 的 `inputs.property`，
否则改了 gradle.properties 而增量构建没重跑，元数据里的版本范围会悄悄停留在旧值。

### 构建工具版本（`gradle/libs.versions.toml`）—— 与 `neo_version` 强耦合

| 项 | 值 | 说明 |
| --- | --- | --- |
| `moddevgradle` | **`2.0.147`** | 官方 26.2 示例模板规定的版本 |
| `forgegradle` | `[7.0.21,8.0)` | 区间依赖，换时间构建可能拿到不同版本 |
| `shadow` | `8.3.8` | 固定 |
| `loom` | `1.17-SNAPSHOT` | 浮动 |
| `paperApi` | `26.1.2.build.74-stable` | **故意取最低支持版本**，见下方 Bukkit 小节 |

**升级 `neo_version` 时必须同步检查 MDG 版本**，否则会出现极难定位的失败：
NeoForge 0.88 **构件内**的 `ats/accesstransformer.cfg`（在 userdev jar 里，不在本仓库）
新增了 `public net.minecraft.core.HolderSet$Named contents()` 和 `public net.minecraft.core.HolderSet$1 contents()`
两条 AT。MDG 的 JST 把 AT 应用到**源码**时，`HolderSet$1` 是匿名类、源码里没有同名声明，
于是只有 `Named` 被改成 `public`，匿名子类仍是 `protected` → `recompile` 阶段报
`contents() 无法覆盖 ... 尝试使用更弱的访问级别`，**7055 个源文件编译直接失败**。
MDG 2.0.144 + JST 2.0.10 会踩这个坑，2.0.147（JST 2.0.11）不会。

### Bukkit/Paper 版本兼容 —— 26.1.x ~ 26.3

插件没有 mod 那样的依赖声明，"能装上"完全由两件事保证：

1. **编译目标 = 最低支持版本**：`paperApi = 26.1.2.build.74-stable`，而不是最新的 26.2/26.3。
   这样编译器会当场拦住"误用了 26.2 才有的 API"，从而天然保证 26.1.x 上可加载。
   本插件的 API 面极小且都是老 API——`Bukkit`、`EventHandler`、`EventPriority`、
   `PlayerCommandPreprocessEvent`、`ServerCommandEvent`、`JavaPlugin`，26.1→26.3 无变动。
2. **`plugin.yml` 的 `api-version: '26.1'`（取最低支持版本的 major.minor）**：
   写成 `26.2` 会让 26.1.x 服务器认为插件需要更新的 API。
   - Paper 的 `PluginMeta#getAPIVersion` javadoc 明确补丁版本会被归并（`26.1.2` → `26.1`）；
   - 各版本 paper-api jar 内的 `apiVersioning.json` 佐证这一格式：
     `26.1.2.build.74` → `26.1.2`、`26.2.build.119` → `26.2`、`26.3-pre-2` → `26.3`；
   - 此前写的 `1.21` 是错值，但**Paper 从不因 api-version 拒载**（`CraftServer` 里没有校验，
     `UnsafeValues#isSupportedApiVersion` 只被 `isLegacyPlugin()` 用来判定 pre-1.13 遗留插件，
     LuckPerms 至今仍写 `1.13`），所以这个错误一直是隐性的。

Java 下限不变：26.x 的 paper-api 类文件是 major 69（Java 25），故 `options.release = 25`。

### Fabric/Quilt 版本兼容 —— 26.1 ~ 26.3

1. **编译目标与声明范围分开**：`minecraft_version=26.2` 同时被 forge 用作构件坐标
   （`net.minecraftforge:forge:26.2-65.1.3`），**改全局属性会弄坏 forge**，因此 Fabric 单开
   `minecraft_version_fabric=26.1.2`（root `ext.minecraftVersionFabric` → loom 的 `minecraft` 依赖），
   声明范围 `minecraft_version_range_fabric=>=26.1 <27`，`fabric_api_version` 同步降到 `0.155.3+26.1.2`。
   > **fabric-api 是按 MC 版本发布的**：拿 `+26.1.2` 的 fabric-api 跑 26.2 服务端会被 Loader 判为
   > `Incompatible mods found` 直接拒启（实测踩过），给 26.2/26.3 测试装服时必须换对应版本。
2. **按最低版本编译立刻抓到真 bug**：原客户端 mixin 用 `mc.gui.hud`（**26.2 才有**），
   而 `Gui.getChat()`（≤26.1）与 `Gui.hud`（26.2+）两版互斥 → 在 26.1 上**根本编译不过**。
   改用 `LocalPlayer#sendSystemMessage(Component)`（26.1.2 与 26.2 都存在；javap 确认两者
   内部分别走 `getChatListener()` 与 `gui.chatListener()`，行为由 Mojang 保证）。
3. **mixin 不需要 refmap**：Loom 1.17 **默认关闭 Mixin AP**，加 `loom.mixin { }` 反而报
   `The mixin annotation is no longer enabled by default...` 弃用提示；生产（intermediary）
   由 Fabric Loader 的 **Mixin 0.17.x 运行时重映射**。因此 `*.mixins.json` 里**不要**写 `"refmap"` 键
   （写了会刷 `Reference map ... could not be read` 警告）。已在**生产服务端**实测 mixin 全部应用成功。
4. **mixin 目标逐版核对**（named jar javap）：`DedicatedServer.handleConsoleInput`、
   `ServerGamePacketListenerImpl.handleChatCommand/handleSignedChatCommand`、
   `ClientPacketListener.sendChat/sendCommand` 在 **26.1.2 与 26.2 全部存在**。
5. **`fabric.mod.json` 没有 `credits` 根字段**（Fabric Loader 报 `Unsupported root entry "credits"`）→ 已删除，
   致谢由 README/AGENTS 承载；`fabric/build.gradle` 的 expand 里也不再传 `mod_credits`。
6. **Quilt** 直接吃 Fabric 那份 jar（Quilt 兼容 Fabric 模组），无独立产物。

### NeoForge/Forge 版本兼容 —— 26.1 ~ 26.3

与 Fabric 不同，**loader 版本与 MC 版本绑死**（`net.minecraftforge:forge:<mc>-<fg>`、`neo_version=<mc>.<build>`），
所以这里不改构建版本，只按 §2 的既定原则**单独下调范围与 `*_min`**：

1. **元数据**（已构建核验）：`minecraft_version_range=[26.1,27)`、`neo_version_min=26.1.0.0`、
   `forge_version_min=62.0.0` —— 生成的 `neoforge.mods.toml` 是 `[26.1.0.0,)` + `[26.1,27)`、
   `mods.toml` 是 `[62.0.0,)` + `[26.1,27)`。**编译仍在 26.2**（`minecraft_version`/`neo_version`/`forge_version` 不动）。
   > forge 主版本与 MC 的对应：**62=26.1.0、63=26.1.1、64=26.1.2、65=26.2、66=26.3**，
   > 所以下限取 `62.0.0` 才与 `[26.1,27)` 一致（取 64.x 会把 26.1.0/26.1.1 挡在外面）。
2. ⚠️ **NeoForge 的 26.3 版本带 `-beta` 后缀**（`26.3.0.16-beta` …，共 16 个），
   26.1/26.2 则无后缀（`26.1.2.109`、`26.2.0.88`）——查版本/拼 URL 时**必须带后缀**，否则一律 404（踩过）。
3. **平台 API 逐类核对（floor/ceiling 双版本）**：
   - Forge `26.1.2-64.1.3` / `26.3-66.0.6` 的 universal：`RegisterCommandsEvent`、`CommandEvent`、
     `ServerStartedEvent`、`ServerStoppingEvent`、`MinecraftForge` **两版都在**；`@Mod` 在同版本的
     `javafmllanguage` 构件里 **两版都在**（`FMLPaths` 同理，属 FML 层、版本无关）。
   - NeoForge `26.1.2.109` / `26.3.0.16-beta` 的 universal：我们用的 8 个 `net.neoforged.*` 类
     （`NeoForge`、`RegisterCommandsEvent`、`CommandEvent`、`ServerStarted/StoppingEvent`、
     `RegisterClientCommandsEvent`、`ClientPlayerNetworkEvent`、`ClientStoppedEvent`）**两版都在**。
   - vanilla 侧（mixin 目标与 `sendChat`/`sendCommand`/`handleConsoleInput`、`GuiGraphicsExtractor`）
     由 **Fabric 生产实测（26.1.2 与 26.3）+ loom named jar** 双重背书 —— 两端用同一套 mojmap 名字。
4. **运行时实测状态见 §6**：四格（两端 × floor/ceiling）已用**生产安装器 + RCON** 全部通过；
   走 dev run 会撞上第 6 条与 §6 的网络坑，因此运行时验证一律用生产环境。
5. **按 floor 编译当场抓到的真 bug（已修）**：`HistoryCliForge` 与 `HistoryCliNeoForge` 都用
   `mc.gui.hud` 发"无匹配"提示 —— `Gui.hud` **26.2 才有**，而 `Gui.getChat()`（≤26.1）与之两版互斥，
   在 26.1 上**编译不过**（与 Fabric 同一个坑，见上方 Fabric 小节第 2 条）。两端统一改为
   `LocalPlayer#sendSystemMessage(Component)`（只判 `mc.player != null`），与 Fabric 侧写法一致。
   > 这正是"按最低版本编译"的价值：只按 26.2 编译的话，这个 bug 要到 26.1 真机上才暴露。
6. ⚠️ **Forge dev run 起不来（既有问题，与版本无关）**：`jar { enabled = false }`（为避免与 shadowJar
   同名互覆）之下，FML 在 dev 里只拿到 `build/resources/main` **一个根** —— 该目录有 `mods.toml`
   却没有任何 class，直接报 `constructed 0 mods ... The following classes are missing`。
   故 Forge 的运行时验证**走生产安装器实测**（与 Fabric/Bukkit/Paper 同一套方法），不依赖
   `:forge:runServer`；NeoForge/Forge 共用的 dev run 说明因此只列 Fabric/NeoForge。
7. **编译矩阵（两端 × floor/ceiling，2026-09-29 全部通过）**：

   | | floor | ceiling |
   | --- | --- | --- |
   | Forge | `26.1.2-64.1.3` ✅ | `26.3-66.0.6` ✅ |
   | NeoForge | `26.1.2.109` ✅ | `26.3.0.16-beta` ✅ |

   用 `-P` 覆盖构建版本即可复现（不改 `gradle.properties`）：
   ```powershell
   gradlew :forge:compileJava   -Pminecraft_version=26.1.2 -Pforge_version=64.1.3
   gradlew :forge:compileJava   -Pminecraft_version=26.3   -Pforge_version=66.0.6
   gradlew :neoforge:compileJava -Pneo_version=26.1.2.109
   gradlew :neoforge:compileJava -Pneo_version=26.3.0.16-beta
   ```
   > `minecraft_version` 只被 forge 构件坐标消费、`neo_version` 只被 MDG 消费，
   > 所以命令行覆盖它们不会波及其它模块（Fabric 已切到独立的 `minecraft_version_fabric`）。
   > **但是**：换版本会触发 FG/MDG 重新解析该版本的 loader 构件，机器上会遇到上面 §6 的网络坑，
   > 需按配方预置缓存（mavenizer 缓存 / `.m2` 的 `mojang-meta` 件）。
8. ⚠️ **`@Mod` 类里不能出现任何客户端类型（真机实测；与 MC 版本无关的通用坑）**：
   - **症状**：Forge/NeoForge 专用服务器一起服就 FATAL ——
     `Attempted to load class net/minecraft/client/gui/screens/Screen for invalid dist DEDICATED_SERVER`，
     栈顶是 `FMLModContainer.constructMod → Class.getDeclaredConstructor → RuntimeDistCleaner`，
     即**构造 `@Mod` 类的瞬间**就崩，mod 没有任何初始化机会。
   - **成因**（javap 常量池证据）：`HistoryCliForge` / `HistoryCliNeoForge` 曾经
     1. 构造器里直接写配置屏 lambda `parent -> new HistoryConfigScreen(parent)` —— 合成方法
        `lambda$new$0(Screen): Screen` 把 `Screen` 写进 `@Mod` 类常量池；
     2. `interceptClientCommand` 方法体里直接写 `Minecraft.getInstance()` / `mc.player.sendSystemMessage`。
        **只把调用放进 `if (dist.isClient())` 挡不住**：JVM 在 link/verify 阶段就按常量池解析这些类型。
   - **修法**：客户端引用全部下沉到客户端专用类
     （`forge/…/client/ForgeClientHooks`、`neoforge/…/client/NeoForgeClientHooks`），
     `@Mod` 类只留一句 `if (dist.isClient()) ForgeClientHooks.registerClient(...)` —— 该 `invokestatic`
     的描述符是 `()V`、不含客户端类型，且服务端分支不执行 → 钩子类永不被加载。
     客户端调用方（`ForgeClientEvents`、两端 mixin）随之改调钩子类。
   - **回归判据**（可复现，构建后必查）：
     ```powershell
     javap -p -v -classpath <解压后的 jar> org.anjisuan608.historycli.forge.HistoryCliForge | findstr net/minecraft/client
     ```
     **必须无输出**；一旦有输出，专用服务器必崩。
   - 该 bug 长期潜伏的原因：Forge/NeoForge 服务端此前从未真机测过（AGENTS 里一直是 ⬜）。

产物命名：`historycli-<加载器>-<版本>.jar`。每个 shadow 模块**只产出这一个 jar**——
普通 `jar` 任务被禁用（它与 `shadowJar` 默认文件名相同、会互相覆盖，可能发布出缺少 `common` 的空壳 jar），
所以 `build/libs` 里不会再出现第二个版本的产物。

### 字节码级别（分层）

`common` 与 `velocity`/`bungee`/`sponge` 用 `--release 21`，`fabric`/`neoforge`/`forge`/`bukkit`/`paper` 用 `--release 25`：

| 模块 | release | 原因 |
| --- | --- | --- |
| common | 21 | 会被 shade 进插件与代理，必须跟着降级 |
| velocity / bungee | 21 | Velocity 3.x 要求 Java 21、BungeeCord 约 17/21；用 25 编译会在真实代理上 `UnsupportedClassVersionError` |
| sponge | 21 | Sponge 平台（1.21.x）跑在 Java 21 上，同理不能用 25 |
| fabric / neoforge / forge / bukkit / paper | 25 | 它们要读 Minecraft 26.2 / Paper 26.x 的 class major 69 类文件 |

验证方式：解压产物看 class 文件头的 major 版本（65=Java 21，69=Java 25）。

---

## 3. 数据模型：mod 自持独立日志

所有端的历史统一存本模组自己的文件，格式为**每行一条命令**：

| 端 | 路径 |
| --- | --- |
| 客户端 / 专用服务器（Fabric/NeoForge/Forge） | `<游戏目录>/local/historycli/command_history.log` |
| 集成服务器（单人/局域网，`enable_integrated_history=true`） | `<存档>/data/lichenhistorycli/command_history.log` |
| Bukkit | `<服务器目录>/local/historycli/command_history.log` |
| Sponge | `<服务器目录>/local/historycli/command_history.log`（与 Bukkit 同一路径） |
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
| Sponge | `config/lichenhistorycli/lichen-history-cli.json`（与 Fabric 同一套 `HistoryCliConfigIO` 结构，取 `server` 分节；根级 `language` 可选，默认 `en_us`） |
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

- **bukkit 版与 paper 版的命令名、别名完全一致，注册方式不同**：
  bukkit 版走 `plugin.yml` 的 `commands` 字段；**paper 版的 `paper-plugin.yml` 没有该字段**，
  只能经 `LifecycleEvents.COMMANDS` 用 Brigadier 注册——顺带得到两个好处：
  返回 1/0 让 `/execute` 这类自动化能感知失败（Bukkit 路径恒返回 true），
  且 `LifecycleEventManager` 会在每次需要时（含 `/reload`）重新注册，不必自己处理重载时序。
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
.\gradlew.ps1 build          # 全 9 模块
.\gradlew.ps1 :common:test   # 单测
```

- 需要 JDK 25 + Gradle（wrapper **9.8.0**，已加 `distributionSha256Sum` 校验；
  `networkTimeout=60000` + `retries=3`，避免大发行版在慢链路上 10 秒读超时直接失败）。
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
7. **崩溃不丢历史**：Velocity/BungeeCord **与 paper 版**每分钟定时异步落盘；
   **bukkit 版**与 mod 端仅在 `onDisable`/停服/断线时写盘，进程被 kill 会丢失本次会话新增记录。
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
    - ✅ **Forge / NeoForge 服务端四格全部通过**（2026-09-29，**生产安装器** + RCON 驱动，非 dev run）：

      | | floor | ceiling |
      | --- | --- | --- |
      | Forge | `26.1.2-64.1.3` ✅ | `26.3-66.0.6` ✅ |
      | NeoForge | `26.1.2.109` ✅ | `26.3.0.16-beta` ✅ |

      每格判据全部成立：mod 加载日志 `Lichen History CLI (Forge/NeoForge server) loaded`
      → 原版对照 `list`/`help` 有回显 → `historycliserver`（裸，回显 1..N 列表）、
      `historycliserver list`（回显 1..4）、`historycliserver reload`（`Config reloaded`）
      → `stop` 干净停服打印 `history saved` → `local/historycli/command_history.log`
      收全 **6 条** 命令 → **errors 0**（我方 try/catch 兜底 0 触发）。
      > 这一轮同时修掉两个只有真机才会暴露的问题（见 §2「NeoForge/Forge 版本兼容」第 5、8 条）：
      > `@Mod` 类混入客户端类型 → 专用服务器构造 mod 类即崩；`gui.hud` → 26.1 编译不过。
      > 测试装置：`boot-test5.ps1`（RCON 驱动）——**控制台 stdin 在本机连纯净原版都会失败**，
      > 见上面「喂控制台的两条硬规矩」与 `MC_DEBUG_*` 开关。
    - ⬜ 其余端待实测：Fabric **客户端**、Forge/NeoForge **客户端**（配置屏与客户端 mixin，
      本轮把客户端代码整体搬进了 `*ClientHooks`，客户端 dist 尚未回归）、Velocity/Bungee 代理端。
    - ✅ **Fabric 服务端已实测**（2026-09-29）：
      - **MC 26.1.2 生产环境**（`fabric-installer` 组装、intermediary 映射，非 dev/Mojmap）：
        mod 加载（含 jar-in-jar 嵌入的 `common`）→ **mixin 全部应用、零报错** → `Done (3.018s)` →
        4 条命令执行并打印列表 → **71 bytes 历史入盘**。这一轮同时证实了两件事：
        **① mixin 不需要 refmap**（Loom 1.17 默认关闭 Mixin AP，生产由 Fabric Loader 的 Mixin 0.17.x
        运行时重映射；加 `loom.mixin{}` 反而报弃用、写 `"refmap"` 键反而刷 "could not be read" 警告，
        两者均已移除）；**② `fabric.mod.json` 不认 `credits` 根字段**（`Unsupported root entry`，已删）。
      - **MC 26.3 用户真机实测**：构建成功、模组正常运行。
      - 版本覆盖 `>=26.1 <27`（编译目标 `minecraft_version_fabric=26.1.2`）；mixin 目标已用 named jar
        javap 核对 **26.1.2 / 26.2 全部存在**，26.3 由上面的实测背书。
      - **26.2 生产未单独测**：给它装服时误用了 `fabric-api +26.1.2`（只认 26.1.x）→
        `Incompatible mods found`——这是**测试装置**问题，换 `0.161.0+26.2` 即可；26.2 夹在已验证的
        编译下限与实测上限之间，风险很低但如实记录。
    - ✅ **Bukkit 系已实测**（2026-09-28，三台 26.1.2 真机：`paper-26.1.2-74`、
      `Spigot-566f972-690a402`、`Folia-26.1.2-8`）：加载 → 启用 → RCON 调用
      （`list` / `reload` → `Config reloaded` / `plugins` → 显示本插件）→ 停服 `history saved` 三端全通；
      **`folia-supported: true` 在 Folia 上成立**，`api-version: '26.1'` 三端均被接受。
    - ✅ **paper 版 jar 已实测**（2026-09-28）：`paper-plugin.yml` 让 Paper **用自己的加载器**
      加载它——`/plugins` 把它列在 **`Paper Plugins`** 分组（对照：bukkit 版列在 `Bukkit Plugins`）；
      命令经 `LifecycleEvents.COMMANDS` 用 **Brigadier** 注册，主命令与全部别名
      （`historyclis`/`historycliser`/`historys`/`historyser`）实测可用；Folia 上同样成立；
      并打出 `Paper build: Brigadier commands + clickable rows + async flush every 1m`；
      **kill -9 防丢验证通过**——不发 `stop` 直接强杀 JVM，磁盘上仍有 3 条历史，
      证明 `AsyncScheduler` 定时落盘生效（此时 `onDisable` 从未执行）；
      **误装到 Spigot 时优雅降级**：打印「请改用 historycli-bukkit-*.jar」后干净禁用。
      > 类型隔离的坑（实测踩过两次）：只把 Paper 类型移出**方法描述符**不够——
      > JVM 在**类校验阶段**就会解析方法体内 `LifecycleEvents.COMMANDS` 等引用的类型，
      > 曾导致 Spigot 上 `NoClassDefFoundError: LifecycleEventType`，连提示都来不及打印。
      > 结论：`PaperHistoryPlugin` 字节码里**一个 Paper 类型都不能有**，
      > 全部委托给只有确认是 Paper 之后才会被调用的 `PaperSupport`（已用逐字节扫描验证）。
    - ✅ **sponge 版 jar 已在 SpongeVanilla 26.3（Sponge API 21）真机实测通过**（2026-09-28）：
      `spongevanilla-…-universal.jar` 的 Main-Class 就是 `InstallerMain`——**直接运行即可装出服务端**
      （`libraries/` 95 个文件、`Done (3.131s)`）；把 jar 放进 `mods/` 后：
      `Loaded plugin(s): [spongevanilla, sponge, spongeapi, minecraft, lichenhistorycli]`
      → `onConstruct` → `Lichen History CLI (Sponge) enabled, language=en_us` → `onShutdown`；
      主命令与全部别名（`historyclis`/`historycliser`/`historys`/`historyser`）经 RCON 实测可执行，
      列表输出 `1..N` 逐条对应；**录制链路成立**——7 条控制台/RCON 命令全部入史到
      `local/historycli/command_history.log` 并在停服时落盘。
      这一次覆盖了原先标注的三个"未验证"项：**API 21 上能链接并加载**、
      **`"spongeapi": "12.0.0"` 按下限（而非精确）解释**、**控制台/RCON 会触发 `ExecuteCommandEvent`**。
      > 踩坑：Sponge 会**为每种命令类型各发一次** `RegisterCommandEvent`（事件泛型即该次的命令类型）。
      > 监听器若声明成父类型 `RegisterCommandEvent<Command>`，会连 Parameterized 那次一起收到，
      > 平台内部强转 → `ClassCastException: RawHistoryCommand cannot be cast to Command$Parameterized`。
      > **必须收窄为 `RegisterCommandEvent<Command.Raw>`**（实测踩到，已修）。
      > 另注：`plugins` 经 RCON 回 "Unknown or incomplete command"，**不带插件的基线同样如此**
      > → 属 Sponge 的 RCON 主体权限/命令可见性，与本插件无关；
      > 尚未触发的是 `!` 展开经 `CommandManager.process()` 的分发（测试命令里没有 `!!`）。
    - ✅ **Sponge 平台事实与 API 兼容性已核对**（2026-09-28，取自 `repo.spongepowered.org` 全量 metadata）：
      **SpongeVanilla 提供 26.x**——`26.1`/`26.1.1`/`26.1.2`（Sponge API **19**）、`26.2`（API **20**）、
      `26.3`（API **21**，最新 `26.3-21.0.0-RC2721`），另有 `1.21.11`（API 18）、`1.21.10`（API 17）等；
      **26.x 均为 RC/实验性**。Sponge API **稳定版止于 17.0.0**，18/19/20 只有 `-SNAPSHOT`，**21 尚无任何构件**
      → 26.3 的 API 无法核验。**已逐签名比对 12 / 17 / 19 / 20**：本项目用到的全部 API 元素
      （`Command` 的 6 个抽象方法、`Command.Raw`、`ExecuteCommandEvent.Pre`、`RegisterCommandEvent.register`、
      `ArgumentReader.input/cursor/remaining`、`CommandCause.first/subject/audience`、
      `Sponge.asyncScheduler/configManager/server`、`Task.Builder`、`CommandCompletion.of`、
      `CommandManager.process`、`ConfigRoot.directory`、`Scheduler.submit`、`ScheduledTask.cancel`）
      **四版完全一致** → 编译目标取 12（最低支持版本），**一个 jar 实测覆盖到 26.3**；
      `Command.Raw.commandTree()` 在 12→19 由无参变为 `commandTree(RegistryHolder)`，
      但它是 `default` 且我们不覆盖，故无影响（API 21 无构件可 diff，改由 26.3 真机实测直接背书）。
    - **Sponge 无 op API**（op 属权限体系）：非玩家发送者（控制台/命令方块）一律放行，玩家按节点判定。
    - ⚠️ **踩坑记录（网络，按主机分野）**：
      - **跑 Gradle 时带上** `$env:JAVA_TOOL_OPTIONS='-Djava.net.preferIPv4Stack=true'`：
        实测**去掉它会连续两次**在 `:forge` 配置期报 `HttpConnectTimeoutException: HTTP connect timed out`，
        带上后历史构建基本都成功——它**显著提高成功率但不能保证**（带上后仍偶发超时，
        是 Mojang 侧瞬时不可达，**重跑即可**）。
      - **同一个开关**却会让 `launchermeta.mojang.com` 的 **TLS 握手被重置**（不带反而 200）
        → 跑 `fabric-installer` / SpongeVanilla 安装器这类**直连 Mojang 清单**的工具时要**去掉**它。
      - 结论：**构建带、安装器不带**。`:forge` 配置期超时或 `maven.fabricmc.net` 读超时时，先怀疑网络、
        重跑；急着验证其它模块可**绕开 `:forge` 的配置期**：
        `gradlew --configure-on-demand :common:build :fabric:build ...`（已验证可用，省去 Mojang 下载）。
      - **Java → `maven.minecraftforge.net` 连接超时**（FG7 的 mcmaven 在 `downloadSources` /
        `injectData` 处卡死）：用 PowerShell 把构件预置进 mavenizer 缓存即可绕开 ——
        `%USERPROFILE%\.gradle\caches\minecraftforge\forgegradle\mavenizer\caches\maven\{MinecraftForge,forge}\net\minecraftforge\forge\<mc>-<fg>\`，
        放 `forge-<v>-universal.jar`、`-sources.jar`、`-userdev.jar` 各自的 `.jar` + 同名 `.sha1`
        （内容是纯 40 位 hex、**无换行**）。预置后 26.1.2 / 26.3 的完整 decompile+patch 管线均跑通（已实测）。
      - **`net.neoforged:minecraft-dependencies:<mc>` 只存在于 `https://maven.neoforged.net/mojang-meta/`** ——
        MDG 的 `RepositoriesPlugin` 会额外建这个仓，而 `releases` 里**连目录都没有**（直接探 404，易误判"不存在"）。
        本机 init 脚本删掉了所有含 `neoforged.net` 的仓 → 必须用 PowerShell 从 mojang-meta 把它的
        `.pom`+`.module` 预置进 `.m2`（26.1.2 与 26.3 都踩过，症状是
        `Could not find net.neoforged:minecraft-dependencies:X`）。
      - **NeoForge 安装器优先读 `.m2`**（日志会打 `Downloaded file locally from ... valid checksum`）：
        远端被重置时，把 `These libraries failed to download` 清单里的 GAV 从 **Maven Central**
        （`repo.maven.apache.org`，Java/PowerShell 都通）补进 `.m2` 再重跑即可；通用库
        （asm-util/asm-analysis/night-config core+toml/maven-artifact/typetools/
        terminalconsoleappender/jline-reader/jline-terminal）都能这样补齐（已实测）。
    - ✅ **控制台录制路径已实测**（stdin 注入，Paper 26.1.2 + bukkit jar）：
      4 条控制台命令全部入史并落盘到 `command_history.log`，`Command exception` 0 条
      → `ServerCommandEvent` 录制链路端到端成立。
      > 踩坑记录：命令必须**等 `Done` 之后**再注入——与 `Done` 同 tick 时
      > 原版 `CommandSourceStack.getLevel()` 还是 null，所有命令（含 `/stop`）都会 NPE。
      > 这是测试装置的问题，与插件无关（异常栈里没有任何本项目类）。
      > **喂控制台的两条硬规矩（Forge 侧实测踩过，插件端同样适用）**：
      > ① **别用 cmd 的 `echo` 走管道** —— 它产出 `stop\r\n`，而 stdin 是管道时原版**不剥尾随 CR**：
      >    带贪心参数的命令会把 CR 吞进参数、**裸命令（如 `stop`）直接 parse 失败**
      >    （报 `Incorrect argument for command` + `stop <--[HERE]`）。改用 **LF-only 的 stdin 文件**
      >    （`[IO.File]::WriteAllText($f, ($cmds -join "`n") + "`n", ASCII)` + `-RedirectStandardInput $f`）：
      >    命令会在 `Done` 后的首个 tick 统一执行，实测**不会**踩到上面的 `getLevel()==null`。
      > ② **别用 `timeout /t N` 做延时** —— 它在管道里读 stdin，会打印
      >    `错误: 不支持带有输入重定向的...` 并**立刻退出**（stderr 可见），后续命令瞬间全部灌入。
      >    要延时用 `ping -n <秒+1> 127.0.0.1 >nul`（不读 stdin）。
      >
      > **原版会吞掉命令异常**：任何逃逸的未受检异常都被转译成 `command.failed`
      > （"An unexpected error occurred trying to execute that command"）发给命令源，
      > **日志里没有任何堆栈**（`latest.log` / `debug.log` / stdout 全查过）。
      > 反汇编 `net.minecraft.commands.Commands` 才发现：堆栈只在
      > `SharedConstants.DEBUG_VERBOSE_COMMAND_ERRORS || IS_RUNNING_IN_IDE` 时才打
      > （`sendFailure(describeError(t))` + `LOGGER.error("'{}' threw an exception", cmd, t)`）。
      > **让原版开口的办法**（`SharedConstants.debugFlag` 模板为 `MC_DEBUG_\u0001`）：
      > 给服务端 JVM 加 `-DMC_DEBUG_ENABLED=true -DMC_DEBUG_VERBOSE_COMMAND_ERRORS=true`
      > —— 命令异常的完整堆栈就会进 `latest.log`，这是定位这类问题的唯一现场。
      > 即便如此，mod 自己也应 try/catch 并 `LOGGER.error(..., t)`：那两个开关只有测试时才开。
      > `ForgeServerCommand` / `NeoForgeServerCommand` 的 `run(...)`/`requires(...)`、
      > 两个 mod 类的 `onCommand(...)` 已按此兜住。
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
- 本地临时产物一律走 `.gitignore`，**不要**把它们的路径写进本文档——其他协作者克隆下来没有这些目录，
  文档里出现只会误导读代码的模型/人。
