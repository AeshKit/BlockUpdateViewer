package aesh.kai.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

import static aesh.kai.BlockUpdateViewer.LOGGER;

public class BUVConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_FILE = FabricLoader.getInstance().getConfigDir().resolve("blockupdateviewer.json");

    public static BUVConfig INSTANCE = new BUVConfig();

    public boolean isRenderingEnabled = true;
    public boolean verboseLogging = false;
    public boolean networkLogging = false;
    public boolean logToFile = true;
    public boolean logDuplicates = false;
    public boolean persistentLog = false;
    public int lingerTicks = 0;
    public int maxUpdates = 1_000;
    public int PPOpacityPercent = 25;
    public int NCOpacityPercent = 25;
    public int ComparatorOpacityPercent = 50;
    public int colorPP = 0x88FF77;
    public int colorNC = 0x7788FF;
    public int colorComparator = 0xFF7788;

    public boolean culling = true;
    public boolean occlusionCulling = true;
    public int occlusionCullStart = 3;
//    public boolean highlightAsOutline = false;
    public int boxSizePercent = 90;

    public int logQueueMax = 2_000_000;

    public RenderMode renderMode = RenderMode.SOLID;

    public enum RenderMode {
        OUTLINE(0, "config.blockupdateviewer.option.renderMode.outline"),
        SOLID(1, "config.blockupdateviewer.option.renderMode.solid");

        private final int id;
        private final String translationKey;

        RenderMode(int id, String translationKey) {
            this.id = id;
            this.translationKey = translationKey;
        }

        public int getId() { return id; }
        public Component getName() { return Component.translatable(translationKey); }
    }

    public static void load() {
        if(Files.exists(CONFIG_FILE)) {
            try(Reader reader = Files.newBufferedReader(CONFIG_FILE)) {
                INSTANCE = GSON.fromJson(reader, BUVConfig.class);
            } catch(Exception e) {
                LOGGER.error("Failed to load config", e);
            }
        } else {
            save(); // default config
        }
    }

    public static void save() {
        try(Writer writer = Files.newBufferedWriter(CONFIG_FILE)) {
            GSON.toJson(INSTANCE, writer);
        } catch(Exception e) {
            LOGGER.error("Failed to save config", e);
        }
    }
}