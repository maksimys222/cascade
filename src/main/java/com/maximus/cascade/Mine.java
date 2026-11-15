package com.maximus.cascade;

import java.util.List;

import org.bukkit.Material;
import org.bukkit.Particle;

/**
 * Шахта — своя стена со своими рудами, ценой, обстановкой вокруг и особым правилом.
 * В каждой 5 руд: чем правее в списке, тем дороже.
 */
public record Mine(
	String name,
	String color,
	Material icon,
	double price,
	double valueMult,
	Material[] ores,
	String[] oreNames,
	String[] oreColors,
	Theme theme,
	Rule rule,
	String ruleText) {

	/** Цена руды по её месту в списке шахты. */
	public static final int[] ORE_VALUE = {1, 2, 4, 7, 12};

	public enum Rule {
		/** Обычная стена. */
		NONE,
		/** Падают сундуки: опусти сундук в нижний ряд — он откроется. */
		CHESTS,
		/** Часть руды во льду: лёд не собирается в ряды, удар или ряд рядом растапливает его. */
		ICE,
		/** Обсидиан: киркой не пробить и в ряды не собирается — только взрывом. */
		OBSIDIAN,
		/** Гравитация наоборот: всё падает вверх. */
		UPSIDE_DOWN
	}

	/** Обстановка участка. */
	public record Theme(Material floor, Material edge, Material frame, Material backing, Material light,
			long time, Particle ambient) {
	}

	public static final List<Mine> ALL = List.of(
		new Mine("Каменоломня", "&7", Material.STONE_PICKAXE, 0, 1,
			new Material[] {Material.COAL_ORE, Material.COPPER_ORE, Material.IRON_ORE, Material.GOLD_ORE, Material.LAPIS_ORE},
			new String[] {"Уголь", "Медь", "Железо", "Золото", "Лазурит"},
			new String[] {"&8", "&6", "&f", "&e", "&9"},
			new Theme(Material.SMOOTH_STONE, Material.POLISHED_BLACKSTONE, Material.POLISHED_DEEPSLATE, Material.DEEPSLATE_TILES,
				Material.LANTERN, 6000, null),
			Rule.NONE, "Обычная стена — учись раскручивать каскады"),
		new Mine("Глубинная шахта", "&8", Material.CHEST, 25_000, 8,
			new Material[] {Material.DEEPSLATE_IRON_ORE, Material.DEEPSLATE_GOLD_ORE, Material.DEEPSLATE_REDSTONE_ORE,
				Material.DEEPSLATE_LAPIS_ORE, Material.DEEPSLATE_DIAMOND_ORE},
			new String[] {"Железо", "Золото", "Редстоун", "Лазурит", "Алмаз"},
			new String[] {"&f", "&e", "&c", "&9", "&b"},
			new Theme(Material.COBBLED_DEEPSLATE, Material.DEEPSLATE_BRICKS, Material.CHISELED_DEEPSLATE, Material.SCULK,
				Material.SOUL_LANTERN, 18000, Particle.ASH),
			Rule.CHESTS, "Падают &6сундуки&7: опусти сундук в нижний ряд — он откроется"),
		new Mine("Ледяная пещера", "&b", Material.BLUE_ICE, 1_500_000, 70,
			new Material[] {Material.GOLD_ORE, Material.LAPIS_ORE, Material.REDSTONE_ORE, Material.EMERALD_ORE, Material.DIAMOND_ORE},
			new String[] {"Золото", "Лазурит", "Редстоун", "Изумруд", "Алмаз"},
			new String[] {"&e", "&9", "&c", "&a", "&b"},
			new Theme(Material.SNOW_BLOCK, Material.PACKED_ICE, Material.BLUE_ICE, Material.PACKED_ICE,
				Material.SEA_LANTERN, 13000, Particle.SNOWFLAKE),
			Rule.ICE, "Часть руды &bво льду&7: удар или ряд рядом растапливает лёд"),
		new Mine("Недра", "&c", Material.NETHERRACK, 90_000_000, 650,
			new Material[] {Material.NETHER_QUARTZ_ORE, Material.NETHER_GOLD_ORE, Material.GILDED_BLACKSTONE,
				Material.ANCIENT_DEBRIS, Material.GLOWSTONE},
			new String[] {"Кварц", "Адское золото", "Позолота", "Древние обломки", "Светокамень"},
			new String[] {"&f", "&6", "&e", "&4", "&e"},
			new Theme(Material.NETHERRACK, Material.NETHER_BRICKS, Material.RED_NETHER_BRICKS, Material.BLACKSTONE,
				Material.SHROOMLIGHT, 18000, Particle.LAVA),
			Rule.OBSIDIAN, "Попадается &5обсидиан&7: киркой не пробить — только взрывом"),
		new Mine("Звёздный разлом", "&d", Material.END_STONE, 8_000_000_000.0, 7000,
			new Material[] {Material.END_STONE, Material.PURPUR_BLOCK, Material.PEARLESCENT_FROGLIGHT,
				Material.CRYING_OBSIDIAN, Material.OCHRE_FROGLIGHT},
			new String[] {"Эндерняк", "Пурпур", "Жемчуг", "Плачущий обсидиан", "Звёздная охра"},
			new String[] {"&e", "&d", "&f", "&5", "&6"},
			new Theme(Material.END_STONE_BRICKS, Material.PURPUR_PILLAR, Material.OBSIDIAN, Material.BLACK_CONCRETE,
				Material.END_ROD, 18000, Particle.PORTAL),
			Rule.UPSIDE_DOWN, "&dГравитация наоборот&7: всё падает вверх"));

	public static Mine of(PlayerData d) {
		return ALL.get(Math.clamp(d.mine, 0, ALL.size() - 1));
	}

	public double oreValue(int ore) {
		return ORE_VALUE[ore] * valueMult;
	}
}
