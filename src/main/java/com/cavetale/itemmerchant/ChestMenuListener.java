package com.cavetale.itemmerchant;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;

final class ChestMenuListener implements Listener {
    @EventHandler(ignoreCancelled = false, priority = EventPriority.LOWEST)
    void onInventoryOpen(InventoryOpenEvent event) {
        if (event.getInventory().getHolder() instanceof ChestMenu) {
            ((ChestMenu) event.getInventory().getHolder()).onInventoryOpen(event);
        }
    }

    @EventHandler(ignoreCancelled = false, priority = EventPriority.LOWEST)
    void onInventoryClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof ChestMenu) {
            ((ChestMenu) event.getInventory().getHolder()).onInventoryClose(event);
        }
    }

    @EventHandler(ignoreCancelled = false, priority = EventPriority.LOWEST)
    void onInventoryClick(InventoryClickEvent event) {
        if (event.getInventory().getHolder() instanceof ChestMenu) {
            ((ChestMenu) event.getInventory().getHolder()).onInventoryClick(event);
        }
    }

    @EventHandler(ignoreCancelled = false, priority = EventPriority.LOWEST)
    void onInventoryDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof ChestMenu) {
            ((ChestMenu) event.getInventory().getHolder()).onInventoryDrag(event);
        }
    }
}
