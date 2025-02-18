package de.nikey.trust;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;

public final class Trust extends JavaPlugin implements TabExecutor, Listener {

    private static Trust plugin;

    @Override
    public void onEnable() {
        plugin = this;
        getCommand("trust").setExecutor(this);
        getCommand("trust").setTabCompleter(this);
        Bukkit.getPluginManager().registerEvents(this, this);
        saveDefaultConfig();
    }

    public static Trust getPlugin() {
        return plugin;
    }

    @Override
    public void onDisable() {
        saveConfig();
    }


    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String @NotNull [] args) {
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

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        Player attacker = null;

        if (event.getDamager() instanceof Player) {
            attacker = (Player) event.getDamager();
        } else if (event.getDamager() instanceof Projectile projectile && projectile.getShooter() instanceof Player) {
            attacker = (Player) projectile.getShooter();
        }

        if (!(event.getEntity() instanceof Player victim) || attacker == null) {
            return;
        }

        UUID attackerUUID = attacker.getUniqueId();
        UUID victimUUID = victim.getUniqueId();

        if (isTrusted(attackerUUID, victimUUID) && !hasFriendlyFire(attackerUUID)) {
            event.setCancelled(true);
        }
    }

    private void sendUsage(Player player) {
        player.sendMessage(Component.text("Usage: /trust <list|add|remove|friendlyfire>"));
    }

    private void handleList(Player player) {
        List<UUID> trusted = getTrustedPlayers(player.getUniqueId());

        if (trusted.isEmpty()) {
            player.sendMessage(Component.text("You haven't trusted anyone yet."));
            return;
        }

        player.sendMessage(Component.text("Trusted players:").color(NamedTextColor.DARK_AQUA));
        for (UUID uuid : trusted) {
            Player onlinePlayer = Bukkit.getPlayer(uuid);
            if (onlinePlayer != null && player.canSee(onlinePlayer)) {
                player.sendMessage(Component.text("- " + onlinePlayer.getName()).color(NamedTextColor.AQUA));
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

        List<UUID> trusted = new ArrayList<>(getTrustedPlayers(player.getUniqueId()));
        if (trusted.contains(targetUUID)) {
            player.sendMessage(Component.text(targetName + " is already trusted."));
            return;
        }

        trusted.add(targetUUID);
        saveTrustData(player.getUniqueId(), trusted, hasFriendlyFire(player.getUniqueId()));
        player.sendMessage(Component.text("You have trusted " + targetName + ".").color(NamedTextColor.GREEN));
    }

    private void handleRemove(Player player, String targetName) {
        UUID targetUUID = Bukkit.getPlayerUniqueId(targetName);
        if (targetUUID == null) {
            player.sendMessage(Component.text("Player not found."));
            return;
        }

        List<UUID> trusted = new ArrayList<>(getTrustedPlayers(player.getUniqueId()));
        if (!trusted.remove(targetUUID)) {
            player.sendMessage(Component.text("That player is not trusted."));
            return;
        }

        saveTrustData(player.getUniqueId(), trusted, hasFriendlyFire(player.getUniqueId()));
    player.sendMessage(Component.text("You have removed trust for " + targetName + ".").color(NamedTextColor.RED));
    }


    private void handleFriendlyFire(Player player, boolean enable) {
        saveTrustData(player.getUniqueId(), getTrustedPlayers(player.getUniqueId()), enable);
        player.sendMessage(Component.text("Friendly Fire has been " + (enable ? "enabled" : "disabled") + ".").color(NamedTextColor.GRAY));
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
                List<UUID> trusted = getTrustedPlayers(player.getUniqueId());

                if (trusted.isEmpty()) {
                    return Bukkit.getOnlinePlayers().stream()
                            .filter(player::canSee)
                            .map(Player::getName)
                            .toList();
                }
                return trusted.stream()
                        .map(uuid -> Bukkit.getOfflinePlayer(uuid).getName())
                        .collect(Collectors.toList());
            }

            if (args[0].equalsIgnoreCase("friendlyfire")) {
                return Arrays.asList("on", "off");
            }
        }

        return Collections.emptyList();
    }

    public static boolean isTrusted(UUID owner, UUID target) {
        return getTrustedPlayers(owner).contains(target);
    }

    public static List<UUID> getTrustedPlayers(UUID playerUUID) {
        return getPlugin().getConfig().getStringList("trust." + playerUUID + ".trusted").stream()
                .map(UUID::fromString)
                .toList();
    }

    public static boolean hasFriendlyFire(UUID playerUUID) {
        return getPlugin().getConfig().getBoolean("trust." + playerUUID + ".friendlyFire", false);
    }

    public void saveTrustData(UUID playerUUID, List<UUID> trustedPlayers, boolean friendlyFire) {
        getConfig().set("trust." + playerUUID + ".trusted", trustedPlayers.stream().map(UUID::toString).toList());
        getConfig().set("trust." + playerUUID + ".friendlyFire", friendlyFire);
        saveConfig();
    }

}
