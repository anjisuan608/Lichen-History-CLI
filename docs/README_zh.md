# Lichen History CLI

[English](./README.md) | **简体中文** | [繁體中文](./README_zh_Hant.md)

---

Lichen History CLI 把熟悉的 **bash `history` 命令** 带进 Minecraft —— 既能作为模组，也能作为服务端插件。无论你是服务器维护者、管理员，还是需要反复执行大量指令的地图作者，你输入过的命令都只差几下按键：浏览它、搜索它、重新执行它。历史记录保存在项目**自己**的日志文件里，原版的 `command_history.txt` 与上下键内存历史完全不受影响。

**支持平台** —— *模组*：**Fabric(Quilt)**、**NeoForge**、**Forge** · *服务端插件*：**CraftBukkit**、**Spigot**、**Paper**、**Purpur**、**Leaves**、**Leaf**、**Folia**、**Sponge** · *代理端*：**Velocity**、**BungeeCord(Waterfall)**。<!-- 具体版本范围见下方[支持平台表](#-支持平台)。 -->

## ✨ 特性

- **灵感来自 bash `history`** —— 像在终端里一样列出、浏览并重新执行之前输入过的命令。
- **命令回显与展开** —— 重跑上一条（`!!`）、指定条目（`!n`）、倒数第 N 条（`!-n`）、或以某字符串开头最近的命令（`!string`）。
- **完整的 `history` 子命令** —— 列出历史、显示最近 N 条、清空历史、写入/追加到文件、重载等。
- **整套网络同一套语法** —— 客户端、专用服务器与代理端用法完全一致。
- **永不触碰原版文件** —— 历史保存在项目自己的日志里（mod 与服务端插件构建为 `local/historycli/`，代理端为 `plugins/<插件 id>/`），原版 `command_history.txt` 与上下键内存历史完全不受影响。
- **细粒度权限** —— 服务端与代理端构建都提供按子命令划分的权限节点（`historycli.*`、`historycli.use` 等），兼容 LuckPerms。
- **更耐崩的历史** —— 代理端与 paper/sponge 插件构建**每分钟异步落盘**，进程被强杀也不丢本次会话的记录。

<!-- 
## 🧩 支持平台

| 平台 | 产物 | Minecraft | 说明 |
| --- | --- | --- | --- |
| Fabric / Quilt | `historycli-fabric-*.jar` | 26.2+ | 模组 —— 客户端与专用服务器 |
| NeoForge | `historycli-neoforge-*.jar` | 26.2+ | 模组 —— 客户端与专用服务器 |
| Forge | `historycli-forge-*.jar` | 26.2+ | 模组 —— 客户端与专用服务器 |
| CraftBukkit / Spigot（也能跑在 Paper 上） | `historycli-bukkit-*.jar` | 26.1.x ~ 26.3 | 普通 Bukkit 插件（`plugin.yml`） |
| Paper / Purpur / Leaves / Leaf + Folia | `historycli-paper-*.jar` | 26.1.x ~ 26.3 | **Paper 原生插件**（`paper-plugin.yml`） |
| Sponge（SpongeVanilla / SpongeForge） | `historycli-sponge-*.jar` | 1.21.1 ~ 26.3¹，Sponge API ≥ 12 | Sponge 插件（`sponge_plugins.json`） |
| Velocity / BungeeCord (Waterfall) | `historycli-velocity-*.jar` / `historycli-bungee-*.jar` | —（代理端，不绑定 MC 版本） | 代理端插件 |

¹ Sponge 的 26.x 构建为 **RC / 实验性**：SpongeVanilla 提供 26.1.x（Sponge API 19）、26.2（API 20）、26.3（API 21）。本 jar 以 **Sponge API 12** 编译——其 API 面经逐签名核对到 API 20 为止**完全未变**——并在 **SpongeVanilla 26.3（Sponge API 21）上真机实测通过**，因此**一个 jar 覆盖 MC 1.21.1 ~ 26.3**。

> ### 服务端插件该装哪个？
> - **`historycli-paper-*.jar`** —— **Paper 原生插件**：Paper 经 `paper-plugin.yml` 加载，`/plugins` 里列在 **Paper Plugins** 分组；额外提供可点击的历史行与每分钟异步落盘。适用于 Paper、Purpur、Leaves、Leaf 与 Folia。
> - **`historycli-bukkit-*.jar`** —— 面向 CraftBukkit / Spigot 的普通 Bukkit 插件（装到 Paper 上也能用，会列在 **Bukkit Plugins** 分组）。
> - 两者**插件名相同，不能同时安装**。paper 版检测到非 Paper 服务器时会打印明确提示并自行禁用，而不是崩溃。
>
> **没有"客户端版 / 服务端版"两种构建。** 每个加载器只产出**一个 jar**，
> 客户端、专用服务器、集成服务器的行为在**运行时按当前环境注入**
> （mixin 与事件按运行环境注册，而不是构建期拆分）。
> NeoForge / Forge 以 **26.2** 编译并声明依赖（`[26.2,27)`）。
 -->

## 📖 用法概览

- **客户端**：`/historycliclient`（别名 `historyclic`、`historycli`）—— 本地浏览自己的命令历史。
- **服务端**：`/historycliserver`（别名 `historyclis`、`historycliser`）—— op 级命令，管理服务端历史；较短的 `/historyserver` 只提供 bash 标准子集。bukkit、paper、sponge 三个构建的命令与别名**完全一致**。
- **代理端**：`historycliproxy` / `historyproxy` —— 代理端命令历史，由权限节点控制。

### 参数说明

以下参数适用于上面列出的命令。**完整命令**（`/historycliserver`、`historycliproxy`、客户端的 `/historycliclient`）支持全部参数；**短命令**（`/historyserver`、`historyproxy`、客户端的普通命令）是 bash 标准子集，**不含** `list`、`-d`、`reload` 与 `!` 系列。

| 参数 | 作用 |
| --- | --- |
| （无参数）、`list` | 列出历史（`list` 仅完整命令支持） |
| `<num>` | 显示最近 `<num>` 条 |
| `-c` | 清空内存中的历史 |
| `-w` | 把整个历史写入文件（原子替换） |
| `-a` | 追加尚未写入文件的条目 |
| `-r` | 把文件内容读回内存（插到头部，不会与已有文件行重复） |
| `-d <n>` | 删除第 `<n>` 条；文件里那行保留到你执行 `-w` 才会同步 |
| `reload` | 重新读取配置（`language`、`record_history`、`history_size`） |
| `help`、`?` | 打印帮助 |
| `!!` | 重跑上一条 |
| `!n` | 重跑第 `n` 条 |
| `!-n` | 从末尾倒数第 `n` 条 |
| `!string` | 重跑最近一条以 `string` 开头的命令（区分大小写） |

> **说明**
> - `!` 系列通过完整命令触发，例如 `/historycliserver !!`、`historycliproxy !5`；**代理端控制台不接受裸 `!!`**。
> - 入史的是**展开结果**，`!!` 这类请求本身不会作为字面条目留在历史里。
> - 写操作（`-c`/`-w`/`-a`/`-r`/`-d`/`reload`/`!`）在服务端与代理端需要对应的 `historycli.*` 权限节点；`historycli.use` 只放行只读的列表与帮助。

---

## 📜 许可证

- **代码**：GNU 宽通用公共许可证 v3.0 或更新版本（**LGPL-3.0-or-later**）。
- **本文档**：知识共享 署名-相同方式共享 4.0（**CC BY-SA 4.0**）。
- **图标**（`icon/icon.png`）：**CC BY-NC-SA 4.0（禁商业）** —— 与代码许可不同，分发前请阅读 [icon/README.md](../icon/README.md)。
