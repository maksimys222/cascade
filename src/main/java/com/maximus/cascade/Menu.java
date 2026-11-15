package com.maximus.cascade;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import net.kyori.adventure.text.Component;

/** Меню улучшений: рюкзак, ценность руды, скорость кирки и продажа. */
public class Menu {
	private static final int BAG = 10;
	private static final int VALUE = 12;
	private static final int SPEED = 14;
	private static final int SELL = 16;

	public static class Holder implements InventoryHolder {
		Inventory inv;

		@Override
		public Inventory getInventory() {
			return inv;
		}
	}

	private final Cascade plugin;

	public Menu(Cascade plugin) {
		this.plugin = plugin;
	}

	public void open(Player p) {
		Holder h = new Holder();
		h.inv = Bukkit.createInventory(h, 27, Text.c("&8Улучшения"));
		fill(p, h.inv);
		p.openInventory(h.inv);
	}

	private void fill(Player p, Inventory inv) {
		PlayerData d = plugin.store().get(p.getUniqueId());
		if (d == null) {
			return;
		}
		ItemStack pane = item(Material.GRAY_STAINED_GLASS_PANE, " ");
		for (int i = 0; i < inv.getSize(); i++) {
			inv.setItem(i, pane);
		}
		inv.setItem(BAG, upgrade(d, Material.CHEST, "&6Рюкзак", d.bagLevel, -1, Balance.bagCost(d),
			"&7Вмещает: &f" + Balance.capacity(d) + " руды",
			"&7Дальше: &a" + Math.round(Balance.capacity(d) * 1.5)));
		inv.setItem(VALUE, upgrade(d, Material.GOLD_NUGGET, "&eЦенность руды", d.valueLevel, -1, Balance.valueCost(d),
			"&7Сейчас: &f×" + fmt(Balance.valueMult(d)),
			"&7Дальше: &a×" + fmt(Balance.valueMult(d) + 0.25)));
		boolean maxSpeed = d.speedLevel >= Balance.SPEED_MAX;
		inv.setItem(SPEED, upgrade(d, Material.DIAMOND_PICKAXE, "&bСкорость кирки", d.speedLevel, Balance.SPEED_MAX, Balance.speedCost(d),
			"&7Пауза между ударами: &f" + Balance.cooldown(d) + " мс",
			maxSpeed ? "&aМаксимум" : "&7Дальше: &a" + Math.max(100, Balance.cooldown(d) - 50) + " мс"));
		inv.setItem(SELL, item(Material.GOLD_INGOT, "&a&lПродать рюкзак",
			"&7Руды: &f" + d.bag + "&7/" + Balance.capacity(d),
			"&7Получишь: &e" + Num.fmt(d.bagValue) + " ⛃",
			"",
			"&eЛКМ — продать"));
		inv.setItem(22, item(Material.SUNFLOWER, "&fМонеты: &e" + Num.fmt(d.coins) + " ⛃",
			"&7Рекорд каскада: &d" + d.bestChain + " шаг."));
	}

	private static String fmt(double v) {
		return String.format(java.util.Locale.ROOT, "%.2f", v).replaceAll("0+$", "").replaceAll("\\.$", "");
	}

	private static ItemStack upgrade(PlayerData d, Material m, String name, int level, int max, double cost, String now, String next) {
		boolean done = max >= 0 && level >= max;
		return item(m, name + " &7(ур. " + level + ")",
			now,
			next,
			"",
			done ? "&aПолностью улучшено" : "&7Цена: " + (d.coins >= cost ? "&e" : "&c") + Num.fmt(cost) + " ⛃",
			done ? "" : "&eЛКМ — улучшить");
	}

	public void click(Player p, int slot) {
		PlayerData d = plugin.store().get(p.getUniqueId());
		if (d == null) {
			return;
		}
		switch (slot) {
			case BAG -> buy(p, d, Balance.bagCost(d), () -> d.bagLevel++);
			case VALUE -> buy(p, d, Balance.valueCost(d), () -> d.valueLevel++);
			case SPEED -> {
				if (d.speedLevel < Balance.SPEED_MAX) {
					buy(p, d, Balance.speedCost(d), () -> d.speedLevel++);
				}
			}
			case SELL -> plugin.sell(p);
			default -> {
				return;
			}
		}
		fill(p, p.getOpenInventory().getTopInventory());
		plugin.board().update(p);
	}

	private void buy(Player p, PlayerData d, double cost, Runnable apply) {
		if (d.coins < cost) {
			p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.7f, 1f);
			p.sendActionBar(Text.c("&cНе хватает " + Num.fmt(cost - d.coins) + " ⛃"));
			return;
		}
		d.coins -= cost;
		apply.run();
		p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.5f);
	}

	static ItemStack item(Material m, String name, String... lore) {
		ItemStack it = new ItemStack(m);
		ItemMeta meta = it.getItemMeta();
		meta.displayName(Text.c(name));
		List<Component> l = new ArrayList<>();
		for (String s : lore) {
			l.add(Text.c(s));
		}
		meta.lore(l);
		meta.addItemFlags(ItemFlag.values());
		it.setItemMeta(meta);
		return it;
	}
}
