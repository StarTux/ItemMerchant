package com.cavetale.itemmerchant;

import com.winthier.generic_events.GenericEvents;
import com.winthier.sql.SQLDatabase;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.java.annotation.command.Command;
import org.bukkit.plugin.java.annotation.command.Commands;
import org.bukkit.plugin.java.annotation.dependency.Dependency;
import org.bukkit.plugin.java.annotation.permission.Permission;
import org.bukkit.plugin.java.annotation.permission.Permissions;
import org.bukkit.plugin.java.annotation.plugin.ApiVersion;
import org.bukkit.plugin.java.annotation.plugin.Description;
import org.bukkit.plugin.java.annotation.plugin.Plugin;
import org.bukkit.plugin.java.annotation.plugin.Website;
import org.bukkit.plugin.java.annotation.plugin.author.Author;

@Plugin(name = "ItemMerchant", version = "0.1")
@Dependency("GenericEvents")
@Dependency("SQL")
@Description("Sell or buy items to or from virtual merchants");
@ApiVersion(ApiVersion.Target.v1_13)
@Author("StarTux")
@Website("https://cavetale.com")
@Commands(@Command(name = "itemmerchant",
                   desc = "Admin interface",
                   aliases = {},
                   permission = "itemmerchant.itemmerchant",
                   usage = "/<command>"))
@Permissions(@Permission(name = "itemmerchant.itemmerchant",
                         desc = "Use /itemmerchant",
                         defaultValue = PermissionDefault.OP))

/**
 * The goal is to have an inventory window which will dynamically
 * update its title to reflect the current selling price. To
 * accomplish this, the inventory will have to be closed and reopened
 * with items intact whenever the player changes the content.
 */
public final class ItemMerchantPlugin extends JavaPlugin implements Listener {
    private final Map<UUID, InventoryContext> openInventories = new HashMap<>();
    private final Map<Material, SQLItem> itemPrices = new HashMap<>();
    private double baseFactor = 0.5;
    private double capacityFactor = 0.25;
    private double randomFactor = 0.25;
    private SQLDatabase database;

    // Plugin Overrides

    @RequiredArgsConstructor
    final static class InventoryContext {
        private final Inventory inventory;
        private final InventoryView view;
        private final double price;
        boolean valid = true;
        boolean closedByPlugin;
    }

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        baseFactor = getConfig().getDouble("price.Base", 0.5);
        capacityFactor = getConfig().getDouble("price.Capacity", 0.25);
        randomFactor = getConfig().getDouble("price.Random", 0.25);
        database = new SQLDatabase(this);
        database.registerTable(SQLItem.class);
        database.createAllTables();
        loadItemPrices();
        long delay = 20 * 60 * 10;
        getServer().getScheduler().runTaskTimer(this, () -> updateItemPrices(), delay, delay);
    }

    @Override
    public void onDisable() {
        for (UUID uuid: openInventories.keySet()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) player.closeInventory();
        }
        openInventories.clear();
    }

    @Override
    public boolean onCommand(CommandSender sender, org.bukkit.command.Command command, String alias, String[] args) {
        openSellInventory((Player)sender, ChatColor.BLUE + "Sell me Items", 4 * 9);
        return true;
    }

    // IO

    /**
     * Replace the current cache with the contents of the database.
     */
    void loadItemPrices() {
        itemPrices.clear();
        for (SQLItem row: database.find(SQLItem.class).findList()) {
            Material mat = Material.valueOf(row.getMaterial().toUpperCase());
            itemPrices.put(mat, row);
        }
    }

    /**
     * Update all prices according to their base price, capacity, and
     * the current time. Prices change twice a Minecraft day,
     * i.e. every 10 minutes.
     */
    void updateItemPrices() {
        final List<SQLItem> items = new ArrayList<>(itemPrices.values());
        double time = (double)(System.currentTimeMillis() / 1000L * 60L * 10L) * Math.PI / 10.0;
        for (SQLItem item: items) {
            double price = calculateItemPrice(item, time);
            item.setPrice(price);
        }
        final SQLDatabase db = database.async();
        getServer().getScheduler().runTaskAsynchronously(this, () -> {
                for (SQLItem item: items) db.save(item, "price");
            });
    }

    // Utility

    double getSellingPrice(ItemStack item) {
        if (new ItemStack(item.getType(), item.getAmount()).equals(item)) {
            SQLItem sqli = itemPrices.get(item.getType());
            if (sqli == null) return 0.0;
            return sqli.getPrice() * (double)item.getAmount();
        } else {
            return -1.0;
        }
    }

    double getSellingPrice(Inventory inventory) {
        double result = 0.0;
        for (ItemStack item: inventory) {
            if (item == null || item.getType() == Material.AIR) continue;
            double itemPrice = getSellingPrice(item);
            // Return false if a single item is invalid.
            if (itemPrice < 0.01) return itemPrice;
            result += itemPrice;
        }
        return result;
    }


    double calculateItemPrice(SQLItem row, double rtime) {
        final double time = rtime + row.getTimeOffset() * 2.0 * Math.PI;
        double rnd = 0.75 * Math.sin(time) + 0.25 * Math.sin(8 * time);
        double cap;
        if (row.getStorage() == 0) {
            cap = 2.0;
        } else {
            cap = Math.max(0.0, 2.0 - row.getStorage() / row.getCapacity());
        }
        double price =  row.getBasePrice() * (baseFactor + capacityFactor * cap + randomFactor * rnd);
        return price;
    }

    // API

    public InventoryContext openSellInventory(Player player, String title, int size) {
        if (title.length() > 32) title = title.substring(0, 32);
        Inventory inventory = getServer().createInventory(null, size, title);
        InventoryView view = player.openInventory(inventory);
        InventoryContext context = new InventoryContext(inventory, view, 0.0);
        openInventories.put(player.getUniqueId(), context);
        return context;
    }

    // Event Handlers

    @EventHandler(ignoreCancelled = false, priority = EventPriority.LOW)
    public void onInventoryClose(InventoryCloseEvent event) {
        // Preamble. Return fast.
        if (!(event.getPlayer() instanceof Player)) return;
        final Player player = (Player)event.getPlayer();
        final UUID playerId = player.getUniqueId();
        final InventoryContext context = openInventories.get(playerId);
        if (context == null) return;
        // Remove and check
        openInventories.remove(playerId);
        if (!context.inventory.equals(event.getView().getTopInventory())) {
            getLogger().warning("Inventory of " + player.getName() + "does not match!");
            return;
        }
        if (context.closedByPlugin) return;
        // Logic starts here
        double price = getSellingPrice(context.inventory);
        if (price >= 0.01) {
            GenericEvents.givePlayerMoney(playerId, price, this, "Items sold");
        } else {
            for (ItemStack item: context.inventory) {
                if (item == null || item.getType() == Material.AIR) continue;
                for (ItemStack drop: player.getInventory().addItem(item).values()) {
                    player.getWorld().dropItem(player.getEyeLocation(), drop);
                }
            }
        }
    }

    @EventHandler(ignoreCancelled = false, priority = EventPriority.LOW)
    public void onInventoryClick(InventoryClickEvent event) {
        // Preamble. Return fast.
        if (!(event.getWhoClicked() instanceof Player)) return;
        final Player player = (Player)event.getWhoClicked();
        final UUID playerId = player.getUniqueId();
        final InventoryContext context = openInventories.get(playerId);
        if (context == null) return;
        // Logic
        getServer().getScheduler().runTask(this, () -> updateSellInventory(player, context));
    }

    @EventHandler(ignoreCancelled = false, priority = EventPriority.LOW)
    public void onInventoryDrag(InventoryDragEvent event) {
        // Preamble. Return fast.
        if (!(event.getWhoClicked() instanceof Player)) return;
        final Player player = (Player)event.getWhoClicked();
        final UUID playerId = player.getUniqueId();
        final InventoryContext context = openInventories.get(playerId);
        if (context == null) return;
        // Logic
        getServer().getScheduler().runTask(this, () -> updateSellInventory(player, context));
    }

    /**
     * Called by the above listeners to update an item sell inventory
     * once the contents have been modified.
     * This is done on a new tick because closing inventories from
     * within related events is illegal.
     */
    private void updateSellInventory(final Player player, final InventoryContext context) {
        if (!context.valid) return;
        ItemStack cursor = context.view.getCursor();
        if (cursor != null && cursor.getType() != Material.AIR) return;
        double price = getSellingPrice(context.inventory);
        if (Math.abs(context.price - price) < 0.01) return;
        String priceStr;
        if (price < 0.01) {
            priceStr = ChatColor.DARK_RED + "INVALID ITEM";
        } else {
            priceStr = ChatColor.DARK_GREEN + GenericEvents.formatMoney(price);
        }
        if (priceStr.length() > 32) priceStr = priceStr.substring(0, 32);
        int size = context.inventory.getSize();
        Inventory newInventory = getServer().createInventory(null, size, priceStr);
        for (int i = 0; i < size; i += 1) {
            newInventory.setItem(i, context.inventory.getItem(i));
            context.inventory.setItem(i, null);
        }
        context.closedByPlugin = true; // Mark context for the event to ignore this
        final UUID playerId = player.getUniqueId();
        InventoryView newView = player.openInventory(newInventory); // Implies Close
        InventoryContext newContext = new InventoryContext(newInventory, newView, price);
        openInventories.put(player.getUniqueId(), newContext);
    }
}
