# Lichen History CLI

**English** | [简体中文](./README_zh.md) | [繁體中文](./README_zh_Hant.md)

---

Lichen History CLI brings the familiar **bash `history` command** to Minecraft — as a mod and as a server plugin. Whether you are a server owner, an admin, or a map maker who lives in the console, every command you have run stays a couple of keystrokes away: browse it, search it, re-run it. History is kept in the project's **own** log file, so vanilla `command_history.txt` and the up/down-arrow buffer are never touched.

**Supported platforms** — *mods*: **Fabric**, **NeoForge**, **Forge** · *server plugins*: **CraftBukkit**, **Spigot**, **Paper**, **Purpur**, **Leaves**, **Leaf**, **Folia**, **Sponge** · *proxies*: **Velocity**, **BungeeCord (Waterfall)**.<!-- Exact version ranges are in the [platform table](#-platforms) below. -->

## ✨ Features

- **Inspired by bash `history`** — list, browse and re-execute previously typed commands, just like in a terminal.
- **Command recall & expansion** — rerun the last command (`!!`), a specific entry (`!n`), a previous entry (`!-n`), or the most recent command starting with a given string (`!string`).
- **Full `history` subcommands** — list entries, show the last `N`, clear the history, write/append to file, reload, and more.
- **One syntax across your whole network** — client, dedicated servers and proxies all speak the same command set.
- **Never touches vanilla files** — history lives in the project's own log (`local/historycli/` on mod and server-plugin builds, `plugins/<id>/` on proxies); the vanilla `command_history.txt` and arrow-key memory stay completely untouched.
- **Fine-grained permissions** — per-subcommand nodes (`historycli.*`, `historycli.use`, …) on both server and proxy builds, compatible with LuckPerms.
- **Durable history** — the proxy builds and the paper/sponge plugin builds flush the log to disk every minute, so even a hard kill keeps the current session's entries.

<!-- 
## 🧩 Platforms

| Platform | Artifact | Minecraft | What it is |
| --- | --- | --- | --- |
| Fabric / Quilt | `historycli-fabric-*.jar` | 26.2+ | mod — client and dedicated server |
| NeoForge | `historycli-neoforge-*.jar` | 26.2+ | mod — client and dedicated server |
| Forge | `historycli-forge-*.jar` | 26.2+ | mod — client and dedicated server |
| CraftBukkit / Spigot (also runs on Paper) | `historycli-bukkit-*.jar` | 26.1.x ~ 26.3 | plain Bukkit plugin (`plugin.yml`) |
| Paper / Purpur / Leaves / Leaf + Folia | `historycli-paper-*.jar` | 26.1.x ~ 26.3 | **native Paper plugin** (`paper-plugin.yml`) |
| Sponge (SpongeVanilla / SpongeForge) | `historycli-sponge-*.jar` | 1.21.1 ~ 26.3¹, Sponge API ≥ 12 | Sponge plugin (`sponge_plugins.json`) |
| Velocity / BungeeCord (Waterfall) | `historycli-velocity-*.jar` / `historycli-bungee-*.jar` | — (proxy side) | proxy plugin |

¹ Sponge's 26.x builds are **experimental (RC)**: SpongeVanilla ships 26.1.x (Sponge API 19), 26.2 (API 20) and 26.3 (API 21). This jar compiles against **Sponge API 12** — its API surface was verified byte-for-byte identical through API 20 — and it has been **runtime-tested on SpongeVanilla 26.3 (Sponge API 21)**, so **MC 1.21.1 ~ 26.3** is covered by one jar.

> ### Which server plugin should I install?
> - **`historycli-paper-*.jar`** — a *native Paper plugin*: Paper loads it through `paper-plugin.yml`, so `/plugins` lists it under **Paper Plugins**. It adds clickable history rows and a per-minute async flush. Use it on Paper, Purpur, Leaves, Leaf and Folia.
> - **`historycli-bukkit-*.jar`** — the plain Bukkit plugin for CraftBukkit and Spigot (it also runs on Paper, where it shows up under **Bukkit Plugins**).
> - The two **share one plugin name and must not be installed together**. The paper build detects a non-Paper server, prints a clear message and disables itself instead of crashing.
>
> **There is no "client build" and no "server build".** Each loader ships a **single jar**;
> client, dedicated-server and integrated-server behaviour is selected **at runtime** by the
> environment the mod is running in (mixin/event registration is environment-scoped, not build-scoped).
> NeoForge/Forge are compiled and dependency-checked against **26.2** (`[26.2,27)`).
-->

## 📖 Usage overview

- **Client**: `/historycliclient` (also `historyclic`, `historycli`) — browse your own command history locally.
- **Server**: `/historycliserver` (also `historyclis`, `historycliser`) — op-level command for the server history; the shorter `/historyserver` exposes a bash-style subset. The same commands and aliases work identically on the bukkit, paper and sponge builds.
- **Proxy**: `historycliproxy` / `historyproxy` — command history for the proxy, gated by permission nodes.

### Arguments

All arguments below belong to the commands listed above. The **full** commands (`/historycliserver`, `historycliproxy`, the client `/historycliclient`) accept everything; the **short** ones (`/historyserver`, `historyproxy`, the plain client command) are a bash-style subset without `list`, `-d`, `reload` and the `!` forms.

| Argument | Effect |
| --- | --- |
| *(none)*, `list` | Show the history (`list` only exists on the full commands) |
| `<num>` | Show the last `<num>` entries |
| `-c` | Clear the in-memory history |
| `-w` | Write the whole history to file (atomic replace) |
| `-a` | Append the entries that are not in the file yet |
| `-r` | Read the file back into memory (inserted at the head, existing file lines are not duplicated) |
| `-d <n>` | Delete entry `<n>`; the file keeps that line until you run `-w` |
| `reload` | Re-read the config (`language`, `record_history`, `history_size`) |
| `help`, `?` | Print help |
| `!!` | Re-run the last command |
| `!n` | Re-run entry `n` |
| `!-n` | Re-run the n-th entry counting from the end |
| `!string` | Re-run the most recent command starting with `string` (case-sensitive) |

> **Notes**
> - The `!` forms are dispatched through the full command, e.g. `/historycliserver !!` or `historycliproxy !5`; proxy consoles do **not** accept a bare `!!`.
> - Expansion results are what gets recorded — the `!!` request itself is not kept as a literal entry.
> - Write actions (`-c`/`-w`/`-a`/`-r`/`-d`/`reload`/`!`) need the matching `historycli.*` permission node on server and proxy builds; `historycli.use` only grants read-only listing and help.

---

## 📜 License

- **Code**: GNU Lesser General Public License v3.0 or later (**LGPL-3.0-or-later**).
- **Documentation**: Creative Commons Attribution-ShareAlike 4.0 (**CC BY-SA 4.0**).
- **Icon** (`icon/icon.png`): **CC BY-NC-SA 4.0 (non-commercial)** — this differs from the code license; see [icon/README.md](../icon/README.md) before redistributing.
