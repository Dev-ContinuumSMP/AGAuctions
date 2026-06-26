# AGAuctions

<p align="center">
	<img src="https://raw.githubusercontent.com/Dev-ContinuumSMP/AGAuctions/main/docs/images/logo.png" alt="AGAuctions Logo" />
</p>

<p align="center">
	A polished auction house and buy-order marketplace plugin for Paper and Folia servers.
</p>

<p align="center">
	<img alt="Java 21" src="https://img.shields.io/badge/Java-21-2ea043" />
	<img alt="Paper 1.21+" src="https://img.shields.io/badge/Paper-1.21%2B-0094ff" />
	<img alt="Folia Supported" src="https://img.shields.io/badge/Folia-Supported-7c3aed" />
	<img alt="License" src="https://img.shields.io/badge/License-View%20LICENSE-f59e0b" />
</p>

AGAuctions gives your economy a true two-way market: sellers list and compete for buyers, while buyers post demand through buy orders that other players can fill.

## Highlights

- AH-first flow centered around /ah
- Buy-now listings and timed auctions with bidding
- Buy orders for materials and in-hand custom items
- Offline-safe claims and collection flows
- PDC-aware custom item identity matching
- Optional strict identity and meta validation
- Item blacklisting for safer listings and buy-order creation
- Vault economy support when available
- Folia-compatible runtime support
- Modular YAML configuration system

## Quick Start

### Requirements

- Java 21
- Paper 1.21+ (Folia supported)

### Build

```bash
# Windows
mvnw.cmd clean package

# Linux/macOS
./mvnw clean package
```

### Install

1. Build the jar using Maven Wrapper.
2. Place the built jar in your server plugins directory.
3. Start the server once to generate plugin data and config files.
4. Edit configuration files to match your economy and GUI preferences.
5. Restart the server.

## Commands

### Auction House

| Command | Description |
|---|---|
| /ah | Open the auction house |
| /ah sell <price> [amount] | Create a buy-now listing |
| /ah auction <startPrice> [duration] [amount] | Create a timed auction listing |
| /ah buy <id> | Buy a direct listing |
| /ah bid <id> <amount> | Place a bid on an auction listing |
| /ah claim | Claim auction outcomes and settlements |

### Buy Orders

| Command | Description |
|---|---|
| /ah orders [material|collect|claim] | Open buy orders from AH flow |
| /ah order [material|collect|claim] | Alias of /ah orders |
| /ah buyorder <material> <amount> <priceEach> | Create a material buy order from AH flow |
| /ah buyorder hand <amount> <priceEach> | Create a buy order for the held item from AH flow |
| /ah buyorders <material> <amount> <priceEach> | Alias of /ah buyorder |
| /ah buyorders hand <amount> <priceEach> | Alias of /ah buyorder hand |
| /buyorder <material> <amount> <priceEach> | Legacy create command |
| /buyorder hand <amount> <priceEach> | Legacy held-item create command |
| /orders [material|collect|claim|reload] | Legacy orders path |

## Permissions

| Permission | Description | Default |
|---|---|---|
| buyorders.use | View and fill buy orders | true |
| buyorders.ah.use | Use auction house features | true |
| buyorders.ah.sell | Create auction listings | true |
| buyorders.create | Create buy orders | true |
| buyorders.admin | Admin controls (cancel/reload) | op |

## Screenshots

### Auction House

<p align="center">
	<img alt="Auction House" src="https://img.shields.io/badge/Auction%20House-Core%20Market-2563eb" />
	<img alt="Buy Now" src="https://img.shields.io/badge/Buy--Now-Enabled-0ea5e9" />
	<img alt="Bidding" src="https://img.shields.io/badge/Bidding-Live-1d4ed8" />
</p>

| Preview | Preview | Preview |
|---|---|---|
| <img src="https://raw.githubusercontent.com/Dev-ContinuumSMP/AGAuctions/main/docs/images/ah-overview.png" alt="Auction House Overview" width="300" /> | <img src="https://raw.githubusercontent.com/Dev-ContinuumSMP/AGAuctions/main/docs/images/ah-statistics.png" alt="Auction House Statistics" width="300" /> | <img src="https://raw.githubusercontent.com/Dev-ContinuumSMP/AGAuctions/main/docs/images/ah-filter.png" alt="Auction House Filter" width="300" /> |
| <img src="https://raw.githubusercontent.com/Dev-ContinuumSMP/AGAuctions/main/docs/images/ah-search.png" alt="Auction House Search" width="300" /> | <img src="https://raw.githubusercontent.com/Dev-ContinuumSMP/AGAuctions/main/docs/images/ah-claims.png" alt="Auction House Claims" width="300" /> | <img src="https://raw.githubusercontent.com/Dev-ContinuumSMP/AGAuctions/main/docs/images/ah-close.png" alt="Auction House Close" width="300" /> |
| <img src="https://raw.githubusercontent.com/Dev-ContinuumSMP/AGAuctions/main/docs/images/ah-page-switch.png" alt="Auction House Page Switch" width="300" /> |  |  |

### Orders

<p align="center">
	<img alt="Buy Orders" src="https://img.shields.io/badge/Buy%20Orders-Demand%20Flow-16a34a" />
	<img alt="Sorting" src="https://img.shields.io/badge/Sorting-Quick-22c55e" />
	<img alt="Search" src="https://img.shields.io/badge/Search-Instant-15803d" />
</p>

| Preview | Preview | Preview |
|---|---|---|
| <img src="https://raw.githubusercontent.com/Dev-ContinuumSMP/AGAuctions/main/docs/images/orders-overview.png" alt="Orders Overview" width="300" /> | <img src="https://raw.githubusercontent.com/Dev-ContinuumSMP/AGAuctions/main/docs/images/orders-filter.png" alt="Orders Collection Shortcut" width="300" /> | <img src="https://raw.githubusercontent.com/Dev-ContinuumSMP/AGAuctions/main/docs/images/orders-search.png" alt="Orders Search" width="300" /> |
| <img src="https://raw.githubusercontent.com/Dev-ContinuumSMP/AGAuctions/main/docs/images/orders-refresh.png" alt="Orders Refresh" width="300" /> | <img src="https://raw.githubusercontent.com/Dev-ContinuumSMP/AGAuctions/main/docs/images/orders-close.png" alt="Orders Close" width="300" /> | <img src="https://raw.githubusercontent.com/Dev-ContinuumSMP/AGAuctions/main/docs/images/orders-page-switch.png" alt="Orders Page Switch" width="300" /> |

### Collection

<p align="center">
	<img alt="Collection" src="https://img.shields.io/badge/Collection-Claim%20Queue-a855f7" />
	<img alt="Claim All" src="https://img.shields.io/badge/Claim%20All-Available-c084fc" />
	<img alt="Offline Safe" src="https://img.shields.io/badge/Offline-Safe-9333ea" />
</p>

| Preview | Preview | Preview |
|---|---|---|
| <img src="https://raw.githubusercontent.com/Dev-ContinuumSMP/AGAuctions/main/docs/images/collection-overview.png" alt="Collection Overview" width="300" /> | <img src="https://raw.githubusercontent.com/Dev-ContinuumSMP/AGAuctions/main/docs/images/collection-claim-all.png" alt="Collection Claim All" width="300" /> | <img src="https://raw.githubusercontent.com/Dev-ContinuumSMP/AGAuctions/main/docs/images/collection-waiting.png" alt="Collection Waiting Count" width="300" /> |
| <img src="https://raw.githubusercontent.com/Dev-ContinuumSMP/AGAuctions/main/docs/images/collection-back-to-orders.png" alt="Collection Back To Orders" width="300" /> |  |  |

## Configuration

AGAuctions is configured through modular YAML files:

- config.yml: Core behavior, economy provider, item matching, AH limits
- guis.yml: GUI slots, icons, lore, labels, and layout behavior
- locale.yml: Player-facing messages and language text
- gui-feature-matrix.yml: Per-profile GUI feature toggles
- blacklisted-items.yml: Material and name restrictions
- item-groups.yml: Category-style grouping support
- webhook-dispatch.yml: Webhook templates and dispatch settings

## Data Storage

- Persistence uses H2 database storage
- Data files are stored under the plugin data folder
- Legacy migration paths for older flat-file data are preserved

## Release

- Version: 1.0
- Artifact: AGAuctions-1.0.jar

## Support

Issues and pull requests are welcome.

When reporting a bug, include:

- Server software and version
- Java version
- Plugin version
- Reproduction steps
- Relevant logs

## License

See LICENSE.
