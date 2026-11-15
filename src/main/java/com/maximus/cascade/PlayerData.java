package com.maximus.cascade;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/** Прогресс игрока. */
public class PlayerData {
	public final UUID id;
	public String name;
	public int plot = -1;
	public double coins;
	public double earnedTotal;
	/** Сколько руды в рюкзаке и сколько она стоит. */
	public long bag;
	public double bagValue;
	public int bagLevel;
	public int valueLevel;
	public int speedLevel;
	public int bestChain;
	public long hits;
	/** Текущая шахта и открытые шахты (битовая маска). */
	public int mine;
	public int unlocked = 1;
	public int level = 1;
	public double xp;
	/** Взятые узлы дерева навыков. */
	public final Set<String> nodes = new LinkedHashSet<>();
	public transient Skills.Stats statsCache;

	public PlayerData(UUID id) {
		this.id = id;
	}

	public boolean hasMine(int i) {
		return (unlocked & (1 << i)) != 0;
	}
}
