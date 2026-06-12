# AntiVPN

Detects and blocks VPN/proxy connections before players finish logging in.
Runs natively on **Paper** servers and **Velocity** proxies. Lightweight,
free, and requires no API key.

## Downloads

The build produces one jar per platform:

| Platform | Jar | Drop into |
|---|---|---|
| Paper 1.21+ (Java 21) | `paper/target/AntiVPN-Paper-<version>.jar` | `plugins/` of the server |
| Velocity 3.x (Java 17+) | `velocity/target/AntiVPN-Velocity-<version>.jar` | `plugins/` of the proxy |

On a Velocity network you only need the Velocity jar — connections are
screened once at the proxy, before they ever reach a backend server.

## Features

- **Pre-login screening** — async checks on `AsyncPlayerPreLoginEvent`
  (Paper) and `PreLoginEvent` with an async continuation (Velocity); blocked
  connections never join.
- **Works with any JSON detection API** — defaults to
  [fastasfuck.net](https://api.fastasfuck.net) (no key, no registration).
  The URL (`{ip}` placeholder supported) and the response field
  (`isVPN`, `security.vpn`, …) are configurable.
- **IP whitelist/blacklist with CIDR support** — exact IPs and ranges like
  `10.0.0.0/8` or `2001:db8::/32`, IPv4 and IPv6.
- **Private-address bypass** — loopback/LAN/unique-local addresses skip the
  check by default (no pointless API calls in dev setups or behind proxies
  without IP forwarding).
- **Thread-safe, bounded TTL cache** keeps API traffic minimal; entry limit
  protects memory against bot floods.
- **Retry with backoff** on transient API failures; 4xx responses are not
  retried.
- **Configurable fail-mode**: `allow` (default, fail-open) or `deny`
  (fail-closed) when the API can't be reached.
- **Name whitelist** for trusted players, editable in-game.
- **Staff alerts** — players with `antivpn.notify` see in-game alerts when a
  connection is blocked.
- **Live stats** — blocks, cache size, hit rate, API calls/errors, average
  latency.
- **MiniMessage-formatted messages** — every user-facing string is
  configurable with modern `<red>`/`<gradient>`/etc. tags.
- **Tab completion** for every subcommand, on both platforms.

## Commands

Available as `/antivpn` (alias `/avpn`) on both platforms.

| Command | Description |
|---|---|
| `/antivpn reload` | Reload `config.yml` |
| `/antivpn debug` | Toggle debug logging (runtime only) |
| `/antivpn check <player\|ip>` | Check an online player or any IP literal |
| `/antivpn whitelist add \| remove \| list [name]` | Manage the name whitelist |
| `/antivpn stats` | Show operational metrics |
| `/antivpn cache clear \| size` | Inspect or clear the IP cache |
| `/antivpn help` | Show the help message |

## Permissions

| Permission | Description |
|---|---|
| `antivpn.admin` | Run `/antivpn` subcommands (default: op on Paper) |
| `antivpn.notify` | Receive in-game alerts when a connection is blocked |

On Velocity, grant permissions through a proxy permission plugin (e.g.
LuckPerms-Velocity); the console always has access.

## Configuration

`config.yml` is created on first start — in `plugins/AntiVPN/` on Paper and
`plugins/antivpn/` on Velocity. The keys are identical on both platforms:

```yaml
api:
  url: "https://api.fastasfuck.net/vpn/check/"   # {ip} placeholder supported
  response-field: "isVPN"   # dots descend into nested JSON objects
  timeout-ms: 5000
  retries: 1
  backoff-ms: 500

cache:
  duration-hours: 24
  max-entries: 50000
  cleanup-interval-minutes: 30

fail-mode: allow          # "allow" or "deny" when the API is unreachable
skip-private-ips: true    # don't check loopback/LAN addresses
notify-admins: true
debug-mode: false

whitelist: ["Basti20999"]   # player names that bypass the check
ip-whitelist: []            # exact IPs or CIDR ranges, always allowed
ip-blacklist: []            # exact IPs or CIDR ranges, always denied

messages:
  kick: "<red>VPN/Proxy connections are not allowed here.</red>"
  # ... all chat output is configurable; see config.yml for the full list
```

All messages use [MiniMessage](https://docs.advntr.dev/minimessage/format.html)
syntax. Placeholders such as `<player>`, `<ip>`, `<source>`, `<name>`, `<n>`,
`<blocks>`, and `<error>` are substituted before parsing.

Using a different detection API only takes two keys, e.g. for
[vpnapi.io](https://vpnapi.io):

```yaml
api:
  url: "https://vpnapi.io/api/{ip}?key=YOUR_KEY"
  response-field: "security.vpn"
```

Note: on Velocity, `/antivpn whitelist add|remove` rewrites `config.yml`
without the inline comments (Paper preserves them).

## Building

```bash
mvn verify
```

Requires JDK 21 and Maven. Jars land in `paper/target/` and
`velocity/target/`. The project is split into three modules: `common`
(platform-independent core, fully unit-tested), `paper`, and `velocity`.

## Upgrading from 1.x

- Paper configs keep working: all existing keys (`api.*`, `cache.*`,
  `fail-mode`, `whitelist`, `ip-whitelist`, `ip-blacklist`, `messages.*`)
  are unchanged, and new keys fall back to sensible defaults.
- `ip-whitelist`/`ip-blacklist` now also accept CIDR ranges.
- `/antivpn debug` no longer writes to `config.yml`; set `debug-mode` there
  if you want debug logging to survive restarts.
- The default API timeout/retry budget was tightened (5 s / 1 retry) so slow
  API days can't hold logins for longer than the client timeout.

## Compatibility

- **Paper** 1.21+ on Java 21 (Spigot is untested; the plugin relies on
  Paper's bundled Adventure/MiniMessage).
- **Velocity** 3.x on Java 17+.
