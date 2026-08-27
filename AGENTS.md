# Lichen History CLI

> 在 Minecraft 中实现类 bash 的 `history` 命令，管理**本模组自持的命令历史日志**。

## 许可证

- **代码**：以 GNU Lesser General Public License v3.0 或更新版本（LGPL-3.0-or-later）授权。
- **项目文档**：以 Creative Commons Attribution-ShareAlike 4.0（CC BY-SA 4.0）授权。

---

## 1. 概述

面向服务的多平台 Minecraft 历史命令工具。模组在所有支持的平台上维护**自己独立的命令历史日志**，**从不读写或干涉原版**的 `command_history.txt` / `.console_history` / 上下键内存历史。因此不存在"接管/共存"冲突，也不会有 `/!` 原文残留问题。

### 支持平台

| 平台 | 说明 |
| --- | --- |
| Fabric / Quilt | 客户端 + 服务端 |
| NeoForge | 服务端（单 jar 覆盖 26.1~26.2） |
| Forge | 服务端（单 jar 覆盖 26.1~26.2，官网已发布至 26.2-65.1.3） |
| Bukkit 系（Spigot/Paper/Purpur/Leaves/Leaf） | 仅服务端，兼容 Folia |
| Velocity / BungeeCord(Waterfall) | 代理端 |

---

## 2. 构建结构（单仓库多模块，仿 LuckPerms）

```
settings.gradle              # include :common :fabric :neoforge :forge :bukkit :velocity :bungee
build.gradle                 # 版本三态逻辑 + subprojects 统一
gradle/libs.versions.toml    # 版本目录
common/
  HistoryStore               # 内存缓冲 + 文件读写 + 展开（!!/!n/!-n/!string）
  HistoryParser              # 子命令参数解析
  HistoryCommandHandler      # 平台无关命令分派（修一次全端生效）
  PermissionNode             # 代理端权限节点常量
fabric/                      # loom + jar-in-jar 嵌入 common
neoforge/  forge/            # 服务端命令 + CommandEvent 历史记录
bukkit/                      # 服务端命令 + ServerCommandEvent，folia-supported
velocity/  bungee/           # 代理端命令 + 命令事件自记录
```

### 版本三态

`build.gradle` 的 `resolveVersion()`：
- 本地构建 → `SNAPSHOT`
- GitHub Actions（`CI`）→ 提交哈希前 7 位
- tag 发版 → 标签名（过滤前导 `v`）

产物命名：`historycli-<加载器>-<版本>.jar`。

---

## 3. 数据模型：mod 自持独立日志

所有端的历史统一存本模组自己的文件，格式为**每行一条命令**：

| 端 | 路径 |
| --- | --- |
| 客户端 / 服务端（Fabric/NeoForge/Forge） | `<游戏目录>/local/historycli/command_history.log` |
| Bukkit | `<服务器目录>/local/historycli/command_history.log` |
| 代理端 | `plugins/<mod>/command_history.log` |

- `local/` 为 Forge/NeoForge mod 数据惯例目录。
- 原版历史文件 / 上下键内存：**不读、不写、不干涉**。

---

## 4. 命令体系

### 客户端（仅本机生效）

| 完整命令 | 普通命令 |
| --- | --- |
| `/historycliclient`（别名 `historyclic`、`historycli`） | `/historyclient`（别名 `historyc`、`history`） |

- 短名按"尽力注册"处理，被占用则跳过。
- 子命令：列表、`<num>`、`-c/-w/-a/-r`、`-d`、`reload`（完整命令）；普通命令为 bash 标准子集（无 `reload`/`!`/`-d`）。
- `!` 系列（`!!`、`!n`、`!-n`、`!string`）聊天框直接输入即展开，只操作本 mod 日志。

### 服务端

| 完整命令 | 普通命令 |
| --- | --- |
| `/historycliserver`（别名 `historyclis`、`historycliser`） | `/historyserver`（别名 `historys`、`historyser`） |

- 仅专用服务器，需 `Permissions.COMMANDS_GAMEMASTER`（原 op level 2）。
- Neoforge/Forge 控制台命令经 `CommandEvent` 入史。

### 代理端

| 完整命令 | 普通命令 |
| --- | --- |
| `historycliproxy`（别名 `historyclipro`、`historyclip`） | `historyproxy`（别名 `historypro`、`historyp`） |

- 无 op，权限节点（子命令级）：`historycli.list/clear/write/append/read/delete/execute/reload`，通配 `historycli.*`、`historycli.use`，支持 LuckPerms。

### 历史展开（bash 语义）

`!!` 上一条、`!n` 第 n 条、`!-n` 倒数第 n 条、`!string` 最近以 string 开头的命令（大小写敏感）。展开后记录展开命令，并以「去掉前导 `/`」的命令执行。

---

## 5. 构建与测试

```powershell
.\gradlew.ps1 build          # 全 7 模块
.\gradlew.ps1 :common:test   # 单测（HistoryStore / HistoryCommandHandler）
```

- 需要 JDK 25 + Gradle（wrapper 9.6.1）。
- 运行目录：客户端 `run/Client`，服务端 `run/Server`（Fabric/NeoForge）。

---

## 6. 实现状态

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
- NeoForge/Forge 控制台**直接**输入 `!5` 暂未展开（走 JLine，`historycliserver !5` 可用）。
- 各端均无运行时实测（编译 + 单测通过）。

---

## 7. 环境与目录

- 工作目录、`common`/`fabric`/… 各平台模块下载依赖由 Gradle 管理。
- 代码包名统一 `org.anjisuan608.historycli`；平台 ID `lichenhistorycli`；显示名 "Lichen History CLI"（不支持空格的平台用驼峰 `LichenHistoryCli`）。
