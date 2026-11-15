package com.maximus.cascade;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public class Listeners implements Listener {
	private final Cascade plugin;
	private final NamespacedKey toolKey;

	public Listeners(Cascade plugin) {
		this.plugin = plugin;
		this.toolKey = new NamespacedKey(plugin, "tool");
	}

	private boolean inGame(Player p) {
		return p.getWorld().equals(plugin.plots().world()) && p.getGameMode() != GameMode.CREATIVE;
	}

	// ---------- вход / выход ----------

	@EventHandler
	public void onJoin(PlayerJoinEvent e) {
		join(e.getPlayer());
	}

	public void join(Player p) {
		PlayerData d = plugin.store().load(p.getUniqueId(), p.getName());
		if (p.getGameMode() != GameMode.CREATIVE) {
			p.setGameMode(GameMode.SURVIVAL);
		}
		// Стена высокая — увеличиваем дальность удара по блокам.
		AttributeInstance reach = p.getAttribute(Attribute.BLOCK_INTERACTION_RANGE);
		if (reach != null) {
			reach.setBaseValue(12);
		}
		giveTools(p);
		p.getInventory().setHeldItemSlot(0);
		plugin.board().show(p);
		plugin.enterPlot(p, d);
		plugin.showLevel(p, d);
		plugin.pack().send(p);
		if (d.hits == 0) {
			p.sendMessage(Text.c("&6&l◆ Добро пожаловать в Каскад!"));
			p.sendMessage(Text.c("&7Бей киркой по любому блоку стены — он выбьется, а всё сверху упадёт."));
			p.sendMessage(Text.c("&e3+ одинаковых руды в ряд &7лопаются — и так по цепочке. Каждый шаг каскада дороже!"));
			p.sendMessage(Text.c("&74 в ряд → &cдинамит&7, угол Г/Т → &6бомба&7, 5 в ряд → &dаметист &7(все руды цвета)."));
			p.sendMessage(Text.c("&7По бокам — &6Скупщик&7, &bКузнец&7, &dМудрец &7и &aПроводник&7: кликни по ним ПКМ."));
		}
	}

	@EventHandler
	public void onQuit(PlayerQuitEvent e) {
		plugin.stopWall(e.getPlayer().getUniqueId());
		plugin.npcs().remove(e.getPlayer().getUniqueId());
		plugin.store().unload(e.getPlayer().getUniqueId());
		plugin.board().hide(e.getPlayer().getUniqueId());
	}

	@EventHandler
	public void onRespawn(PlayerRespawnEvent e) {
		PlayerData d = plugin.store().get(e.getPlayer().getUniqueId());
		if (d != null) {
			e.setRespawnLocation(plugin.plots().spawn(d));
		}
	}

	// ---------- удары и предметы ----------

	@EventHandler(priority = EventPriority.HIGH)
	public void onInteract(PlayerInteractEvent e) {
		Player p = e.getPlayer();
		if (e.getHand() != EquipmentSlot.HAND) {
			return;
		}
		boolean right = e.getAction() == Action.RIGHT_CLICK_AIR || e.getAction() == Action.RIGHT_CLICK_BLOCK;
		if (right && isTool(e.getItem())) {
			e.setCancelled(true);
			if (e.getItem().getType() == Material.NETHER_STAR) {
				plugin.mainMenu().open(p);
			} else if (e.getItem().getType() == Material.GOLD_INGOT) {
				plugin.sell(p);
			}
			return;
		}
		if (e.getAction() == Action.LEFT_CLICK_BLOCK && e.getClickedBlock() != null && inGame(p)) {
			PlayerData d = plugin.store().get(p.getUniqueId());
			Wall wall = plugin.wall(p.getUniqueId());
			int[] cell = d == null ? null : plugin.plots().cell(d, e.getClickedBlock());
			if (cell != null && wall != null) {
				e.setCancelled(true);
				wall.hit(cell[0], cell[1]);
			}
		}
	}

	@EventHandler(priority = EventPriority.HIGH)
	public void onNpcClick(PlayerInteractEntityEvent e) {
		Npcs.Role role = plugin.npcs().role(e.getRightClicked());
		if (role != null) {
			e.setCancelled(true);
			if (e.getHand() == EquipmentSlot.HAND) {
				plugin.npcs().open(e.getPlayer(), role);
			}
		}
	}

	@EventHandler(priority = EventPriority.HIGH)
	public void onNpcHit(EntityDamageByEntityEvent e) {
		Npcs.Role role = plugin.npcs().role(e.getEntity());
		if (role != null) {
			e.setCancelled(true);
			if (e.getDamager() instanceof Player p) {
				plugin.npcs().open(p, role);
			}
		}
	}

	@EventHandler
	public void onBlockDamage(BlockDamageEvent e) {
		if (inGame(e.getPlayer())) {
			e.setCancelled(true);
		}
	}

	@EventHandler
	public void onBreak(BlockBreakEvent e) {
		if (inGame(e.getPlayer())) {
			e.setCancelled(true);
		}
	}

	@EventHandler
	public void onPlace(BlockPlaceEvent e) {
		if (inGame(e.getPlayer())) {
			e.setCancelled(true);
		}
	}

	// ---------- защита ----------

	@EventHandler
	public void onHunger(FoodLevelChangeEvent e) {
		if (e.getEntity() instanceof Player p && inGame(p)) {
			e.setCancelled(true);
		}
	}

	@EventHandler
	public void onDamage(EntityDamageEvent e) {
		if (e.getEntity() instanceof Player p && inGame(p)) {
			e.setCancelled(true);
		}
	}

	@EventHandler
	public void onMove(PlayerMoveEvent e) {
		if (plugin.plots().fellOff(e.getTo())) {
			PlayerData d = plugin.store().get(e.getPlayer().getUniqueId());
			if (d != null) {
				e.getPlayer().teleport(plugin.plots().spawn(d));
			}
		}
	}

	// ---------- меню и предметы ----------

	@EventHandler
	public void onInventoryClick(InventoryClickEvent e) {
		if (!(e.getWhoClicked() instanceof Player p)) {
			return;
		}
		var holder = e.getView().getTopInventory().getHolder();
		if (holder instanceof Menu.Holder || holder instanceof MainMenu.Holder
			|| holder instanceof SkillMenu.Holder || holder instanceof MineMenu.Holder) {
			e.setCancelled(true);
			// круглая карта навыков занимает и инвентарь игрока — туда тоже можно кликнуть
			boolean top = e.getClickedInventory() == e.getView().getTopInventory();
			if (!top && !(holder instanceof SkillMenu.Holder && e.getClickedInventory() != null)) {
				return;
			}
			int slot = e.getRawSlot();
			switch (holder) {
				case Menu.Holder h -> plugin.menu().click(p, slot);
				case MainMenu.Holder h -> plugin.mainMenu().click(p, slot);
				case SkillMenu.Holder h -> plugin.skillMenu().click(p, h, slot, e.getClick());
				case MineMenu.Holder h -> plugin.mineMenu().click(p, slot);
				default -> {
				}
			}
			return;
		}
		if (isTool(e.getCurrentItem()) || isTool(e.getCursor())
			|| (e.getHotbarButton() >= 0 && isTool(p.getInventory().getItem(e.getHotbarButton())))) {
			e.setCancelled(true);
		}
	}

	@EventHandler
	public void onDrag(InventoryDragEvent e) {
		var holder = e.getView().getTopInventory().getHolder();
		if (holder instanceof Menu.Holder || holder instanceof MainMenu.Holder
			|| holder instanceof SkillMenu.Holder || holder instanceof MineMenu.Holder) {
			e.setCancelled(true);
		}
	}

	@EventHandler
	public void onDrop(PlayerDropItemEvent e) {
		if (isTool(e.getItemDrop().getItemStack())) {
			e.setCancelled(true);
		}
	}

	@EventHandler
	public void onSwap(PlayerSwapHandItemsEvent e) {
		if (isTool(e.getMainHandItem()) || isTool(e.getOffHandItem())) {
			e.setCancelled(true);
		}
	}

	/** Кирка, слиток продажи и звезда меню. Древо навыков прячет их, пока открыто. */
	public void giveTools(Player p) {
		p.getInventory().clear();
		p.getInventory().setItem(0, tool(Material.DIAMOND_PICKAXE, "&b⛏ Кирка каскада", "&7Бей ЛКМ по руде в стене"));
		p.getInventory().setItem(7, tool(Material.GOLD_INGOT, "&a⛃ Продать рюкзак &7(ПКМ)", "&7Вся руда из рюкзака → монеты"));
		p.getInventory().setItem(8, tool(Material.NETHER_STAR, "&6◆ Меню &7(ПКМ)", "&7Продажа, улучшения, навыки, шахты"));
	}

	@EventHandler
	public void onClose(InventoryCloseEvent e) {
		if (!(e.getInventory().getHolder() instanceof SkillMenu.Holder) || !(e.getPlayer() instanceof Player p)) {
			return;
		}
		// если сразу открылась другая страница древа — хотбар не возвращаем
		plugin.getServer().getScheduler().runTask(plugin, () -> {
			if (p.isOnline() && !(p.getOpenInventory().getTopInventory().getHolder() instanceof SkillMenu.Holder)) {
				giveTools(p);
			}
		});
	}

	private ItemStack tool(Material m, String name, String... lore) {
		ItemStack it = Menu.item(m, name, lore);
		ItemMeta meta = it.getItemMeta();
		meta.setUnbreakable(true);
		meta.getPersistentDataContainer().set(toolKey, PersistentDataType.BYTE, (byte) 1);
		it.setItemMeta(meta);
		return it;
	}

	private boolean isTool(ItemStack it) {
		return it != null && it.hasItemMeta() && it.getItemMeta().getPersistentDataContainer().has(toolKey, PersistentDataType.BYTE);
	}
}
