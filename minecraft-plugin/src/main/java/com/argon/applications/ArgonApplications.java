package com.argon.applications;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
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
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class ArgonApplications extends JavaPlugin implements Listener {
    private final Map<UUID, String> stages = new HashMap<>();
    private final Map<UUID, BukkitTask> promptTasks = new HashMap<>();
    private final Set<UUID> approved = new HashSet<>();
    private final Set<UUID> afk = new HashSet<>();
    private final Map<UUID, org.bukkit.Location> afkOrigins = new HashMap<>();
    private final Map<UUID, NamedTextColor> chatColours = new ConcurrentHashMap<>();
    private final HttpClient notificationClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private static final List<String> CHAT_COLOUR_NAMES = List.of(
            "black", "dark_blue", "dark_green", "dark_aqua",
            "dark_red", "dark_purple", "gold", "gray",
            "dark_gray", "blue", "green", "aqua",
            "red", "light_purple", "yellow", "white"
    );

    private static final Map<String, NamedTextColor> CHAT_COLOURS = Map.ofEntries(
            Map.entry("black", NamedTextColor.BLACK),
            Map.entry("dark_blue", NamedTextColor.DARK_BLUE),
            Map.entry("dark_green", NamedTextColor.DARK_GREEN),
            Map.entry("dark_aqua", NamedTextColor.DARK_AQUA),
            Map.entry("dark_red", NamedTextColor.DARK_RED),
            Map.entry("dark_purple", NamedTextColor.DARK_PURPLE),
            Map.entry("gold", NamedTextColor.GOLD),
            Map.entry("gray", NamedTextColor.GRAY),
            Map.entry("dark_gray", NamedTextColor.DARK_GRAY),
            Map.entry("blue", NamedTextColor.BLUE),
            Map.entry("green", NamedTextColor.GREEN),
            Map.entry("aqua", NamedTextColor.AQUA),
            Map.entry("red", NamedTextColor.RED),
            Map.entry("light_purple", NamedTextColor.LIGHT_PURPLE),
            Map.entry("yellow", NamedTextColor.YELLOW),
            Map.entry("white", NamedTextColor.WHITE)
    );

    private YamlConfiguration data;
    private File dataFile;

    @Override
    public void onEnable() {
        saveDefaultConfig();
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

        if (data.getConfigurationSection("chat-colours") != null) {
            for (String id : data.getConfigurationSection("chat-colours").getKeys(false)) {
                try {
                    String name = data.getString("chat-colours." + id, "").toLowerCase(Locale.ROOT);
                    NamedTextColor colour = CHAT_COLOURS.get(name);
                    if (colour != null) {
                        chatColours.put(UUID.fromString(id), colour);
                    }
                } catch (IllegalArgumentException ignored) {
                    getLogger().warning("Ignoring invalid chat-colour entry: " + id);
                }
            }
        }

        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("ArgonApplications enabled; approved UUIDs loaded: " + approved.size());

        // Reassert Adventure for unapproved players in case another plugin changes their mode.
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (pending(online) && online.getGameMode() != GameMode.ADVENTURE) {
                    online.setGameMode(GameMode.ADVENTURE);
                }
            }
        }, 1L, 10L);

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
                sender.sendMessage("/app approve|remove|reject <player>");
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
             } else if (args[0].equalsIgnoreCase("remove")) {
                approved.remove(id);
                data.set("pending." + id, null);
                saveData();
                Player online = Bukkit.getPlayer(id);
                if (online != null) begin(online);
                sender.sendMessage("Removed " + target.getName() + " from the Argon whitelist.");
            } else if (args[0].equalsIgnoreCase("reject")) {
                data.set("pending." + id, null);
                saveData();
                sender.sendMessage("Removed application for " + target.getName());
            } else {
                sender.sendMessage("/app approve|whitelist|remove|reject <player>");
            }
            return true;
        });

        Objects.requireNonNull(getCommand("afk")).setExecutor((sender, command, label, args) -> {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("/afk can only be used by a player.");
                return true;
            }
            UUID id = player.getUniqueId();
            if (afk.remove(id)) {
                afkOrigins.remove(id);
                player.sendMessage("AFK protection disabled.");
            } else {
                afk.add(id);
                afkOrigins.put(id, player.getLocation().clone());
                // Clear any mobs that had already selected this player before AFK was enabled.
                for (org.bukkit.entity.Entity nearby : player.getNearbyEntities(64, 64, 64)) {
                    if (nearby instanceof org.bukkit.entity.Mob mob && player.equals(mob.getTarget())) {
                        mob.setTarget(null);
                    }
                }
                player.sendMessage("AFK protection enabled: mobs won't target or hurt you; hunger is paused. Players can hit you, but cannot kill you.");
            }
            return true;
        });

        Objects.requireNonNull(getCommand("colour")).setExecutor((sender, command, label, args) -> {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Only players can change their chat-name colour.");
                return true;
            }
            if (args.length == 0) {
                player.sendMessage("Available chat colours (Java + Bedrock): " + String.join(", ", CHAT_COLOUR_NAMES));
                player.sendMessage("Usage: /colour <colour>");
                return true;
            }
            if (args.length != 1) {
                player.sendMessage("Usage: /colour <colour>");
                return true;
            }

            String colourName = args[0].toLowerCase(Locale.ROOT);
            NamedTextColor colour = CHAT_COLOURS.get(colourName);
            if (colour == null) {
                player.sendMessage("Unknown colour. Available: " + String.join(", ", CHAT_COLOUR_NAMES));
                return true;
            }

            chatColours.put(player.getUniqueId(), colour);
            data.set("chat-colours." + player.getUniqueId(), colourName);
            saveData();
            player.sendMessage(Component.text("Your chat name colour is now ", NamedTextColor.GRAY)
                    .append(Component.text(colourName, colour))
                    .append(Component.text(".", NamedTextColor.GRAY)));
            return true;
        });
        Objects.requireNonNull(getCommand("colour")).setTabCompleter((sender, command, alias, args) -> {
            if (args.length != 1) return List.of();
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return CHAT_COLOUR_NAMES.stream().filter(name -> name.startsWith(prefix)).toList();
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
            long submittedAt = System.currentTimeMillis();
            boolean bedrockPlayer = isBedrock(player);
            data.set(key + ".submitted", submittedAt);
            data.set(key + ".bedrock", bedrockPlayer);
            saveData();

            sendApplicationNotification(player, realName, submittedAt, bedrockPlayer);

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

    private void sendApplicationNotification(Player player, String realName, long submittedAt, boolean bedrockPlayer) {
        String endpoint = getConfig().getString("application-notification.endpoint", "").trim();
        String sharedSecret = getConfig().getString("application-notification.secret", "").trim();
        if (endpoint.isEmpty() || sharedSecret.isEmpty()) {
            getLogger().warning("Application notification is not configured. The application was saved locally, but no email was sent.");
            return;
        }

        String payload = "{"
                + "\"first_name\":\"" + jsonEscape(realName) + "\","
                + "\"minecraft_username\":\"" + jsonEscape(player.getName()) + "\","
                + "\"player_uuid\":\"" + player.getUniqueId() + "\","
                + "\"edition\":\"" + (bedrockPlayer ? "BEDROCK" : "JAVA") + "\","
                + "\"server_name\":\"" + jsonEscape(Bukkit.getServer().getName()) + "\","
                + "\"server_version\":\"" + jsonEscape(Bukkit.getVersion()) + "\","
                + "\"submitted_at\":\"" + Instant.ofEpochMilli(submittedAt) + "\""
                + "}";

        HttpRequest request;
        try {
            request = HttpRequest.newBuilder(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/json")
                    .header("X-Argon-Token", sharedSecret)
                    .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                    .build();
        } catch (IllegalArgumentException exception) {
            getLogger().severe("Invalid application notification endpoint in config.yml: " + exception.getMessage());
            return;
        }

        String username = player.getName();
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            try {
                HttpResponse<String> response = notificationClient.send(
                        request,
                        HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
                );
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    getLogger().info("Application notification email sent for " + username + ".");
                } else {
                    getLogger().warning("Application notification failed for " + username
                            + " (HTTP " + response.statusCode() + "): " + response.body());
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                getLogger().warning("Application notification interrupted for " + username + ".");
            } catch (Exception exception) {
                getLogger().warning("Could not send application notification for " + username + ": "
                        + exception.getMessage());
            }
        });
    }

    private String jsonEscape(String value) {
        StringBuilder escaped = new StringBuilder(value.length() + 16);
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                case '\b' -> escaped.append("\\b");
                case '\f' -> escaped.append("\\f");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (character < 0x20) escaped.append(String.format(Locale.ROOT, "\\u%04x", (int) character));
                    else escaped.append(character);
                }
            }
        }
        return escaped.toString();
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
        player.showTitle(Title.title(
                Component.text("READ CHAT!", NamedTextColor.GOLD, TextDecoration.BOLD),
                Component.text("Your approval instructions are in chat.", NamedTextColor.WHITE),
                Title.Times.times(Duration.ofMillis(150), Duration.ofSeconds(5), Duration.ofMillis(400))
        ));

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
        // Keep the approval instructions in normal chat-sized text instead of a large,
        // repeatedly displayed full-screen title.
        player.sendMessage(Component.text("You need approval from the elites", NamedTextColor.GOLD));
        player.sendMessage(Component.text(
                "just type in the chat /send (your name) so the elites can decide if you should play or not",
                NamedTextColor.WHITE
        ));
        player.sendMessage(Component.text(
                "Just type your real name in - we can't tell who it is that wants to join otherwise",
                NamedTextColor.GRAY
        ));
        player.sendMessage(Component.text(
                "Don't do silly things like break other peoples stuff or you will be deprived of everything this dunya has to offer...yuck",
                NamedTextColor.DARK_GRAY
        ));
        player.sendMessage(Component.text(
                "Privacy notice: /send emails your submitted first name, Minecraft username and UUID, Java/Bedrock edition, submission time and server version to argonmcadmin@gmail.com and stores the application record. No IP address is collected.",
                NamedTextColor.GRAY
        ));
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
            online.sendActionBar(Component.text(
                    "Approval required — use /send (your name)",
                    NamedTextColor.GOLD
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

    private NamedTextColor ensureChatColour(Player player) {
        UUID id = player.getUniqueId();
        NamedTextColor existing = chatColours.get(id);
        if (existing != null) return existing;

        String name = CHAT_COLOUR_NAMES.get(new Random().nextInt(CHAT_COLOUR_NAMES.size()));
        NamedTextColor selected = CHAT_COLOURS.get(name);
        chatColours.put(id, selected);
        data.set("chat-colours." + id, name);
        saveData();
        return selected;
    }

    @EventHandler
    public void chatNameColour(AsyncChatEvent event) {
        NamedTextColor colour = chatColours.getOrDefault(event.getPlayer().getUniqueId(), NamedTextColor.WHITE);
        event.renderer((source, sourceDisplayName, message, viewer) ->
                sourceDisplayName.color(colour)
                        .append(Component.text(": ", NamedTextColor.GRAY))
                        .append(message.color(NamedTextColor.WHITE))
        );
    }

    @EventHandler
    public void join(PlayerJoinEvent event) {
        ensureChatColour(event.getPlayer());
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
    public void afkMovement(org.bukkit.event.player.PlayerMoveEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        if (!afk.contains(id) || event.getTo() == null) return;
        org.bukkit.Location origin = afkOrigins.get(id);
        if (origin == null) {
            afkOrigins.put(id, event.getFrom().clone());
            return;
        }
        // Two blocks of displacement in any direction automatically ends AFK mode.
        if (event.getTo().distanceSquared(origin) >= 4.0) {
            afk.remove(id);
            afkOrigins.remove(id);
            event.getPlayer().sendMessage("AFK protection disabled because you moved 2 blocks.");
        }
    }

    @EventHandler
    public void quit(PlayerQuitEvent event) {
        afkOrigins.remove(event.getPlayer().getUniqueId());
        afk.remove(event.getPlayer().getUniqueId());
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
    public void afkMobTarget(EntityTargetLivingEntityEvent event) {
        if (event.getTarget() instanceof Player player && afk.contains(player.getUniqueId())
                && !(event.getEntity() instanceof Player)) event.setCancelled(true);
    }

    private Player getPlayerAttacker(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) return player;
        if (event.getDamager() instanceof Projectile projectile
                && projectile.getShooter() instanceof Player player) return player;
        return null;
    }

    @EventHandler
    public void afkDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || !afk.contains(player.getUniqueId())) return;

        // AFK players ignore mobs, environmental damage, and other non-player damage.
        // Direct attacks and projectiles fired by players are still allowed.
        if (!(event instanceof EntityDamageByEntityEvent byEntity) || getPlayerAttacker(byEntity) == null) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void afkPvPNonlethal(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim) || !afk.contains(victim.getUniqueId())
                || getPlayerAttacker(event) == null) return;

        // Absorption is included so a player can take hits but never be reduced below one health.
        double safeDamage = Math.max(0.0, victim.getHealth() - 1.0 + victim.getAbsorptionAmount());
        if (event.getFinalDamage() > safeDamage) {
            if (safeDamage <= 0.0) event.setCancelled(true);
            else event.setDamage(Math.min(event.getDamage(), safeDamage));
        }
    }

    @EventHandler
    public void afkHunger(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player player && afk.contains(player.getUniqueId())) event.setCancelled(true);
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
        if (!Set.of("/send", "/msg", "/tell", "/help", "/colour", "/color").contains(label)) {
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
        restorePlayerState(player);
        player.sendMessage("You are whitelisted. Welcome to survival!");
    }
}
