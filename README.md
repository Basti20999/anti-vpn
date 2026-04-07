# AntiVPN Plugin

A Minecraft Bukkit/Spigot plugin that automatically detects and blocks VPN and proxy connections.

## Features

### Shield Automatic VPN/Proxy Detection
- Automatically checks all players on server join
- Uses the free fastasfuck.net API for reliable detection
- Detects VPN and proxy connections with high accuracy
- Asynchronous API requests for optimal server performance
- **No API key registration required**

### Performance Optimization
- **24-hour cache system** - already checked IPs are cached
- Asynchronous processing prevents server lag
- Configurable timeouts (10 seconds) for API requests
- Minimal latency through efficient API usage

### Whitelist System
- Manage players that should never be blocked
- Simple commands to add/remove players
- Whitelist is permanently stored in the configuration

### Administration & Debug
- **Reload function** - update configuration without server restart
- **Debug mode** - detailed logs for troubleshooting and monitoring
- **Manual check** - manually check individual player IPs
- Comprehensive error handling and logging

### Commands
- `/antivpn reload` - Reload configuration
- `/antivpn debug` - Toggle debug mode on/off
- `/antivpn check <player>` - Check the IP of an online player
- `/antivpn whitelist add <name>` - Add player to whitelist
- `/antivpn whitelist remove <name>` - Remove player from whitelist
- `/antivpn whitelist list` - Show all whitelist entries

### Configuration
- **No API key required** - Works immediately after installation
- **Customizable kick message** - Personalize the message for blocked players
- **Debug mode** - Toggleable via config or command
- **Whitelist** - Pre-defined players in the configuration

## Installation & Setup

1. Copy the plugin into the `plugins/` folder
2. Start the server (automatically creates `config.yml`)
3. **Done!** - No further configuration needed
4. Optional: Adjust config and reload with `/antivpn reload`

## API Details
- **Service**: fastasfuck.net (free)
- **No registration required**
- **No rate limits** for normal usage
- Cache system reduces API requests and improves performance

### API Response Example
```json
{
  "ip": "104.28.158.214",
  "isVPN": true,
  "details": {
    "asn": "AS13335",
    "asnOrg": "CLOUDFLARENET",
    "isp": "Cloudflare, Inc. (CLOUD14)",
    "hostname": null,
    "asnMatch": true,
    "ispMatch": true,
    "ipListed": false
  }
}
```

## Permissions
- `antivpn.admin` - Access to all plugin commands (default: OP)

## Compatibility
- **Minecraft Version**: 1.21+
- **Server Software**: Bukkit, Spigot, Paper
- **Java Version**: 8+

## Security Features
- Automatic error handling for API failures
- Graceful handling of player disconnects during check
- Secure JSON parsing of API responses
- User-Agent for API requests for identification
- Robust cache implementation

## Advantages over other Anti-VPN Plugins
- ✅ **Free** - No API keys or registration required
- ✅ **Ready to use** - Minimal configuration
- ✅ **High accuracy** - Modern VPN detection technology
- ✅ **Performance optimized** - 24h cache + asynchronous processing
- ✅ **User-friendly** - Simple commands and administration

This plugin provides a reliable, performant, and free solution to protect your Minecraft server from VPN/proxy users with minimal configuration and maximum ease of use.
