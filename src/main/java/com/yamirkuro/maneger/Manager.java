package com.yamirkuro.maneger;

import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.event.*;
import com.palmergames.bukkit.towny.event.nation.NationRankRemoveEvent;
import com.palmergames.bukkit.towny.event.nation.NationRankAddEvent;
import com.palmergames.bukkit.towny.event.nation.NationKingChangeEvent;
//import com.palmergames.bukkit.towny.event.nation.;
import com.palmergames.bukkit.towny.object.Nation;
import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.Town;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.node.types.InheritanceNode;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public class Manager extends JavaPlugin implements Listener {

    private LuckPerms luckPerms;
    private final Map<String, Integer> rankPriority = new HashMap<>();
    private final Map<String, String> rankToGroupMap = new HashMap<>();
    private String joinGroup;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        FileConfiguration config = getConfig();

        // Підключення до LuckPerms
        this.luckPerms = getServer().getServicesManager().load(LuckPerms.class);
        if (luckPerms == null) {
            getLogger().severe("LuckPerms не знайдено, плагін вимикається.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Завантаження рангів з конфігу
        if (config.isConfigurationSection("ranks")) {
            for (String rank : Objects.requireNonNull(config.getConfigurationSection("ranks")).getKeys(false)) {
                String group = config.getString("ranks." + rank + ".group");
                int priority = config.getInt("ranks." + rank + ".priority");
                rankToGroupMap.put(rank.toLowerCase(), group);
                rankPriority.put(rank.toLowerCase(), priority);
            }
        }

        joinGroup = config.getString("join_group", null);

        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("[RankSync] Плагін успішно увімкнено.");
    }
    //коли місто створюється
    @EventHandler
    public void onTownCreate(NewTownEvent event) {
        Bukkit.getScheduler().runTaskLater(this, () -> {
            Resident mayor = event.getTown().getMayor();
            if (mayor != null) {
                Player player = Bukkit.getPlayerExact(mayor.getName());
                if (player != null) {
                    player.sendMessage("§a[TRS] Ви стали мером міста " + event.getTown().getName());
                    handleRankChange(player.getName(), "mayor_group", true);
                }
            }
        }, 1L); // затримка в 1 тік, щоб Towny встиг завершити створення міста

    }
    @EventHandler
    public void onAddRank(TownAddResidentRankEvent event) {
        handleRankChange(event.getResident().getName(), event.getRank().toLowerCase(), true);
    }
    @EventHandler
    public void onNationCreate(NewNationEvent event) {
        Nation nation = event.getNation();
        Resident king = nation.getKing();
        Player player = Bukkit.getPlayerExact(king.getName());

        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (player != null) {
                player.sendMessage("§a[TRS] Ви створили націю " + nation.getName() + " і стали її королем.");
                handleRankChange(player.getName(), "nation_king", true);
            }

        }, 1L); //

    }
    @EventHandler
    public void onRemoveRank(TownRemoveResidentRankEvent event) {
        handleRankChange(event.getResident().getName(), event.getRank().toLowerCase(), false);
    }
    @EventHandler
    public void onNationKingChange(NationKingChangeEvent event) {
        Resident newKing = event.getNewKing();
        if (newKing != null) {
            handleRankChange(newKing.getName(), "nation_king", true);
        }
        Resident oldKing = event.getOldKing();
        if (oldKing != null) {
            handleRankChange(oldKing.getName(), "nation_king", false);
        }
    }


    @EventHandler
    public void onNationAddRank(NationRankAddEvent event) {
        handleRankChange(event.getResident().getName(), "nation_" + event.getRank().toLowerCase(), true);
    }

    @EventHandler
    public void onNationRemoveRank(NationRankRemoveEvent event) {
        handleRankChange(event.getResident().getName(), "nation_" + event.getRank().toLowerCase(), false);
    }

    @EventHandler
    public void onJoinTown(TownAddResidentEvent event) {
        handleRankChange(event.getResident().getName(), joinGroup, true);
    }

    @EventHandler
    public void onLeaveTown(TownRemoveResidentEvent event) {
        handleRankChange(event.getResident().getName(), joinGroup, false);
        // Додатково видаляємо всі ранги при виході
        for (String rank : rankToGroupMap.keySet()) {
            handleRankChange(event.getResident().getName(), rank, false);
        }
    }

    private void handleRankChange(String playerName, String rankName, boolean add) {
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerName);
        UUID uuid = offlinePlayer.getUniqueId();

        if (rankName == null || !rankPriority.containsKey(rankName.toLowerCase())) {
            getLogger().warning("[RankSync] Ранг не налаштовано в плагіні: " + rankName);
            return;
        }

        if (add) {
            addGroup(uuid, rankName.toLowerCase());
            if (offlinePlayer.isOnline()) {
                offlinePlayer.getPlayer().sendMessage("§a[TRS] Видано групу за ранг: " + rankName);
            }
        } else {
            removeGroup(uuid, rankName.toLowerCase());
            if (offlinePlayer.isOnline()) {
                offlinePlayer.getPlayer().sendMessage("§c[TRS] Знято групу за ранг: " + rankName);
            }
        }
    }

    private void addGroup(UUID uuid, String rankName) {
        String groupName = rankToGroupMap.getOrDefault(rankName, rankName);

        luckPerms.getUserManager().loadUser(uuid).thenAcceptAsync(user -> {
            InheritanceNode node = InheritanceNode.builder(groupName).build();
            user.data().add(node);
            luckPerms.getUserManager().saveUser(user);
            getLogger().info("[RankSync] Видано групу " + groupName + " гравцю " + user.getUsername());
        });
    }

    private void removeGroup(UUID uuid, String rankName) {
        String groupName = rankToGroupMap.getOrDefault(rankName, rankName);

        luckPerms.getUserManager().loadUser(uuid).thenAcceptAsync(user -> {
            InheritanceNode node = InheritanceNode.builder(groupName).build();
            user.data().remove(node);
            luckPerms.getUserManager().saveUser(user);
            getLogger().info("[RankSync] Знято групу " + groupName + " з гравця " + user.getUsername());
        });
    }
}


