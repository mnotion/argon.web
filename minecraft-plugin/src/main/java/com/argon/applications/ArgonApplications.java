package com.argon.applications;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.*;

public final class ArgonApplications extends JavaPlugin implements Listener {
    private final Map<UUID, String> stages = new HashMap<>();
    private final Map<UUID, BukkitTask> promptTasks = new HashMap<>();
    private final Set<UUID> approved = new HashSet<>();
    private YamlConfiguration data;
    private File dataFile;

    @Override
    public void onEnable() {
        dataFile = new File(getDataFolder(), "applications.yml");
        if (!dataFile.exists()) {
            try {
                getDataFolder().mkdirs();
                dataFile.createNewFile();
            } catch (IOException e) {
                getLogger().severe("Could not create applications.yml: " + e.getMessage());
            }
        }
        data = YamlConfiguration.loadConfiguration(dataFile);
        for (String id : data.getStringList("approved")) {
            try {
                approved.add(UUID.fromString(id));
            } catch (IllegalArgumentException ignored) {
                getLogger().warning("Ignoring invalid approved UUID: " + id);
            }
        }

        getServer().getPluginManager().registerEvents(this, this);

        Objects.requireNonNull(getCommand("app")).setExecutor((sender, command, label, args) -> {
            if (!sender.hasPermission("argonapplications.admin")) {
                sender.sendMessage("No permission.");
                return true;
            }
            if (args.length == 0 || args[0].equalsIgnoreCase("list")) {
                sender.sendMessage("Pending: " + (data.getConfigurationSection("pending") == null
                        ? "[]" : data.getConfigurationSection("pending").getKeys(false)));
                return true;
            }
            if (args.length < 2) {
                sender.sendMessage("/app approve|reject <player>");
                return true;
            }

            OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
            UUID id = target.getUniqueId();
            if (args[0].equalsIgnoreCase("approve") || args[0].equalsIgnoreCase("whitelist")) {
                approved.add(id);
                saveData();
                Player online = Bukkit.getPlayer(id);
                if (online != null) {
                    release(online);
                }
                sender.sendMessage("Added " + target.getName() + " to the Argon player whitelist.");
            } else if (args[0].equalsIgnoreCase("reject")) {
                data.set("pending." + id, null);
                saveData();
                sender.sendMessage("Removed application for " + target.getName());
            } else {
                sender.sendMessage("/app approve|whitelist|reject <player>");
            }
            return true;
        });

        Objects.requireNonNull(getCommand("send")).setExecutor((sender, command, label, args) -> {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Only players can submit an application.");
                return true;
            }
            UUID id = player.getUniqueId();
            if (!pending(player)) {
                player.sendMessage("You do not need to submit an application.");
                return true;
            }
            String existingName = data.getString("pending." + id + ".firstName", "");
            if (existingName != null && !existingName.isBlank()) {
                player.sendMessage("Your name has already been submitted.");
                return true;
            }
            if (args.length == 0) {
                player.sendMessage("Usage: /send <your real first name>");
                return true;
            }

            String realName = String.join(" ", args).trim();
            if (!realName.matches("[A-Za-z][A-Za-z' -]{0,29}") || realName.endsWith(" ") || realName.contains("  ")) {
                player.sendMessage("Enter a valid first name (letters, spaces, apostrophes or hyphens; max 30 characters).");
                return true;
            }

            String key = "pending." + id;
            data.set(key + ".player", player.getName());
            data.set(key + ".firstName", realName);
            data.set(key + ".submitted", System.currentTimeMillis());
            data.set(key + ".bedrock", isBedrock(player));
            saveData();

            stages.remove(id);
            stopNamePrompt(id);
            player.showTitle(Title.title(
                    Component.text("APPLICATION SENT", NamedTextColor.GREEN),
                    Component.text("You can explore while staff review your application.", NamedTextColor.WHITE),
                    Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(4), Duration.ofMillis(500))
            ));
            player.sendMessage("Application submitted. You can keep exploring while staff review it.");
            return true;
        });
    }

    private void saveData() {
        data.set("approved", approved.stream().map(UUID::toString).toList());
        try {
            data.save(dataFile);
        } catch (IOException e) {
            getLogger().severe("Could not save applications.yml: " + e.getMessage());
        }
    }

    private boolean bypass(Player player) {
        return player.isOp() || player.hasPermission("argonapplications.bypass");
    }

    private boolean pending(Player player) {
        return !approved.contains(player.getUniqueId()) && !bypass(player);
    }

    private void begin(Player player) {
        UUID id = player.getUniqueId();
        String key = "pending." + id;

        // Preserve the player's original survival items only once.
        if (!data.contains(key + ".saved")) {
            data.set(key + ".saved", player.getInventory().getContents());
            data.set(key + ".armor", player.getInventory().getArmorContents());
            data.set(key + ".offhand", player.getInventory().getItemInOffHand());
            data.set(key + ".gamemode", player.getGameMode().name());
        }
        data.set(key + ".player", player.getName());
        data.set(key + ".bedrock", isBedrock(player));
        saveData();

        player.getInventory().clear();
        player.getInventory().setArmorContents(new ItemStack[4]);
        player.getInventory().setItemInOffHand(null);
        player.setGameMode(GameMode.ADVENTURE);
        player.setFoodLevel(20);
        player.setSaturation(20);
        player.setHealth(20);

        String submittedName = data.getString(key + ".firstName", "");
        if (submittedName != null && !submittedName.isBlank()) {
            stages.remove(id);
            stopNamePrompt(id);
            player.sendMessage("Your application is pending staff approval. You can keep exploring.");
        } else {
            stages.put(id, "name");
            showNamePrompt(player);
            startNamePrompt(player);
        }
    }

    private void showNamePrompt(Player player) {
        player.showTitle(Title.title(
                Component.text("APPLICATION REQUIRED", NamedTextColor.GOLD),
                Component.text("Type /send <your real first name>", NamedTextColor.WHITE),
                Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(5), Duration.ofMillis(300))
        ));
        player.sendMessage(Component.text("Submit your real first name with: /send <name>", NamedTextColor.YELLOW));
    }

    private void startNamePrompt(Player player) {
        UUID id = player.getUniqueId();
        stopNamePrompt(id);
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(this, () -> {
            Player online = Bukkit.getPlayer(id);
            if (online == null || !online.isOnline() || !"name".equals(stages.get(id))) {
                stopNamePrompt(id);
                return;
            }
            online.showTitle(Title.title(
                    Component.text("APPLICATION REQUIRED", NamedTextColor.GOLD),
                    Component.text("Type /send <your real first name>", NamedTextColor.WHITE),
                    Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(5), Duration.ofMillis(300))
            ));
        }, 100L, 100L);
        promptTasks.put(id, task);
    }

    private void stopNamePrompt(UUID id) {
        BukkitTask task = promptTasks.remove(id);
        if (task != null) {
            task.cancel();
        }
    }

    private boolean isBedrock(Player player) {
        try {
            Class<?> api = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            Object instance = api.getMethod("getInstance").invoke(null);
            return (boolean) api.getMethod("isFloodgatePlayer", UUID.class).invoke(instance, player.getUniqueId());
        } catch (Exception ignored) {
            return false;
        }
    }

    @EventHandler
    public void join(PlayerJoinEvent event) {
        new BukkitRunnable() {
            @Override
            public void run() {
                Player player = event.getPlayer();
                if (!player.isOnline()) {
                    return;
                }
                if (approved.contains(player.getUniqueId()) || bypass(player)) {
                    restorePlayerState(player);
                } else {
                    // Every unapproved player uses the application lobby, not just first-time joiners.
                    begin(player);
                }
            }
        }.runTaskLater(this, 2L);
    }

    @EventHandler
    public void quit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        stages.remove(id);
        stopNamePrompt(id);
    }

    @EventHandler
    public void damage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && pending(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void interact(PlayerInteractEvent event) {
        if (pending(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void interactEntity(PlayerInteractEntityEvent event) {
        if (pending(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void inventoryOpen(InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player player && pending(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void inventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player && pending(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void inventoryDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player && pending(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void swapHands(PlayerSwapHandItemsEvent event) {
        if (pending(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void consume(PlayerItemConsumeEvent event) {
        if (pending(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void blockBreak(BlockBreakEvent event) {
        if (pending(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void blockPlace(BlockPlaceEvent event) {
        if (pending(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void combat(EntityDamageByEntityEvent event) {
        if ((event.getDamager() instanceof Player attacker && pending(attacker))
                || (event.getEntity() instanceof Player victim && pending(victim))) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void hunger(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player player && pending(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void drop(PlayerDropItemEvent event) {
        if (pending(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void pickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player && pending(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void command(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (!pending(player)) {
            return;
        }
        String label = event.getMessage().split(" ", 2)[0].toLowerCase(Locale.ROOT);
        if (!Set.of("/send", "/msg", "/tell", "/help").contains(label)) {
            event.setCancelled(true);
        }
    }

    private void restorePlayerState(Player player) {
        String key = "pending." + player.getUniqueId();
        if (data.contains(key + ".saved")) {
            Object inventory = data.get(key + ".saved");
            if (inventory instanceof ItemStack[] items) {
                player.getInventory().setContents(items);
            }
            Object armor = data.get(key + ".armor");
            if (armor instanceof ItemStack[] items) {
                player.getInventory().setArmorContents(items);
            }
            ItemStack offhand = data.getItemStack(key + ".offhand");
            player.getInventory().setItemInOffHand(offhand);
            String mode = data.getString(key + ".gamemode", "SURVIVAL");
            try {
                player.setGameMode(GameMode.valueOf(mode));
            } catch (IllegalArgumentException ignored) {
                player.setGameMode(GameMode.SURVIVAL);
            }
            data.set(key, null);
            saveData();
        }

        stages.remove(player.getUniqueId());
        stopNamePrompt(player.getUniqueId());
    }

    private void release(Player player) {
        enterSurvival(player);
        player.sendMessage("You are whitelisted. Welcome to survival!");
    }
}
