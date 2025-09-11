# AntiVPN Plugin

Ein Minecraft Bukkit/Spigot Plugin, das VPN- und Proxy-Verbindungen automatisch erkennt und blockiert.

## Features

### 🛡️ Automatische VPN/Proxy-Erkennung
- Überprüft automatisch alle Spieler beim Server-Beitritt
- Nutzt die kostenlose vpn.otp.cx API für zuverlässige Erkennung
- Erkennt VPN- und Proxy-Verbindungen mit hoher Genauigkeit
- Asynchrone API-Abfragen für optimale Server-Performance
- **Keine API-Key Registrierung erforderlich**

### ⚡ Performance-Optimierung
- **24-Stunden Cache-System** - bereits überprüfte IPs werden zwischengespeichert
- Asynchrone Verarbeitung verhindert Server-Lag
- Configurable Timeouts (10 Sekunden) für API-Anfragen
- Minimale Latenz durch effiziente API-Nutzung

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
- **Keine API-Key erforderlich** - Funktioniert sofort nach Installation
- **Anpassbare Kick-Nachricht** - Personalisiere die Nachricht für blockierte Spieler
- **Debug-Modus** - Ein/Aus schaltbar über Config oder Command
- **Whitelist** - Vordefinierte Spieler in der Konfiguration

## Installation & Setup

1. Plugin in den `plugins/` Ordner kopieren
2. Server starten (erstellt automatisch `config.yml`)
3. **Fertig!** - Keine weitere Konfiguration nötig
4. Optional: Config anpassen und mit `/antivpn reload` neu laden

## API-Details
- **Service**: vpn.otp.cx (kostenlos)
- **Keine Registrierung erforderlich**
- **Keine Rate-Limits** für normale Nutzung
- Cache-System reduziert API-Anfragen und verbessert Performance

### API-Antwort Beispiel
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
- `antivpn.admin` - Zugriff auf alle Plugin-Befehle (Standard: OP)

## Kompatibilität
- **Minecraft Version**: 1.21+
- **Server Software**: Bukkit, Spigot, Paper
- **Java Version**: 8+

## Sicherheitsfeatures
- Automatische Fehlerbehandlung bei API-Ausfällen
- Graceful Behandlung von Player-Disconnects während der Überprüfung
- Sichere JSON-Parsing der API-Antworten
- User-Agent für API-Anfragen zur Identifikation
- Robuste Cache-Implementierung

## Vorteile gegenüber anderen Anti-VPN Plugins
- ✅ **Kostenlos** - Keine API-Keys oder Registrierung erforderlich
- ✅ **Sofort einsatzbereit** - Minimale Konfiguration
- ✅ **Hohe Genauigkeit** - Moderne VPN-Erkennungstechnologie
- ✅ **Performance-optimiert** - 24h Cache + asynchrone Verarbeitung
- ✅ **Benutzerfreundlich** - Einfache Commands und Administration

Das Plugin bietet eine zuverlässige, performante und kostenlose Lösung zum Schutz deines Minecraft-Servers vor VPN/Proxy-Nutzern mit minimaler Konfiguration und maximaler Benutzerfreundlichkeit.
