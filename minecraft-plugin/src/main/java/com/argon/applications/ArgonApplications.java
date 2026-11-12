package com.argon.applications;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
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
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class ArgonApplications extends JavaPlugin implements Listener {
    private final Map<UUID, String> stages = new HashMap<>();
    private final Map<UUID, BukkitTask> promptTasks = new HashMap<>();
    private final Set<UUID> approved = new HashSet<>();
    private final Set<UUID> afk = new HashSet<>();
    private final Map<UUID, org.bukkit.Location> afkOrigins = new HashMap<>();
    private final Map<UUID, Long> combatTags = new HashMap<>();
    private final Map<UUID, Long> timeouts = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> timeoutTasks = new HashMap<>();
    private final Set<UUID> timeoutAddedAfk = new HashSet<>();
    private static final long PVP_AFK_COOLDOWN_MILLIS = 60_000L;
    private final Map<UUID, TextColor> chatColours = new ConcurrentHashMap<>();
    private final HttpClient notificationClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private static final List<String> CHAT_COLOUR_NAMES = List.of(
            "black", "dark_blue", "dark_green", "dark_aqua",
            "dark_red", "dark_purple", "gold", "gray",
            "dark_gray", "blue", "green", "aqua",
            "red", "light_purple", "yellow", "white",
            "pink", "light_pink", "hot_pink", "deep_pink", "pale_pink", "blush",
            "rose", "dusty_rose", "coral", "light_coral", "salmon", "peach",
            "apricot", "orange", "dark_orange", "amber", "tangerine", "lemon",
            "mustard", "lime", "chartreuse", "olive", "mint", "mint_green",
            "seafoam", "sage", "pastel_green", "emerald", "jade", "forest_green",
            "spring_green", "teal", "turquoise", "aquamarine", "cyan", "ice_blue",
            "sky_blue", "baby_blue", "powder_blue", "steel_blue", "royal_blue", "dodger_blue",
            "navy", "midnight_blue", "indigo", "periwinkle", "lavender", "lilac",
            "violet", "purple", "plum", "orchid", "magenta", "fuchsia",
            "grape", "burgundy", "maroon", "crimson", "ruby", "scarlet",
            "brick_red", "chocolate", "coffee", "caramel", "tan", "beige",
            "cream", "ivory", "sand", "taupe", "slate", "charcoal",
            "graphite", "silver", "platinum", "rose_gold", "copper", "bronze"
    );

    private static final Map<String, TextColor> CHAT_COLOURS = createChatColours();

    private static Map<String, TextColor> createChatColours() {
        Map<String, TextColor> colours = new LinkedHashMap<>();
        colours.put("black", NamedTextColor.BLACK);
        colours.put("dark_blue", NamedTextColor.DARK_BLUE);
        colours.put("dark_green", NamedTextColor.DARK_GREEN);
        colours.put("dark_aqua", NamedTextColor.DARK_AQUA);
        colours.put("dark_red", NamedTextColor.DARK_RED);
        colours.put("dark_purple", NamedTextColor.DARK_PURPLE);
        colours.put("gold", NamedTextColor.GOLD);
        colours.put("gray", NamedTextColor.GRAY);
        colours.put("dark_gray", NamedTextColor.DARK_GRAY);
        colours.put("blue", NamedTextColor.BLUE);
        colours.put("green", NamedTextColor.GREEN);
        colours.put("aqua", NamedTextColor.AQUA);
        colours.put("red", NamedTextColor.RED);
        colours.put("light_purple", NamedTextColor.LIGHT_PURPLE);
        colours.put("yellow", NamedTextColor.YELLOW);
        colours.put("white", NamedTextColor.WHITE);

        colours.put("pink", TextColor.color(0xFFC0CB));
        colours.put("light_pink", TextColor.color(0xFFB6C1));
        colours.put("hot_pink", TextColor.color(0xFF69B4));
        colours.put("deep_pink", TextColor.color(0xFF1493));
        colours.put("pale_pink", TextColor.color(0xFADADD));
        colours.put("blush", TextColor.color(0xDE5D83));
        colours.put("rose", TextColor.color(0xFF007F));
        colours.put("dusty_rose", TextColor.color(0xDCAE96));
        colours.put("coral", TextColor.color(0xFF7F50));
        colours.put("light_coral", TextColor.color(0xF08080));
        colours.put("salmon", TextColor.color(0xFA8072));
        colours.put("peach", TextColor.color(0xFFE5B4));
        colours.put("apricot", TextColor.color(0xFBCEB1));
        colours.put("orange", TextColor.color(0xFFA500));
        colours.put("dark_orange", TextColor.color(0xFF8C00));
        colours.put("amber", TextColor.color(0xFFBF00));
        colours.put("tangerine", TextColor.color(0xF28500));
        colours.put("lemon", TextColor.color(0xFFF44F));
        colours.put("mustard", TextColor.color(0xFFDB58));
        colours.put("lime", TextColor.color(0xBFFF00));
        colours.put("chartreuse", TextColor.color(0x7FFF00));
        colours.put("olive", TextColor.color(0x808000));
        colours.put("mint", TextColor.color(0x98FF98));
        colours.put("mint_green", TextColor.color(0xAAF0D1));
        colours.put("seafoam", TextColor.color(0x93E9BE));
        colours.put("sage", TextColor.color(0x9CAF88));
        colours.put("pastel_green", TextColor.color(0xC1E1C1));
        colours.put("emerald", TextColor.color(0x50C878));
        colours.put("jade", TextColor.color(0x00A86B));
        colours.put("forest_green", TextColor.color(0x228B22));
        colours.put("spring_green", TextColor.color(0x00FF7F));
        colours.put("teal", TextColor.color(0x008080));
        colours.put("turquoise", TextColor.color(0x40E0D0));
        colours.put("aquamarine", TextColor.color(0x7FFFD4));
        colours.put("cyan", TextColor.color(0x00FFFF));
        colours.put("ice_blue", TextColor.color(0xD7F3F5));
        colours.put("sky_blue", TextColor.color(0x87CEEB));
        colours.put("baby_blue", TextColor.color(0x89CFF0));
        colours.put("powder_blue", TextColor.color(0xB0E0E6));
        colours.put("steel_blue", TextColor.color(0x4682B4));
        colours.put("royal_blue", TextColor.color(0x4169E1));
        colours.put("dodger_blue", TextColor.color(0x1E90FF));
        colours.put("navy", TextColor.color(0x000080));
        colours.put("midnight_blue", TextColor.color(0x191970));
        colours.put("indigo", TextColor.color(0x4B0082));
        colours.put("periwinkle", TextColor.color(0xCCCCFF));
        colours.put("lavender", TextColor.color(0xE6E6FA));
        colours.put("lilac", TextColor.color(0xC8A2C8));
        colours.put("violet", TextColor.color(0x8F00FF));
        colours.put("purple", TextColor.color(0x800080));
        colours.put("plum", TextColor.color(0xDDA0DD));
        colours.put("orchid", TextColor.color(0xDA70D6));
        colours.put("magenta", TextColor.color(0xFF00FF));
        colours.put("fuchsia", TextColor.color(0xFF00FF));
        colours.put("grape", TextColor.color(0x6F2DA8));
        colours.put("burgundy", TextColor.color(0x800020));
        colours.put("maroon", TextColor.color(0x800000));
        colours.put("crimson", TextColor.color(0xDC143C));
        colours.put("ruby", TextColor.color(0xE0115F));
        colours.put("scarlet", TextColor.color(0xFF2400));
        colours.put("brick_red", TextColor.color(0xCB4154));
        colours.put("chocolate", TextColor.color(0x7B3F00));
        colours.put("coffee", TextColor.color(0x6F4E37));
        colours.put("caramel", TextColor.color(0xC68E17));
        colours.put("tan", TextColor.color(0xD2B48C));
        colours.put("beige", TextColor.color(0xF5F5DC));
        colours.put("cream", TextColor.color(0xFFFDD0));
        colours.put("ivory", TextColor.color(0xFFFFF0));
        colours.put("sand", TextColor.color(0xC2B280));
        colours.put("taupe", TextColor.color(0x483C32));
        colours.put("slate", TextColor.color(0x708090));
        colours.put("charcoal", TextColor.color(0x36454F));
        colours.put("graphite", TextColor.color(0x383838));
        colours.put("silver", TextColor.color(0xC0C0C0));
        colours.put("platinum", TextColor.color(0xE5E4E2));
        colours.put("rose_gold", TextColor.color(0xB76E79));
        colours.put("copper", TextColor.color(0xB87333));
        colours.put("bronze", TextColor.color(0xCD7F32));
        return Collections.unmodifiableMap(colours);
    }

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

        if (data.getConfigurationSection("combat-tags") != null) {
            long now = System.currentTimeMillis();
            for (String id : data.getConfigurationSection("combat-tags").getKeys(false)) {
                try {
                    UUID playerId = UUID.fromString(id);
                    long taggedAt = data.getLong("combat-tags." + id, 0L);
                    if (taggedAt > 0L && now >= taggedAt && now - taggedAt < PVP_AFK_COOLDOWN_MILLIS) {
                        combatTags.put(playerId, taggedAt);
                    } else {
                        data.set("combat-tags." + id, null);
                    }
                } catch (IllegalArgumentException ignored) {
                    data.set("combat-tags." + id, null);
                    getLogger().warning("Ignoring invalid combat tag UUID: " + id);
                }
            }
        }

        if (data.getConfigurationSection("chat-colours") != null) {
            for (String id : data.getConfigurationSection("chat-colours").getKeys(false)) {
                try {
                    String name = data.getString("chat-colours." + id, "").toLowerCase(Locale.ROOT);
                    TextColor colour = CHAT_COLOURS.get(name);
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
            handleAppCommand(sender, args);
            return true;
        });

        Objects.requireNonNull(getCommand("afk")).setExecutor((sender, command, label, args) -> {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("/afk can only be used by a player.");
                return true;
            }
            UUID id = player.getUniqueId();
            if (afk.contains(id)) {
                afk.remove(id);
                afkOrigins.remove(id);
                player.sendMessage(Component.text("you've left AFK mode", NamedTextColor.RED));
            } else {
                long lastCombat = combatTags.getOrDefault(id, 0L);
                long elapsed = System.currentTimeMillis() - lastCombat;
                if (lastCombat > 0L && elapsed < PVP_AFK_COOLDOWN_MILLIS) {
                    long secondsRemaining = (PVP_AFK_COOLDOWN_MILLIS - elapsed + 999L) / 1000L;
                    player.sendMessage(Component.text(
                            "You cannot enter AFK mode for " + secondsRemaining + "s after PvP.",
                            NamedTextColor.RED
                    ));
                    return true;
                }
                afk.add(id);
                afkOrigins.put(id, player.getLocation().clone());
                // Clear any mobs that had already selected this player before AFK was enabled.
                for (org.bukkit.entity.Entity nearby : player.getNearbyEntities(64, 64, 64)) {
                    if (nearby instanceof org.bukkit.entity.Mob mob && player.equals(mob.getTarget())) {
                        mob.setTarget(null);
                    }
                }
                player.sendMessage(Component.text("you're AFK!", NamedTextColor.YELLOW));
            }
            return true;
        });

        Objects.requireNonNull(getCommand("colour")).setExecutor((sender, command, label, args) -> {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Only players can change their chat-name colour.");
                return true;
            }
            if (args.length == 0) {
                player.sendMessage("Available chat-name colours (RGB palette):");
                for (int start = 0; start < CHAT_COLOUR_NAMES.size(); start += 20) {
                    int end = Math.min(start + 20, CHAT_COLOUR_NAMES.size());
                    player.sendMessage(String.join(", ", CHAT_COLOUR_NAMES.subList(start, end)));
                }
                player.sendMessage("Usage: /colour <colour> (also /color, /colours, /colors)");
                return true;
            }
            if (args.length != 1) {
                player.sendMessage("Usage: /colour <colour>");
                return true;
            }

            String colourName = args[0].toLowerCase(Locale.ROOT);
            TextColor colour = CHAT_COLOURS.get(colourName);
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
            if (args.length == 0) {
                player.sendMessage("Usage: /send <your real first name>");
                return true;
            }

            String realName = String.join(" ", args).trim();
            if (!realName.matches("[A-Za-z][A-Za-z' -]{0,29}") || realName.endsWith(" ") || realName.contains("  ")) {
                player.sendMessage("Enter a valid first name (letters, spaces, apostrophes or hyphens; max 30 characters).");
                return true;
            }

            int limit = getDailyLimit(id);
            int used = getDailyRequestsUsed(id);
            if (used >= limit) {
                player.sendActionBar(limitReachedActionBar(id));
                player.sendMessage(Component.text(
                        "You've reached your daily application request limit. Come back tomorrow or ask the elites to raise it.",
                        NamedTextColor.RED
                ));
                return true;
            }

            recordApplicationRequest(id);
            String key = "pending." + id;
            data.set(key + ".player", player.getName());
            data.set(key + ".firstName", realName);
            long submittedAt = System.currentTimeMillis();
            boolean bedrockPlayer = isBedrock(player);
            data.set(key + ".submitted", submittedAt);
            data.set(key + ".bedrock", bedrockPlayer);
            saveData();

            sendApplicationNotification(player, realName, submittedAt, bedrockPlayer);

            stages.put(id, "name");
            startNamePrompt(player);
            player.showTitle(Title.title(
                    Component.text("just gotta wait...", NamedTextColor.YELLOW, TextDecoration.BOLD),
                    Component.empty(),
                    Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(5), Duration.ofMillis(500))
            ));
            player.sendMessage(Component.text(
                    "now just wait until the elites verify you're not a chinese spy...",
                    NamedTextColor.YELLOW
            ));
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

    private void showRejectedTitle(Player player, String reason) {
        player.showTitle(Title.title(
                Component.text("REJECTED", NamedTextColor.RED, TextDecoration.BOLD),
                Component.text("Reason: " + reason, NamedTextColor.WHITE),
                Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(8), Duration.ofMillis(500))
        ));
    }

    @Override
    public void onDisable() {
        if (data == null) return;
        for (Map.Entry<UUID, Long> entry : combatTags.entrySet()) {
            data.set("combat-tags." + entry.getKey(), entry.getValue());
        }
        saveData();
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

    private void clearMobTargets(Player player) {
        for (org.bukkit.entity.Entity nearby : player.getNearbyEntities(64, 64, 64)) {
            if (nearby instanceof org.bukkit.entity.Mob mob && player.equals(mob.getTarget())) {
                mob.setTarget(null);
            }
        }
    }

    private void begin(Player player) {
        UUID id = player.getUniqueId();
        String key = "pending." + id;

        // Keep a marked snapshot for this pending session. A legacy survival snapshot
        // is retained across upgrades; stale, unmarked Adventure snapshots are replaced.
        boolean activeSnapshot = data.getBoolean(key + ".active", false);
        boolean legacySnapshot = !data.contains(key + ".active") && data.contains(key + ".saved");
        if (!activeSnapshot && !legacySnapshot) {
            data.set(key + ".saved", player.getInventory().getContents());
            data.set(key + ".armor", player.getInventory().getArmorContents());
            data.set(key + ".offhand", player.getInventory().getItemInOffHand());
            data.set(key + ".gamemode", player.getGameMode().name());
            data.set(key + ".health", player.getHealth());
            data.set(key + ".food", player.getFoodLevel());
            data.set(key + ".saturation", player.getSaturation());
            data.set(key + ".exhaustion", player.getExhaustion());
            data.set(key + ".level", player.getLevel());
            data.set(key + ".exp", player.getExp());
            data.set(key + ".total-experience", player.getTotalExperience());
            data.set(key + ".fire-ticks", player.getFireTicks());
            data.set(key + ".max-air", player.getMaximumAir());
            data.set(key + ".allow-flight", player.getAllowFlight());
            data.set(key + ".flying", player.isFlying());
            List<Map<String, Object>> effects = player.getActivePotionEffects().stream().map(effect -> {
                Map<String, Object> savedEffect = new HashMap<>();
                savedEffect.put("type", effect.getType().getKey().toString());
                savedEffect.put("duration", effect.getDuration());
                savedEffect.put("amplifier", effect.getAmplifier());
                savedEffect.put("ambient", effect.isAmbient());
                savedEffect.put("particles", effect.hasParticles());
                savedEffect.put("icon", effect.hasIcon());
                return savedEffect;
            }).toList();
            data.set(key + ".effects", effects);
        }
        data.set(key + ".active", true);
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
        for (org.bukkit.potion.PotionEffect effect : new ArrayList<>(player.getActivePotionEffects())) {
            player.removePotionEffect(effect.getType());
        }
        clearMobTargets(player);
        player.showTitle(Title.title(
                Component.text("READ CHAT!", NamedTextColor.GOLD, TextDecoration.BOLD),
                Component.empty(),
                Title.Times.times(Duration.ofMillis(150), Duration.ofSeconds(5), Duration.ofMillis(400))
        ));

        String submittedName = data.getString(key + ".firstName", "");
        stages.put(id, "name");
        if (submittedName == null || submittedName.isBlank()) {
            showNamePrompt(player);
        } else {
            player.sendMessage("Your application is pending staff approval. You can keep exploring.");
        }
        startNamePrompt(player);
    }

    private void showNamePrompt(Player player) {
        player.sendMessage(Component.text("You need approval from the elites", NamedTextColor.GOLD));
        player.sendMessage(Component.empty());
        player.sendMessage(Component.text(
                "Just type in the chat /send (your name) so the elites can decide if you should play or not",
                NamedTextColor.WHITE
        ));
        player.sendMessage(Component.empty());
        player.sendMessage(Component.text(
                "Type in your real name - we can't tell who it is that wants to join otherwise",
                NamedTextColor.GREEN
        ));
        player.sendMessage(Component.empty());
        player.sendMessage(Component.text("Don't do silly stuff....", NamedTextColor.RED));
    }

    private void startNamePrompt(Player player) {
        UUID id = player.getUniqueId();
        stopNamePrompt(id);
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(this, () -> {
            Player online = Bukkit.getPlayer(id);
            if (online == null || !online.isOnline() || !pending(online)) {
                stopNamePrompt(id);
                return;
            }
            if (!timeouts.containsKey(id)) {
                int limit = getDailyLimit(id);
                int used = getDailyRequestsUsed(id);
                online.sendActionBar(used >= limit
                        ? limitReachedActionBar(id)
                        : Component.text("Use /send (your name)", NamedTextColor.YELLOW));
            }
        }, 0L, 100L);
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

    private TextColor ensureChatColour(Player player) {
        UUID id = player.getUniqueId();
        TextColor existing = chatColours.get(id);
        if (existing != null) return existing;

        String name = CHAT_COLOUR_NAMES.get(new Random().nextInt(CHAT_COLOUR_NAMES.size()));
        TextColor selected = CHAT_COLOURS.get(name);
        chatColours.put(id, selected);
        data.set("chat-colours." + id, name);
        saveData();
        return selected;
    }

    @EventHandler
    public void chatNameColour(AsyncChatEvent event) {
        TextColor colour = chatColours.getOrDefault(event.getPlayer().getUniqueId(), NamedTextColor.WHITE);
        event.renderer((source, sourceDisplayName, message, viewer) ->
                Component.text(source.getName(), colour)
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
                if (approved.contains(player.getUniqueId())) {
                    restorePlayerState(player);
                } else if (bypass(player)) {
                    // Bypass players are not restricted; ignore old, inactive snapshots.
                    if (data.getBoolean("pending." + player.getUniqueId() + ".active", false)) {
                        restorePlayerState(player);
                    }
                } else {
                    // Every unapproved player uses the application lobby, not just first-time joiners.
                    begin(player);
                }
                long timeoutUntil = data.getLong("timeouts." + player.getUniqueId() + ".until", 0L);
                if (timeoutUntil > System.currentTimeMillis()) {
                    activateTimeout(player, timeoutUntil, true);
                } else if (timeoutUntil > 0L) {
                    UUID playerId = player.getUniqueId();
                    timeouts.remove(playerId);
                    afk.remove(playerId);
                    afkOrigins.remove(playerId);
                    timeoutAddedAfk.remove(playerId);
                    data.set("timeouts." + playerId, null);
                    saveData();
                }
            }
        }.runTaskLater(this, 2L);
    }

    @EventHandler
    public void afkMovement(org.bukkit.event.player.PlayerMoveEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        if (!afk.contains(id) || timeouts.containsKey(id) || event.getTo() == null) return;
        org.bukkit.Location origin = afkOrigins.get(id);
        if (origin == null) {
            afkOrigins.put(id, event.getFrom().clone());
            return;
        }
        // Two blocks of displacement in any direction automatically ends AFK mode.
        if (event.getTo().distanceSquared(origin) >= 4.0) {
            afk.remove(id);
            afkOrigins.remove(id);
            event.getPlayer().sendMessage(Component.text("you've left AFK mode", NamedTextColor.RED));
        }
    }

    @EventHandler
    public void quit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        afkOrigins.remove(id);
        afk.remove(id);
        timeoutAddedAfk.remove(id);
        BukkitTask timeoutTask = timeoutTasks.remove(id);
        if (timeoutTask != null) timeoutTask.cancel();
        stages.remove(id);
        stopNamePrompt(id);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void damage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && pending(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void pendingPotionEffects(EntityPotionEffectEvent event) {
        if (!(event.getEntity() instanceof Player player) || !pending(player)) return;
        if (event.getAction() == EntityPotionEffectEvent.Action.ADDED
                || event.getAction() == EntityPotionEffectEvent.Action.CHANGED) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void afkMobTarget(EntityTargetLivingEntityEvent event) {
        if (!(event.getEntity() instanceof Player)
                && event.getTarget() instanceof Player player
                && (pending(player) || afk.contains(player.getUniqueId()))) {
            event.setCancelled(true);
        }
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

        // AFK players cannot be damaged by any source, including other players.
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void afkCannotAttack(EntityDamageByEntityEvent event) {
        Player attacker = getPlayerAttacker(event);
        if (attacker != null && afk.contains(attacker.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void afkCannotLaunchProjectiles(ProjectileLaunchEvent event) {
        if (event.getEntity().getShooter() instanceof Player shooter
                && (afk.contains(shooter.getUniqueId()) || pending(shooter))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void trackPvpCombat(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        Player attacker = getPlayerAttacker(event);
        if (attacker == null || attacker.getUniqueId().equals(victim.getUniqueId())) return;
        if (afk.contains(attacker.getUniqueId()) || afk.contains(victim.getUniqueId())) return;

        long now = System.currentTimeMillis();
        combatTags.put(attacker.getUniqueId(), now);
        combatTags.put(victim.getUniqueId(), now);
        combatTags.entrySet().removeIf(entry -> now - entry.getValue() >= PVP_AFK_COOLDOWN_MILLIS);
    }

    @EventHandler
    public void afkHunger(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player player && afk.contains(player.getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void timeoutMovement(PlayerMoveEvent event) {
        if (!timeouts.containsKey(event.getPlayer().getUniqueId()) || event.getTo() == null) return;
        Location from = event.getFrom();
        Location to = event.getTo();
        if (!Objects.equals(from.getWorld(), to.getWorld())
                || from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ()) {
            event.setTo(new Location(from.getWorld(), from.getX(), from.getY(), from.getZ(),
                    to.getYaw(), to.getPitch()));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void timeoutChat(AsyncChatEvent event) {
        if (timeouts.containsKey(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void pendingTeleport(PlayerTeleportEvent event) {
        if (timeouts.containsKey(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
            return;
        }
        if (pending(event.getPlayer())) {
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

    @EventHandler(priority = EventPriority.HIGHEST)
    public void combat(EntityDamageByEntityEvent event) {
        Player attacker = getPlayerAttacker(event);
        if ((attacker != null && pending(attacker))
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
        if (pending(event.getPlayer()) || timeouts.containsKey(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void pickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player
                && (pending(player) || timeouts.containsKey(player.getUniqueId()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void command(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (timeouts.containsKey(player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }
        if (!pending(player)) {
            return;
        }
        String label = event.getMessage().split(" ", 2)[0].toLowerCase(Locale.ROOT);
        if (!Set.of("/send", "/colour", "/color", "/colours", "/colors").contains(label)) {
            event.setCancelled(true);
        }
    }

    private ItemStack[] readItemStackArray(String path, int expectedSize) {
        Object raw = data.get(path);
        if (raw instanceof ItemStack[] items) {
            return Arrays.copyOf(items, items.length);
        }
        if (raw instanceof List<?> list) {
            if (list.size() > expectedSize) return null;
            ItemStack[] items = new ItemStack[expectedSize];
            for (int i = 0; i < list.size(); i++) {
                Object value = list.get(i);
                if (value != null && !(value instanceof ItemStack)) return null;
                items[i] = (ItemStack) value;
            }
            return items;
        }
        return null;
    }

    private void restorePlayerState(Player player) {
        UUID id = player.getUniqueId();
        String key = "pending." + id;
        boolean activeSnapshot = data.getBoolean(key + ".active", false);
        boolean legacySnapshot = !data.contains(key + ".active") && data.contains(key + ".saved");

        // An operator/bypass player may have an old backup from earlier testing.
        // It must never overwrite the operator's current inventory unless this plugin
        // explicitly marked a real pending session as active.
        if (bypass(player) && !activeSnapshot) {
            stages.remove(id);
            stopNamePrompt(id);
            return;
        }

        if (data.contains(key + ".saved") && (activeSnapshot || legacySnapshot)) {
            ItemStack[] contents = readItemStackArray(key + ".saved", player.getInventory().getSize());
            ItemStack[] armor = readItemStackArray(key + ".armor", 4);
            boolean restored = contents != null;
            if (contents != null) {
                player.getInventory().setContents(contents);
            } else {
                getLogger().severe("Could not restore saved inventory for " + player.getName()
                        + ": saved inventory data has an unsupported format. Preserving backup in applications.yml.");
            }
            if (armor != null) {
                player.getInventory().setArmorContents(armor);
            } else if (data.contains(key + ".armor")) {
                restored = false;
                getLogger().severe("Could not restore saved armour for " + player.getName()
                        + ": saved armour data has an unsupported format. Preserving backup in applications.yml.");
            }
            player.getInventory().setItemInOffHand(data.getItemStack(key + ".offhand"));

            // Restore effects first because some effects change the maximum-health attribute.
            for (Map<?, ?> rawEffect : data.getMapList(key + ".effects")) {
                try {
                    Object keyValue = rawEffect.get("type");
                    org.bukkit.NamespacedKey effectKey = keyValue == null
                            ? null : org.bukkit.NamespacedKey.fromString(keyValue.toString());
                    org.bukkit.potion.PotionEffectType effectType = effectKey == null
                            ? null : org.bukkit.Registry.POTION_EFFECT_TYPE.get(effectKey);
                    if (effectType == null) continue;
                    Object durationValue = rawEffect.get("duration");
                    Object amplifierValue = rawEffect.get("amplifier");
                    int duration = durationValue instanceof Number number ? number.intValue() : 0;
                    int amplifier = amplifierValue instanceof Number number ? number.intValue() : 0;
                    boolean ambient = Boolean.TRUE.equals(rawEffect.get("ambient"));
                    boolean particles = !Boolean.FALSE.equals(rawEffect.get("particles"));
                    boolean icon = !Boolean.FALSE.equals(rawEffect.get("icon"));
                    player.addPotionEffect(new org.bukkit.potion.PotionEffect(
                            effectType, duration, amplifier, ambient, particles, icon));
                } catch (RuntimeException exception) {
                    getLogger().warning("Skipping invalid saved potion effect for " + player.getName() + ".");
                }
            }

            String modeName = data.getString(key + ".gamemode", "SURVIVAL");
            try {
                GameMode mode = GameMode.valueOf(modeName);
                // Older builds could capture Adventure after restrictions were applied.
                if (mode == GameMode.ADVENTURE) mode = GameMode.SURVIVAL;
                player.setGameMode(mode);
            } catch (IllegalArgumentException ignored) {
                player.setGameMode(GameMode.SURVIVAL);
            }

            if (data.contains(key + ".food")) player.setFoodLevel(Math.max(0, Math.min(20, data.getInt(key + ".food"))));
            if (data.contains(key + ".saturation")) player.setSaturation(Math.max(0.0f, (float) data.getDouble(key + ".saturation")));
            if (data.contains(key + ".exhaustion")) player.setExhaustion(Math.max(0.0f, (float) data.getDouble(key + ".exhaustion")));
            if (data.contains(key + ".level")) player.setLevel(Math.max(0, data.getInt(key + ".level")));
            if (data.contains(key + ".exp")) player.setExp(Math.max(0.0f, Math.min(1.0f, (float) data.getDouble(key + ".exp"))));
            if (data.contains(key + ".total-experience")) player.setTotalExperience(Math.max(0, data.getInt(key + ".total-experience")));
            if (data.contains(key + ".fire-ticks")) player.setFireTicks(Math.max(0, data.getInt(key + ".fire-ticks")));
            if (data.contains(key + ".max-air")) player.setMaximumAir(Math.max(1, data.getInt(key + ".max-air")));
            if (data.contains(key + ".health")) {
                double health = data.getDouble(key + ".health", player.getHealth());
                player.setHealth(Math.max(0.1, Math.min(player.getMaxHealth(), health)));
            }
            boolean allowFlight = data.getBoolean(key + ".allow-flight", player.getAllowFlight());
            player.setAllowFlight(allowFlight);
            if (allowFlight) player.setFlying(data.getBoolean(key + ".flying", false));

            if (restored) {
                data.set(key, null);
                saveData();
                getLogger().info("Restored inventory and player stats for " + player.getName() + ".");
            } else {
                getLogger().severe("Preserved the pending-player backup for " + player.getName()
                        + " because the inventory could not be fully decoded.");
            }
        }

        stages.remove(id);
        stopNamePrompt(id);
    }

    private void release(Player player) {
        restorePlayerState(player);
        player.sendMessage(Component.text("you're allowed to play!", NamedTextColor.GREEN));
        player.showTitle(Title.title(
                Component.text("enjoy", NamedTextColor.GREEN, TextDecoration.BOLD),
                Component.empty(),
                Title.Times.times(Duration.ofMillis(250), Duration.ofSeconds(5), Duration.ofMillis(500))
        ));
    }

    private String todayKey() {
        return LocalDate.now(ZoneId.systemDefault()).toString();
    }

    private int getDailyLimit(UUID id) {
        return Math.max(0, data.getInt("application-limits." + id + ".daily-limit", 3));
    }

    private int getDailyRequestsUsed(UUID id) {
        String base = "application-limits." + id;
        if (!todayKey().equals(data.getString(base + ".date", ""))) return 0;
        return Math.max(0, data.getInt(base + ".count", 0));
    }

    private void recordApplicationRequest(UUID id) {
        String base = "application-limits." + id;
        int used = getDailyRequestsUsed(id);
        data.set(base + ".date", todayKey());
        data.set(base + ".count", used + 1);
        saveData();
    }

    private Component limitReachedActionBar(UUID id) {
        int limit = getDailyLimit(id);
        return Component.text("limit of " + limit + " /send requests reached. ", NamedTextColor.RED)
                .append(Component.text(
                        "Come back tomorrow or let the elites know so they can omit the limit",
                        NamedTextColor.YELLOW
                ));
    }

    private void handleAppCommand(org.bukkit.command.CommandSender sender, String[] args) {
        if (!sender.hasPermission("argonapplications.admin")) {
            sender.sendMessage("No permission.");
            return;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("list")) {
            sender.sendMessage("Pending: " + (data.getConfigurationSection("pending") == null
                    ? "[]" : data.getConfigurationSection("pending").getKeys(false)));
            return;
        }

        String action = args[0].toLowerCase(Locale.ROOT);
        if (action.equals("bc") || action.equals("broadcast")) {
            if (args.length < 2) {
                sender.sendMessage("Usage: /app bc <text> (alias: /app broadcast <text>)");
                return;
            }
            String message = String.join(" ", Arrays.copyOfRange(args, 1, args.length)).trim();
            if (message.isBlank()) {
                sender.sendMessage("Broadcast text cannot be empty.");
                return;
            }
            if (message.length() > 256) {
                sender.sendMessage("Broadcast text must be 256 characters or fewer.");
                return;
            }

            Component title = Component.text(message, NamedTextColor.YELLOW, TextDecoration.BOLD);
            int recipients = 0;
            for (Player online : Bukkit.getOnlinePlayers()) {
                online.showTitle(Title.title(
                        title,
                        Component.empty(),
                        Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(5), Duration.ofMillis(700))
                ));
                online.playSound(online.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1.0f, 1.35f);
                recipients++;
            }
            sender.sendMessage(Component.text("Broadcast sent to " + recipients + " online players.", NamedTextColor.YELLOW));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage("Usage: /app approve|remove|reject|decline|resetlimit|addlimit|timeout <player> [value]");
            return;
        }

        Player onlineTarget = Bukkit.getPlayerExact(args[1]);
        OfflinePlayer target = onlineTarget != null ? onlineTarget : Bukkit.getOfflinePlayer(args[1]);
        UUID id = onlineTarget != null ? onlineTarget.getUniqueId() : target.getUniqueId();
        String displayName = onlineTarget != null ? onlineTarget.getName()
                : (target.getName() == null ? args[1] : target.getName());

        if (action.equals("resetlimit")) {
            String base = "application-limits." + id;
            data.set(base + ".date", todayKey());
            data.set(base + ".count", 0);
            saveData();
            sender.sendMessage("Reset today's /send usage for " + displayName + ". They can use up to "
                    + getDailyLimit(id) + " requests today.");
            Player online = Bukkit.getPlayer(id);
            if (online != null && pending(online)) startNamePrompt(online);
            return;
        }

        if (action.equals("addlimit")) {
            if (args.length != 3) {
                sender.sendMessage("Usage: /app addlimit <player> <daily request limit>");
                return;
            }
            int newLimit;
            try {
                newLimit = Integer.parseInt(args[2]);
            } catch (NumberFormatException exception) {
                sender.sendMessage("The daily limit must be a whole number greater than zero.");
                return;
            }
            if (newLimit < 1 || newLimit > 100000) {
                sender.sendMessage("The daily limit must be between 1 and 100000.");
                return;
            }
            data.set("application-limits." + id + ".daily-limit", newLimit);
            saveData();
            sender.sendMessage("Set " + displayName + "'s daily /send limit to " + newLimit + ".");
            Player online = Bukkit.getPlayer(id);
            if (online != null && pending(online)) startNamePrompt(online);
            return;
        }

        if (action.equals("timeout")) {
            if (args.length != 3) {
                sender.sendMessage("Usage: /app timeout <player> <seconds>");
                return;
            }
            long seconds;
            try {
                seconds = Long.parseLong(args[2]);
            } catch (NumberFormatException exception) {
                sender.sendMessage("Timeout must be a whole number of seconds greater than zero.");
                return;
            }
            if (seconds < 1 || seconds > 2147483L) {
                sender.sendMessage("Timeout must be between 1 second and 24 days.");
                return;
            }
            long until = System.currentTimeMillis() + seconds * 1000L;
            data.set("timeouts." + id + ".until", until);
            saveData();
            Player online = Bukkit.getPlayer(id);
            if (online != null) activateTimeout(online, until, true);
            sender.sendMessage("Timed out " + displayName + " for " + formatDuration(seconds * 1000L) + ".");
            return;
        }

        if (action.equals("decline") && args.length < 3) {
            sender.sendMessage("Usage: /app decline <player> <reason>");
            return;
        }

        if (action.equals("approve") || action.equals("whitelist")) {
            approved.add(id);
            data.set("declined." + id, null);
            saveData();
            Player online = Bukkit.getPlayer(id);
            if (online != null) release(online);
            sender.sendMessage("Added " + displayName + " to the Argon player whitelist.");
        } else if (action.equals("remove")) {
            approved.remove(id);
            data.set("declined." + id, null);
            data.set("pending." + id, null);
            saveData();
            Player online = Bukkit.getPlayer(id);
            if (online != null) begin(online);
            sender.sendMessage("Removed " + displayName + " from the Argon whitelist. They can apply again.");
        } else if (action.equals("decline")) {
            String reason = String.join(" ", Arrays.copyOfRange(args, 2, args.length)).trim();
            if (reason.isBlank()) {
                sender.sendMessage("Usage: /app decline <player> <reason>");
                return;
            }
            if (reason.length() > 220) {
                sender.sendMessage("The decline reason must be 220 characters or fewer.");
                return;
            }
            data.set("declined." + id + ".reason", reason);
            data.set("declined." + id + ".player", displayName);
            data.set("declined." + id + ".declinedAt", System.currentTimeMillis());
            data.set("pending." + id + ".firstName", null);
            data.set("pending." + id + ".submitted", null);
            saveData();
            Player online = Bukkit.getPlayer(id);
            if (online != null) {
                stages.put(id, "name");
                showRejectedTitle(online, reason);
                online.sendMessage(Component.text(
                        "You can submit another application with /send (your name), subject to today's limit.",
                        NamedTextColor.YELLOW
                ));
                startNamePrompt(online);
            }
            sender.sendMessage("Declined " + displayName + "'s application. They may reapply within their daily limit.");
        } else if (action.equals("reject")) {
            data.set("pending." + id + ".firstName", null);
            data.set("pending." + id + ".submitted", null);
            saveData();
            Player online = Bukkit.getPlayer(id);
            if (online != null && pending(online)) startNamePrompt(online);
            sender.sendMessage("Cleared " + displayName + "'s application. They may apply again.");
        } else {
            sender.sendMessage("Usage: /app approve|remove|reject|decline|resetlimit|addlimit|timeout <player> [value]");
        }
    }

    private void activateTimeout(Player player, long until, boolean showTitle) {
        UUID id = player.getUniqueId();
        if (until <= System.currentTimeMillis()) {
            finishTimeout(player);
            return;
        }
        if (afk.add(id)) timeoutAddedAfk.add(id);
        afkOrigins.putIfAbsent(id, player.getLocation().clone());
        timeouts.put(id, until);
        clearMobTargets(player);
        player.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
        stopNamePrompt(id);
        if (showTitle) {
            player.showTitle(Title.title(
                    Component.text("timeout bro", NamedTextColor.RED, TextDecoration.BOLD),
                    Component.empty(),
                    Title.Times.times(Duration.ofMillis(100), Duration.ofSeconds(4), Duration.ofMillis(300))
            ));
        }
        player.sendActionBar(Component.text("timeout for: " + formatDuration(until - System.currentTimeMillis()),
                NamedTextColor.YELLOW));

        BukkitTask oldTask = timeoutTasks.remove(id);
        if (oldTask != null) oldTask.cancel();
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(this, () -> {
            Player online = Bukkit.getPlayer(id);
            Long expiry = timeouts.get(id);
            if (online == null || expiry == null) {
                BukkitTask current = timeoutTasks.remove(id);
                if (current != null) current.cancel();
                return;
            }
            long remaining = expiry - System.currentTimeMillis();
            if (remaining <= 0L) {
                finishTimeout(online);
            } else {
                online.sendActionBar(Component.text("timeout for: " + formatDuration(remaining),
                        NamedTextColor.YELLOW));
            }
        }, 20L, 20L);
        timeoutTasks.put(id, task);
    }

    private void finishTimeout(Player player) {
        UUID id = player.getUniqueId();
        timeouts.remove(id);
        data.set("timeouts." + id, null);
        saveData();
        BukkitTask task = timeoutTasks.remove(id);
        if (task != null) task.cancel();
        if (timeoutAddedAfk.remove(id)) {
            afk.remove(id);
            afkOrigins.remove(id);
        }
        player.sendActionBar(Component.empty());
        if (pending(player)) startNamePrompt(player);
        player.sendMessage(Component.text("Your timeout has ended.", NamedTextColor.YELLOW));
    }

    private String formatDuration(long millis) {
        long totalSeconds = Math.max(0L, (millis + 999L) / 1000L);
        long hours = totalSeconds / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;
        return String.format(Locale.ROOT, "%02d:%02d:%02d", hours, minutes, seconds);
    }
}
