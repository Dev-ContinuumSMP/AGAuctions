# AGAuctions

A polished auction house and buy-order marketplace plugin for Paper and Folia servers.

AGAuctions gives your economy a true two-way market:
- Sellers can list items for instant purchase or timed bidding.
- Buyers can post demand through buy orders and get filled by other players.

Built for live servers with secure custom-item handling, configurable GUIs, and reliable H2-backed storage.

## Why AGAuctions

- AH-first command flow centered around `/ah`
- Buy-now listings and timed auctions with bidding
- Buy orders for materials and in-hand items
- Offline-safe item collection and claim flows
- PDC-aware custom item identity matching
- Optional strict identity + meta validation to reduce spoofing
- Blacklist enforcement for listings and buy-order creation
- Vault economy support when available
- Folia-compatible runtime support
- Modular YAML configuration system

## Requirements

- Java 21
- Paper 1.21+ (Folia supported)

## Installation

1. Build the jar using Maven Wrapper.
2. Place the built jar in your server `plugins` directory.
3. Start the server once to generate plugin data and config files.
4. Edit configuration files to match your economy and GUI preferences.
5. Restart or reload the plugin.

Build commands:

```bash
# Windows
mvnw.cmd clean package

# Linux/macOS
./mvnw clean package
```

## Commands

### Auction House

| Command | Description |
|---|---|
| `/ah` | Open the auction house |
| `/ah sell <price> [amount]` | Create a buy-now listing |
| `/ah auction <startPrice> [duration] [amount]` | Create a timed auction listing |
| `/ah buy <id>` | Buy a direct listing |
| `/ah bid <id> <amount>` | Place a bid on an auction listing |
| `/ah claim` | Claim auction outcomes and settlements |

### Buy Orders

| Command | Description |
|---|---|
| `/ah orders [material|claim]` | Open buy orders from AH flow |
| `/ah buyorder <material> <amount> <priceEach>` | Create a buy order from AH flow |
| `/buyorder <material> <amount> <priceEach>` | Legacy create command |
| `/buyorder hand <amount> <priceEach>` | Create a buy order for the held item |
| `/orders [material|collect|claim|reload]` | Legacy orders path |

## Permissions

| Permission | Description | Default |
|---|---|---|
| `buyorders.use` | View and fill buy orders | `true` |
| `buyorders.ah.use` | Use auction house features | `true` |
| `buyorders.ah.sell` | Create auction listings | `true` |
| `buyorders.create` | Create buy orders | `true` |
| `buyorders.admin` | Admin controls (cancel/reload) | `op` |

## Configuration Files

AGAuctions is designed to be configured by server owners through modular YAML files:

- `config.yml`: Core behavior, economy provider, item matching, AH limits
- `guis.yml`: GUI slots, icons, lore, labels, and layout behavior
- `locale.yml`: Player-facing messages and language text
- `gui-feature-matrix.yml`: Per-profile GUI feature toggles
- `blacklisted-items.yml`: Material/name restrictions for safety rules
- `item-groups.yml`: Category-style grouping support
- `webhook-dispatch.yml`: Webhook templates and dispatch settings

## Data and Storage

- Persistence uses H2 database storage.
- Data files are stored under the plugin data folder.
- Legacy migration paths for older flat-file data are preserved where applicable.

## Release

- Current release: `1.0`
- Artifact: `AGAuctions-1.0.jar`

## Contributing and Support

Issues and pull requests are welcome.

When reporting a bug, include:
- Server software and version
- Java version
- Plugin version
- Reproduction steps
- Relevant logs

## License

See `LICENSE`.
