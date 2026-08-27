# Lichen History CLI

[English](./README.md) | [简体中文](./README_zh.md) | **繁體中文**

---

一個把熟悉的 **bash `history` 指令** 帶進 Minecraft 的模組。無論你是伺服器維護者、管理員，還是需要反覆執行大量指令的地圖作者，Lichen History CLI 都能把你輸入過的指令牢牢記住——輕鬆瀏覽、搜尋，並在幾下按鍵內重新執行。Lichen History CLI 為 Minecraft 帶來一份乾淨、類 bash 風格的歷史指令列表，可瀏覽、搜尋、重新執行——全部由模組自行管理，且不干涉原版行為。

## ✨ 特色

- **靈感來自 bash `history`** —— 像在終端機裡一樣列出、瀏覽並重新執行之前輸入過的指令。
- **指令回顯與展開** —— 重跑上一條（`!!`）、指定條目（`!n`）、倒數第 N 條（`!-n`）、或以某字串開頭最近的指令（`!string`）。
- **完整的 `history` 子指令** —— 列出歷史、顯示最近 N 條、清除歷史、寫入/附加到檔案、重載等。
- **涵蓋你整個網路** —— 用戶端、專用伺服器、代理端。
- **永不觸碰原版檔案** —— 模組在 `local/historycli/` 維護**自己**的歷史日誌，原版 `command_history.txt` 與上下鍵記憶體歷史完全不受影響。
- **友善的權限體系** —— 代理端支援細粒度權限節點，相容 LuckPerms。

## 🧩 支援平台

| 平台 | 端 |
| --- | --- |
| Fabric / Quilt | 用戶端 + 伺服器端 |
| NeoForge | 伺服器端 |
| Forge | 伺服器端 |
| Bukkit / Spigot / Paper / Purpur / Leaves / Leaf | 伺服器端（相容 Folia） |
| Velocity / BungeeCord (Waterfall) | 代理端 |

## 📖 用法概覽

- **用戶端**：`/historycliclient`（別名 `historyclic`、`historycli`）—— 在本機瀏覽自己的指令歷史。
- **伺服器端**：`/historycliserver`（別名 `historyclis`、`historycliser`）—— op 指令，管理伺服器歷史。
- **代理端**：`historycliproxy` / `historyproxy` —— 代理端指令歷史，由權限節點控制。

在聊天框輸入 `/!` 或 `!!` 即可瞬間重跑上一條指令。

---

## 📜 授權條款

- **程式碼**：GNU 寬通用公共授權條款 v3.0 或更新版本（**LGPL-3.0-or-later**）。
- **本文檔**：創用 CC 姓名標示-相同方式分享 4.0（**CC BY-SA 4.0**）。
