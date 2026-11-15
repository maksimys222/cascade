package com.maximus.cascade;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Древо навыков: 6 веток по 38 узлов. Всё описано в ресурсе tree.txt (его же читает генератор графики).
 * Раскладка ветки — сетка 9×6, ряд 5 — корень у ядра, ряд 0 — внешний край. Родитель узла — ближайший узел
 * в ряду ниже. Узел можно изучить, если изучен родитель (корень — сразу). Очко навыка — за каждый уровень.
 */
public final class Skills {
	public enum Shape { SMALL, BIG, KEY }

	/** Суммарные бонусы игрока; поля задаются по имени из tree.txt. */
	public static class Stats {
		public double valuePct;
		public double doubleChance;
		public boolean heavyHand;
		public boolean topOreDouble;
		public double multPct;
		public boolean resonance;
		public double lateBoostPct;
		public boolean storm;
		public double sparkChance;
		public double boomPct;
		public double dynChance;
		public double rainbowChance;
		public boolean crossDynamite;
		public boolean bigBomb;
		public double sellPct;
		public double bagPct;
		public double discount;
		public boolean autoSell;
		public boolean wholesale;
		public double luckyChance;
		public double chestChance;
		public double goldRush;
		public boolean jackpot;
		public boolean fortune;
		public double speedPct;
		public double xpPct;
		public boolean autoPick;
		public boolean fastFall;
	}

	public record Effect(String name, String desc, String icon, String field, String value) {
		void apply(Stats s) {
			try {
				Field f = Stats.class.getField(field);
				if (f.getType() == boolean.class) {
					f.setBoolean(s, true);
				} else {
					f.setDouble(s, f.getDouble(s) + Double.parseDouble(value));
				}
			} catch (ReflectiveOperationException e) {
				throw new IllegalStateException("Нет поля Stats." + field, e);
			}
		}
	}

	public record Node(String id, int index, int branch, int x, int y, Shape shape, Effect effect, String parent) {
		public boolean root() {
			return parent == null;
		}
	}

	public record Branch(String name, String color, int rgb, String icon, String about, List<Node> nodes) {
		public Node at(int x, int y) {
			for (Node n : nodes) {
				if (n.x() == x && n.y() == y) {
					return n;
				}
			}
			return null;
		}
	}

	public static final int COLS = 9;
	public static final int ROWS = 6;
	public static final long RESET_COST_PER_NODE = 500;
	public static final List<Branch> BRANCHES = new ArrayList<>();
	public static final Map<String, Node> NODES = new LinkedHashMap<>();

	static {
		try (InputStream in = Skills.class.getResourceAsStream("/tree.txt")) {
			load(new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)).lines().toList());
		} catch (IOException e) {
			throw new IllegalStateException("Не удалось прочитать tree.txt", e);
		}
	}

	private static void load(List<String> lines) {
		String[] head = null;
		List<Effect> small = new ArrayList<>();
		List<Effect> big = new ArrayList<>();
		List<Effect> key = new ArrayList<>();
		List<String> rows = new ArrayList<>();
		for (String raw : lines) {
			String line = raw.strip();
			if (line.isEmpty() || line.startsWith("#") || line.startsWith("icon ")) {
				continue;
			}
			String[] parts = line.split(" \\| ", 2);
			String[] t = parts[0].split(" ", 2);
			switch (t[0]) {
				case "branch" -> {
					head = new String[] {t[1], parts[1]};
					small.clear();
					big.clear();
					key.clear();
					rows.clear();
				}
				case "small", "big", "key" -> {
					String[] f = t[1].split(" ", 4);
					Effect e = new Effect(f[3], parts[1], f[2], f[0], f[1]);
					(t[0].equals("small") ? small : t[0].equals("big") ? big : key).add(e);
				}
				case "row" -> {
					rows.add(t[1]);
					if (rows.size() == ROWS) {
						addBranch(head, small, big, key, rows);
					}
				}
				default -> throw new IllegalStateException("tree.txt: непонятная строка " + line);
			}
		}
	}

	private static void addBranch(String[] head, List<Effect> small, List<Effect> big, List<Effect> key, List<String> rows) {
		String[] h = head[0].split(" ", 4);
		int branch = BRANCHES.size();
		List<Node> nodes = new ArrayList<>();
		int[] counters = new int[3];
		for (int y = ROWS - 1; y >= 0; y--) {
			for (int x = 0; x < COLS; x++) {
				char c = rows.get(y).charAt(x);
				Shape shape = switch (c) {
					case 'r', 'o' -> Shape.SMALL;
					case 'O' -> Shape.BIG;
					case 'K' -> Shape.KEY;
					default -> null;
				};
				if (shape == null) {
					continue;
				}
				List<Effect> pool = shape == Shape.SMALL ? small : shape == Shape.BIG ? big : key;
				Effect eff = pool.get(counters[shape.ordinal()]++ % pool.size());
				String parent = c == 'r' ? null : parentOf(rows, branch, x, y);
				Node n = new Node("b" + branch + "r" + y + "c" + x, NODES.size(), branch, x, y, shape, eff, parent);
				nodes.add(n);
				NODES.put(n.id(), n);
			}
		}
		BRANCHES.add(new Branch(h[3], h[0], Integer.parseInt(h[1], 16), h[2], head[1], nodes));
	}

	/** Ближайший узел в ряду ниже; при равенстве — тот, что ближе к середине. */
	private static String parentOf(List<String> rows, int branch, int x, int y) {
		String below = rows.get(y + 1);
		int best = -1;
		for (int px = 0; px < COLS; px++) {
			if (below.charAt(px) == '.') {
				continue;
			}
			if (best < 0 || Math.abs(px - x) < Math.abs(best - x)
				|| (Math.abs(px - x) == Math.abs(best - x) && Math.abs(px - 4) < Math.abs(best - 4))) {
				best = px;
			}
		}
		return "b" + branch + "r" + (y + 1) + "c" + best;
	}

	// ---------------- Состояние игрока ----------------

	public static boolean taken(PlayerData d, Node n) {
		return d.nodes.contains(n.id());
	}

	public static boolean available(PlayerData d, Node n) {
		return !taken(d, n) && (n.root() || d.nodes.contains(n.parent()));
	}

	public static int points(PlayerData d) {
		return d.level - 1;
	}

	public static int freePoints(PlayerData d) {
		return points(d) - d.nodes.size();
	}

	public static int takenIn(PlayerData d, Branch b) {
		int n = 0;
		for (Node node : b.nodes()) {
			if (taken(d, node)) {
				n++;
			}
		}
		return n;
	}

	public static Stats stats(PlayerData d) {
		if (d.statsCache == null) {
			Stats s = new Stats();
			for (String id : d.nodes) {
				Node n = NODES.get(id);
				if (n != null) {
					n.effect().apply(s);
				}
			}
			d.statsCache = s;
		}
		return d.statsCache;
	}

	private Skills() {
	}
}
