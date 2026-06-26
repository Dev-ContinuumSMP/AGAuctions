package com.example.buyorders.command;

import com.example.buyorders.BuyOrders;
import com.example.buyorders.gui.AuctionHouseGUI;
import com.example.buyorders.gui.CollectionGUI;
import com.example.buyorders.gui.OrdersGUI;
import com.example.buyorders.manager.AuctionHouseManager;
import com.example.buyorders.manager.OrderManager;
import com.example.buyorders.model.AuctionListing;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class AuctionHouseCommand implements CommandExecutor, TabCompleter {

    private final BuyOrders plugin;
    private final BuyOrderCommand buyOrderDelegate;

    public AuctionHouseCommand(BuyOrders plugin) {
        this.plugin = plugin;
        this.buyOrderDelegate = new BuyOrderCommand(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.msg("player-only"));
            return true;
        }

        if (!player.hasPermission("buyorders.ah.use")) {
            player.sendMessage(plugin.msg("no-permission"));
            return true;
        }

        if (args.length > 0 && matchesSubcommand(args[0], "sell")) {
            if (!player.hasPermission("buyorders.ah.sell")) {
                player.sendMessage(plugin.msg("no-permission"));
                return true;
            }

            if (args.length < 2) {
                player.sendMessage(BuyOrders.color("&eUsage: /ah sell <price> [amount]"));
                return true;
            }

            double price;
            int amount;
            try {
                price = Double.parseDouble(args[1]);
                amount = args.length >= 3 ? Integer.parseInt(args[2]) : player.getInventory().getItemInMainHand().getAmount();
                if (price <= 0 || amount <= 0) throw new NumberFormatException();
            } catch (Exception ex) {
                player.sendMessage(BuyOrders.color("&cInvalid price or amount."));
                return true;
            }

            ItemStack hand = player.getInventory().getItemInMainHand();
            String error = plugin.getAuctionHouseManager().createListing(player, hand, amount, price);
            if (error != null) {
                player.sendMessage(BuyOrders.color(error));
                return true;
            }

            player.sendMessage(BuyOrders.color("&aListed &e" + amount + "x " + hand.getType().name().toLowerCase() + " &afor &e"
                    + plugin.getCurrencyManager().format(price) + "&a."));
            return true;
        }

        if (args.length > 0 && matchesSubcommand(args[0], "auction")) {
            if (!player.hasPermission("buyorders.ah.sell")) {
                player.sendMessage(plugin.msg("no-permission"));
                return true;
            }

            if (args.length < 2) {
                player.sendMessage(BuyOrders.color("&eUsage: /ah auction <startPrice> [durationSeconds] [amount]"));
                return true;
            }

            double startingPrice;
            long durationSeconds;
            int amount;
            try {
                startingPrice = Double.parseDouble(args[1]);
                durationSeconds = args.length >= 3 ? Long.parseLong(args[2]) : plugin.getConfig().getLong("ah.bidding.default-duration-seconds", 3600L);
                amount = args.length >= 4 ? Integer.parseInt(args[3]) : player.getInventory().getItemInMainHand().getAmount();
                if (startingPrice <= 0 || durationSeconds <= 0 || amount <= 0) throw new NumberFormatException();
            } catch (Exception ex) {
                player.sendMessage(BuyOrders.color("&cInvalid starting price, duration, or amount."));
                return true;
            }

            ItemStack hand = player.getInventory().getItemInMainHand();
            String error = plugin.getAuctionHouseManager().createAuctionListing(player, hand, amount, startingPrice, durationSeconds);
            if (error != null) {
                player.sendMessage(BuyOrders.color(error));
                return true;
            }

            player.sendMessage(BuyOrders.color("&aCreated auction for &e" + amount + "x "
                    + hand.getType().name().toLowerCase() + " &astarting at &e"
                    + plugin.getCurrencyManager().format(startingPrice) + "&a."));
            return true;
        }

        if (args.length > 0 && matchesSubcommand(args[0], "bid")) {
            if (args.length < 3) {
                player.sendMessage(BuyOrders.color("&eUsage: /ah bid <listingId> <amount>"));
                return true;
            }

            AuctionListing listing = plugin.getAuctionHouseManager().findListingByShortId(args[1]);
            if (listing == null) {
                player.sendMessage(BuyOrders.color("&cListing not found. Use the short ID shown in the AH lore."));
                return true;
            }

            double bidAmount;
            try {
                bidAmount = Double.parseDouble(args[2]);
                if (bidAmount <= 0.0D) throw new NumberFormatException();
            } catch (Exception ex) {
                player.sendMessage(BuyOrders.color("&cInvalid bid amount."));
                return true;
            }

            var result = plugin.getAuctionHouseManager().placeBid(player, listing.getId(), bidAmount);
            if (result.error() != null) {
                player.sendMessage(BuyOrders.color(result.error()));
                return true;
            }

            player.sendMessage(BuyOrders.color(result.extended()
                    ? "&aBid placed at &e" + plugin.getCurrencyManager().format(result.bidAmount()) + "&a. Auction was extended."
                    : "&aBid placed at &e" + plugin.getCurrencyManager().format(result.bidAmount()) + "&a."));
            return true;
        }

        if (args.length > 0 && matchesSubcommand(args[0], "buy")) {
            if (args.length < 2) {
                player.sendMessage(BuyOrders.color("&eUsage: /ah buy <listingId>"));
                return true;
            }

            AuctionListing listing = plugin.getAuctionHouseManager().findListingByShortId(args[1]);
            if (listing == null) {
                player.sendMessage(BuyOrders.color("&cListing not found. Use the short ID shown in the AH lore."));
                return true;
            }

            var result = plugin.getAuctionHouseManager().purchaseListing(player, listing.getId());
            if (result.error() != null) {
                player.sendMessage(BuyOrders.color(result.error()));
                return true;
            }

            player.sendMessage(BuyOrders.color(result.deliveredToInventory()
                    ? "&aPurchased listing successfully."
                    : "&aPurchased listing. Item moved to AH claims."));
            return true;
        }

        if (args.length > 0 && matchesSubcommand(args[0], "buyorder", "buyorders")) {
            String[] forwarded = Arrays.copyOfRange(args, 1, args.length);
            if (forwarded.length == 0) {
                player.sendMessage(BuyOrders.color("&eUsage: /ah buyorder <material> <amount> <priceEach>"));
                player.sendMessage(BuyOrders.color("&eUsage: /ah buyorders <material> <amount> <priceEach>"));
                player.sendMessage(BuyOrders.color("&eUsage: /ah buyorder hand <amount> <priceEach>"));
                player.sendMessage(BuyOrders.color("&eUsage: /ah buyorders hand <amount> <priceEach>"));
                return true;
            }
            return buyOrderDelegate.onCommand(sender, command, label, forwarded);
        }

        if (args.length > 0 && matchesSubcommand(args[0], "orders", "order")) {
            if (args.length >= 2 && (args[1].equalsIgnoreCase("collect")
                    || args[1].equalsIgnoreCase("collection")
                    || args[1].equalsIgnoreCase("claim"))) {
                new CollectionGUI(plugin, player).open();
                return true;
            }

            Material filter = null;
            if (args.length >= 2) {
                filter = OrderManager.parseMaterial(args[1]);
                if (filter == null || filter.isAir() || !filter.isItem()) {
                    player.sendMessage(plugin.msg("unknown-material", "{material}", args[1]));
                    return true;
                }
            }

            new OrdersGUI(plugin, player, filter).open();
            return true;
        }

        if (args.length > 0 && matchesSubcommand(args[0], "claim")) {
            AuctionHouseManager manager = plugin.getAuctionHouseManager();
            int claimed = manager.claimAll(player);
            if (claimed <= 0) {
                player.sendMessage(BuyOrders.color("&eYou do not have any AH claims waiting."));
            } else {
                player.sendMessage(BuyOrders.color("&aClaimed &e" + claimed + " &aAH item stack(s)."));
            }
            return true;
        }

        new AuctionHouseGUI(plugin, player).open();
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> suggestions = new ArrayList<>();
            String typed = args[0].toLowerCase();
            if ("sell".startsWith(typed)) suggestions.add("sell");
            if ("auction".startsWith(typed)) suggestions.add("auction");
            if ("buy".startsWith(typed)) suggestions.add("buy");
            if ("buyorder".startsWith(typed)) suggestions.add("buyorder");
            if ("buyorders".startsWith(typed)) suggestions.add("buyorders");
            if ("bid".startsWith(typed)) suggestions.add("bid");
            if ("claim".startsWith(typed)) suggestions.add("claim");
            if ("order".startsWith(typed)) suggestions.add("order");
            if ("orders".startsWith(typed)) suggestions.add("orders");
            return suggestions;
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("sell")) {
            return List.of("<price>");
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("sell")) {
            return List.of("<amount>");
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("auction")) {
            return List.of("<startPrice>");
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("auction")) {
            return List.of("<durationSeconds>");
        }

        if (args.length == 4 && args[0].equalsIgnoreCase("auction")) {
            return List.of("<amount>");
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("bid")) {
            return List.of("<listingId>");
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("buy")) {
            return List.of("<listingId>");
        }

        if (args.length >= 2 && matchesSubcommand(args[0], "buyorder", "buyorders")) {
            String[] forwarded = Arrays.copyOfRange(args, 1, args.length);
            List<String> delegated = buyOrderDelegate.onTabComplete(sender, command, alias, forwarded);
            return delegated == null ? Collections.emptyList() : delegated;
        }

        if (args.length == 2 && matchesSubcommand(args[0], "orders", "order")) {
            List<String> suggestions = new ArrayList<>();
            String typed = args[1].toLowerCase();

            if ("claim".startsWith(typed)) suggestions.add("claim");
            if ("collect".startsWith(typed)) suggestions.add("collect");

            Arrays.stream(Material.values())
                    .filter(Material::isItem)
                    .map(m -> m.name().toLowerCase())
                    .filter(name -> name.startsWith(typed))
                    .limit(25)
                    .forEach(suggestions::add);

            return suggestions;
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("bid")) {
            return List.of("<amount>");
        }

        return Collections.emptyList();
    }

    private boolean matchesSubcommand(String input, String... aliases) {
        if (input == null || input.isBlank()) return false;

        String[] tokens = input.toLowerCase().split("/");
        for (String token : tokens) {
            for (String alias : aliases) {
                if (token.equals(alias.toLowerCase())) {
                    return true;
                }
            }
        }

        return false;
    }
}
