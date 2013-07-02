package de.blablubbabc.paintball;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.DyeColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;
import org.kitteh.tag.TagAPI;

import de.blablubbabc.paintball.extras.Airstrike;
import de.blablubbabc.paintball.extras.Ball;
import de.blablubbabc.paintball.extras.Flashbang;
import de.blablubbabc.paintball.extras.Gifts;
import de.blablubbabc.paintball.extras.Grenade;
import de.blablubbabc.paintball.extras.GrenadeM2;
import de.blablubbabc.paintball.extras.ItemManager;
import de.blablubbabc.paintball.extras.Mine;
import de.blablubbabc.paintball.extras.Orbitalstrike;
import de.blablubbabc.paintball.extras.Sniper;
import de.blablubbabc.paintball.extras.Turret;
import de.blablubbabc.paintball.statistics.arena.ArenaSetting;
import de.blablubbabc.paintball.statistics.player.match.tdm.TDMMatchStat;
import de.blablubbabc.paintball.statistics.player.match.tdm.TDMMatchStats;
import de.blablubbabc.paintball.utils.Sounds;
import de.blablubbabc.paintball.utils.Timer;
import de.blablubbabc.paintball.utils.Translator;
import de.blablubbabc.paintball.utils.Utils;

public class Match {

	private Paintball plugin;
	private Map<Player, Integer> livesLeft = new HashMap<Player, Integer>();
	private Map<Player, Integer> respawnsLeft = new HashMap<Player, Integer>();
	private List<Player> redT = new ArrayList<Player>();
	private List<Player> blueT = new ArrayList<Player>();
	private List<Player> bothTeams = new ArrayList<Player>();
	private List<Player> spec = new ArrayList<Player>();
	private List<Player> allPlayers = new ArrayList<Player>();
	private Map<Player, Integer> protection = new HashMap<Player, Integer>();
	private Map<String, Scoreboard> scoreboards = new HashMap<String, Scoreboard>();
	private Set<String> justRespawned = new HashSet<String>();
	// STATS
	private Map<String, TDMMatchStats> playerMatchStats = new HashMap<String, TDMMatchStats>();
	/*private Map<String, Integer> shots = new HashMap<String, Integer>();
	private Map<String, Integer> hits = new HashMap<String, Integer>();
	private Map<String, Integer> kills = new HashMap<String, Integer>();
	private Map<String, Integer> deaths = new HashMap<String, Integer>();
	private Map<String, Integer> teamattacks = new HashMap<String, Integer>();
	private Map<String, Integer> grenades = new HashMap<String, Integer>();
	private Map<String, Integer> airstrikes = new HashMap<String, Integer>();*/

	private Random random;

	private Map<String, Location> playersLoc = new HashMap<String, Location>();;
	private boolean matchOver = false;

	private String arena;

	private Timer startTimer;
	private Timer roundTimer;

	private List<Location> redspawns;
	private List<Location> bluespawns;
	private List<Location> specspawns;

	private int spawnBlue;
	private int spawnRed;
	private int spawnSpec;

	public int setting_balls;
	public int setting_grenades;
	public int setting_airstrikes;
	public int setting_lives;
	public int setting_respawns;
	public int setting_round_time;

	public boolean started = false;

	public List<Player> winners = new ArrayList<Player>();
	public List<Player> loosers = new ArrayList<Player>();
	public String win = "";
	public String loose = "";

	public Match(final Paintball plugin, Set<Player> red, Set<Player> blue, Set<Player> spec,
			Set<Player> random, String arena) {
		this.plugin = plugin;
		this.arena = arena;
		this.started = false;
		this.random = new Random();

		this.redspawns = plugin.arenaManager.getRedSpawns(arena);
		this.bluespawns = plugin.arenaManager.getBlueSpawns(arena);
		this.specspawns = plugin.arenaManager.getSpecSpawns(arena);

		// random spawns
		this.spawnBlue = Utils.random.nextInt(bluespawns.size());
		this.spawnRed = Utils.random.nextInt(redspawns.size());
		this.spawnSpec = Utils.random.nextInt(specspawns.size());

		calculateSettings();

		// TEAMS
		for (Player p : red) {
			this.redT.add(p);
			addToPlayerLists(p);
		}
		for (Player p : blue) {
			this.blueT.add(p);
			addToPlayerLists(p);
		}
		for (Player p : spec) {
			this.spec.add(p);
			this.allPlayers.add(p);
		}
		// this.spec = spec;

		// randoms:
		List<Player> rand = new ArrayList<Player>();
		for (Player p : random) {
			rand.add(p);
		}
		Collections.shuffle(rand);
		for (Player p : rand) {
			// players.add(p);
			if (this.blueT.size() < this.redT.size()) {
				this.blueT.add(p);
				addToPlayerLists(p);
			} else if (this.redT.size() <= this.blueT.size()) {
				this.redT.add(p);
				addToPlayerLists(p);
			}
		}

		// LISTS FINISHED

		for (Player player : getAllPlayer()) {
			// LIVES + RESPAWNS
			livesLeft.put(player, setting_lives);
			respawnsLeft.put(player, setting_respawns);
			// STATS
			String playerName = player.getName();
			playerMatchStats.put(playerName, new TDMMatchStats(this, playerName, plugin.playerManager.getPlayerStats(playerName)));

			// SCOREBOARD
			initMatchScoreboard(player);
			
			PlayerDataStore.clearPlayer(player, true, true);
			spawnPlayer(player);
		}

		for (Player p : this.spec) {
			PlayerDataStore.clearPlayer(p, true, true);
			spawnSpec(p);
		}
		// colorchanges:
		changeAllColors();
		updateTags();

		// WAITING TIMER:
		this.started = false;
		
		startTimer = new Timer(Paintball.instance, 0L, 20L, plugin.countdownStart, new Runnable() {
			
			@Override
			public void run() {
				for (Player p : getAllPlayer()) {
					Location ploc = p.getLocation();
					Location loc = playersLoc.get(p.getName());
					if (ploc.getBlockX() != loc.getBlockX() || ploc.getBlockY() != loc.getBlockY() || ploc.getBlockZ() != loc.getBlockZ()) {
						loc.setPitch(ploc.getPitch());
						loc.setYaw(ploc.getYaw());
						p.teleport(loc);
					}	
				}
			}
		}, new Runnable() {
			
			@Override
			public void run() {
				Map<String, String> vars = new HashMap<String, String>();
				vars.put("seconds", String.valueOf(startTimer.getTime()));
				String msg = Translator.getString("COUNTDOWN_START", vars);
				for (Player player : getAll()) {
					if (plugin.useXPBar) player.setLevel(startTimer.getTime());
					player.sendMessage(msg);
				}
			}
		}, new Runnable() {
			
			@Override
			public void run() {
				startTimer = null;
				// START:
				started = true;
				// lives + start!:
				Map<String, String> vars = new HashMap<String, String>();
				vars.put("lives", String.valueOf(setting_lives));
				if (setting_respawns == -1) {
					vars.put("respawns", Translator.getString("INFINITE"));
				} else {
					vars.put("respawns", String.valueOf(setting_respawns));
				}
				vars.put("round_time", String.valueOf(setting_round_time));

				plugin.feeder.status(Translator.getString("MATCH_SETTINGS_INFO", vars));
				plugin.feeder.status(Translator.getString("MATCH_START"));

				makeAllVisible();
				startRoundTimer();
			}
		});
		
	}

	private void addToPlayerLists(Player p) {
		this.allPlayers.add(p);
		this.bothTeams.add(p);
	}

	public void endTimers() {
		if (startTimer != null) startTimer.end();
		if (roundTimer != null) roundTimer.end();
	}
	
	private void startRoundTimer() {
		roundTimer = new Timer(Paintball.instance, 0L, 20L, setting_round_time, new Runnable() {
			
			@Override
			public void run() {
				// Spawn protection:
				Iterator<Map.Entry<Player, Integer>> iter = protection.entrySet()
						.iterator();
				while (iter.hasNext()) {
					Map.Entry<Player, Integer> entry = iter.next();
					Player p = entry.getKey();
					int t = entry.getValue() - 1;
					if (t <= 0) {
						if (p.isOnline() && isSurvivor(p))
							p.sendMessage(Translator.getString("PROTECTION_OVER"));
						iter.remove();
					} else {
						protection.put(p, t);
					}
				}
				// level bar
				if (plugin.useXPBar) {
					for (Player player : Lobby.LOBBY.getMembers()) {
						player.setLevel(roundTimer.getTime());
					}
				}
			}
		}, new Runnable() {
			
			@Override
			public void run() {
				plugin.feeder.roundTime(roundTimer.getTime());
			}
		}, new Runnable() {
			
			@Override
			public void run() {
				// level bar
				if (plugin.useXPBar) {
					for (Player player : Lobby.LOBBY.getMembers()) {
						player.setLevel(roundTimer.getTime());
					}
				}
				roundTimer = null;
				// END:
				if (matchOver)
					return;
				// winner?
				List<Player> winnerTeam = getWinner();
				if (winnerTeam == null) {
					// draw:
					gameEnd(true, null, null, null, null);
				} else {
					Player p = winnerTeam.get(0);
					gameEnd(false, winnerTeam, getEnemyTeam(p), getTeamName(p),
							getEnemyTeamName(p));
				}
			}
		});
	}

	private List<Player> getWinner() {
		// compare survivors:
		if (survivors(redT) > survivors(blueT))
			return redT;
		if (survivors(blueT) > survivors(redT))
			return blueT;
		// else: survivors(blueT) == survivors(redT)-> DRAW
		return null;
	}

	// SPAWNS

	@SuppressWarnings("deprecation")
	public synchronized void spawnPlayer(final Player player) {
		boolean red = false;
		Location loc;
		if (redT.contains(player)) {
			red = true;
			if (spawnRed > (redspawns.size() - 1))
				spawnRed = 0;
			loc = redspawns.get(spawnRed);
			spawnRed++;
		} else if (blueT.contains(player)) {
			if (spawnBlue > (bluespawns.size() - 1))
				spawnBlue = 0;
			loc = bluespawns.get(spawnBlue);
			spawnBlue++;
		} else {
			return;
		}
		player.teleport(loc);
		// sound
		Sounds.playEquipLoadout(player);
		// afk Location
		playersLoc.put(player.getName(), loc);
		// PLAYER
		PlayerDataStore.clearPlayer(player, false, false);
		// INVENTORY

		player.getInventory().setHelmet(
				Utils.setLeatherArmorColor(new ItemStack(Material.LEATHER_HELMET, 1),
						Lobby.getTeam(getTeamName(player)).colorA()));
		player.getInventory().setChestplate(
				Utils.setLeatherArmorColor(new ItemStack(Material.LEATHER_CHESTPLATE, 1),
						Lobby.getTeam(getTeamName(player)).colorA()));
		player.getInventory().setLeggings(
				Utils.setLeatherArmorColor(new ItemStack(Material.LEATHER_LEGGINGS, 1),
						Lobby.getTeam(getTeamName(player)).colorA()));
		player.getInventory().setBoots(
				Utils.setLeatherArmorColor(new ItemStack(Material.LEATHER_BOOTS, 1),
						Lobby.getTeam(getTeamName(player)).colorA()));
		
		// SHOP ITEM
		if (plugin.shop) player.getInventory().setItem(7, plugin.shopManager.item.clone());
		
		// TEAM WOOL
		if (red) {
			player.getInventory().setItem(8, ItemManager.setMeta(new ItemStack(Material.WOOL, 1, (short)0, DyeColor.RED.getWoolData())));
		} else {
			player.getInventory().setItem(8, ItemManager.setMeta(new ItemStack(Material.WOOL, 1, (short)0, DyeColor.BLUE.getWoolData())));
		}
		
		if (setting_balls > 0)
			player.getInventory().addItem(ItemManager.setMeta(new ItemStack(Material.SNOW_BALL, setting_balls)));
		else if (setting_balls == -1)
			player.getInventory().addItem(ItemManager.setMeta(new ItemStack(Material.SNOW_BALL, 10)));
		if (setting_grenades > 0)
			player.getInventory().addItem(ItemManager.setMeta(new ItemStack(Material.EGG, setting_grenades)));
		else if (setting_grenades == -1)
			player.getInventory().addItem(ItemManager.setMeta(new ItemStack(Material.EGG, 10)));
		if (setting_airstrikes > 0)
			player.getInventory().addItem(ItemManager.setMeta(new ItemStack(Material.STICK, setting_airstrikes)));
		else if (setting_airstrikes == -1)
			player.getInventory().addItem(ItemManager.setMeta(new ItemStack(Material.STICK, 10)));
		// gifts
		if (plugin.giftsEnabled) {
			int r = random.nextInt(1000);
			if (plugin.giftOnSpawnChance > (r / 10)) {
				Gifts.receiveGift(player, 1, false);
			}
		}
		player.updateInventory();
		// MESSAGE
		Map<String, String> vars = new HashMap<String, String>();
		vars.put("team_color", Lobby.getTeam(getTeamName(player)).color().toString());
		vars.put("team", getTeamName(player));
		player.sendMessage(Translator.getString("BE_IN_TEAM", vars));
		// SPAWN PROTECTION
		if (plugin.protectionTime > 0) {
			vars.put("protection", String.valueOf(plugin.protectionTime));
			protection.put(player, plugin.protectionTime);
			player.sendMessage(Translator.getString("PROTECTION", vars));
		}
		// JUST RESPAWNED TIMER
		// this will not work that accurate like intended, if the player gets killed, while being justRespawned.
		// For that reason is the timers delay very short.
		final String name = player.getName();
		justRespawned.add(name);
		plugin.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable() {

			@Override
			public void run() {
				justRespawned.remove(name);
			}
			
		}, 12L);
	}
	
	private void initMatchScoreboard(Player player) {
		String playerName = player.getName();
		Scoreboard matchBoard = scoreboards.get(playerName);
		if (matchBoard == null) {
			matchBoard = Bukkit.getScoreboardManager().getNewScoreboard();
			scoreboards.put(playerName, matchBoard);
		}
		
		// TODO own header (round timer maybe ?)
		String header = Translator.getString("SCOREBOARD_LOBBY_HEADER"); 
		Objective objective = matchBoard.registerNewObjective(header.length() > 16 ? header.substring(0, 16) : header, "dummy");
		objective.setDisplaySlot(DisplaySlot.SIDEBAR);
		updateMatchScoreboard(playerName);
		player.setScoreboard(matchBoard);
	}
	
	public void updateMatchScoreboard(String playerName) {
		Scoreboard matchBoard = scoreboards.get(playerName);
		if (matchBoard != null) {
			Objective objective = matchBoard.getObjective(DisplaySlot.SIDEBAR);
			TDMMatchStats stats = playerMatchStats.get(playerName);
			for (TDMMatchStat stat : TDMMatchStat.values()) {
				// skip airstrikes and grenades count:
				if (stat == TDMMatchStat.AIRSTRIKES || stat == TDMMatchStat.GRENADES) continue;	
				// TODO own match stat language support
				String scoreName = Translator.getString("SCOREBOARD_LOBBY_" + stat.getPlayerStat().getKey().toUpperCase());
				Score score = objective.getScore(Bukkit.getOfflinePlayer(scoreName));
				score.setScore(stats.getStat(stat));
			}
		}
	}
	
	public boolean isJustRespawned(String playerName) {
		return justRespawned.contains(playerName);
	}

	@SuppressWarnings("deprecation")
	public synchronized void spawnSpec(Player player) {
		if (spawnSpec > (specspawns.size() - 1))
			spawnSpec = 0;
		player.teleport(specspawns.get(spawnSpec));
		spawnSpec++;
		// INVENTORY
		player.getInventory().setHelmet(
				Utils.setLeatherArmorColor(new ItemStack(Material.LEATHER_HELMET, 1), Lobby.SPECTATE.colorA()));
		player.getInventory().setChestplate(
				Utils.setLeatherArmorColor(new ItemStack(Material.LEATHER_CHESTPLATE, 1), Lobby.SPECTATE.colorA()));
		player.getInventory().setLeggings(
				Utils.setLeatherArmorColor(new ItemStack(Material.LEATHER_LEGGINGS, 1), Lobby.SPECTATE.colorA()));
		player.getInventory().setBoots(
				Utils.setLeatherArmorColor(new ItemStack(Material.LEATHER_BOOTS, 1), Lobby.SPECTATE.colorA()));
		
		player.getInventory().setItem(8, ItemManager.setMeta(new ItemStack(Material.WOOL, 1, (short)0, DyeColor.YELLOW.getWoolData())));
		
		player.updateInventory();
		// MESSAGE
		Map<String, String> vars = new HashMap<String, String>();
		vars.put("team_color", Lobby.getTeam(player).color().toString());
		vars.put("team", Lobby.getTeam(player).getName());
		player.sendMessage(Translator.getString("BE_SPECTATOR", vars));
	}

	// INVENTORY
	private void calculateSettings() {
		Map<ArenaSetting, Integer> settings = plugin.arenaManager.getArenaSettings(arena);
		// BALLS
		setting_balls = plugin.balls + settings.get(ArenaSetting.BALLS);
		if (setting_balls < -1)
			setting_balls = -1;
		// GRENADES
		setting_grenades = plugin.grenadeAmount + settings.get(ArenaSetting.GRENADES);
		if (setting_grenades < -1)
			setting_grenades = -1;
		// AIRSTRIKES
		setting_airstrikes = plugin.airstrikeAmount + settings.get(ArenaSetting.AIRSTRIKES);
		if (setting_airstrikes < -1)
			setting_airstrikes = -1;
		// LIVES
		setting_lives = plugin.lives + settings.get(ArenaSetting.LIVES);
		if (setting_lives < 1)
			setting_lives = 1;
		// RESPAWNS
		setting_respawns = plugin.respawns + settings.get(ArenaSetting.RESPAWNS);
		if (setting_respawns < -1)
			setting_respawns = -1;
		// ROUND TIME
		setting_round_time = plugin.roundTimer + settings.get(ArenaSetting.ROUND_TIME);
		if (setting_round_time < 30)
			setting_round_time = 30;
	}

	private void makeAllVisible() {
		for (Player pl : getAll()) {
			for (Player p : getAll()) {
				if (!p.equals(pl))
					pl.showPlayer(p);
			}
		}
	}

	public void changeAllColors() {
		for (Player p : redT) {
			// chatnames
			String n = Lobby.RED.color() + p.getName();
			if (n.length() > 16)
				n = (String) n.subSequence(0, n.length() - (n.length() - 16));
			/*
			 * if(plugin.chatnames) { p.setDisplayName(n+white); }
			 */
			// listnames
			if (plugin.listnames) {
				p.setPlayerListName(n);
			}
		}
		for (Player p : blueT) {
			// chatnames
			String n = Lobby.BLUE.color() + p.getName();
			if (n.length() > 16)
				n = (String) n.subSequence(0, n.length() - (n.length() - 16));
			/*
			 * if(plugin.chatnames) { p.setDisplayName(n+white); }
			 */
			// listnames
			if (plugin.listnames) {
				p.setPlayerListName(n);
			}
		}
	}

	public void updateTags() {
		// player tags:
		if (plugin.tagAPI != null && plugin.tags) {
			for (Player p : plugin.getServer().getOnlinePlayers()) {
				Set<Player> set = new HashSet<Player>();
				for (Player pl : getAll()) {
					if (pl != p) {
						set.add(pl);
					}
				}
				TagAPI.refreshPlayer(p, set);
			}
		}
	}

	public void undoAllColors() {
		for (Player p : getAllPlayer()) {
			/*
			 * if(plugin.chatnames) { p.setDisplayName(p.getName()); }
			 */
			// listnames
			if (plugin.listnames) {
				if (Lobby.isPlaying(p))
					p.setPlayerListName(null);
			}
		}
	}

	public int teamSizeRed() {
		return redT.size();
	}

	public int teamSizeBlue() {
		return blueT.size();
	}

	public String getArena() {
		return this.arena;
	}

	public synchronized int survivors(List<Player> team) {
		int survivors = 0;
		for (Player p : team) {
			if (isSurvivor(p)) {
				survivors++;
			}
		}
		return survivors;
	}

	public synchronized boolean isSurvivor(Player player) {
		if (spec.contains(player))
			return true;
		if (getTeam(player) != null) {
			// if(setting_respawns == -1) return true;
			if (respawnsLeft.get(player) != 0)
				return true;
			else if (livesLeft.get(player) > 0)
				return true;
		}
		return false;
	}

	public String getTeamName(Player player) {
		if (redT.contains(player))
			return Lobby.RED.getName();
		if (blueT.contains(player))
			return Lobby.BLUE.getName();
		return null;
	}

	public String getEnemyTeamName(Player player) {
		if (redT.contains(player))
			return Lobby.BLUE.getName();
		if (blueT.contains(player))
			return Lobby.RED.getName();
		return null;
	}

	public boolean isSpec(Player player) {
		if (spec.contains(player))
			return true;
		else
			return false;
	}

	public boolean isRed(Player player) {
		if (redT.contains(player))
			return true;
		else
			return false;
	}

	public boolean isBlue(Player player) {
		if (blueT.contains(player))
			return true;
		else
			return false;
	}

	public List<Player> getTeam(Player player) {
		if (redT.contains(player))
			return redT;
		if (blueT.contains(player))
			return blueT;
		return null;
	}

	public List<Player> getEnemyTeam(Player player) {
		if (redT.contains(player))
			return blueT;
		if (blueT.contains(player))
			return redT;
		return null;
	}

	public boolean inMatch(Player player) {
		return allPlayers.contains(player);
	}

	public boolean enemys(Player player1, Player player2) {
		if (redT.contains(player1) && blueT.contains(player2))
			return true;
		if (redT.contains(player2) && blueT.contains(player1))
			return true;
		return false;
	}

	public boolean friendly(Player player1, Player player2) {
		if (redT.contains(player1) && redT.contains(player2))
			return true;
		if (blueT.contains(player1) && blueT.contains(player2))
			return true;
		return false;
	}

	public List<Player> getAllPlayer() {
		// ArrayList<Player> players = new ArrayList<Player>();
		// players.addAll(redT);
		// players.addAll(blueT);
		return bothTeams;
	}

	public List<Player> getAllSpec() {
		/*
		 * ArrayList<Player> list = new ArrayList<Player>(); for (Player p :
		 * spec) { list.add(p); } return list;
		 */
		return spec;
	}

	public List<Player> getAll() {
		/*
		 * // return players; ArrayList<Player> list = new
		 * ArrayList<Player>(getAllPlayer()); for (Player p : spec) {
		 * list.add(p); } return list;
		 */
		return allPlayers;
	}

	public boolean isProtected(Player player) {
		return protection.containsKey(player);
	}

	// AKTIONS

	public synchronized void left(Player player) {
		// team?
		if (getTeam(player) != null) {
			// 0 leben aka tot
			livesLeft.put(player, 0);
			respawnsLeft.put(player, 0);
			// spawn protection
			protection.remove(player);
			// afk detection-> remove player
			if (plugin.afkDetection) {
				plugin.afkRemove(player.getName());
			}
			resetWeaponStuff(player);
			// survivors?->endGame
			// math over already?
			if (matchOver)
				return;
			if (survivors(getTeam(player)) == 0) {
				gameEnd(false, getEnemyTeam(player), getTeam(player), getEnemyTeamName(player),
						getTeamName(player));
			}
		} else if (spec.contains(player))
			spec.remove(player);
	}

	public int respawnsLeft(Player player) {
		return respawnsLeft.get(player);
	}
	
	private synchronized void respawn(Player player) {
		livesLeft.put(player, setting_lives);
		if (setting_respawns != -1)
			respawnsLeft.put(player, respawnsLeft.get(player) - 1);
		// message
		Map<String, String> vars = new HashMap<String, String>();
		vars.put("lives", String.valueOf(setting_lives));
		if (setting_respawns == -1)
			vars.put("respawns", Translator.getString("INFINITE"));
		else
			vars.put("respawns", String.valueOf(respawnsLeft.get(player)));
		player.sendMessage(Translator.getString("RESPAWN", vars));
		// spawn protection
		protection.remove(player);
		// spawn
		spawnPlayer(player);
	}

	public void onShot(Player player) {
		String playerName = player.getName();
		// STATS
		TDMMatchStats matchStats = playerMatchStats.get(playerName);
		matchStats.addStat(TDMMatchStat.SHOTS, 1);
		matchStats.calculateQuotes();
	}

	public void onGrenade(Player player) {
		String playerName = player.getName();
		// STATS
		TDMMatchStats matchStats = playerMatchStats.get(playerName);
		matchStats.addStat(TDMMatchStat.GRENADES, 1);
	}

	public void onAirstrike(Player player) {
		String playerName = player.getName();
		// STATS
		TDMMatchStats matchStats = playerMatchStats.get(playerName);
		matchStats.addStat(TDMMatchStat.AIRSTRIKES, 1);
	}

	public synchronized void onHitByBall(Player target, Player shooter, Origin source) {
		// math over already?
		if (matchOver)
			return;

		// target already dead?
		if (livesLeft.get(target) <= 0)
			return;
		
		String targetName = target.getName();
		String shooterName = shooter.getName();
		
		Map<String, String> vars = new HashMap<String, String>();
		vars.put("target", targetName);
		vars.put("shooter", shooterName);
		
		// Teams?
		if (enemys(target, shooter)) {
			// player not dead already?
			if (livesLeft.get(target) > 0) {
				// protection
				if (isProtected(target)) {
					Sounds.playProtected(shooter, target);
					shooter.sendMessage(Translator.getString("YOU_HIT_PROTECTED", vars));
					target.sendMessage(Translator.getString("YOU_WERE_HIT_PROTECTED", vars));
				} else {
					int healthLeft = livesLeft.get(target) - 1;
					// -1 life
					livesLeft.put(target, healthLeft);
					
					// STATS
					TDMMatchStats matchStats = playerMatchStats.get(shooterName);
					matchStats.addStat(TDMMatchStat.HITS, 1);
					matchStats.addStat(TDMMatchStat.POINTS, plugin.pointsPerHit);
					matchStats.addStat(TDMMatchStat.MONEY, plugin.cashPerHit);
					matchStats.calculateQuotes();
					
					// dead?->frag
					// message:
					if (healthLeft <= 0) {
						frag(target, shooter, source);
					} else {
						// xp bar
						if (plugin.useXPBar) {
							target.setExp((float)healthLeft / setting_lives);
						}
						Sounds.playHit(shooter, target);
						
						vars.put("hits_taken", String.valueOf(setting_lives - healthLeft));
						vars.put("health_left", String.valueOf(healthLeft));
						vars.put("health", String.valueOf(setting_lives));
						
						shooter.sendMessage(Translator.getString("YOU_HIT", vars));
						target.sendMessage(Translator.getString("YOU_WERE_HIT", vars));
					}
				}
			}
		} else if (friendly(target, shooter)) {
			// STATS
			TDMMatchStats matchStats = playerMatchStats.get(shooterName);
			matchStats.addStat(TDMMatchStat.TEAMATTACKS, 1);
			matchStats.addStat(TDMMatchStat.POINTS, plugin.pointsPerTeamattack);
			
			// SOUND EFFECT
			Sounds.playTeamattack(shooter);
			
			if (plugin.pointsPerTeamattack != 0) {
				vars.put("points", String.valueOf(plugin.pointsPerTeamattack));
				shooter.sendMessage(Translator.getString("YOU_HIT_MATE_POINTS", vars));
			} else {
				shooter.sendMessage(Translator.getString("YOU_HIT_MATE", vars));
			}
		}
	}

	public synchronized void frag(final Player target, Player killer, Origin source) {
		// math over already?
		if (matchOver)
			return;
		
		String targetName = target.getName();
		String killerName = killer.getName();
		
		Sounds.playFrag(killer, target);

		// STATS
		// KILLER:
		TDMMatchStats killerStats = playerMatchStats.get(killerName);
		killerStats.addStat(TDMMatchStat.KILLS, 1);
		killerStats.addStat(TDMMatchStat.POINTS, plugin.pointsPerKill);
		killerStats.addStat(TDMMatchStat.MONEY, plugin.cashPerKill);
		killerStats.calculateQuotes();
		// TARGET:
		TDMMatchStats targetStats = playerMatchStats.get(targetName);
		targetStats.addStat(TDMMatchStat.DEATHS, 1);
		targetStats.calculateQuotes();
		
		// 0 lives = -> out
		livesLeft.put(target, 0);
		// spawn protection
		protection.remove(target);

		// FEED
		Map<String, String> vars = new HashMap<String, String>();
		vars.put("target", targetName);
		vars.put("killer", killerName);
		vars.put("points", String.valueOf(plugin.pointsPerKill));
		vars.put("money", String.valueOf(plugin.cashPerKill));
		killer.sendMessage(Translator.getString("YOU_KILLED", vars));
		target.sendMessage(Translator.getString("YOU_WERE_KILLED", vars));
		plugin.feeder.feed(target, killer, this);

		// afk detection on frag
		if (plugin.afkDetection) {
			if (target.getLocation().getWorld().equals(playersLoc.get(targetName).getWorld())
					&& target.getLocation().distance(playersLoc.get(targetName)) <= plugin.afkRadius
					&& targetStats.getStat(TDMMatchStat.SHOTS) == 0 && targetStats.getStat(TDMMatchStat.KILLS) == 0) {
				plugin.afkSet(targetName, plugin.afkGet(targetName) + 1);
			} else {
				plugin.afkRemove(targetName);
			}
		}

		if (isSurvivor(target)) {
			// respawn(target);
			// afk check
			if (plugin.afkDetection && (plugin.afkGet(targetName) >= plugin.afkMatchAmount)) {
				// consequences after being afk:
				plugin.afkRemove(targetName);
				respawnsLeft.put(target, 0);
				plugin.getServer().getScheduler().runTask(plugin, new Runnable() {
					@Override
					public void run() {
						plugin.enterLobby(target);
					}
				});

				Lobby.getTeam(target).removeMember(target);
				plugin.feeder.afkLeave(target, this);
				target.sendMessage(Translator.getString("YOU_LEFT_TEAM"));
			} else
				respawn(target);
		} else {
			resetWeaponStuff(target);
			plugin.getServer().getScheduler().runTask(plugin, new Runnable() {
				@Override
				public void run() {
					plugin.enterLobby(target);
				}
			});
		}

		// survivors?->endGame
		if (survivors(getTeam(target)) == 0) {
			gameEnd(false, getTeam(killer), getTeam(target), getTeamName(killer),
					getTeamName(target));
		}

	}

	public synchronized void death(final Player target) {
		// math over already?
		if (matchOver)
			return;

		String targetName = target.getName();
		
		// STATS
		// TARGET:
		TDMMatchStats targetStats = playerMatchStats.get(targetName);
		targetStats.addStat(TDMMatchStat.DEATHS, 1);
		targetStats.calculateQuotes();
		
		// FEED
		target.sendMessage(Translator.getString("YOU_DIED"));
		plugin.feeder.death(target, this);
		// no survivors? -> endGame
		// 0 lives -> out
		livesLeft.put(target, 0);
		// spawn protection
		protection.remove(target);

		// afk detection on death
		if (plugin.afkDetection) {
			if (target.getLocation().getWorld().equals(playersLoc.get(targetName).getWorld())
					&& target.getLocation().distance(playersLoc.get(targetName)) <= plugin.afkRadius
					&& targetStats.getStat(TDMMatchStat.SHOTS) == 0 && targetStats.getStat(TDMMatchStat.KILLS) == 0) {
				plugin.afkSet(targetName, plugin.afkGet(targetName) + 1);
			} else {
				plugin.afkRemove(targetName);
			}
		}

		if (isSurvivor(target)) {
			// afk check
			if (plugin.afkDetection && (plugin.afkGet(targetName) >= plugin.afkMatchAmount)) {
				// consequences after being afk:
				plugin.afkRemove(targetName);
				respawnsLeft.put(target, 0);
				resetWeaponStuff(target);
				plugin.getServer().getScheduler().runTask(plugin, new Runnable() {
					@Override
					public void run() {
						plugin.enterLobby(target);
					}
				});

				Lobby.getTeam(target).removeMember(target);
				plugin.feeder.afkLeave(target, this);
				target.sendMessage(Translator.getString("YOU_LEFT_TEAM"));
			} else
				respawn(target);
		} else {
			resetWeaponStuff(target);
			plugin.getServer().getScheduler().runTask(plugin, new Runnable() {
				@Override
				public void run() {
					plugin.enterLobby(target);
				}
			});
		}

		// survivors?->endGame
		if (survivors(getTeam(target)) == 0) {
			gameEnd(false, getEnemyTeam(target), getTeam(target), getEnemyTeamName(target),
					getTeamName(target));
		}
	}

	private synchronized void gameEnd(final boolean draw, List<Player> winnerS,
			List<Player> looserS, String winS, String looseS) {
		matchOver = true;
		endTimers();
		undoAllColors();
		for (Player p : getAllPlayer()) {
			resetWeaponStuff(p);
		}
		resetWeaponStuffEnd();
		if (!draw) {
			for (Player p : winnerS) {
				this.winners.add(p);
			}
			for (Player p : looserS) {
				this.loosers.add(p);
			}
			this.win = winS;
			this.loose = looseS;
		}
		final Match this2 = this;
		plugin.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable() {

			@Override
			public void run() {
				plugin.matchManager.gameEnd(this2, draw, playersLoc, spec, playerMatchStats);
			}
		}, 1L);
	}

	public void resetWeaponStuff(Player p) {
		// remove turrets:
		ArrayList<Turret> pturrets = new ArrayList<Turret>(Turret.getTurrets(p.getName()));
		for (Turret t : pturrets) {
			t.die(false);
		}
		// remove mines:
		ArrayList<Mine> pmines = new ArrayList<Mine>(Mine.getMines(p.getName()));
		for (Mine m : pmines) {
			m.explode(false);
		}
		
		// remove zooming
		if (Sniper.isZooming(p))
			Sniper.setNotZooming(p);
		
	}
	
	public void resetWeaponStuffEnd() {
		//remove airstrikes
		Airstrike.clear();
		//remove orbitalstrikes
		Orbitalstrike.clear();
		//remove grenades
		Grenade.clear();
		GrenadeM2.clear();
		Flashbang.clear();
		Ball.clear();
	}

	/*public void resetWeaponStuffDeath(Player p) {
		// remove turrets:
		ArrayList<Turret> pturrets = new ArrayList<Turret>(Turret.getTurrets(p.getName()));
		for (Turret t : pturrets) {
			t.die(true);
		}
		// remove mines:
		ArrayList<Mine> pmines = new ArrayList<Mine>(Mine.getMines(p.getName()));
		for (Mine m : pmines) {
			m.explode(false);
		}
		// remove zooming
		if (Sniper.isZooming(p))
			Sniper.setNotZooming(p);
	}*/

}
