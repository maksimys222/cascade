package com.maximus.cascade;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextColor;

/**
 * Рисует древо в заголовке окна символами шрифта cascade:gui. Где стоит каждый символ и какой он ширины —
 * из ресурса tree_glyphs.txt (его пишет tools/GenArt.java). Цвет символа — состояние узла или линии.
 */
public final class TreeTitle {
	private static final Key FONT = Key.key("cascade", "gui");
	/** Заголовок начинается в 8 px от левого края окна. */
	private static final int START = 8;
	private static final int OFF = 0x4A4652;

	private record Glyph(char ch, int x, int adv) {
	}

	private static final Map<String, Glyph> GLYPHS = new HashMap<>();

	static {
		try (InputStream in = TreeTitle.class.getResourceAsStream("/tree_glyphs.txt")) {
			new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)).lines().forEach(line -> {
				String[] t = line.split(" ");
				GLYPHS.put(t[0] + " " + t[1], new Glyph((char) Integer.parseInt(t[2], 16), Integer.parseInt(t[3]), Integer.parseInt(t[4])));
			});
		} catch (IOException e) {
			throw new IllegalStateException("Не удалось прочитать tree_glyphs.txt", e);
		}
	}

	private final TextComponent.Builder out = Component.text();
	private int cursor = START;

	private TreeTitle() {
	}

	/** Сдвиг курсора к x пробелами −256…+256. */
	private void moveTo(int x) {
		int delta = x - cursor;
		StringBuilder sb = new StringBuilder();
		int left = Math.abs(delta);
		for (int i = 8; i >= 0; i--) {
			while (left >= 1 << i) {
				sb.append((char) ((delta < 0 ? 0xF801 : 0xF821) + i));
				left -= 1 << i;
			}
		}
		if (!sb.isEmpty()) {
			out.append(Component.text(sb.toString()).font(FONT));
		}
		cursor = x;
	}

	private void put(String key, int rgb) {
		Glyph g = GLYPHS.get(key);
		if (g == null) {
			return;
		}
		moveTo(g.x());
		out.append(Component.text(String.valueOf(g.ch()), TextColor.color(rgb)).font(FONT));
		cursor += g.adv();
	}

	private Component finish(String label) {
		moveTo(START);
		if (label != null) {
			out.append(Component.text(label, TextColor.color(0xFFFFFF)));
		}
		return out.build();
	}

	// ---------------- цвета ----------------

	static int mix(int a, int b, double t) {
		int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
		int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
		int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
		return (r << 16) | (g << 8) | bl;
	}

	/** Цвет кольца узла: изучен — цвет ветки, доступен — светлый, закрыт — тёмный. */
	static int ringColor(PlayerData d, Skills.Node n) {
		int rgb = Skills.BRANCHES.get(n.branch()).rgb();
		return Skills.taken(d, n) ? mix(rgb, 0xFFFFFF, 0.15) : Skills.available(d, n) ? mix(rgb, 0xFFFFFF, 0.6) : mix(rgb, 0x2A2638, 0.72);
	}

	private static int iconColor(PlayerData d, Skills.Node n) {
		return Skills.taken(d, n) ? 0xFFFFFF : Skills.available(d, n) ? 0xD8D8D8 : 0x606060;
	}

	/** Линия к узлу: оба конца изучены — светится, путь открыт — полутон, иначе тёмная. */
	private static int lineColor(PlayerData d, Skills.Node n) {
		int rgb = Skills.BRANCHES.get(n.branch()).rgb();
		boolean from = n.root() || d.nodes.contains(n.parent());
		if (from && Skills.taken(d, n)) {
			return mix(rgb, 0xFFFFFF, 0.3);
		}
		return from ? mix(rgb, OFF, 0.55) : OFF;
	}

	// ---------------- страницы ----------------

	/** Круглая карта всего древа. */
	public static Component overview(PlayerData d) {
		TreeTitle t = new TreeTitle();
		for (int q = 1; q <= 4; q++) {
			t.put("bg -" + q, 0xFFFFFF);
		}
		for (Skills.Node n : Skills.NODES.values()) {
			t.put("oline " + n.id(), lineColor(d, n));
		}
		for (Skills.Node n : Skills.NODES.values()) {
			t.put("ring " + n.id(), ringColor(d, n));
		}
		for (Skills.Node n : Skills.NODES.values()) {
			t.put("icon " + n.id(), iconColor(d, n));
		}
		return t.finish(null);
	}

	/** Страница ветки: знамя и линии-связи (узлы — предметы в клетках). */
	public static Component branch(PlayerData d, int branch) {
		TreeTitle t = new TreeTitle();
		t.put("bg " + branch, 0xFFFFFF);
		for (Skills.Node n : Skills.BRANCHES.get(branch).nodes()) {
			if (!n.root()) {
				t.put("pline " + n.id(), lineColor(d, n));
			}
		}
		Skills.Branch b = Skills.BRANCHES.get(branch);
		return t.finish(b.name() + " · " + Skills.takenIn(d, b) + "/" + b.nodes().size());
	}
}
