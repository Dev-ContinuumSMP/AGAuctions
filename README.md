## AxOrdersAddon

**AxOrdersAddon** adds a simple, polished buy-orders system to **AxAuctions**.

Players can create buy orders for vanilla items or custom items, deposit the payment up front, and let other players fill those orders through a clean in-game GUI. It is built for servers that want a player-driven market where demand matters just as much as selling.

### Features

- Create buy orders for materials or the item in your hand
- Supports custom items using item metadata and PersistentDataContainer keys
- AxAuctions currency hook support
- Vault support when available, with fallback to other registered AxAuctions currencies
- H2 database storage
- Configurable orders GUI
- Editable GUI title, store name, icons, slots, lore, and messages
- Sort orders by price or date
- Pending item delivery for offline buyers
- Admin order cancellation support
- `/orders reload` for config reloads

### Commands

- `/orders` - Open the buy orders GUI
- `/orders <material>` - View orders for a specific material
- `/orders reload` - Reload the plugin config
- `/buyorder <material> <amount> <priceEach>` - Create a material buy order
- `/buyorder hand <amount> <priceEach>` - Create a buy order for the item in your hand

### Permissions

- `axorders.use` - View and fill buy orders
- `axorders.create` - Create buy orders
- `axorders.admin` - Reload config and cancel any order
