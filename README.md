# AntiVPN Plugin

Ein Minecraft Bukkit/Spigot Plugin, das VPN- und Proxy-Verbindungen automatisch erkennt und blockiert.

## Features

### 🛡️ Automatische VPN/Proxy-Erkennung
- Überprüft automatisch alle Spieler beim Server-Beitritt
- Nutzt die proxycheck.io API für zuverlässige Erkennung
- Erkennt verschiedene Proxy-Typen: VPN, HTTP, HTTPS, SOCKS
- Asynchrone API-Abfragen für optimale Server-Performance

### ⚡ Performance-Optimierung
- **24-Stunden Cache-System** - bereits überprüfte IPs werden zwischengespeichert
- Asynchrone Verarbeitung verhindert Server-Lag
- Configurable Timeouts (10 Sekunden) für API-Anfragen

### 👥 Whitelist-System
- Verwalte Spieler, die niemals blockiert werden sollen
- Einfache Befehle zum Hinzufügen/Entfernen von Spielern
- Whitelist wird dauerhaft in der Konfiguration gespeichert

### 🔧 Administration & Debug
- **Reload-Funktion** - Konfiguration ohne Server-Neustart aktualisieren
- **Debug-Modus** - Detaillierte Logs für Fehlersuche und Monitoring
- **Manual Check** - Einzelne Spieler-IPs manuell überprüfen
- Umfassende Fehlerbehandlung und Logging

### 📋 Commands
- `/antivpn reload` - Konfiguration neu laden
- `/antivpn debug` - Debug-Modus ein/ausschalten  
- `/antivpn check <Spieler>` - IP eines Online-Spielers überprüfen
- `/antivpn whitelist add <Name>` - Spieler zur Whitelist hinzufügen
- `/antivpn whitelist remove <Name>` - Spieler von Whitelist entfernen
- `/antivpn whitelist list` - Alle Whitelist-Einträge anzeigen

### ⚙️ Konfiguration
- **API-Key Setup** - Einfache Integration mit proxycheck.io
- **Anpassbare Kick-Nachricht** - Personalisiere die Nachricht für blockierte Spieler
- **Debug-Modus** - Ein/Aus schaltbar über Config oder Command
- **Whitelist** - Vordefinierte Spieler in der Konfiguration

## Installation & Setup

1. Plugin in den `plugins/` Ordner kopieren
2. Server starten (erstellt automatisch `config.yml`)
3. Kostenlosen API-Key auf [proxycheck.io](https://proxycheck.io) erstellen
4. API-Key in der `config.yml` eintragen
5. Config mit `/antivpn reload` neu laden

## API-Limits
- **Kostenloser Plan**: 1.000 Anfragen pro Tag
- **Kostenpflichtige Pläne**: Höhere Limits verfügbar
- Cache-System reduziert API-Anfragen erheblich

## Permissions
- `antivpn.admin` - Zugriff auf alle Plugin-Befehle (Standard: OP)

## Kompatibilität
- **Minecraft Version**: 1.21+
- **Server Software**: Bukkit, Spigot, Paper
- **Java Version**: 8+

## Sicherheitsfeatures
- Automatische API-Key Validierung beim Start
- Graceful Fehlerbehandlung bei API-Ausfällen
- Sichere Behandlung von Player-Disconnects während der Überprüfung
- User-Agent für API-Anfragen zur Identifikation

Das Plugin bietet eine zuverlässige, performante Lösung zum Schutz deines Minecraft-Servers vor VPN/Proxy-Nutzern mit minimaler Konfiguration und maximaler Flexibilität.
