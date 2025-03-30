    package com.mishanstvo.plantwatch;

    import org.bukkit.Bukkit;
    import org.bukkit.Material;
    import org.bukkit.block.Block;
    import org.bukkit.block.data.Ageable;
    import org.bukkit.configuration.file.FileConfiguration;
    import org.bukkit.configuration.file.YamlConfiguration;
    import org.bukkit.event.EventHandler;
    import org.bukkit.event.Listener;
    import org.bukkit.event.block.BlockBreakEvent;
    import org.bukkit.event.block.BlockPlaceEvent;
    import org.bukkit.plugin.java.JavaPlugin;
    import org.bukkit.entity.Player;
    import org.bukkit.Chunk;
    import org.bukkit.World;
    import org.bukkit.command.Command;
    import org.bukkit.command.CommandSender;
    import org.bukkit.command.ConsoleCommandSender;
    import org.bukkit.command.CommandExecutor;
    import java.io.File;
    import java.io.FileReader;
    import java.io.FileWriter;
    import java.io.IOException;
    import java.sql.Connection;
    import java.sql.DriverManager;
    import java.sql.PreparedStatement;
    import java.sql.ResultSet;
    import java.sql.SQLException;
    import java.util.List;
    import java.util.Random;
    import java.util.logging.Level;
    import java.util.ArrayList;
    import com.google.gson.Gson;
    import com.google.gson.JsonElement;
    import com.google.gson.JsonObject;
    import com.google.gson.JsonParser;
    import com.google.gson.GsonBuilder;
    import java.util.Map;
    import java.util.HashMap;
    import java.io.InputStream;
import java.io.OutputStream;
import java.io.FileOutputStream;
import java.net.URL;
import java.util.Enumeration;
    

    public class PlantWatchPlugin extends JavaPlugin implements Listener {
        private Connection connection;
        private File configFile;
        private FileConfiguration config;
        private int updateInterval;
        private boolean batchUpdate;
        private int maxUpdatesPerTick;
        private List<Material> trackedBlocks;  // Список отслеживаемых материалов
        private int playerRadiusCheck; // Добавляем переменную здесь
        private boolean paused = false; // Добавляем переменную для паузы
        private final Random random = new Random();
        //private int plantsBeingUpdated = 0; // Переменная для отслеживания обновляемых блоков
        private int currentlyUpdating = 0; // Переменная для отслеживания количества обновляемых растений
        private Map<String, String> translations = new HashMap<>();
        private String currentLanguage;


        @Override
public void onEnable() {
    writeLanguageFile();  // Вызываем функцию для записи файла
    loadConfig();
    loadLanguage(currentLanguage);  // Загружаем язык
    setupDatabase();
    startUpdateTask();
    removeUntrackedPlants();  // Новый метод для удаления растений, которые больше не отслеживаются
    Bukkit.getPluginManager().registerEvents(this, this);
    getLogger().info("PlantWatchPlugin enabled!");
}

        @Override
        public void onDisable() {
            closeDatabase();
            getLogger().info("PlantWatchPlugin disabled!");
        }

        private void loadConfig() {
    saveDefaultConfig();
    configFile = new File(getDataFolder(), "config.yml");
    config = YamlConfiguration.loadConfiguration(configFile);

    updateInterval = config.getInt("update-interval", 20);
    batchUpdate = config.getBoolean("batch-update", false);
    maxUpdatesPerTick = config.getInt("max-updates-per-tick", 10);
    playerRadiusCheck = config.getInt("player-radius-check", 160); // Читаем радиус
    currentLanguage = config.getString("lang", "en");  // По умолчанию английский
    // Загружаем отслеживаемые блоки из конфига
    trackedBlocks = new ArrayList<>();
    for (String blockName : config.getStringList("tracked-blocks")) {
        try {
            Material material = Material.getMaterial(blockName);
            if (material != null) {
                trackedBlocks.add(material);
            } else {
                getLogger().warning("Invalid block type in config: " + blockName);
            }
        } catch (IllegalArgumentException e) {
            getLogger().warning("Invalid block type in config: " + blockName);
        }
    }
}
public static void writeLanguageFile() {
        // Создаем объект JsonObject
        JsonObject languageContent = new JsonObject();
        languageContent.addProperty("plugin.enabled", "PlantWatch Plugin enabled!");
        languageContent.addProperty("plugin.disabled", "PlantWatch Plugin disabled!");
        languageContent.addProperty("plugin.reloaded", "PlantWatch Plugin reloaded!");
        languageContent.addProperty("update.interval", "Update Interval: %d");
        languageContent.addProperty("command.usage", "Usage: /plantwatch <stats|pause|setinterval|reloadconfig>");
        languageContent.addProperty("stats.title", "PlantWatch Stats");
        languageContent.addProperty("batch.update", "Batch Update: %b");
        languageContent.addProperty("max.updates.per.tick", "Max Updates per Tick: %d");
        languageContent.addProperty("player.radius.check", "Player Radius Check: %d");
        languageContent.addProperty("currently.updating", "Currently Updating %d plants.");
        languageContent.addProperty("invalid.interval", "Invalid interval.");

        // Получаем путь к папке lang
        File langFolder = new File("plugins/PlantWatch/lang");
        if (!langFolder.exists()) {
            langFolder.mkdirs();  // Создаем папку lang, если ее нет
        }

        // Создаем файл en.json
        File langFile = new File(langFolder, "en.json");
        if (langFile.exists()) {
            System.out.println("Language file already exists at " + langFile.getAbsolutePath());
            return;  // Прерываем выполнение, если файл уже есть
        }
        // Используем Gson для записи JSON в файл
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        try (FileWriter writer = new FileWriter(langFile)) {
            gson.toJson(languageContent, writer);
            System.out.println("Language file created successfully at " + langFile.getAbsolutePath());
        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("Failed to write language file.");
        }
    }

 // Метод для загрузки переводов
    private void loadLanguage(String languageCode) {
        File langFile = new File(getDataFolder(), "lang/" + languageCode + ".json");
        if (!langFile.exists()) {
            getLogger().warning("Language file for " + languageCode + " not found. Falling back to English.");
            languageCode = "en"; // Если файл не найден, используем английский
            langFile = new File(getDataFolder(), "lang/en.json");
        }

        try (FileReader reader = new FileReader(langFile)) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
    translations.put(entry.getKey(), entry.getValue().getAsString());
}
        } catch (IOException e) {
            getLogger().log(Level.SEVERE, "Failed to load language file " + languageCode, e);
        }
    }
    // Метод для получения перевода
    public String getMessage(String key, Object... args) {
        String message = translations.getOrDefault(key, key); // Если перевод не найден, возвращаем ключ
        return String.format(message, args);
    }


        private void setupDatabase() {
            try {
                File dbFile = new File(getDataFolder(), "plants.db");
                connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
                try (PreparedStatement stmt = connection.prepareStatement(
                        "CREATE TABLE IF NOT EXISTS tracked_blocks (x INTEGER, y INTEGER, z INTEGER, world TEXT, type TEXT, player TEXT, PRIMARY KEY (x, y, z, world))")) {
                    stmt.execute();
                }
            } catch (SQLException e) {
                getLogger().log(Level.SEVERE, "Database connection failed!", e);
            }
        }

        private void closeDatabase() {
            if (connection != null) {
                try {
                    connection.close();
                } catch (SQLException e) {
                    getLogger().log(Level.SEVERE, "Failed to close database connection!", e);
                }
            }
        }

        private void startUpdateTask() {
            Bukkit.getScheduler().runTaskTimer(this, this::updatePlants, updateInterval, updateInterval);
        }

        private void updatePlants() {
        getLogger().info("Checking for plant growth...");
        try (PreparedStatement stmt = connection.prepareStatement("SELECT x, y, z, world, type FROM tracked_blocks")) {
            ResultSet rs = stmt.executeQuery();
            int count = 0;
            while (rs.next() && (!batchUpdate && count < maxUpdatesPerTick || batchUpdate)) {
                World world = Bukkit.getWorld(rs.getString("world"));
                if (world == null) continue;

                int x = rs.getInt("x");
                int y = rs.getInt("y");
                int z = rs.getInt("z");
                Block block = world.getBlockAt(x, y, z);

                // Проверяем игроков в заданном радиусе
                int radiusSquared = playerRadiusCheck * playerRadiusCheck;
                boolean hasPlayersNearby = world.getPlayers().stream()
                    .anyMatch(player -> player.getLocation().distanceSquared(block.getLocation()) <= radiusSquared);

                if (!hasPlayersNearby) { // Если в радиусе никого нет
                    if (!block.getChunk().isLoaded()) {
                        block.getChunk().load();
                    }

                    if (block.getBlockData() instanceof Ageable ageable) {
                        if (ageable.getAge() == ageable.getMaximumAge()) {
                            continue; // Если растение уже выросло — пропускаем
                        }

                    
                        int oldAge = ageable.getAge();
                        boolean isNight = world.getTime() > 12300 && world.getTime() < 23850;
                        int growthChance = isNight ? 8 : 4; // В 2 раза медленнее ночью
                        if (random.nextInt(growthChance) == 0) {
                            ageable.setAge(oldAge + 1);
                            block.setBlockData(ageable);
                            getLogger().info(String.format("Plant at (%d, %d, %d) in %s grew from %d to %d",
                                    x, y, z, world.getName(), oldAge, ageable.getAge()));
                        }

                    
                        count++;
                    }
                }
            }
            currentlyUpdating = count;
            getLogger().info("Updated " + count + " plants this cycle.");
        } catch (SQLException e) {
            getLogger().log(Level.SEVERE, "Failed to update plant growth!", e);
        }
    }


       @EventHandler
public void onBlockPlace(BlockPlaceEvent event) {
    Player player = event.getPlayer();
    Block block = event.getBlock();

    // Проверяем, отслеживается ли блок в конфиге
    if (trackedBlocks.contains(block.getType())) {
        // Проверяем, что блок не закомментирован в конфиге
        if (config.getStringList("tracked-blocks").contains(block.getType().name())) {
            try (PreparedStatement stmt = connection.prepareStatement(
                    "INSERT OR IGNORE INTO tracked_blocks (x, y, z, world, type, player) VALUES (?, ?, ?, ?, ?, ?)")) {
                stmt.setInt(1, block.getX());
                stmt.setInt(2, block.getY());
                stmt.setInt(3, block.getZ());
                stmt.setString(4, block.getWorld().getName());
                stmt.setString(5, block.getType().name());
                stmt.setString(6, player.getName());
                stmt.execute();
                getLogger().info("Added plant at " + block.getLocation() + " by " + player.getName());
            } catch (SQLException e) {
                getLogger().log(Level.SEVERE, "Failed to insert block into database!", e);
            }
        }
    }
}

@EventHandler
public void onBlockBreak(BlockBreakEvent event) {
    Block block = event.getBlock();
    
    // Проверяем, отслеживается ли блок в конфиге
    if (trackedBlocks.contains(block.getType())) {
        // Удаляем блок из базы данных, если он был разрушен
        try (PreparedStatement stmt = connection.prepareStatement(
                "DELETE FROM tracked_blocks WHERE x = ? AND y = ? AND z = ? AND world = ?")) {
            stmt.setInt(1, block.getX());
            stmt.setInt(2, block.getY());
            stmt.setInt(3, block.getZ());
            stmt.setString(4, block.getWorld().getName());
            stmt.execute();
            getLogger().info("Removed plant at " + block.getLocation());
        } catch (SQLException e) {
            getLogger().log(Level.SEVERE, "Failed to remove block from database!", e);
        }
    }
}
private void removeUntrackedPlants() {
    Bukkit.getScheduler().runTask(this, () -> {
        try {
            try (PreparedStatement stmt = connection.prepareStatement("SELECT x, y, z, world, type FROM tracked_blocks")) {
                ResultSet rs = stmt.executeQuery();
                while (rs.next()) {
                    String blockTypeName = rs.getString("type");
                    Material material = Material.getMaterial(blockTypeName);

                    if (material == null || !trackedBlocks.contains(material)) {
                        try (PreparedStatement deleteStmt = connection.prepareStatement(
                                "DELETE FROM tracked_blocks WHERE x = ? AND y = ? AND z = ? AND world = ?")) {
                            deleteStmt.setInt(1, rs.getInt("x"));
                            deleteStmt.setInt(2, rs.getInt("y"));
                            deleteStmt.setInt(3, rs.getInt("z"));
                            deleteStmt.setString(4, rs.getString("world"));
                            deleteStmt.execute();
                            getLogger().info("Removed untracked plant at " + rs.getInt("x") + ", " + rs.getInt("y") + ", " + rs.getInt("z") + " in world " + rs.getString("world"));
                        } catch (SQLException e) {
                            getLogger().log(Level.SEVERE, "Failed to remove untracked plant from database!", e);
                        }
                    }
                }
            }
        } catch (SQLException e) {
            getLogger().log(Level.SEVERE, "Failed to check and remove untracked plants from database!", e);
        }
    });
}

        // Команды плагина
        @Override
        public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
            if (!(sender instanceof Player) && !(sender instanceof ConsoleCommandSender)) {
                sender.sendMessage("This command can only be used by players or console.");
                return false;
            }

            if (args.length == 0) {
                sender.sendMessage("Usage: /plantwatch <stats|pause|setinterval|reloadconfig>");
                return false;
            }

            switch (args[0].toLowerCase()) {
                case "stats":
                    showStats(sender);
                    break;

                case "pause":
                    togglePause(sender);
                    break;

                case "setinterval":
                    if (args.length < 2) {
                        sender.sendMessage("Usage: /plantwatch setinterval <interval>");
                        return false;
                    }
                    try {
                        int newInterval = Integer.parseInt(args[1]);
                        setUpdateInterval(sender, newInterval);
                    } catch (NumberFormatException e) {
                        sender.sendMessage("Invalid interval. Please enter a valid number.");
                    }
                    break;

                case "reloadconfig":
                    reloadConfig(sender);
                    break;

                default:
                    sender.sendMessage("Unknown command. Usage: /plantwatch <stats|pause|setinterval|reloadconfig>");
                    break;
            }
            return true;
        }

        private void showStats(CommandSender sender) {
            sender.sendMessage(getMessage("stats.title"));
            sender.sendMessage(getMessage("update.interval", updateInterval));
            sender.sendMessage(getMessage("batch.update", batchUpdate));
            sender.sendMessage(getMessage("max.updates.per.tick", maxUpdatesPerTick));
            sender.sendMessage(getMessage("player.radius.check", playerRadiusCheck));
            sender.sendMessage(getMessage("currently.updating", currentlyUpdating));
        }

        private void togglePause(CommandSender sender) {
            paused = !paused;
            sender.sendMessage(paused ? "Updates are now paused." : "Updates have resumed.");
        }

        private void setUpdateInterval(CommandSender sender, int interval) {
            updateInterval = interval;
            config.set("update-interval", interval);
            saveConfig();
            sender.sendMessage("Update interval set to " + interval);
        }

        private void reloadConfig(CommandSender sender) {
            reloadConfig();
            loadConfig();
            loadLanguage(currentLanguage);  // Перезагружаем язык
            removeUntrackedPlants();  // Новый метод для удаления растений, которые больше не отслеживаются
            sender.sendMessage("Configuration has been reloaded.");
        }
    }