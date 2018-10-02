package com.cavetale.itemmerchant;

import com.winthier.generic_events.GenericEvents;
import com.winthier.sql.SQLDatabase;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
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
@Description("Sell or buy items to or from virtual merchants")
@ApiVersion(ApiVersion.Target.v1_13)
@Author("StarTux")
@Website("https://cavetale.com")
@Commands(@Command(name = "itemmerchant",
                   desc = "Admin interface",
                   aliases = {"im"},
                   permission = "itemmerchant.itemmerchant",
                   usage = "USAGE"
                   + "\n/im sell [player] - Open selling inventory"
                   + "\n/im buy [player] <category> - Open buying inventory"
                   + "\n/im setprice [item] <amount> [capacity] - Set price of item"
                   + "\n/im info [item] - Get info on item"
                   + "\n/im list - List item prices"
                   + "\n/im reload - Reload configurations"
                   + "\n/im update - Update all item prices"))
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
    private double baseFactor;
    private double capacityFactor;
    private double randomFactor;
    private double recoveryFactor = 0.01;
    private long updateInterval = 1000L * 60L * 5L;
    private SQLDatabase database;
    private static final int DEFAULT_CAPACITY = 1000;
    private double dbgRND, dbgCAP, dbgTIME;
    private long lastUpdateTime;

    // Plugin Overrides

    @RequiredArgsConstructor
    static final class InventoryContext {
        private final Inventory inventory;
        private final InventoryView view;
        private final double price;
        boolean valid = true;
        boolean closedByPlugin;
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        importConfig();
        database = new SQLDatabase(this);
        database.registerTable(SQLItem.class);
        database.createAllTables();
        loadItemPrices();
        getServer().getScheduler().runTaskTimer(this, () -> updateItemPrices(false), 200, 200);
        getServer().getPluginManager().registerEvents(this, this);
        lastUpdateTime = System.currentTimeMillis() / updateInterval;
    }

    void importConfig() {
        reloadConfig();
        baseFactor = getConfig().getDouble("price.Base");
        capacityFactor = getConfig().getDouble("price.Capacity");
        randomFactor = getConfig().getDouble("price.Random");
        recoveryFactor = getConfig().getDouble("RecoveryFactor");
        updateInterval = Math.max(1L, getConfig().getLong("UpdateInterval")) * 1000L * 60L;
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
        Player player = sender instanceof Player ? (Player)sender : null;
        Player target; // Targeted by command, where it applies
        if (args.length == 0) return false;
        switch (args[0]) {
        case "sell":
            if (args.length == 1 || args.length == 2) {
                // Find target
                if (args.length >= 2) {
                    String name = args[1];
                    target = getServer().getPlayerExact(name);
                    if (target == null) {
                        sender.sendMessage("Player not found: " + name);
                        return true;
                    }
                } else {
                    if (player == null) {
                        sender.sendMessage("Player expected");
                        return true;
                    }
                    target = player;
                }
                openSellInventory(target, ChatColor.BLUE + "Sell me Items", 4 * 9);
                sender.sendMessage("Opened selling inventory for " + target.getName());
                return true;
            }
            break;
        case "buy":
            if (args.length == 2 || args.length == 3) {
                int argi = 1;
                if (args.length >= 3) {
                    String name = args[argi++];
                    target = getServer().getPlayerExact(name);
                    if (target == null) {
                        sender.sendMessage("Player not found: " + name);
                        return true;
                    }
                } else {
                    target = player;
                }
                String shopName = args[argi++];
                // TODO: find and open buy shop
                return true;
            }
            break;
        case "setprice":
            if (args.length >= 3 && args.length <= 4) {
                List<Material> mats = new ArrayList<>();
                String arg = args[1];
                if (arg.startsWith("*")) {
                    arg = arg.substring(1);
                    for (Material mat: Material.values()) {
                        if (mat.name().endsWith(arg.toUpperCase())) {
                            mats.add(mat);
                        }
                    }
                } else {
                    try {
                        mats.add(Material.valueOf(arg.toUpperCase()));
                    } catch (IllegalArgumentException iae) { }
                }
                if (mats.isEmpty()) {
                    sender.sendMessage("No item matched " + arg);
                    return true;
                }
                double price;
                arg = args[2];
                try {
                    price = Double.parseDouble(arg);
                } catch (NumberFormatException nfe) {
                    player.sendMessage(ChatColor.RED + "Invalid price: " + arg);
                    return true;
                }
                int capacity = -1;
                if (args.length >= 4) {
                    arg = args[3];
                    try {
                        capacity = Integer.parseInt(arg);
                    } catch (NumberFormatException nfe) {
                        player.sendMessage(ChatColor.RED + "Invalid capacity: " + arg);
                        return true;
                    }
                }
                for (Material mat: mats) {
                    SQLItem row = itemPrices.get(mat);
                    if (row != null) {
                        row.setBasePrice(price);
                        if (capacity > 0) row.setCapacity(capacity);
                        database.save(row);
                    } else {
                        row = new SQLItem(mat, price, capacity > 0 ? capacity : DEFAULT_CAPACITY);
                        database.save(row);
                        itemPrices.put(mat, row);
                    }
                    sender.sendMessage("Set price of " + mat.name().toLowerCase() + " to " + GenericEvents.formatMoney(price) + ", capacity=" + row.getCapacity() + ".");
                }
                return true;
            }
            break;
        case "fill":
            if (args.length == 1) {
                int count = 0;
                for (Material mat: Material.values()) {
                    if (!mat.isItem() || itemPrices.containsKey(mat)) continue;
                    SQLItem row = new SQLItem(mat, 0.10, DEFAULT_CAPACITY);
                    database.save(row);
                    itemPrices.put(mat, row);
                    count += 1;
                }
                sender.sendMessage(count + " Items inserted");
                return true;
            }
            break;
        case "info":
            if (args.length == 1 || args.length == 2) {
                Material mat;
                if (args.length == 1) {
                    if (player == null) {
                        sender.sendMessage("Player expected.");
                        return true;
                    }
                    ItemStack item = player.getInventory().getItemInMainHand();
                    if (item == null || item.getType() == Material.AIR) {
                        player.sendMessage(ChatColor.RED + "No item in hand.");
                        return true;
                    }
                    mat = item.getType();
                } else {
                    try {
                        mat = Material.valueOf(args[1].toUpperCase());
                    } catch (IllegalArgumentException iae) {
                        sender.sendMessage(ChatColor.RED + "Unknown item: " + args[1]);
                        return true;
                    }
                }
                SQLItem row = itemPrices.get(mat);
                if (row == null) {
                    sender.sendMessage(ChatColor.RED + "No entry for " + mat);
                    return true;
                }
                double base = row.getBasePrice();
                sender.sendMessage(row.getMaterial() + " §7price§8=§e" + fmt(row.getPrice()) + "§8/§6" + fmt(base) + " §7store§8=§e" + fmt(row.getStorage()) + "§8/§6" + fmt(row.getCapacity()) + " §7off§8=§e" + fmt(row.getTimeOffset()));
                calculateItemPrice(row, dbgTIME);
                sender.sendMessage("§ccap§4=§f" + fmt(dbgCAP) + "§8*§f" + fmt(capacityFactor) + "§8=>§f" + fmt(dbgCAP * capacityFactor) + "§8*§f" + fmt(base) + "§8=>§f" + fmt(dbgCAP * capacityFactor * base));
                sender.sendMessage("§crnd§4=§f" + fmt(dbgRND) + "§8*§f" + fmt(randomFactor) + "§8=>§f" + fmt(dbgRND * randomFactor) + "§8*§f" + fmt(base) + "§8=>§f" + fmt(dbgRND * randomFactor * base));
                sender.sendMessage("§cfactor§8=§f" + fmt(baseFactor + capacityFactor * dbgCAP + randomFactor * dbgRND));
                return true;
            }
            break;
        case "list":
            if (args.length == 1) {
                sender.sendMessage("" + ChatColor.YELLOW + itemPrices.size() + " Items:");
                for (SQLItem row: itemPrices.values()) {
                    sender.sendMessage(ChatColor.YELLOW + String.format("%s base=%.02f off=%.02f cap=%d stor=%d price=%.02f", row.getMaterial(), row.getBasePrice(), row.getTimeOffset(), row.getCapacity(), row.getStorage(), row.getPrice()));
                }
                return true;
            }
            break;
        case "update":
            if (args.length == 1) {
                updateItemPrices(true);
                sender.sendMessage("Item prices updated");
                return true;
            }
            break;
        case "reload":
            if (args.length == 1) {
                importConfig();
                sender.sendMessage("Configuration reloaded.");
                return true;
            }
            break;
        default:
            break;
        }
        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, org.bukkit.command.Command command, String alias, String[] args) {
        if (args.length == 0) return null;
        String cmd = args[0];
        String arg = args[args.length - 1];
        if ((cmd.equals("setprice") && args.length == 2)
            || cmd.equals("info") && args.length == 2) {
            return Arrays.stream(Material.values()).map(Material::name).filter(n -> n.startsWith(arg.toUpperCase())).collect(Collectors.toList());
        }
        return null;
    }

    // --- IO

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
     * the current time. Prices change every few minutes.
     */
    void updateItemPrices(boolean force) {
        // Every 10 minutes
        final long time = System.currentTimeMillis() / updateInterval;
        final boolean timeChanged = (time != lastUpdateTime);
        lastUpdateTime = time;
        if (!timeChanged && !force) return;
        if (timeChanged) {
            getLogger().info("Updating prices and reducing storage...");
        } else {
            getLogger().info("Updating prices...");
        }
        final List<SQLItem> items = new ArrayList<>(itemPrices.values());
        for (SQLItem item: items) {
            double price = calculateItemPrice(item, (double)time * Math.PI / 10.0);
            item.setPrice(price);
            if (timeChanged) {
                int storage = item.getStorage();
                int capacity = item.getCapacity();
                if (storage > capacity) {
                    storage -= (int)(Math.random() * recoveryFactor * (double)capacity);
                    if (storage < 0) storage = 0;
                    item.setStorage(storage);
                }
            }
        }
        for (SQLItem item: items) database.save(item, "price", "storage");
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
            if (itemPrice < 0) return itemPrice;
            result += itemPrice;
        }
        return result;
    }

    double calculateItemPrice(SQLItem row, double rtime) {
        final double time = rtime + row.getTimeOffset() * 2.0 * Math.PI;
        double rnd = (0.75 * Math.sin(time) + 0.25 * Math.sin(8 * time)) * 0.5 + 0.5;
        double cap;
        if (row.getStorage() == 0 || row.getStorage() <= row.getCapacity()) {
            cap = 1.0;
        } else {
            cap = 1.0 / ((double)row.getStorage() / (double)row.getCapacity());
            cap = Math.min(1.0, Math.max(0.0, cap));
        }
        double price = row.getBasePrice() * (baseFactor + capacityFactor * cap + randomFactor * rnd);
        this.dbgTIME = rtime;
        this.dbgRND = rnd;
        this.dbgCAP = cap;
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
        int total = 0;
        if (price > 0) {
            Set<SQLItem> dirty = new HashSet<>();
            Map<Material, Integer> totals = new HashMap<>();
            for (ItemStack item: context.inventory) {
                if (item == null || item.getType() == Material.AIR) continue;
                Material mat = item.getType();
                SQLItem row = itemPrices.get(mat);
                if (row == null) continue; // Should never happen
                dirty.add(row);
                row.setStorage(row.getStorage() + item.getAmount());
                total += item.getAmount();
                Integer amount = totals.get(mat);
                if (amount == null) amount = 0;
                amount += item.getAmount();
                totals.put(mat, amount);
            }
            for (SQLItem d: dirty) database.save(d, "storage");
            GenericEvents.givePlayerMoney(playerId, price, this, total + " items sold");
            player.sendMessage("" + ChatColor.GREEN + total + " Items sold for " + GenericEvents.formatMoney(price) + ".");
            StringBuilder sb = new StringBuilder(player.getName()).append(" sold");
            for (Map.Entry<Material, Integer> entry: totals.entrySet()) {
                sb.append(" ").append(entry.getValue()).append("x").append(entry.getKey().name().toLowerCase());
            }
            sb.append(" for ").append(GenericEvents.formatMoney(price)).append(".");
            getLogger().info(sb.toString());
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
        if (price < 0) {
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

    String fmt(double num) {
        return String.format("%.02f", num);
    }
}
