package com.maximus.cascade;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Villager;

/**
 * НПС на участке игрока: Скупщик, Кузнец, Мудрец и Проводник.
 * Это жители без ИИ, которые поворачиваются к игроку; клик открывает их меню.
 */
public class Npcs {
	public enum Role {
		SELLER("&6&lСкупщик", "&7ПКМ — продать руду", Villager.Profession.MASON, Villager.Type.DESERT, 5, -1.5, Material.BARREL),
		SMITH("&b&lКузнец", "&7ПКМ — улучшения", Villager.Profession.TOOLSMITH, Villager.Type.PLAINS, 5, 1.5, Material.ANVIL),
		SAGE("&d&lМудрец", "&7ПКМ — древо исследований", Villager.Profession.LIBRARIAN, Villager.Type.SNOW, -5, -1.5, Material.LECTERN),
		GUIDE("&a&lПроводник", "&7ПКМ — другие шахты", Villager.Profession.CARTOGRAPHER, Villager.Type.TAIGA, -5, 1.5, Material.CARTOGRAPHY_TABLE);

		final String title;
		final String hint;
		final Villager.Profession profession;
		final Villager.Type type;
		/** Смещение от центра участка: X (блоки) и Z. */
		final int dx;
		final double z;
		final Material prop;

		Role(String title, String hint, Villager.Profession profession, Villager.Type type, int dx, double z, Material prop) {
			this.title = title;
			this.hint = hint;
			this.profession = profession;
			this.type = type;
			this.dx = dx;
			this.z = z;
			this.prop = prop;
		}
	}

	private record Npc(UUID owner, Role role, Villager villager, TextDisplay label) {
	}

	private final Cascade plugin;
	private final Map<UUID, Npc> byEntity = new HashMap<>();
	private final Map<UUID, List<Npc>> byOwner = new HashMap<>();

	public Npcs(Cascade plugin) {
		this.plugin = plugin;
	}

	public void spawn(Player p, PlayerData d) {
		remove(p.getUniqueId());
		World w = plugin.plots().world();
		int cx = plugin.plots().cx(d);
		List<Npc> list = new ArrayList<>();
		for (Role role : Role.values()) {
			// реквизит у бортика за спиной НПС
			w.getBlockAt(cx + role.dx + Integer.signum(role.dx), Plots.Y, (int) Math.floor(role.z)).setType(role.prop, false);
			Location at = new Location(w, cx + role.dx + 0.5, Plots.Y, role.z, role.dx > 0 ? 90f : -90f, 0f);
			Villager v = w.spawn(at, Villager.class, e -> {
				e.setPersistent(false);
				e.setAI(false);
				e.setInvulnerable(true);
				e.setSilent(true);
				e.setCollidable(false);
				e.setRemoveWhenFarAway(false);
				e.setProfession(role.profession);
				e.setVillagerType(role.type);
				e.setVillagerLevel(5);
			});
			TextDisplay label = w.spawn(at.clone().add(0, 2.35, 0), TextDisplay.class, t -> {
				t.setPersistent(false);
				t.setBillboard(Display.Billboard.CENTER);
				t.text(Text.c(role.title + "\n" + role.hint));
			});
			Npc npc = new Npc(p.getUniqueId(), role, v, label);
			list.add(npc);
			byEntity.put(v.getUniqueId(), npc);
		}
		byOwner.put(p.getUniqueId(), list);
	}

	public void remove(UUID owner) {
		List<Npc> list = byOwner.remove(owner);
		if (list == null) {
			return;
		}
		for (Npc n : list) {
			byEntity.remove(n.villager().getUniqueId());
			n.villager().remove();
			n.label().remove();
		}
	}

	public void removeAll() {
		new ArrayList<>(byOwner.keySet()).forEach(this::remove);
	}

	/** Роль НПС, если это наш житель. */
	public Role role(Entity e) {
		Npc n = byEntity.get(e.getUniqueId());
		return n == null ? null : n.role();
	}

	/** НПС смотрят на хозяина участка. */
	public void lookAtOwners() {
		for (List<Npc> list : byOwner.values()) {
			for (Npc n : list) {
				Player p = plugin.getServer().getPlayer(n.owner());
				if (p == null || !p.getWorld().equals(n.villager().getWorld())) {
					continue;
				}
				Location from = n.villager().getEyeLocation();
				Location to = p.getEyeLocation();
				double dx = to.getX() - from.getX();
				double dz = to.getZ() - from.getZ();
				double dy = to.getY() - from.getY();
				float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
				float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
				n.villager().setRotation(yaw, pitch);
			}
		}
	}

	public void open(Player p, Role role) {
		switch (role) {
			case SELLER -> plugin.sell(p);
			case SMITH -> plugin.menu().open(p);
			case SAGE -> plugin.skillMenu().open(p);
			case GUIDE -> plugin.mineMenu().open(p);
		}
	}
}
