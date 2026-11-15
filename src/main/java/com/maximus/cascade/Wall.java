package com.maximus.cascade;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;

/**
 * Стена руды одного игрока. Удар выбивает блок, всё сверху падает (анимация через BlockDisplay),
 * ряды 3+ одинаковых лопаются, и так по кругу — каскад с растущим множителем.
 * Ряды хранятся «логически»: ряд 0 — тот, куда всё падает (в Звёздном разломе это верх стены).
 */
public class Wall {
	public static final int W = 7;
	public static final int H = 9;

	enum Special {
		DYN_H, DYN_V, BOMB, RAINBOW
	}

	enum Kind {
		ORE, CHEST, ROCK
	}

	static final class Cell {
		final Kind kind;
		final int ore;
		Special sp;
		boolean frozen;

		Cell(Kind kind, int ore) {
			this.kind = kind;
			this.ore = ore;
		}

		/** Можно ли собрать в ряд вместе с other. */
		boolean matches(Cell other) {
			return other != null && kind == Kind.ORE && other.kind == Kind.ORE && !frozen && !other.frozen && ore == other.ore;
		}

		boolean matchable() {
			return kind == Kind.ORE && !frozen;
		}
	}

	private record Run(List<int[]> cells, boolean horizontal) {
	}

	private final Cascade plugin;
	private final Player player;
	private final PlayerData d;
	private final World world;
	private final Mine mine;
	private final boolean up;
	/** g[колонка][логический ряд]. */
	private final Cell[][] g = new Cell[W][H];
	private final Random rnd = new Random();
	private final List<BlockDisplay> falling = new ArrayList<>();
	private TextDisplay holo;
	private boolean busy;
	private boolean dead;
	private long lastHit;
	private int step;
	private double runValue;
	private long runOres;
	/** Множитель всего удара (удача, джекпот, буря). */
	private double runMult;
	private boolean stormArmed;
	private int holoVersion;

	public Wall(Cascade plugin, Player player, PlayerData d) {
		this.plugin = plugin;
		this.player = player;
		this.d = d;
		this.world = plugin.plots().world();
		this.mine = Mine.of(d);
		this.up = mine.rule() == Mine.Rule.UPSIDE_DOWN;
	}

	public boolean busy() {
		return busy;
	}

	// ---------- создание / удаление ----------

	public void reset() {
		for (int c = 0; c < W; c++) {
			for (int r = 0; r < H; r++) {
				Cell cell;
				int tries = 0;
				do {
					cell = newCell(false);
				} while (tries++ < 20 && ((c > 1 && cell.matches(g[c - 1][r]) && cell.matches(g[c - 2][r]))
					|| (r > 1 && cell.matches(g[c][r - 1]) && cell.matches(g[c][r - 2]))));
				g[c][r] = cell;
				render(c, r);
			}
		}
		if (holo != null) {
			holo.remove();
		}
		holo = world.spawn(plugin.plots().hologram(d), TextDisplay.class, t -> {
			t.setPersistent(false);
			t.setBillboard(Display.Billboard.CENTER);
			t.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(1.6f, 1.6f, 1.6f), new AxisAngle4f()));
		});
		idleHolo();
	}

	public void destroy() {
		dead = true;
		falling.forEach(BlockDisplay::remove);
		falling.clear();
		if (holo != null) {
			holo.remove();
		}
	}

	private Cell newCell(boolean extras) {
		Skills.Stats s = Skills.stats(d);
		if (extras && (mine.rule() == Mine.Rule.CHESTS && rnd.nextDouble() < 0.025 || rnd.nextDouble() < s.chestChance)) {
			return new Cell(Kind.CHEST, -1);
		}
		if (mine.rule() == Mine.Rule.OBSIDIAN && rnd.nextDouble() < 0.05) {
			return new Cell(Kind.ROCK, -1);
		}
		int ore = extras && rnd.nextDouble() < s.goldRush ? 4 : rnd.nextInt(5);
		Cell cell = new Cell(Kind.ORE, ore);
		if (mine.rule() == Mine.Rule.ICE && rnd.nextDouble() < 0.12) {
			cell.frozen = true;
		}
		if (extras && rnd.nextDouble() < s.rainbowChance) {
			cell.sp = Special.RAINBOW;
		} else if (extras && rnd.nextDouble() < s.dynChance) {
			cell.sp = rnd.nextBoolean() ? Special.DYN_H : Special.DYN_V;
		}
		return cell;
	}

	private Material material(Cell cell) {
		if (cell.kind == Kind.CHEST) {
			return Material.BARREL;
		}
		if (cell.kind == Kind.ROCK) {
			return Material.OBSIDIAN;
		}
		if (cell.frozen) {
			return Material.ICE;
		}
		if (cell.sp == null) {
			return mine.ores()[cell.ore];
		}
		return switch (cell.sp) {
			case DYN_H, DYN_V -> Material.TNT;
			case BOMB -> Material.MAGMA_BLOCK;
			case RAINBOW -> Material.AMETHYST_BLOCK;
		};
	}

	/** Логический ряд ↔ физический (в разломе стена перевёрнута). */
	private int phys(int r) {
		return up ? H - 1 - r : r;
	}

	private Block block(int c, int r) {
		return world.getBlockAt(plugin.plots().wallX(d, c), Plots.wallY(phys(r)), Plots.WZ);
	}

	private void render(int c, int r) {
		block(c, r).setType(g[c][r] == null ? Material.AIR : material(g[c][r]), false);
	}

	private Location center(int c, int r) {
		return block(c, r).getLocation().add(0.5, 0.5, 0.2);
	}

	private int fallTicks() {
		return Skills.stats(d).fastFall ? 2 : 4;
	}

	// ---------- удар ----------

	/** Удар игрока по физическому блоку стены. */
	public void hit(int c, int physRow) {
		hitCell(c, phys(physRow), false);
	}

	/** Автокирка: бьёт случайную обычную руду. */
	public void autoHit() {
		if (busy || dead) {
			return;
		}
		List<int[]> options = new ArrayList<>();
		for (int c = 0; c < W; c++) {
			for (int r = 0; r < H; r++) {
				if (g[c][r] != null && g[c][r].matchable()) {
					options.add(new int[] {c, r});
				}
			}
		}
		if (!options.isEmpty()) {
			int[] p = options.get(rnd.nextInt(options.size()));
			hitCell(p[0], p[1], true);
		}
	}

	private void hitCell(int c, int r, boolean auto) {
		Cell cell = g[c][r];
		if (busy || dead || cell == null) {
			return;
		}
		long now = System.currentTimeMillis();
		if (!auto && now - lastHit < Balance.cooldown(d)) {
			return;
		}
		Skills.Stats s = Skills.stats(d);
		if (d.bag >= Balance.capacity(d)) {
			if (s.autoSell) {
				plugin.sell(player);
			} else {
				if (!auto) {
					player.sendActionBar(Text.c("&c&lРюкзак полон! &fПродай у Скупщика, слитком или /sell"));
					player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.7f, 1f);
				}
				return;
			}
		}
		if (cell.kind == Kind.ROCK) {
			if (!auto) {
				player.sendActionBar(Text.c("&5Обсидиан киркой не пробить — &fтолько взрывом"));
				player.playSound(center(c, r), Sound.BLOCK_ANVIL_LAND, 0.4f, 1.8f);
			}
			return;
		}
		if (cell.kind == Kind.CHEST) {
			if (!auto) {
				player.sendActionBar(Text.c("&6Бочку не разбить — &fопусти её в нижний ряд, и она откроется"));
			}
			return;
		}
		lastHit = now;
		busy = true;
		step = 0;
		runValue = 0;
		runOres = 0;
		runMult = 1;
		d.hits++;
		if (stormArmed) {
			stormArmed = false;
			runMult *= 3;
			player.sendActionBar(Text.c("&b&l⚡ Буря! &fУдар ×3"));
		}
		if (rnd.nextDouble() < s.luckyChance) {
			runMult *= s.fortune ? 3 : 2;
			player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 2f);
		}
		if (s.jackpot && rnd.nextDouble() < 0.005) {
			runMult *= 50;
			player.showTitle(Title.title(Text.c("&6&lДЖЕКПОТ!"), Text.c("&eЭтот удар ×50"),
				Title.Times.times(Duration.ZERO, Duration.ofMillis(1500), Duration.ofMillis(400))));
			player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.2f);
		}
		if (cell.frozen) {
			cell.frozen = false;
			render(c, r);
			player.playSound(center(c, r), Sound.BLOCK_GLASS_BREAK, 1f, 1.3f);
			world.spawnParticle(Particle.BLOCK, center(c, r), 12, 0.3, 0.3, 0.3, 0, Material.ICE.createBlockData());
			later(2, this::check);
			return;
		}
		Set<Integer> set = new LinkedHashSet<>();
		set.add(key(c, r));
		boolean blast = (s.heavyHand && d.hits % 10 == 0) || rnd.nextDouble() < s.sparkChance;
		if (blast) {
			addArea(set, c, r);
		} else if (rnd.nextDouble() < s.doubleChance && r + 1 < H && g[c][r + 1] != null && g[c][r + 1].matchable()) {
			set.add(key(c, r + 1));
		}
		expand(set);
		player.playSound(center(c, r), Sound.BLOCK_STONE_BREAK, 1f, 1f);
		clear(set, 1, blast);
		later(2, this::fall);
	}

	private static int key(int c, int r) {
		return c * 100 + r;
	}

	private void addArea(Set<Integer> set, int c, int r) {
		for (int x = c - 1; x <= c + 1; x++) {
			for (int y = r - 1; y <= r + 1; y++) {
				if (x >= 0 && x < W && y >= 0 && y < H && g[x][y] != null) {
					set.add(key(x, y));
				}
			}
		}
	}

	/** Добавляет к набору всё, что взрывают попавшие в него особые блоки (цепочкой). */
	private void expand(Set<Integer> set) {
		boolean cross = Skills.stats(d).crossDynamite;
		ArrayDeque<Integer> queue = new ArrayDeque<>(set);
		while (!queue.isEmpty()) {
			int k = queue.poll();
			int c = k / 100;
			int r = k % 100;
			Cell cell = g[c][r];
			if (cell == null || cell.sp == null) {
				continue;
			}
			Set<Integer> hits = new LinkedHashSet<>();
			switch (cell.sp) {
				case DYN_H, DYN_V -> {
					if (cell.sp == Special.DYN_H || cross) {
						for (int x = 0; x < W; x++) {
							hits.add(key(x, r));
						}
					}
					if (cell.sp == Special.DYN_V || cross) {
						for (int y = 0; y < H; y++) {
							hits.add(key(c, y));
						}
					}
				}
				case BOMB -> {
					int reach = Skills.stats(d).bigBomb ? 2 : 1;
					for (int x = c - reach; x <= c + reach; x++) {
						for (int y = r - reach; y <= r + reach; y++) {
							if (x >= 0 && x < W && y >= 0 && y < H && g[x][y] != null) {
								hits.add(key(x, y));
							}
						}
					}
				}
				case RAINBOW -> {
					for (int x = 0; x < W; x++) {
						for (int y = 0; y < H; y++) {
							if (g[x][y] != null && g[x][y].kind == Kind.ORE && g[x][y].ore == cell.ore) {
								hits.add(key(x, y));
							}
						}
					}
				}
			}
			for (int h : hits) {
				if (g[h / 100][h % 100] != null && set.add(h)) {
					queue.add(h);
				}
			}
		}
	}

	/** Убирает блоки, кладёт руду в рюкзак с множителем шага. */
	private void clear(Set<Integer> set, double mult, boolean boom) {
		double sum = 0;
		long count = 0;
		for (int k : set) {
			int c = k / 100;
			int r = k % 100;
			Cell cell = g[c][r];
			if (cell == null) {
				continue;
			}
			world.spawnParticle(Particle.BLOCK, center(c, r), 10, 0.3, 0.3, 0.3, 0, material(cell).createBlockData());
			if (cell.kind == Kind.ORE) {
				sum += mine.oreValue(cell.ore) * (cell.ore == 4 && Skills.stats(d).topOreDouble ? 2 : 1);
				count++;
				boom |= cell.sp != null;
			} else if (cell.kind == Kind.CHEST) {
				openChest(c, r);
			}
			g[c][r] = null;
			render(c, r);
		}
		if (boom) {
			Location mid = center(W / 2, H / 2);
			world.spawnParticle(Particle.EXPLOSION, mid, 3, 1.5, 2, 0.2, 0);
			player.playSound(mid, Sound.ENTITY_GENERIC_EXPLODE, 0.6f, 1.2f);
		}
		double value = sum * mult * runMult * Balance.valueMult(d) * (boom ? 1 + Skills.stats(d).boomPct : 1);
		d.bag += count;
		d.bagValue += value;
		runValue += value;
		runOres += count;
	}

	private void openChest(int c, int r) {
		double reward = mine.oreValue(4) * 40 * Balance.valueMult(d) * (1 + rnd.nextInt(3));
		d.coins += reward;
		d.earnedTotal += reward;
		plugin.addXp(player, d, 25);
		world.spawnParticle(Particle.HAPPY_VILLAGER, center(c, r), 20, 0.4, 0.4, 0.4, 0);
		player.playSound(center(c, r), Sound.BLOCK_BARREL_OPEN, 1f, 1f);
		player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.6f);
		player.sendMessage(Text.c("&6&l▣ Бочка с сокровищами! &e+" + Num.fmt(reward) + " ⛃ &7и опыт"));
	}

	// ---------- падение ----------

	private void fall() {
		if (dead) {
			return;
		}
		Map<BlockDisplay, Integer> drops = new HashMap<>();
		for (int c = 0; c < W; c++) {
			Cell[] col = new Cell[H];
			int[] from = new int[H];
			int n = 0;
			for (int r = 0; r < H; r++) {
				if (g[c][r] != null) {
					col[n] = g[c][r];
					from[n] = r;
					n++;
				}
			}
			for (int i = n; i < H; i++) {
				col[i] = newCell(true);
				from[i] = H + (i - n); // новые прилетают из-за края стены
			}
			for (int r = 0; r < H; r++) {
				if (from[r] == r) {
					continue;
				}
				if (from[r] < H) {
					block(c, from[r]).setType(Material.AIR, false);
				}
				Location at = new Location(world, plugin.plots().wallX(d, c), Plots.wallY(phys(from[r])), Plots.WZ);
				Material m = material(col[r]);
				BlockDisplay bd = world.spawn(at, BlockDisplay.class, e -> {
					e.setPersistent(false);
					e.setBlock(m.createBlockData());
				});
				falling.add(bd);
				drops.put(bd, from[r] - r);
			}
			g[c] = col;
		}
		if (drops.isEmpty()) {
			later(1, this::check);
			return;
		}
		int ticks = fallTicks();
		// Сдвиг задаём на следующий тик — тогда клиент плавно интерполирует его.
		later(1, () -> drops.forEach((bd, dist) -> {
			bd.setInterpolationDelay(0);
			bd.setInterpolationDuration(ticks);
			bd.setTransformation(new Transformation(new Vector3f(0, up ? dist : -dist, 0), new AxisAngle4f(), new Vector3f(1, 1, 1), new AxisAngle4f()));
		}));
		later(2 + ticks, () -> {
			for (int c = 0; c < W; c++) {
				for (int r = 0; r < H; r++) {
					render(c, r);
				}
			}
			drops.keySet().forEach(BlockDisplay::remove);
			falling.removeAll(drops.keySet());
			check();
		});
	}

	// ---------- ряды и каскад ----------

	private List<Run> runs() {
		List<Run> out = new ArrayList<>();
		for (int r = 0; r < H; r++) {
			int c = 0;
			while (c < W) {
				int e = c;
				while (e + 1 < W && g[c][r].matches(g[e + 1][r])) {
					e++;
				}
				if (e - c >= 2) {
					List<int[]> cells = new ArrayList<>();
					for (int k = c; k <= e; k++) {
						cells.add(new int[] {k, r});
					}
					out.add(new Run(cells, true));
				}
				c = e + 1;
			}
		}
		for (int c = 0; c < W; c++) {
			int r = 0;
			while (r < H) {
				int e = r;
				while (e + 1 < H && g[c][r].matches(g[c][e + 1])) {
					e++;
				}
				if (e - r >= 2) {
					List<int[]> cells = new ArrayList<>();
					for (int k = r; k <= e; k++) {
						cells.add(new int[] {c, k});
					}
					out.add(new Run(cells, false));
				}
				r = e + 1;
			}
		}
		return out;
	}

	private void check() {
		// Бочки, дошедшие до нижнего ряда, открываются — и стена снова падает.
		boolean opened = false;
		for (int c = 0; c < W; c++) {
			if (g[c][0] != null && g[c][0].kind == Kind.CHEST) {
				openChest(c, 0);
				g[c][0] = null;
				render(c, 0);
				opened = true;
			}
		}
		if (opened) {
			later(3, this::fall);
			return;
		}
		List<Run> runs = runs();
		if (runs.isEmpty()) {
			finish();
			return;
		}
		step++;
		double mult = Balance.mult(d, step);
		float pitch = (float) Math.min(2.0, 0.6 * Math.pow(2, step * 2 / 12.0));
		player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, pitch);
		if (step >= 2) {
			player.showTitle(Title.title(Component.empty(), Text.c(Balance.stepName(step) + " &6×" + Num.fmt(mult)),
				Title.Times.times(Duration.ZERO, Duration.ofMillis(900), Duration.ofMillis(250))));
			setHolo(Balance.stepName(step) + " &6×" + Num.fmt(mult) + "\n&f+" + Num.fmt(runValue) + " ⛃");
		}
		if (step == 5 || step == 7) {
			player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.2f);
		}

		// Сколько рядов проходит через клетку (пересечение = буква Г/Т → бомба).
		Map<Integer, Integer> cross = new HashMap<>();
		Set<Integer> set = new LinkedHashSet<>();
		for (Run run : runs) {
			for (int[] p : run.cells()) {
				cross.merge(key(p[0], p[1]), 1, Integer::sum);
				set.add(key(p[0], p[1]));
			}
		}
		if (mine.rule() == Mine.Rule.ICE) {
			thawAround(set);
		}
		Map<Integer, Special> spawn = new LinkedHashMap<>();
		for (Run run : runs) {
			int len = run.cells().size();
			if (len >= 4) {
				int[] mid = run.cells().get(len / 2);
				spawn.put(key(mid[0], mid[1]), len >= 5 ? Special.RAINBOW : run.horizontal() ? Special.DYN_H : Special.DYN_V);
			}
		}
		cross.forEach((k, n) -> {
			if (n > 1) {
				spawn.putIfAbsent(k, Special.BOMB);
			}
		});
		for (var e : spawn.entrySet()) {
			Cell cell = g[e.getKey() / 100][e.getKey() % 100];
			if (cell != null && cell.sp == null) {
				set.remove(e.getKey());
				cell.sp = e.getValue();
				render(e.getKey() / 100, e.getKey() % 100);
				player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1.4f);
			}
		}
		expand(set);
		clear(set, mult, false);
		later(Skills.stats(d).fastFall ? 2 : 3, this::fall);
	}

	/** Ряд рядом со льдом растапливает его. */
	private void thawAround(Set<Integer> set) {
		for (int k : set) {
			int c = k / 100;
			int r = k % 100;
			int[][] near = {{c + 1, r}, {c - 1, r}, {c, r + 1}, {c, r - 1}};
			for (int[] p : near) {
				if (p[0] >= 0 && p[0] < W && p[1] >= 0 && p[1] < H && g[p[0]][p[1]] != null && g[p[0]][p[1]].frozen) {
					g[p[0]][p[1]].frozen = false;
					render(p[0], p[1]);
					world.spawnParticle(Particle.BLOCK, center(p[0], p[1]), 8, 0.3, 0.3, 0.3, 0, Material.ICE.createBlockData());
				}
			}
		}
	}

	private void finish() {
		busy = false;
		Skills.Stats s = Skills.stats(d);
		String line = "&7Удар: &e+" + Num.fmt(runValue) + " ⛃ &8(" + runOres + " руды)";
		if (step >= 2) {
			line = Balance.stepName(step) + " &7" + step + " шаг. — &e+" + Num.fmt(runValue) + " ⛃";
		}
		player.sendActionBar(Text.c(line));
		if (step > d.bestChain) {
			boolean first = d.bestChain == 0;
			d.bestChain = step;
			if (!first && step >= 3) {
				player.sendMessage(Text.c("&6&l★ Новый рекорд каскада: &e" + step + " шаг.!"));
				player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1f);
			}
		}
		if (step >= 10) {
			Bukkit.broadcast(Text.c("&d&l⚡ " + player.getName() + " &fраскрутил &d&lлегендарный каскад &f— " + step + " шагов!"));
		}
		if (s.storm && step >= 5) {
			stormArmed = true;
			player.sendMessage(Text.c("&b⚡ Буря заряжена: &fследующий удар ×3"));
		}
		plugin.addXp(player, d, (runOres + step * step * 3) * (1 + s.xpPct));
		if (s.autoSell && d.bag >= Balance.capacity(d)) {
			plugin.sell(player);
		}
		if (step >= 2) {
			setHolo(Balance.stepName(step) + " &7" + step + " шаг.\n&e+" + Num.fmt(runValue) + " ⛃");
		}
		int v = ++holoVersion;
		later(50, () -> {
			if (v == holoVersion && !busy) {
				idleHolo();
			}
		});
	}

	private void idleHolo() {
		setHolo(mine.color() + "&l" + mine.name() + "\n&7Рекорд: &e" + d.bestChain + " шаг.");
	}

	private void setHolo(String text) {
		holoVersion++;
		if (holo != null && !dead) {
			holo.text(Text.c(text));
		}
	}

	private void later(long ticks, Runnable r) {
		Bukkit.getScheduler().runTaskLater(plugin, () -> {
			if (!dead) {
				r.run();
			}
		}, ticks);
	}
}
