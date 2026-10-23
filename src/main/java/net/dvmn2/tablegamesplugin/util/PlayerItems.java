package net.dvmn2.tablegamesplugin.util;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

/** Inventory helpers shared by commands and listeners. Thread: main server thread. */
public final class PlayerItems {

    private PlayerItems() {
    }

    /** Adds {@code item} to the inventory; whatever does not fit is dropped at the player's feet. */
    public static void giveOrDrop(Player player, ItemStack item) {
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(item);
        for (ItemStack extra : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), extra);
        }
    }

    /** Removes one item from the stack in the main hand. */
    public static void consumeOneFromMainHand(Player player) {
        ItemStack current = player.getInventory().getItemInMainHand();
        if (current.getAmount() <= 1) {
            player.getInventory().setItemInMainHand(null);
        } else {
            current.setAmount(current.getAmount() - 1);
        }
    }
}
