package com.example.buyorders.gui;

import com.example.buyorders.BuyOrders;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Location;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.block.sign.SignSide;
import org.bukkit.entity.Player;
import org.bukkit.event.block.SignChangeEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SearchGUI {

    private interface SearchTarget {
        String getSearchQuery();
        void setSearchQuery(String query);
        org.bukkit.inventory.Inventory buildInventory();
    }

    private static final PlainTextComponentSerializer PLAIN_TEXT = PlainTextComponentSerializer.plainText();
    private static final Map<UUID, SearchGUI> ACTIVE_SESSIONS = new ConcurrentHashMap<>();

    private final BuyOrders plugin;
    private final Player player;
    private final SearchTarget searchTarget;
    private final String initialQuery;
    private BlockState previousSignState;
    private BlockState previousSupportState;
    private Location signLocation;
    private boolean open;

    public SearchGUI(BuyOrders plugin, Player player, OrdersGUI ordersGui) {
        this.plugin = plugin;
        this.player = player;
        this.searchTarget = new SearchTarget() {
            @Override
            public String getSearchQuery() {
                return ordersGui.getSearchQuery();
            }

            @Override
            public void setSearchQuery(String query) {
                ordersGui.setSearchQuery(query);
            }

            @Override
            public org.bukkit.inventory.Inventory buildInventory() {
                return ordersGui.buildInventory();
            }
        };
        this.initialQuery = searchTarget.getSearchQuery();
    }

    public SearchGUI(BuyOrders plugin, Player player, CollectionGUI collectionGui) {
        this.plugin = plugin;
        this.player = player;
        this.searchTarget = new SearchTarget() {
            @Override
            public String getSearchQuery() {
                return collectionGui.getSearchQuery();
            }

            @Override
            public void setSearchQuery(String query) {
                collectionGui.setSearchQuery(query);
            }

            @Override
            public org.bukkit.inventory.Inventory buildInventory() {
                return collectionGui.buildInventory();
            }
        };
        this.initialQuery = searchTarget.getSearchQuery();
    }

    public SearchGUI(BuyOrders plugin, Player player, AuctionHouseGUI auctionGui) {
        this.plugin = plugin;
        this.player = player;
        this.searchTarget = new SearchTarget() {
            @Override
            public String getSearchQuery() {
                return auctionGui.getSearchQuery();
            }

            @Override
            public void setSearchQuery(String query) {
                auctionGui.setSearchQuery(query);
            }

            @Override
            public org.bukkit.inventory.Inventory buildInventory() {
                return auctionGui.buildInventory();
            }
        };
        this.initialQuery = searchTarget.getSearchQuery();
    }

    public void open() {
        cleanupSession(false);
        prepareTemporarySign();
        ACTIVE_SESSIONS.put(player.getUniqueId(), this);
        open = true;
        player.openSign((Sign) player.getWorld().getBlockAt(signLocation).getState(), Side.FRONT);
    }

    public static SearchGUI getActive(Player player) {
        return ACTIVE_SESSIONS.get(player.getUniqueId());
    }

    public static boolean handleSignChange(SignChangeEvent event) {
        SearchGUI gui = ACTIVE_SESSIONS.get(event.getPlayer().getUniqueId());
        if (gui == null) return false;
        if (!gui.matchesSession(event.getBlock().getLocation())) return false;

        String query = gui.readQuery(event);
        event.setCancelled(true);

        if (query.isBlank()) {
            event.getPlayer().sendMessage(gui.plugin.msg("search-empty-input"));
            gui.cleanupSession(true);
            return true;
        }

        gui.searchTarget.setSearchQuery(query);
        gui.cleanupSession(true);
        event.getPlayer().sendMessage(gui.plugin.msg("search-set", "{query}", query));
        Bukkit.getScheduler().runTask(gui.plugin, () -> event.getPlayer().openInventory(gui.searchTarget.buildInventory()));
        return true;
    }

    public void cleanup() {
        cleanupSession(true);
    }

    private void prepareTemporarySign() {
        Location location = resolveTemporaryLocation();
        Block supportBlock = location.clone().subtract(0, 1, 0).getBlock();
        Block signBlock = location.getBlock();

        previousSupportState = supportBlock.getState();
        previousSignState = signBlock.getState();

        supportBlock.setType(Material.BARRIER, false);
        signBlock.setType(Material.OAK_SIGN, false);

        Sign sign = (Sign) signBlock.getState();
        SignSide front = sign.getSide(Side.FRONT);
        front.line(0, Component.text(initialQuery == null ? "" : initialQuery));
        front.line(1, Component.text("Type your search"));
        front.line(2, Component.text("Press Done to apply"));
        front.line(3, Component.text(""));
        sign.setWaxed(false);
        sign.setAllowedEditorUniqueId(player.getUniqueId());
        sign.update(true, false);

        signLocation = location;
    }

    private boolean matchesSession(Location location) {
        return signLocation != null
            && location.getWorld() != null
            && location.getWorld().equals(signLocation.getWorld())
            && location.getBlockX() == signLocation.getBlockX()
            && location.getBlockY() == signLocation.getBlockY()
            && location.getBlockZ() == signLocation.getBlockZ();
    }

    private String readQuery(SignChangeEvent event) {
        Component line = event.line(0);
        return line == null ? "" : PLAIN_TEXT.serialize(line).trim();
    }

    private void cleanupSession(boolean removeFromRegistry) {
        if (removeFromRegistry) {
            ACTIVE_SESSIONS.remove(player.getUniqueId(), this);
        }

        if (!open) return;
        open = false;

        if (previousSignState != null) {
            previousSignState.update(true, false);
        }

        if (previousSupportState != null) {
            previousSupportState.update(true, false);
        }

        previousSignState = null;
        previousSupportState = null;
        signLocation = null;
    }

    private Location resolveTemporaryLocation() {
        Location playerLocation = player.getLocation();
        return new Location(
            playerLocation.getWorld(),
            playerLocation.getBlockX() + 2,
            playerLocation.getBlockY(),
            playerLocation.getBlockZ()
        );
    }
}