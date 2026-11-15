package com.maximus.cascade;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.configuration.file.YamlConfiguration;

/** Загрузка/сохранение игроков в YAML и рекорды каскадов. */
public class Store {
	public record TopEntry(String name, int chain) {
	}

	private final Cascade plugin;
	private final File dir;
	private final File metaFile;
	private final Map<UUID, PlayerData> online = new HashMap<>();
	private final Map<UUID, TopEntry> top = new HashMap<>();
	private int nextPlot;

	public Store(Cascade plugin) {
		this.plugin = plugin;
		this.dir = new File(plugin.getDataFolder(), "players");
		this.metaFile = new File(plugin.getDataFolder(), "meta.yml");
		dir.mkdirs();
		nextPlot = YamlConfiguration.loadConfiguration(metaFile).getInt("next-plot", 0);
		File[] files = dir.listFiles((d, n) -> n.endsWith(".yml"));
		if (files != null) {
			for (File f : files) {
				YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
				try {
					top.put(UUID.fromString(f.getName().replace(".yml", "")), new TopEntry(y.getString("name", "?"), y.getInt("best-chain")));
				} catch (IllegalArgumentException ignored) {
					// чужой файл в папке — пропускаем
				}
			}
		}
	}

	public PlayerData get(UUID id) {
		return online.get(id);
	}

	public PlayerData load(UUID id, String name) {
		PlayerData d = new PlayerData(id);
		File f = file(id);
		if (f.exists()) {
			YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
			d.plot = y.getInt("plot", -1);
			d.coins = y.getDouble("coins");
			d.earnedTotal = y.getDouble("earned-total");
			d.bag = y.getLong("bag");
			d.bagValue = y.getDouble("bag-value");
			d.bagLevel = y.getInt("bag-level");
			d.valueLevel = y.getInt("value-level");
			d.speedLevel = y.getInt("speed-level");
			d.bestChain = y.getInt("best-chain");
			d.hits = y.getLong("hits");
			d.mine = y.getInt("mine");
			d.unlocked = y.getInt("unlocked", 1);
			d.level = Math.max(1, y.getInt("level", 1));
			d.xp = y.getDouble("xp");
			d.nodes.addAll(y.getStringList("nodes"));
			d.nodes.removeIf(n -> !Skills.NODES.containsKey(n));
			if (!d.hasMine(d.mine)) {
				d.mine = 0;
			}
		}
		d.name = name;
		if (d.plot < 0) {
			d.plot = nextPlot++;
			saveMeta();
		}
		online.put(id, d);
		return d;
	}

	public void save(PlayerData d) {
		YamlConfiguration y = new YamlConfiguration();
		y.set("name", d.name);
		y.set("plot", d.plot);
		y.set("coins", d.coins);
		y.set("earned-total", d.earnedTotal);
		y.set("bag", d.bag);
		y.set("bag-value", d.bagValue);
		y.set("bag-level", d.bagLevel);
		y.set("value-level", d.valueLevel);
		y.set("speed-level", d.speedLevel);
		y.set("best-chain", d.bestChain);
		y.set("hits", d.hits);
		y.set("mine", d.mine);
		y.set("unlocked", d.unlocked);
		y.set("level", d.level);
		y.set("xp", d.xp);
		y.set("nodes", new ArrayList<>(d.nodes));
		try {
			y.save(file(d.id));
		} catch (IOException e) {
			plugin.getLogger().severe("Не удалось сохранить " + d.name + ": " + e.getMessage());
		}
		top.put(d.id, new TopEntry(d.name, d.bestChain));
	}

	public void unload(UUID id) {
		PlayerData d = online.remove(id);
		if (d != null) {
			save(d);
		}
	}

	public void saveAll() {
		online.values().forEach(this::save);
	}

	public List<TopEntry> top(int n) {
		for (PlayerData d : online.values()) {
			top.put(d.id, new TopEntry(d.name, d.bestChain));
		}
		List<TopEntry> list = new ArrayList<>(top.values());
		list.sort(Comparator.comparingInt(TopEntry::chain).reversed());
		return list.subList(0, Math.min(n, list.size()));
	}

	private File file(UUID id) {
		return new File(dir, id + ".yml");
	}

	private void saveMeta() {
		YamlConfiguration y = new YamlConfiguration();
		y.set("next-plot", nextPlot);
		try {
			y.save(metaFile);
		} catch (IOException e) {
			plugin.getLogger().severe("Не удалось сохранить meta.yml: " + e.getMessage());
		}
	}
}
