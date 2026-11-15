package com.maximus.cascade;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import io.papermc.paper.datacomponent.DataComponentTypes;

/** Меню Проводника: список шахт, покупка и переезд. */
public class MineMenu {
	private static final int[] SLOTS = {11, 12, 13, 14, 15};

	public static class Holder implements InventoryHolder {
		Inventory inv;

		@Override
		public Inventory getInventory() {
			return inv;
		}
	}

	private final Cascade plugin;

	public MineMenu(Cascade plugin) {
		this.plugin = plugin;
	}

	public void open(Player p) {
		Holder h = new Holder();
		h.inv = Bukkit.createInventory(h, 27, Text.c("&8Проводник — шахты"));
		fill(p, h.inv);
		p.openInventory(h.inv);
	}

	private void fill(Player p, Inventory inv) {
		PlayerData d = plugin.store().get(p.getUniqueId());
		if (d == null) {
			return;
		}
		ItemStack pane = Menu.item(Material.BLACK_STAINED_GLASS_PANE, " ");
		for (int i = 0; i < inv.getSize(); i++) {
			inv.setItem(i, pane);
		}
		for (int i = 0; i < Mine.ALL.size(); i++) {
			Mine m = Mine.ALL.get(i);
			boolean here = d.mine == i;
			boolean owned = d.hasMine(i);
			boolean prevOwned = i == 0 || d.hasMine(i - 1);
			String state = here ? "&a▶ Ты здесь"
				: owned ? "&eЛКМ — переехать"
				: !prevOwned ? "&8Сначала открой предыдущую шахту"
				: "&7Цена: " + (d.coins >= m.price() ? "&e" : "&c") + Num.fmt(m.price()) + " ⛃  &eЛКМ — купить";
			StringBuilder ores = new StringBuilder();
			for (int k = 0; k < m.oreNames().length; k++) {
				ores.append(k == 0 ? "" : "&7, ").append(m.oreColors()[k]).append(m.oreNames()[k]);
			}
			ItemStack it = Menu.item(owned || prevOwned ? m.icon() : Material.GRAY_DYE, m.color() + "&l" + m.name(),
				"&7Руда дороже в &f×" + Num.fmt(m.valueMult()),
				ores.toString(),
				"",
				"&7" + m.ruleText(),
				"",
				state);
			it.setData(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, here);
			inv.setItem(SLOTS[i], it);
		}
		inv.setItem(22, Menu.item(Material.SUNFLOWER, "&fМонеты: &e" + Num.fmt(d.coins) + " ⛃",
			"&7Улучшения и навыки работают", "&7во всех шахтах"));
	}

	public void click(Player p, int slot) {
		PlayerData d = plugin.store().get(p.getUniqueId());
		if (d == null) {
			return;
		}
		for (int i = 0; i < SLOTS.length; i++) {
			if (SLOTS[i] != slot) {
				continue;
			}
			Mine m = Mine.ALL.get(i);
			if (d.mine == i) {
				return;
			}
			if (!d.hasMine(i)) {
				if (i > 0 && !d.hasMine(i - 1)) {
					return;
				}
				if (d.coins < m.price()) {
					p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.7f, 1f);
					p.sendActionBar(Text.c("&cНе хватает " + Num.fmt(m.price() - d.coins) + " ⛃"));
					return;
				}
				d.coins -= m.price();
				d.unlocked |= 1 << i;
				p.sendMessage(Text.c("&a&lОткрыта шахта: " + m.color() + "&l" + m.name() + "&a&l!"));
			}
			d.mine = i;
			p.closeInventory();
			plugin.travel(p, d);
			return;
		}
	}
}
