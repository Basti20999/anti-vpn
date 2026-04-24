# AntiVPN Plugin

A Paper/Spigot plugin that detects and blocks VPN/proxy connections on join.
Lightweight, free, and requires no API key.

## Features

- **Async VPN/proxy detection** on `AsyncPlayerPreLoginEvent` via
  [fastasfuck.net](https://api.fastasfuck.net) — no API key, no registration.
- **Thread-safe TTL cache** (`ConcurrentHashMap`) keeps API traffic minimal.
- **Retry with exponential backoff** on transient API failures (IOException, 5xx).
- **Configurable fail-mode**: `allow` (default, fail-open) or `deny` (fail-closed)
  when the API can't be reached.
- **Name whitelist + IP whitelist + IP blacklist** for fine-grained overrides.
- **Admin broadcast** — staff with `antivpn.notify` see in-game alerts when a
  connection is blocked.
- **Live stats** — block count, cache size, hit rate, average API latency.
- **MiniMessage-formatted messages** — every user-facing string is configurable
  with modern `<red>`/`<gradient>`/etc. tags.
- **Tab completion** for every subcommand.
- Built on modern Java 21 `HttpClient` with a single shared instance.

## Commands

| Command | Description |
|---|---|
| `/antivpn reload` | Reload `config.yml` |
| `/antivpn debug` | Toggle debug logging |
| `/antivpn check <player>` | Check an online player's IP |
| `/antivpn whitelist add \| remove \| list [name]` | Manage the name whitelist |
| `/antivpn stats` | Show operational metrics |
| `/antivpn cache clear \| size` | Inspect or clear the IP cache |
| `/antivpn help` | Show the help message |

## Permissions

| Permission | Default | Description |
|---|---|---|
| `antivpn.admin` | `op` | Run `/antivpn` subcommands |
| `antivpn.notify` | `op` | Receive in-game alerts when a connection is blocked |

## Configuration

`config.yml` is created on first start. Key sections:

```yaml
api:
  url: "https://api.fastasfuck.net/vpn/check/"
  timeout-ms: 8000
  retries: 2
  backoff-ms: 400

cache:
  duration-hours: 24
  cleanup-interval-minutes: 30

fail-mode: allow        # "allow" or "deny"
notify-admins: true
debug-mode: false

whitelist: ["Basti20999"]
ip-whitelist: []
ip-blacklist: []

messages:
  kick: "<red>VPN/Proxy connections are not allowed here.</red>"
  # ... all chat output is configurable; see config.yml for the full list
```

All messages use [MiniMessage](https://docs.advntr.dev/minimessage/format.html)
syntax. Placeholders such as `<player>`, `<ip>`, `<source>`, `<name>`, `<n>`,
`<blocks>`, and `<error>` are substituted before parsing.

## Install

1. Drop the JAR into `plugins/`.
2. Start the server — a default `config.yml` is created.
3. Edit and `/antivpn reload` to apply changes.

## Upgrading from 1.0

- The old `kick-message` key moved to `messages.kick` and uses MiniMessage
  instead of legacy `§` codes. The default is preserved; set your custom
  value under `messages.kick` if you had customized it.
- New keys (`api.*`, `cache.*`, `fail-mode`, `notify-admins`, `ip-whitelist`,
  `ip-blacklist`, `messages.*`) fall back to sensible defaults when absent, so
  existing configs continue to work unchanged.

## Compatibility

- **Minecraft**: 1.21+
- **Server**: Paper (Spigot/Bukkit work if Adventure is available, but Paper is
  the supported target).
- **Java**: 21+
