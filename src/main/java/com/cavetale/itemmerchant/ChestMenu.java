package com.cavetale.itemmerchant;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;

@Getter @Setter @RequiredArgsConstructor
public final class ChestMenu implements InventoryHolder {
    private Inventory inventory;
    private Map<Integer, Consumer<InventoryClickEvent>> clicks = new HashMap<>();
    private boolean valid = true;

    // Setup

    public Inventory createInventory(int size, String title) {
        this.inventory = Bukkit.getServer().createInventory(this, size, title);
        return this.inventory;
    }

    public void setClick(int slot, Consumer<InventoryClickEvent> callback) {
        this.clicks.put(slot, callback);
    }

    public void setClick(int slot, ItemStack item, Consumer<InventoryClickEvent> callback) {
        Objects.requireNonNull(this.inventory, "inventory is null").setItem(slot, item);
        setClick(slot, callback);
    }

    public InventoryView open(Player player) {
        return player.openInventory(Objects.requireNonNull(this.inventory, "inventory is null"));
    }

    // Callbacks

    public void onInventoryOpen(InventoryOpenEvent event) {
        return;
    }

    public void onInventoryClose(InventoryCloseEvent event) {
        return;
    }

    public void onInventoryClick(InventoryClickEvent event) {
        event.setCancelled(true);
        if (!this.valid) return;
        if (event.getClickedInventory() == null) return;
        if (!event.getClickedInventory().equals(this.inventory)) return;
        Objects.requireNonNull(this.inventory, "inventory is null");
        Consumer<InventoryClickEvent> run = this.clicks.get(event.getSlot());
        if (run != null) run.accept(event);
    }

    public void onInventoryDrag(InventoryDragEvent event) {
        event.setCancelled(true);
        Objects.requireNonNull(this.inventory, "inventory is null");
    }
}
