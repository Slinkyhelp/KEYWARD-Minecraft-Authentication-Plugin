package com.keyward.inventory.impl;

import com.keyward.Keyward;
import com.keyward.inventory.InventoryButton;
import com.keyward.inventory.InventoryGUI;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Random;

public class CaptchaGUI extends InventoryGUI {
    private final Keyward plugin;
    private final int targetSlot;
    private boolean resolved = false;

    public CaptchaGUI(Keyward plugin, Player player) {
        this.plugin = plugin;
        this.targetSlot = new Random().nextInt(27);
    }

    @Override
    protected Inventory createInventory() {
        return Bukkit.createInventory(null, 27, "\u00A7bVerify: Click the Emerald Block");
    }

    @Override
    public void decorate(Player player) {
        for (int slot = 0; slot < 27; slot++) {
            if (slot == targetSlot) {
                addButton(slot, new InventoryButton()
                        .creator(p -> createItem(Material.EMERALD_BLOCK, "\u00A7aClick Me!"))
                        .consumer(event -> {
                            resolved = true;
                            Player clicker = (Player) event.getWhoClicked();
                            clicker.closeInventory();
                            plugin.getCaptchaManager().handleSuccess(clicker);
                        }));
            } else {
                addButton(slot, new InventoryButton()
                        .creator(p -> createItem(Material.STONE, "\u00A77Not this one"))
                        .consumer(event -> {
                            resolved = true;
                            Player clicker = (Player) event.getWhoClicked();
                            clicker.closeInventory();
                            plugin.getCaptchaManager().handleFailure(clicker);
                        }));
            }
        }
        super.decorate(player);
    }

    @Override
    public void onClose(InventoryCloseEvent event) {
        super.onClose(event);
        if (!resolved) {
            resolved = true;
            Player player = (Player) event.getPlayer();
            plugin.getCaptchaManager().handleFailure(player);
        }
    }

    private ItemStack createItem(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            item.setItemMeta(meta);
        }
        return item;
    }
}