# Lichen History CLI

[English](./README.md) | [简体中文](./README_zh.md) | **繁體中文**

---

Lichen History CLI 把熟悉的 **bash `history` 指令** 帶進 Minecraft —— 既能作為模組，也能作為伺服器外掛。無論你是伺服器維護者、管理員，還是需要反覆執行大量指令的地圖作者，你輸入過的指令都只差幾下按鍵：瀏覽它、搜尋它、重新執行它。歷史紀錄保存在專案**自己**的日誌檔裡，原版的 `command_history.txt` 與上下鍵記憶體歷史完全不受影響。

**支援平台** —— *模組*：**Fabric**、**NeoForge**、**Forge** · *伺服器外掛*：**CraftBukkit**、**Spigot**、**Paper**、**Purpur**、**Leaves**、**Leaf**、**Folia**、**Sponge** · *代理端*：**Velocity**、**BungeeCord(Waterfall)**。<!-- 具體版本範圍見下方[支援平台表](#-支援平台)。-->

## ✨ 特色

- **靈感來自 bash `history`** —— 像在終端機裡一樣列出、瀏覽並重新執行之前輸入過的指令。
- **指令回顯與展開** —— 重跑上一條（`!!`）、指定條目（`!n`）、倒數第 N 條（`!-n`）、或以某字串開頭最近的指令（`!string`）。
- **完整的 `history` 子指令** —— 列出歷史、顯示最近 N 條、清除歷史、寫入/附加到檔案、重載等。
- **整套網路同一套語法** —— 用戶端、專用伺服器與代理端用法完全一致。
- **永不觸碰原版檔案** —— 歷史保存在專案自己的日誌裡（mod 與伺服器外掛構建為 `local/historycli/`，代理端為 `plugins/<外掛 id>/`），原版 `command_history.txt` 與上下鍵記憶體歷史完全不受影響。
- **細粒度權限** —— 伺服器與代理端構建都提供依子指令劃分的權限節點（`historycli.*`、`historycli.use` 等），相容 LuckPerms。
- **更耐崩的歷史** —— 代理端與 paper/sponge 外掛構建**每分鐘非同步落盤**，程序被強殺也不會遺失本次工作階段的紀錄。

<!-- 
## 🧩 支援平台

| 平台 | 產物 | Minecraft | 說明 |
| --- | --- | --- | --- |
| Fabric / Quilt | `historycli-fabric-*.jar` | 26.2+ | 模組 —— 用戶端與專用伺服器 |
| NeoForge | `historycli-neoforge-*.jar` | 26.2+ | 模組 —— 用戶端與專用伺服器 |
| Forge | `historycli-forge-*.jar` | 26.2+ | 模組 —— 用戶端與專用伺服器 |
| CraftBukkit / Spigot（也能跑在 Paper 上） | `historycli-bukkit-*.jar` | 26.1.x ~ 26.3 | 一般 Bukkit 外掛（`plugin.yml`） |
| Paper / Purpur / Leaves / Leaf + Folia | `historycli-paper-*.jar` | 26.1.x ~ 26.3 | **Paper 原生外掛**（`paper-plugin.yml`） |
| Sponge（SpongeVanilla / SpongeForge） | `historycli-sponge-*.jar` | 1.21.1 ~ 26.3¹，Sponge API ≥ 12 | Sponge 外掛（`sponge_plugins.json`） |
| Velocity / BungeeCord (Waterfall) | `historycli-velocity-*.jar` / `historycli-bungee-*.jar` | —（代理端，不綁定 MC 版本） | 代理端外掛 |

¹ Sponge 的 26.x 構建為 **RC / 實驗性**：SpongeVanilla 提供 26.1.x（Sponge API 19）、26.2（API 20）、26.3（API 21）。本 jar 以 **Sponge API 12** 編譯——其 API 面經逐簽名比對到 API 20 為止**完全未變**——並在 **SpongeVanilla 26.3（Sponge API 21）上真機實測通過**，因此**一個 jar 覆蓋 MC 1.21.1 ~ 26.3**。

> ### 伺服器外掛該裝哪一個？
> - **`historycli-paper-*.jar`** —— **Paper 原生外掛**：Paper 經 `paper-plugin.yml` 載入，`/plugins` 裡列在 **Paper Plugins** 分組；額外提供可點擊的歷史列與每分鐘非同步落盤。適用於 Paper、Purpur、Leaves、Leaf 與 Folia。
> - **`historycli-bukkit-*.jar`** —— 面向 CraftBukkit / Spigot 的一般 Bukkit 外掛（裝到 Paper 上也能用，會列在 **Bukkit Plugins** 分組）。
> - 兩者**外掛名稱相同，不可同時安裝**。paper 版偵測到非 Paper 伺服器時會印出明確提示並自行停用，而不是崩潰。
>
> **沒有「用戶端版 / 伺服器版」兩種構建。** 每個載入器只產出**一個 jar**，
> 用戶端、專用伺服器、整合伺服器的行為在**執行時期依環境注入**
> （mixin 與事件依執行環境註冊，而非建置期拆分）。
> NeoForge / Forge 以 **26.2** 編譯並聲明依賴（`[26.2,27)`）。 
-->

## 📖 用法概覽

- **用戶端**：`/historycliclient`（別名 `historyclic`、`historycli`）—— 在本機瀏覽自己的指令歷史。
- **伺服器端**：`/historycliserver`（別名 `historyclis`、`historycliser`）—— op 級指令，管理伺服器歷史；較短的 `/historyserver` 只提供 bash 標準子集。bukkit、paper、sponge 三個構建的指令與別名**完全一致**。
- **代理端**：`historycliproxy` / `historyproxy` —— 代理端指令歷史，由權限節點控制。

### 參數說明

以下參數適用於上面列出的指令。**完整指令**（`/historycliserver`、`historycliproxy`、用戶端的 `/historycliclient`）支援全部參數；**短指令**（`/historyserver`、`historyproxy`、用戶端的一般指令）是 bash 標準子集，**不含** `list`、`-d`、`reload` 與 `!` 系列。

| 參數 | 作用 |
| --- | --- |
| （無參數）、`list` | 列出歷史（`list` 僅完整指令支援） |
| `<num>` | 顯示最近 `<num>` 條 |
| `-c` | 清除記憶體中的歷史 |
| `-w` | 把整個歷史寫入檔案（原子取代） |
| `-a` | 附加尚未寫入檔案的條目 |
| `-r` | 把檔案內容讀回記憶體（插到頭部，不會與既有檔案列重複） |
| `-d <n>` | 刪除第 `<n>` 條；檔案裡那一列保留到你執行 `-w` 才會同步 |
| `reload` | 重新讀取設定（`language`、`record_history`、`history_size`） |
| `help`、`?` | 顯示說明 |
| `!!` | 重跑上一條 |
| `!n` | 重跑第 `n` 條 |
| `!-n` | 從末尾倒數第 `n` 條 |
| `!string` | 重跑最近一條以 `string` 開頭的指令（區分大小寫） |

> **說明**
> - `!` 系列透過完整指令觸發，例如 `/historycliserver !!`、`historycliproxy !5`；**代理端主控台不接受裸 `!!`**。
> - 寫入歷史的是**展開結果**，`!!` 這類請求本身不會作為字面條目留在歷史裡。
> - 寫操作（`-c`/`-w`/`-a`/`-r`/`-d`/`reload`/`!`）在伺服器端與代理端需要對應的 `historycli.*` 權限節點；`historycli.use` 只放行唯讀的列表與說明。

---

## 📜 授權條款

- **程式碼**：GNU 寬通用公共授權條款 v3.0 或更新版本（**LGPL-3.0-or-later**）。
- **本文檔**：創用 CC 姓名標示-相同方式分享 4.0（**CC BY-SA 4.0**）。
- **圖示**（`icon/icon.png`）：**CC BY-NC-SA 4.0（禁商業）** —— 與程式碼授權不同，散布前請見 [icon/README.md](../icon/README.md)。
