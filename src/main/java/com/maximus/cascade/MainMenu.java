package com.maximus.cascade;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

/** Главное меню (звезда в хотбаре): то же, что у НПС, но из любого места. */
public class MainMenu {
	private static final int SELL = 10;
	private static final int UPGRADES = 12;
	private static final int SKILLS = 14;
	private static final int MINES = 16;

	public static class Holder implements InventoryHolder {
		Inventory inv;

		@Override
		public Inventory getInventory() {
			return inv;
		}
	}

	private final Cascade plugin;

	public MainMenu(Cascade plugin) {
		this.plugin = plugin;
	}

	public void open(Player p) {
		PlayerData d = plugin.store().get(p.getUniqueId());
		if (d == null) {
			return;
		}
		Holder h = new Holder();
		h.inv = Bukkit.createInventory(h, 27, Text.c("&8Каскад — меню"));
		ItemStack pane = Menu.item(Material.GRAY_STAINED_GLASS_PANE, " ");
		for (int i = 0; i < 27; i++) {
			h.inv.setItem(i, pane);
		}
		h.inv.setItem(SELL, Menu.item(Material.GOLD_INGOT, "&6&lСкупщик &7— продать",
			"&7Руды: &f" + d.bag + "&7/" + Balance.capacity(d), "&7Получишь: &e" + Num.fmt(d.bagValue) + " ⛃"));
		h.inv.setItem(UPGRADES, Menu.item(Material.ANVIL, "&b&lКузнец &7— улучшения", "&7Рюкзак, ценность руды, скорость"));
		h.inv.setItem(SKILLS, Menu.item(Material.ENCHANTED_BOOK, "&d&lМудрец &7— навыки",
			"&7Уровень &e" + d.level + "&7, свободных очков: &e" + Skills.freePoints(d)));
		h.inv.setItem(MINES, Menu.item(Material.FILLED_MAP, "&a&lПроводник &7— шахты", "&7Сейчас: " + Mine.of(d).color() + Mine.of(d).name()));
		p.openInventory(h.inv);
	}

	public void click(Player p, int slot) {
		switch (slot) {
			case SELL -> {
				plugin.sell(p);
				open(p);
			}
			case UPGRADES -> plugin.menu().open(p);
			case SKILLS -> plugin.skillMenu().open(p);
			case MINES -> plugin.mineMenu().open(p);
			default -> {
			}
		}
	}
}
