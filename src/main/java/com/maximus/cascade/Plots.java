package com.maximus.cascade;

import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.Block;
import org.bukkit.generator.ChunkGenerator;

/**
 * Пустой мир «cascade», у каждого игрока свой участок: платформа и вертикальная стена руды
 * 7×9 перед ней. Участки идут вдоль оси X через 64 блока. Игрок смотрит на юг (+Z).
 */
public class Plots {
	public static final String WORLD = "cascade";
	public static final int Y = 100;
	/** Плоскость стены. */
	public static final int WZ = 5;
	private static final int SPACING = 64;
	private static final int HALF = 6;

	private static class VoidGenerator extends ChunkGenerator {
		@Override
		public boolean shouldGenerateNoise() {
			return false;
		}

		@Override
		public boolean shouldGenerateSurface() {
			return false;
		}

		@Override
		public boolean shouldGenerateCaves() {
			return false;
		}

		@Override
		public boolean shouldGenerateDecorations() {
			return false;
		}

		@Override
		public boolean shouldGenerateMobs() {
			return false;
		}

		@Override
		public boolean shouldGenerateStructures() {
			return false;
		}
	}

	private final World world;

	public Plots() {
		World w = Bukkit.getWorld(WORLD);
		if (w == null) {
			w = new WorldCreator(WORLD).generator(new VoidGenerator()).createWorld();
		}
		world = w;
		world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
		world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
		world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
		world.setTime(6000);
	}

	public World world() {
		return world;
	}

	public int cx(PlayerData d) {
		return d.plot * SPACING;
	}

	/** Колонка 0 — левая с точки зрения игрока (смотрит на +Z, значит слева +X). */
	public int wallX(PlayerData d, int col) {
		return cx(d) + 3 - col;
	}

	public static int wallY(int row) {
		return Y + 1 + row;
	}

	public Location spawn(PlayerData d) {
		return new Location(world, cx(d) + 0.5, Y, -3.5, 0f, -24f);
	}

	public Location hologram(PlayerData d) {
		return new Location(world, cx(d) + 0.5, wallY(Wall.H) + 0.6, WZ + 0.5);
	}

	/** Платформа, рамка и подложка стены. Сами руды ставит Wall. */
	public void build(PlayerData d) {
		Mine.Theme t = Mine.of(d).theme();
		int cx = cx(d);
		world.getChunkAt(cx >> 4, 0).load(true);
		for (int x = -HALF; x <= HALF; x++) {
			for (int z = -7; z <= WZ; z++) {
				boolean edge = Math.abs(x) == HALF || z == -7;
				boolean stripe = !edge && (z == -1 || z == 2);
				world.getBlockAt(cx + x, Y - 1, z).setType(edge || stripe ? t.edge() : t.floor(), false);
				if (z < WZ) {
					for (int y = Y; y < Y + 3; y++) {
						world.getBlockAt(cx + x, y, z).setType(edge ? Material.BARRIER : Material.AIR, false);
					}
				}
			}
		}
		for (int x = -4; x <= 4; x++) {
			for (int y = Y; y <= wallY(Wall.H); y++) {
				world.getBlockAt(cx + x, y, WZ + 1).setType(t.backing(), false);
				boolean frame = Math.abs(x) == 4 || y == Y;
				if (frame && y < wallY(Wall.H)) {
					world.getBlockAt(cx + x, y, WZ).setType(t.frame(), false);
				}
			}
		}
		world.getBlockAt(cx - 4, wallY(Wall.H), WZ).setType(t.light(), false);
		world.getBlockAt(cx + 4, wallY(Wall.H), WZ).setType(t.light(), false);
	}

	/** Центр стены — для частиц обстановки. */
	public Location wallCenter(PlayerData d) {
		return new Location(world, cx(d) + 0.5, wallY(Wall.H / 2), WZ - 1.5);
	}

	/** Колонка и ряд блока стены этого игрока или null. */
	public int[] cell(PlayerData d, Block b) {
		if (!b.getWorld().equals(world) || b.getZ() != WZ) {
			return null;
		}
		int col = wallX(d, 0) - b.getX();
		int row = b.getY() - wallY(0);
		if (col < 0 || col >= Wall.W || row < 0 || row >= Wall.H) {
			return null;
		}
		return new int[] {col, row};
	}

	public boolean fellOff(Location loc) {
		return loc.getWorld().equals(world) && loc.getY() < Y - 20;
	}
}
