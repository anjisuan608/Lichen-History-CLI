# Lichen History CLI

**English** | [简体中文](./README_zh.md) | [繁體中文](./README_zh_Hant.md)

---

A Minecraft mod that brings the familiar **bash `history` command** into the game. Whether you're a server owner, admin, or a map maker who lives in the console and chat, Lichen History CLI keeps every command you've run within easy reach — browse, search, and re-run them in a couple of keystrokes. Lichen History CLI gives Minecraft a clean, bash-style command history you can browse, search, and re-execute — all managed by the mod itself, without interfering with vanilla behavior.

## ✨ Features

- **Inspired by bash `history`** — list, browse, and execute previously typed commands just like in a terminal.
- **Command recall & expansion** — rerun the last command (`!!`), a specific entry (`!n`), a previous entry (`!-n`), or the most recent command starting with a given string (`!string`).
- **Full `history` subcommands** — display lists, show the last `N` entries, clear history, write/append to file, reload, and more.
- **Works across your whole network** — client, dedicated servers, and proxies.
- **Never touches vanilla files** — the mod keeps its **own** command history log in `local/historycli/`, so the vanilla `command_history.txt` and up/down-arrow memory stay completely untouched.
- **Permission-friendly** — proxy support with fine-grained permission nodes, compatible with LuckPerms.

## 🧩 Platforms

| Platform | Side |
| --- | --- |
| Fabric / Quilt | Client + Server |
| NeoForge | Server |
| Forge | Server |
| Bukkit / Spigot / Paper / Purpur / Leaves / Leaf | Server (Folia-ready) |
| Velocity / BungeeCord (Waterfall) | Proxy |

## 📖 Usage overview

- **Client**: `/historycliclient` (also `historyclic`, `historycli`) — browse your own command history locally.
- **Server**: `/historycliserver` (also `historyclis`, `historycliser`) — op command for server history.
- **Proxy**: `historycliproxy` / `historyproxy` — command history for the proxy, gated by permission nodes.

Type `/!` or `!!` in chat to re-run your last command instantly.

---

## 📜 License

- **Code**: GNU Lesser General Public License v3.0 or later (**LGPL-3.0-or-later**).
- **Documentation**: Creative Commons Attribution-ShareAlike 4.0 (**CC BY-SA 4.0**).
