package com.maximus.cascade;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.CustomModelData;
import net.kyori.adventure.key.Key;

/**
 * Древо навыков в инвентаре.
 * Главная — круглая карта всех 150 узлов (рисуется символами в заголовке, см. TreeTitle); клик по сектору
 * открывает ветку. Страница ветки — сетка 9×6 узлов-иконок в цветных кольцах, линии-связи в заголовке,
 * кнопки — на месте инвентаря. Пока древо открыто, хотбар игрока скрыт (Listeners возвращает его при закрытии).
 */
public class SkillMenu {
	public static final int OVERVIEW = -1;
	/** Центр карты (совпадает с GenArt) и слот ядра под ним. */
	private static final double CX = 88;
	private static final double CY = 111;
	private static final double[] RADIUS = {126, 108, 90, 72, 54, 36};
	private static final double STEP = 6;
	private static final int CORE = 49;
	/** Кнопки страницы ветки нарисованы на первом ряду инвентаря игрока (клетки 54–62). */
	private static final int HOME = 54;
	private static final int PREV = 55;
	private static final int TAB0 = 56;
	private static final int NEXT = 62;

	public static class Holder implements InventoryHolder {
		final int page;
		Inventory inv;
		/** Круглая карта: клетка → доступный узел, который она изучает. */
		final Map<Integer, Skills.Node> learnAt = new HashMap<>();

		Holder(int page) {
			this.page = page;
		}

		@Override
		public Inventory getInventory() {
			return inv;
		}
	}

	private final Cascade plugin;

	public SkillMenu(Cascade plugin) {
		this.plugin = plugin;
	}

	public void open(Player p) {
		open(p, OVERVIEW);
	}

	public void open(Player p, int page) {
		PlayerData d = plugin.store().get(p.getUniqueId());
		if (d == null) {
			return;
		}
		int n = Skills.BRANCHES.size();
		if (page != OVERVIEW) {
			page = ((page % n) + n) % n;
		}
		Holder h = new Holder(page);
		h.inv = Bukkit.createInventory(h, 54, page == OVERVIEW ? TreeTitle.overview(d) : TreeTitle.branch(d, page));
		if (page == OVERVIEW) {
			fillOverview(h, d);
		} else {
			fillBranch(h.inv, d, page);
		}
		p.getInventory().clear();
		p.openInventory(h.inv);
	}

	// ---------------- круглая карта ----------------

	/** Центр клетки в координатах окна: сундук, затем инвентарь игрока и хотбар. */
	private static double[] slotCenter(int raw) {
		if (raw < 54) {
			return new double[] {16 + 18 * (raw % 9), 26 + 18 * (raw / 9)};
		}
		if (raw < 81) {
			int i = raw - 54;
			return new double[] {16 + 18 * (i % 9), 148 + 18 * (i / 9)};
		}
		return new double[] {16 + 18 * (raw - 81), 206};
	}

	/** Ветка под клеткой: −2 — ядро, −1 — мимо карты. */
	private static int sectorAt(int raw) {
		double[] c = slotCenter(raw);
		double dx = c[0] - CX;
		double dy = c[1] - CY;
		double dist = Math.hypot(dx, dy);
		if (dist < 16) {
			return -2;
		}
		if (dist > 136) {
			return -1;
		}
		double a = Math.toDegrees(Math.atan2(dy, dx));
		return Math.floorMod((int) Math.round((a + 90) / 60), 6);
	}

	/** Положение узла на круглой карте — те же формулы, что в tools/GenArt.java. */
	private static double[] mapPos(Skills.Node n) {
		double r = RADIUS[n.y()];
		double a = Math.toRadians(-90 + 60 * n.branch() + (n.x() - 4) * STEP);
		return new double[] {CX + r * Math.cos(a), CY + r * Math.sin(a)};
	}

	/**
	 * Каждому доступному узлу — ближайшая свободная клетка окна (клиент сообщает только номер клетки,
	 * а узлы мельче клеток). Дальние узлы на краю круга получают ближайшую клетку внутри окна.
	 */
	private static void assign(Holder h, PlayerData d) {
		List<double[]> pairs = new ArrayList<>();
		for (Skills.Node n : Skills.NODES.values()) {
			if (!Skills.available(d, n)) {
				continue;
			}
			double[] p = mapPos(n);
			for (int raw = 0; raw < 90; raw++) {
				if (raw == CORE) {
					continue;
				}
				double[] c = slotCenter(raw);
				double dist = Math.hypot(c[0] - p[0], c[1] - p[1]);
				if (dist <= 42) {
					pairs.add(new double[] {dist, raw, n.index()});
				}
			}
		}
		pairs.sort(Comparator.comparingDouble(x -> x[0]));
		List<Skills.Node> all = new ArrayList<>(Skills.NODES.values());
		Set<Integer> done = new HashSet<>();
		for (double[] pr : pairs) {
			int raw = (int) pr[1];
			int idx = (int) pr[2];
			if (!done.contains(idx) && !h.learnAt.containsKey(raw)) {
				h.learnAt.put(raw, all.get(idx));
				done.add(idx);
			}
		}
	}

	private void fillOverview(Holder h, PlayerData d) {
		Inventory inv = h.inv;
		assign(h, d);
		int free = Skills.freePoints(d);
		for (var e : h.learnAt.entrySet()) {
			if (e.getKey() >= 54) {
				continue;
			}
			Skills.Node n = e.getValue();
			Skills.Branch b = Skills.BRANCHES.get(n.branch());
			inv.setItem(e.getKey(), model("blank", "&f" + n.effect().name() + " &8· " + b.color() + b.name(),
				"&f" + n.effect().desc(),
				kind(n),
				"",
				free > 0 ? "&eЛКМ — изучить &7(очков: " + free + ")" : "&cНет очков — повышай уровень",
				"&8ПКМ — открыть ветку"));
		}
		for (int slot = 0; slot < 54; slot++) {
			if (h.learnAt.containsKey(slot)) {
				continue;
			}
			int b = sectorAt(slot);
			if (b == -1) {
				continue;
			}
			if (b == -2) {
				continue;
			}
			Skills.Branch br = Skills.BRANCHES.get(b);
			ItemStack it = model("blank", br.color() + "&l" + br.name() + " &7" + Skills.takenIn(d, br) + "/" + br.nodes().size(),
				"&7" + br.about(), "", "&eЛКМ — открыть ветку");
			inv.setItem(slot, it);
		}
		inv.setItem(CORE, model("blank", "&6&lЯдро &7· уровень &e" + d.level,
			"&7Свободных очков: &e" + Skills.freePoints(d),
			"&7Опыт: &f" + Num.fmt(d.xp) + "&7/" + Num.fmt(Balance.xpNeed(d.level)),
			"&7Изучено: &f" + d.nodes.size() + "&7/" + Skills.NODES.size(),
			"",
			"&8Очко навыка — за каждый уровень.",
			"&8Светлые узлы — доступны: кликни, чтобы изучить.",
			"&8Клик по сектору — открыть ветку крупно.",
			"",
			"&cShift+ЛКМ — сбросить навыки &7(" + Num.fmt(Skills.RESET_COST_PER_NODE * d.nodes.size()) + " ⛃)"));
	}

	// ---------------- страница ветки ----------------

	private void fillBranch(Inventory inv, PlayerData d, int page) {
		Skills.Branch b = Skills.BRANCHES.get(page);
		int free = Skills.freePoints(d);
		for (Skills.Node n : b.nodes()) {
			boolean taken = Skills.taken(d, n);
			boolean avail = Skills.available(d, n);
			String kind = switch (n.shape()) {
				case SMALL -> "&7Мелкий навык";
				case BIG -> "&bКрупный навык";
				case KEY -> "&6&lКлючевой навык";
			};
			String action = taken ? "&a✔ Изучен"
				: !avail ? "&8Сначала изучи навык ниже по ветке"
				: free > 0 ? "&eЛКМ — изучить &7(очков: " + free + ")"
				: "&cНет очков — повышай уровень";
			ItemStack it = skillIcon(n.effect().icon(), TreeTitle.ringColor(d, n),
				(taken ? b.color() + "&l" : avail ? "&f" : "&7") + n.effect().name(),
				"&f" + n.effect().desc(),
				kind + (n.root() ? " &8· стартовый" : ""),
				"",
				action);
			it.setData(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, taken && n.shape() == Skills.Shape.KEY);
			inv.setItem(n.y() * 9 + n.x(), it);
		}
	}

	/** Иконка предмета в цветном кольце (модель cascade:skill_<предмет> из ресурспака). */
	private static ItemStack skillIcon(String icon, int rgb, String title, String... lore) {
		ItemStack it = model("skill_" + icon, title, lore);
		it.setData(DataComponentTypes.CUSTOM_MODEL_DATA, CustomModelData.customModelData().addColor(Color.fromRGB(rgb)).build());
		return it;
	}

	/** Предмет с моделью из ресурспака. */
	private static ItemStack model(String name, String title, String... lore) {
		ItemStack it = Menu.item(Material.PAPER, title, lore);
		ItemMeta meta = it.getItemMeta();
		meta.addItemFlags(ItemFlag.values());
		it.setItemMeta(meta);
		it.setData(DataComponentTypes.ITEM_MODEL, Key.key("cascade", name));
		return it;
	}

	// ---------------- клики ----------------

	/** raw — номер клетки в окне (0–53 сундук, 54–89 инвентарь игрока). */
	public void click(Player p, Holder h, int raw, ClickType type) {
		PlayerData d = plugin.store().get(p.getUniqueId());
		if (d == null || raw < 0 || raw > 89) {
			return;
		}
		if (h.page == OVERVIEW) {
			Skills.Node target = h.learnAt.get(raw);
			if (target != null && !type.isRightClick()) {
				if (learn(p, d, target)) {
					open(p, OVERVIEW);
				}
				return;
			}
			int b = target != null ? target.branch() : sectorAt(raw);
			if (b == -2 || raw == CORE) {
				if (type.isShiftClick()) {
					reset(p, d);
				}
			} else if (b >= 0) {
				p.playSound(p.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1f, 1f);
				open(p, b);
			}
			return;
		}
		switch (raw) {
			case HOME -> {
				open(p, OVERVIEW);
				return;
			}
			case PREV -> {
				open(p, h.page - 1);
				return;
			}
			case NEXT -> {
				open(p, h.page + 1);
				return;
			}
			default -> {
			}
		}
		if (raw >= TAB0 && raw < TAB0 + Skills.BRANCHES.size()) {
			open(p, raw - TAB0);
			return;
		}
		Skills.Node n = raw < 54 ? Skills.BRANCHES.get(h.page).at(raw % 9, raw / 9) : null;
		if (n != null && learn(p, d, n)) {
			// заголовок (линии) меняется — открываем страницу заново
			open(p, h.page);
		}
	}

	/** Изучить узел, если можно. */
	private boolean learn(Player p, PlayerData d, Skills.Node n) {
		if (Skills.taken(d, n)) {
			return false;
		}
		if (!Skills.available(d, n)) {
			p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.7f, 1f);
			return false;
		}
		if (Skills.freePoints(d) <= 0) {
			p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.7f, 1f);
			p.sendActionBar(Text.c("&cНет свободных очков — повышай уровень"));
			return false;
		}
		d.nodes.add(n.id());
		d.statsCache = null;
		p.playSound(p.getLocation(), n.shape() == Skills.Shape.KEY ? Sound.UI_TOAST_CHALLENGE_COMPLETE : Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1.2f);
		p.sendActionBar(Text.c("&aИзучено: &f" + n.effect().name() + " &7— " + n.effect().desc()));
		return true;
	}

	private static String kind(Skills.Node n) {
		return switch (n.shape()) {
			case SMALL -> "&7Мелкий навык";
			case BIG -> "&bКрупный навык";
			case KEY -> "&6&lКлючевой навык";
		};
	}

	private void reset(Player p, PlayerData d) {
		long cost = Skills.RESET_COST_PER_NODE * d.nodes.size();
		if (d.nodes.isEmpty()) {
			return;
		}
		if (d.coins < cost) {
			p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.7f, 1f);
			p.sendActionBar(Text.c("&cНе хватает " + Num.fmt(cost - d.coins) + " ⛃"));
			return;
		}
		d.coins -= cost;
		d.nodes.clear();
		d.statsCache = null;
		p.playSound(p.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 1f, 1f);
		open(p, OVERVIEW);
	}
}
