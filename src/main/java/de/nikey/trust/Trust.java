package de.nikey.trust;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;

public final class Trust extends JavaPlugin implements TabExecutor, Listener {

    private final Map<UUID, Set<UUID>> trustMap = new HashMap<>();
    private final Map<UUID, Boolean> friendlyFireMap = new HashMap<>();
    private File configFile;
    private FileConfiguration config;

    @Override
    public void onEnable() {
        getCommand("trust").setExecutor(this);
        getCommand("trust").setTabCompleter(this);
        Bukkit.getPluginManager().registerEvents(this, this);
        loadTrustData();
    }

    @Override
    public void onDisable() {
        saveTrustData();
    }

    private void loadTrustData() {
        configFile = new File(getDataFolder(), "trust.yml");
        if (!configFile.exists()) {
            saveResource("trust.yml", false);
        }
        config = YamlConfiguration.loadConfiguration(configFile);

        for (String key : config.getKeys(false)) {
            UUID owner = UUID.fromString(key);
            List<String> trustedList = config.getStringList(key + ".trusted");
            boolean friendlyFire = config.getBoolean(key + ".friendly_fire", true);

            Set<UUID> trustedUUIDs = new HashSet<>();
            for (String uuidString : trustedList) {
                trustedUUIDs.add(UUID.fromString(uuidString));
            }
            trustMap.put(owner, trustedUUIDs);
            friendlyFireMap.put(owner, friendlyFire);
        }
    }

    private void saveTrustData() {
        for (Map.Entry<UUID, Set<UUID>> entry : trustMap.entrySet()) {
            List<String> trustedList = entry.getValue().stream().map(UUID::toString).toList();
            config.set(entry.getKey().toString() + ".trusted", trustedList);
            config.set(entry.getKey().toString() + ".friendly_fire", friendlyFireMap.getOrDefault(entry.getKey(), true));
        }
        try {
            config.save(configFile);
        } catch (IOException e) {
            getLogger().severe("Could not save trust data!");
            e.printStackTrace();
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use this command!"));
            return true;
        }

        if (args.length < 1) {
            sendUsage(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "list" -> handleList(player);
            case "add" -> {
                if (args.length < 2) {
                    player.sendMessage(Component.text("Usage: /trust add <player>"));
                    return true;
                }
                handleAdd(player, args[1]);
            }
            case "remove" -> {
                if (args.length < 2) {
                    player.sendMessage(Component.text("Usage: /trust remove <player>"));
                    return true;
                }
                handleRemove(player, args[1]);
            }
            case "friendlyfire" -> {
                if (args.length < 2 || (!args[1].equalsIgnoreCase("on") && !args[1].equalsIgnoreCase("off"))) {
                    player.sendMessage(Component.text("Usage: /trust friendlyfire <on|off>"));
                    return true;
                }
                handleFriendlyFire(player, args[1].equalsIgnoreCase("on"));
            }
            default -> sendUsage(player);
        }
        return true;
    }

    private void sendUsage(Player player) {
        player.sendMessage(Component.text("Usage: /trust <list|add|remove>"));
    }

    private void handleFriendlyFire(Player player, boolean enable) {
        friendlyFireMap.put(player.getUniqueId(), enable);
        player.sendMessage(Component.text("Friendly Fire has been " + (enable ? "enabled" : "disabled") + "."));
    }

    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim) || !(event.getDamager() instanceof Player attacker)) {
            return;
        }

        UUID attackerUUID = attacker.getUniqueId();
        UUID victimUUID = victim.getUniqueId();

        if (isTrusted(attackerUUID, victimUUID) && !friendlyFireMap.getOrDefault(attackerUUID, true)) {
            event.setCancelled(true);
            attacker.sendMessage(Component.text("You can't attack ")
                    .append(Component.text(victim.getName()))
                    .append(Component.text(" because Friendly Fire is disabled!")));
        }
    }

    private void handleList(Player player) {
        Set<UUID> trusted = trustMap.getOrDefault(player.getUniqueId(), Collections.emptySet());
        if (trusted.isEmpty()) {
            player.sendMessage(Component.text("You haven't trusted anyone yet."));
            return;
        }
        player.sendMessage(Component.text("Trusted players:"));
        for (UUID uuid : trusted) {
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(uuid);
            if (offlinePlayer.hasPlayedBefore()) {
                player.sendMessage(Component.text("- " + offlinePlayer.getName()));
            }
        }
    }

    private void handleAdd(Player player, String targetName) {
        UUID targetUUID = Bukkit.getPlayerUniqueId(targetName);
        if (targetUUID == null) {
            player.sendMessage(Component.text("Player not found."));
            return;
        }
        if (player.getUniqueId().equals(targetUUID)) {
            player.sendMessage(Component.text("You cannot trust yourself."));
            return;
        }
        trustPlayer(player.getUniqueId(), targetUUID);
        player.sendMessage(Component.text("You have trusted " + targetName + "."));
    }

    private void handleRemove(Player player, String targetName) {
        UUID targetUUID = Bukkit.getPlayerUniqueId(targetName);
        if (targetUUID == null) {
            player.sendMessage(Component.text("Player not found."));
            return;
        }
        if (untrustPlayer(player.getUniqueId(), targetUUID)) {
            player.sendMessage(Component.text("You have removed trust for " + targetName + "."));
        } else {
            player.sendMessage(Component.text("That player is not trusted."));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player player)) return Collections.emptyList();

        if (args.length == 1) {
            return Arrays.asList("list", "add", "remove", "friendlyfire");
        }

        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("add")) {
                return Bukkit.getOnlinePlayers().stream()
                        .filter(player::canSee)
                        .map(Player::getName)
                        .toList();
            }

            if (args[0].equalsIgnoreCase("remove")) {
                return trustMap.getOrDefault(player.getUniqueId(), Collections.emptySet()).stream()
                        .map(Bukkit::getPlayer)
                        .filter(Objects::nonNull)
                        .filter(player::canSee)
                        .map(Player::getName)
                        .toList();
            }

            if (args[0].equalsIgnoreCase("friendlyfire")) {
                return Arrays.asList("on", "off");
            }
        }

        return Collections.emptyList();
    }

    public boolean isTrusted(UUID owner, UUID target) {
        return trustMap.getOrDefault(owner, Collections.emptySet()).contains(target);
    }

    public void trustPlayer(UUID owner, UUID target) {
        trustMap.computeIfAbsent(owner, k -> new HashSet<>()).add(target);
    }

    public boolean untrustPlayer(UUID owner, UUID target) {
        Set<UUID> trusted = trustMap.get(owner);
        return trusted != null && trusted.remove(target);
    }

    public Set<UUID> getTrustedPlayers(UUID owner) {
        return trustMap.getOrDefault(owner, Collections.emptySet());
    }
}
