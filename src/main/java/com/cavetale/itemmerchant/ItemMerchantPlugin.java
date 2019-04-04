package com.cavetale.itemmerchant;

import com.winthier.generic_events.GenericEvents;
import com.winthier.sql.SQLDatabase;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

public final class ItemMerchantPlugin extends JavaPlugin {
    private Map<Material, Double> materialPrices;
    @Getter private SQLDatabase sqlDatabase;

    // Plugin Overrides

    @Override
    public void onEnable() {
        try {
            this.sqlDatabase = new SQLDatabase(this);
            this.sqlDatabase.registerTables(SQLLog.class, SQLPrice.class);
            this.sqlDatabase.createAllTables();
        } catch (Exception e) {
            getLogger().warning("Setting up databases");
            throw new IllegalStateException(e);
        }
        loadMaterialPrices();
        getCommand("itemmerchant").setExecutor(new ItemMerchantCommand(this));
        getServer().getPluginManager().registerEvents(new ChestMenuListener(), this);
    }

    @Override
    public void onDisable() {
        getServer().getOnlinePlayers().forEach((player) -> {
            InventoryView view = player.getOpenInventory();
            if (view != null && view.getTopInventory().getHolder() instanceof ChestMenu) player.closeInventory();
        });
    }

    /**
     * The /sell command.
     */
    @Override
    public boolean onCommand(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Player expected!");
            return true;
        }
        Player player = (Player)sender;
        if (args.length != 0) {
            player.sendMessage(ChatColor.RED + "Usage: /sell");
            return true;
        }
        player.playSound(player.getEyeLocation(), Sound.BLOCK_CHEST_OPEN, SoundCategory.MASTER, 0.5f, 1.0f);
        openShopChest(player);
        return true;
    }

    // Data Import

    void loadMaterialPrices() {
        this.materialPrices = new EnumMap<>(Material.class);
        this.sqlDatabase.find(SQLPrice.class).findList().forEach(row -> {
                Material mat = Material.valueOf(row.getMaterial().toUpperCase());
                this.materialPrices.put(mat, row.getPrice());
            });
    }

    void setMaterialPrice(Material mat, double price) {
        if (price < 0) throw new IllegalArgumentException("Price cannot be negative!");
        if (Double.isNaN(price)) throw new IllegalArgumentException("Price cannot be NaN!");
        if (Double.isInfinite(price)) throw new IllegalArgumentException("Price cannot be infinite!");
        this.materialPrices.put(Objects.requireNonNull(mat, "Material cannot be null!"), price);
        this.sqlDatabase.save(new SQLPrice(mat, price));
    }

    double getMaterialPrice(Material mat) {
        Double res = this.materialPrices.get(Objects.requireNonNull(mat, "Material cannot be null!"));
        return res != null ? res : 0;
    }

    // Import Export

    static Map<Material, Double> importMaterialPrices(File file) {
        Map<Material, Double> result = new HashMap<>();
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        for (String key: cfg.getKeys(false)) {
            final Material mat;
            try {
                mat = Material.valueOf(key.toUpperCase());
            } catch (IllegalArgumentException iae) {
                throw new IllegalArgumentException("Unknown material in prices.yml: " + key);
            }
            result.put(mat, cfg.getDouble(key));
        }
        return result;
    }

    void exportMaterialPrices(File file) {
        YamlConfiguration cfg = new YamlConfiguration();
        this.materialPrices.forEach((k, v) -> cfg.set(k.name().toLowerCase(), v));
        try {
            cfg.save(file);
        } catch (IOException ioe) {
            throw new RuntimeException(ioe);
        }
    }

    // Chest Menu

    @AllArgsConstructor
    final class ItemCache {
        final int index;
        double price;
        int amount;
    }

    InventoryView openShopChest(Player player) {
        Inventory playerInv = player.getInventory();
        Map<Material, ItemCache> items = new EnumMap<>(Material.class);
        ChestMenu menu = new ChestMenu();
        menu.createInventory(4 * 9, "" + ChatColor.DARK_PURPLE + ChatColor.BOLD + "Sell Items");
        for (int menuIndex = 0; menuIndex < 4 * 9; menuIndex += 1) {
            int playerIndex = menuIndex < 27 ? menuIndex + 9 : menuIndex - 27;
            ItemStack item = playerInv.getItem(playerIndex);
            if (item == null) continue;
            Material mat = item.getType();
            if (mat == Material.AIR) continue;
            if (!item.isSimilar(new ItemStack(mat))) continue;
            if (items.containsKey(mat)) {
                items.get(mat).amount += item.getAmount();
            } else {
                Double price = this.materialPrices.get(mat);
                if (price == null || price <= 0.0) price = 0.0;
                items.put(mat, new ItemCache(menuIndex, price, item.getAmount()));
            }
        }
        final String cl = "" + ChatColor.GREEN + ChatColor.BOLD;
        final String pu = "" + ChatColor.DARK_PURPLE + ChatColor.ITALIC;
        final String pr = "" + ChatColor.GREEN + ChatColor.ITALIC + ChatColor.UNDERLINE;
        final String vl = " " + ChatColor.LIGHT_PURPLE + ChatColor.STRIKETHROUGH
            + "                                 ";
        items.forEach((mat, item) -> {
            if (item.price < 0.01) return;
            ItemStack menuItem = new ItemStack(mat);
            ItemMeta meta = menuItem.getItemMeta();
            List<String> lore = new ArrayList<>();
            lore.add(cl + "Left click " + ChatColor.DARK_PURPLE + "to sell one item");
            lore.add("for " + pr + GenericEvents.formatMoney(item.price) + pu + ".");
            int stack = mat.getMaxStackSize();
            if (stack > 1 && item.amount > 1) {
                int amount = Math.min(stack, item.amount);
                lore.add(vl);
                lore.add(cl + "Right click " + ChatColor.DARK_PURPLE + "to sell one stack");
                lore.add("(" + amount + " items) for "
                         + pr + GenericEvents.formatMoney(item.price * (double)amount) + pu + ".");
            }
            if (item.amount > 1) {
                lore.add(vl);
                lore.add(cl + "Shift click " + ChatColor.DARK_PURPLE + "to sell all");
                lore.add("(" + item.amount + " items) for "
                         + pr + GenericEvents.formatMoney(item.price * (double)item.amount) + pu + ".");
            }
            meta.setLore(lore);
            menuItem.setItemMeta(meta);
            menu.setClick(item.index, menuItem, (event) -> onMenuClick(event, menu, mat, item));
        });
        return menu.open(player);
    }

    void onMenuClick(InventoryClickEvent event, ChestMenu menu, Material mat, ItemCache cache) {
        if (cache.price <= 0.01) throw new IllegalArgumentException("Cannot sell " + mat + " for less than 0.01!");
        Player player = (Player)event.getWhoClicked();
        boolean left = event.isLeftClick();
        boolean right = event.isRightClick();
        boolean shift = event.isShiftClick();
        final int amount;
        if (left && !shift) {
            amount = 1;
        } else if (right && !shift) {
            amount = Math.min(mat.getMaxStackSize(), cache.amount);
        } else if (shift) {
            amount = cache.amount;
        } else {
            player.playSound(player.getEyeLocation(), Sound.UI_BUTTON_CLICK, SoundCategory.MASTER, 0.5f, 0.75f);
            return;
        }
        menu.setValid(false);
        sellItems(player, mat, amount, cache.price);
        getServer().getScheduler().runTask(this, () -> openShopChest(player));
    }

    void sellItems(Player player, Material mat, int amount, double pricePerItem) {
        if (pricePerItem <= 0.01) throw new IllegalArgumentException("Cannot sell " + mat + " for less than 0.01!");
        int itemsRemain = amount;
        Inventory inv = player.getInventory();
        ItemStack proto = new ItemStack(mat);
        for (int i = 4 * 9 - 1; i >= 0 && itemsRemain > 0; i -= 1) {
            int playerIndex = i < 27 ? i + 9 : i - 27;
            ItemStack item = inv.getItem(playerIndex);
            if (item == null || item.getType() != mat) continue;
            if (!item.isSimilar(proto)) continue;
            int itemAmount = item.getAmount();
            int sold = Math.min(itemAmount, itemsRemain);
            item.setAmount(itemAmount - sold);
            itemsRemain -= sold;
        }
        int totalSold = amount - itemsRemain;
        double money = (double)totalSold * pricePerItem;
        String nice = niceEnum(mat.name());
        GenericEvents.givePlayerMoney(player.getUniqueId(), money, this, "Sold " + totalSold + "x" + nice);
        getLogger().info(player.getName() + " sold " + totalSold + "x" + mat.name() + " for " + fmt(money) + ".");
        final String rs = "" + ChatColor.RESET;
        final String hl = "" + ChatColor.GREEN;
        final String pr = "" + ChatColor.GREEN + ChatColor.UNDERLINE;
        if (this.sqlDatabase != null) {
            this.sqlDatabase.insertAsync(new SQLLog(player.getUniqueId(), mat, amount, money), null);
        }
        player.sendMessage(rs + "Sold " + hl + totalSold + rs + "x" + hl + nice + rs + " for " + pr
                           + GenericEvents.formatMoney(money) + rs + ".");
        player.playSound(player.getEyeLocation(), Sound.BLOCK_NOTE_BLOCK_GUITAR, SoundCategory.MASTER, 0.5f, 1.25f);
    }

    // Util

    static String niceEnum(String name) {
        return Arrays.stream(name.split("_"))
            .map((s) -> s.substring(0, 1) + s.substring(1).toLowerCase())
            .collect(Collectors.joining(" "));
    }

    static String fmt(double money) {
        return String.format("%.02f", money);
    }
}
