# Lichen History CLI

[English](./README.md) | **简体中文** | [繁體中文](./README_zh_Hant.md)

---

一个把熟悉的 **bash `history` 命令** 带进 Minecraft 的模组。无论你是服务器维护者、管理员，还是需要反复执行大量指令的地图作者，Lichen History CLI 都能把你输入过的命令牢牢记住——轻松浏览、搜索，并在几下按键内重新执行。Lichen History CLI 为 Minecraft 带来一份干净、类 bash 风格的历史命令列表，可浏览、搜索、重新执行——全部由模组自行管理，且不干涉原版行为。

## ✨ 特性

- **灵感来自 bash `history`** —— 像在终端里一样列出、浏览并重新执行之前输入过的命令。
- **命令回显与展开** —— 重跑上一条（`!!`）、指定条目（`!n`）、倒数第 N 条（`!-n`）、或以某字符串开头最近的命令（`!string`）。
- **完整的 `history` 子命令** —— 列出历史、显示最近 N 条、清空历史、写入/追加到文件、重载等。
- **覆盖你整个网络** —— 客户端、专用服务器、代理端。
- **永不触碰原版文件** —— 模组在 `local/historycli/` 维护**自己**的历史日志，原版 `command_history.txt` 与上下键内存历史完全不受影响。
- **友好的权限体系** —— 代理端支持细粒度权限节点，兼容 LuckPerms。

## 🧩 支持平台

| 平台 | 产物 |
| --- | --- |
| Fabric / Quilt | 单一 jar |
| NeoForge | 单一 jar |
| Forge | 单一 jar |
| Bukkit / Spigot / Paper / Purpur / Leaves / Leaf | 单一插件 jar（兼容 Folia） |
| Velocity / BungeeCord (Waterfall) | 单一代理 jar |

> **没有"客户端版 / 服务端版"两种构建。** 每个加载器只产出**一个 jar**，
> 客户端、专用服务器、集成服务器的行为在**运行时按当前环境注入**
> （mixin 与事件按运行环境注册，而不是构建期拆分）。
> NeoForge / Forge 以 **26.2** 编译并声明依赖（`[26.2,27)`）。

## 📖 用法概览

- **客户端**：`/historycliclient`（别名 `historyclic`、`historycli`）—— 本地浏览自己的命令历史。
- **服务端**：`/historycliserver`（别名 `historyclis`、`historycliser`）—— op 命令，管理服务端历史。
- **代理端**：`historycliproxy` / `historyproxy` —— 代理端命令历史，由权限节点控制。

---

## 📜 许可证

- **代码**：GNU 宽通用公共许可证 v3.0 或更新版本（**LGPL-3.0-or-later**）。
- **本文档**：知识共享 署名-相同方式共享 4.0（**CC BY-SA 4.0**）。
- **图标**（`icon/icon.png`）：**CC BY-NC-SA 4.0（禁商业）** —— 与代码许可不同，分发前请阅读 [icon/README.md](../icon/README.md)。
