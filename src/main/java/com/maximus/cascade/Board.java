package com.maximus.cascade;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import io.papermc.paper.scoreboard.numbers.NumberFormat;

/** Табло справа. Строки — фиксированные записи с customName, без мерцания. */
public class Board {
	private final Cascade plugin;
	private final Map<UUID, Scoreboard> boards = new HashMap<>();

	public Board(Cascade plugin) {
		this.plugin = plugin;
	}

	public void show(Player p) {
		Scoreboard sb = Bukkit.getScoreboardManager().getNewScoreboard();
		Objective o = sb.registerNewObjective("cascade", Criteria.DUMMY, Text.c("&6&l◆ КАСКАД ◆"));
		o.setDisplaySlot(DisplaySlot.SIDEBAR);
		o.numberFormat(NumberFormat.blank());
		boards.put(p.getUniqueId(), sb);
		p.setScoreboard(sb);
		update(p);
	}

	public void hide(UUID id) {
		boards.remove(id);
	}

	public void update(Player p) {
		Scoreboard sb = boards.get(p.getUniqueId());
		PlayerData d = plugin.store().get(p.getUniqueId());
		if (sb == null || d == null) {
			return;
		}
		long cap = Balance.capacity(d);
		String bagColor = d.bag >= cap ? "&c" : d.bag >= cap * 0.8 ? "&6" : "&a";
		List<String> lines = List.of(
			"&7",
			"&fМонеты: &e" + Num.fmt(d.coins) + " ⛃",
			"&fРюкзак: " + bagColor + d.bag + "&7/" + cap,
			"&f  на сумму: &e" + Num.fmt(d.bagValue) + " ⛃",
			"&8",
			"&fУровень: &e" + d.level + (Skills.freePoints(d) > 0 ? " &a(+" + Skills.freePoints(d) + " очк.)" : ""),
			"&fРекорд каскада: &d" + d.bestChain + " шаг.",
			"&fШахта: " + Mine.of(d).color() + Mine.of(d).name(),
			"&9",
			"&8ПКМ звездой — меню");
		Objective o = sb.getObjective("cascade");
		for (int i = 0; i < lines.size(); i++) {
			var score = o.getScore("line" + i);
			score.setScore(lines.size() - i);
			score.customName(Text.c(lines.get(i)));
		}
	}

	public void updateAll() {
		Bukkit.getOnlinePlayers().forEach(this::update);
	}
}
