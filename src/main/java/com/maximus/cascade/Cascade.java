package com.maximus.cascade;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class Cascade extends JavaPlugin {
	private Store store;
	private Plots plots;
	private Board board;
	private Menu menu;
	private MainMenu mainMenu;
	private SkillMenu skillMenu;
	private MineMenu mineMenu;
	private Npcs npcs;
	private PackServer pack;
	private Listeners listeners;
	private final Map<UUID, Wall> walls = new HashMap<>();

	@Override
	public void onEnable() {
		saveDefaultConfig();
		pack = new PackServer(this);
		pack.start();
		store = new Store(this);
		plots = new Plots();
		board = new Board(this);
		menu = new Menu(this);
		mainMenu = new MainMenu(this);
		skillMenu = new SkillMenu(this);
		mineMenu = new MineMenu(this);
		npcs = new Npcs(this);
		listeners = new Listeners(this);
		getServer().getPluginManager().registerEvents(listeners, this);
		Bukkit.getScheduler().runTaskTimer(this, () -> {
			board.updateAll();
			npcs.lookAtOwners();
		}, 10, 10);
		Bukkit.getScheduler().runTaskTimer(this, this::ambient, 20, 20);
		Bukkit.getScheduler().runTaskTimer(this, this::autoPick, 60, 60);
		Bukkit.getScheduler().runTaskTimer(this, store::saveAll, 20 * 60, 20 * 60);
		for (Player p : Bukkit.getOnlinePlayers()) {
			listeners.join(p);
		}
		getLogger().info("Каскад запущен");
	}

	@Override
	public void onDisable() {
		walls.values().forEach(Wall::destroy);
		walls.clear();
		if (npcs != null) {
			npcs.removeAll();
		}
		if (store != null) {
			store.saveAll();
		}
		if (pack != null) {
			pack.stop();
		}
	}

	public Store store() {
		return store;
	}

	public Plots plots() {
		return plots;
	}

	public Board board() {
		return board;
	}

	public Menu menu() {
		return menu;
	}

	public MainMenu mainMenu() {
		return mainMenu;
	}

	public SkillMenu skillMenu() {
		return skillMenu;
	}

	public MineMenu mineMenu() {
		return mineMenu;
	}

	public Npcs npcs() {
		return npcs;
	}

	public PackServer pack() {
		return pack;
	}

	public Wall wall(UUID id) {
		return walls.get(id);
	}

	/** Строит участок под текущую шахту, ставит стену, НПС и обстановку. */
	public void enterPlot(Player p, PlayerData d) {
		stopWall(p.getUniqueId());
		plots.build(d);
		p.teleport(plots.spawn(d));
		p.setPlayerTime(Mine.of(d).theme().time(), false);
		Wall w = new Wall(this, p, d);
		walls.put(p.getUniqueId(), w);
		w.reset();
		npcs.spawn(p, d);
	}

	public void travel(Player p, PlayerData d) {
		Mine m = Mine.of(d);
		enterPlot(p, d);
		p.showTitle(net.kyori.adventure.title.Title.title(Text.c(m.color() + "&l" + m.name()), Text.c("&7" + m.ruleText())));
		p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 1f);
	}

	public void stopWall(UUID id) {
		Wall w = walls.remove(id);
		if (w != null) {
			w.destroy();
		}
	}

	public void sell(Player p) {
		PlayerData d = store.get(p.getUniqueId());
		if (d == null) {
			return;
		}
		if (d.bag == 0) {
			p.sendActionBar(Text.c("&7Рюкзак пуст — сначала накопай руды"));
			return;
		}
		Skills.Stats s = Skills.stats(d);
		double got = d.bagValue * (1 + s.sellPct);
		boolean full = d.bag >= Balance.capacity(d);
		if (s.wholesale && full) {
			got *= 1.5;
		}
		d.coins += got;
		d.earnedTotal += got;
		p.sendActionBar(Text.c("&aПродано &f" + d.bag + " руды &a→ &e+" + Num.fmt(got) + " ⛃" + (s.wholesale && full ? " &6(опт ×1.5)" : "")));
		d.bag = 0;
		d.bagValue = 0;
		p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f);
		board.update(p);
	}

	public void addXp(Player p, PlayerData d, double amount) {
		d.xp += amount;
		boolean up = false;
		while (d.xp >= Balance.xpNeed(d.level)) {
			d.xp -= Balance.xpNeed(d.level);
			d.level++;
			up = true;
		}
		if (up) {
			p.showTitle(net.kyori.adventure.title.Title.title(Text.c("&e&lУровень " + d.level + "!"),
				Text.c("&7+1 очко навыка → &dМудрец")));
			p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
		}
		showLevel(p, d);
	}

	/** Уровень и опыт — в ванильной полоске опыта. */
	public void showLevel(Player p, PlayerData d) {
		p.setLevel(d.level);
		p.setExp((float) Math.min(0.999, d.xp / Balance.xpNeed(d.level)));
	}

	private void ambient() {
		for (Player p : Bukkit.getOnlinePlayers()) {
			PlayerData d = store.get(p.getUniqueId());
			if (d == null || !p.getWorld().equals(plots.world())) {
				continue;
			}
			var particle = Mine.of(d).theme().ambient();
			if (particle != null) {
				Location c = plots.wallCenter(d);
				p.spawnParticle(particle, c, 8, 4, 4, 4, 0);
			}
		}
	}

	private void autoPick() {
		for (var e : walls.entrySet()) {
			PlayerData d = store.get(e.getKey());
			if (d != null && Skills.stats(d).autoPick) {
				e.getValue().autoHit();
			}
		}
	}

	@Override
	public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
		switch (cmd.getName()) {
			case "menu" -> {
				if (sender instanceof Player p) {
					mainMenu.open(p);
				}
			}
			case "skills" -> {
				if (sender instanceof Player p) {
					skillMenu.open(p);
				}
			}
			case "mines" -> {
				if (sender instanceof Player p) {
					mineMenu.open(p);
				}
			}
			case "sell" -> {
				if (sender instanceof Player p) {
					sell(p);
				}
			}
			case "wall" -> {
				if (sender instanceof Player p && store.get(p.getUniqueId()) != null) {
					p.teleport(plots.spawn(store.get(p.getUniqueId())));
				}
			}
			case "top" -> {
				sender.sendMessage(Text.c("&6&l◆ Рекорды каскадов"));
				List<Store.TopEntry> top = store.top(10);
				for (int i = 0; i < top.size(); i++) {
					String color = i == 0 ? "&6" : i == 1 ? "&f" : i == 2 ? "&c" : "&7";
					sender.sendMessage(Text.c(color + (i + 1) + ". &f" + top.get(i).name() + " &7— &d" + top.get(i).chain() + " шаг."));
				}
			}
			case "cadmin" -> {
				return admin(sender, args);
			}
			default -> {
				return false;
			}
		}
		return true;
	}

	/** /cadmin coins|xp <игрок> <сколько> | /cadmin reset <игрок> — для тестов. */
	private boolean admin(CommandSender sender, String[] args) {
		if (args.length < 2) {
			sender.sendMessage(Text.c("&7/cadmin coins|xp <игрок> <сколько> | /cadmin reset <игрок>"));
			return true;
		}
		Player target = Bukkit.getPlayerExact(args[1]);
		PlayerData d = target == null ? null : store.get(target.getUniqueId());
		if (d == null) {
			sender.sendMessage(Text.c("&cИгрок не в сети"));
			return true;
		}
		double amount;
		try {
			amount = Double.parseDouble(args.length > 2 ? args[2] : "0");
		} catch (NumberFormatException e) {
			sender.sendMessage(Text.c("&cНе число"));
			return true;
		}
		switch (args[0]) {
			case "coins" -> d.coins += amount;
			case "xp" -> addXp(target, d, amount);
			case "reset" -> enterPlot(target, d);
			case "skills" -> skillMenu.open(target, (int) amount);
			case "grant" -> {
				// для тестов: изучить amount случайных доступных навыков бесплатно
				java.util.Random rnd = new java.util.Random();
				for (int i = 0; i < amount; i++) {
					List<Skills.Node> open = Skills.NODES.values().stream().filter(n -> Skills.available(d, n)).toList();
					if (open.isEmpty()) {
						break;
					}
					d.nodes.add(open.get(rnd.nextInt(open.size())).id());
				}
				d.level = Math.max(d.level, d.nodes.size() + 1);
				d.statsCache = null;
			}
			case "mines" -> mineMenu.open(target);
			default -> {
				sender.sendMessage(Text.c("&ccoins, xp или reset"));
				return true;
			}
		}
		sender.sendMessage(Text.c("&aГотово: " + d.name + " — " + Num.fmt(d.coins) + " ⛃, ур. " + d.level));
		return true;
	}
}
