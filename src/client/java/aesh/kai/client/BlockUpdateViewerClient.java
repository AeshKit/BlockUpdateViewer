package aesh.kai.client;

import aesh.kai.BlockUpdateViewer;
import aesh.kai.client.config.BUVConfig;
import aesh.kai.client.log.BULogger;
import aesh.kai.client.log.ThisTick;
import aesh.kai.client.render.Renderer;
import aesh.kai.client.render.Tracker;
import aesh.kai.client.ui.DBFilterUI;
import aesh.kai.network.BUVHandshake;
import aesh.kai.network.UpdateCollector;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.NonNull;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashSet;
import java.util.Set;

import static aesh.kai.BlockUpdateViewer.LOGGER;

public class BlockUpdateViewerClient implements ClientModInitializer {
	public static BUVConfig config;
	private static final UpdateCollector.UpdateType[] TYPES = UpdateCollector.UpdateType.values();

	public static final KeyMapping.Category BUV_CATEGORY = KeyMapping.Category.register(
			Identifier.fromNamespaceAndPath(BlockUpdateViewer.MOD_ID, "main")
	);

	public static final KeyMapping OPEN_FILTER_KEY = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.block-update-viewer.open_dbfilter",
			InputConstants.Type.KEYSYM,
			GLFW.GLFW_KEY_BACKSLASH,
			BUV_CATEGORY
	));

	@Override
	public void onInitializeClient() {
		BUVConfig.load();
		config = BUVConfig.INSTANCE;

		Renderer.init();

		ClientPlayConnectionEvents.JOIN.register((_, _, client) -> {
			if(config.logToFile) {
				Path dbPath = resolveLogPath(client);
				BULogger.open(dbPath);
				BULogger.startWriterThread();
			}
			ClientPlayNetworking.send(new BUVHandshake());
			if(config.networkLogging) LOGGER.info("Sent handshake to server");
		});

		ClientPlayConnectionEvents.DISCONNECT.register((_, _) -> {
			if(config.isRenderingEnabled) Tracker.clear();
		});

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if(config.isRenderingEnabled)
				Tracker.purgeBefore(ThisTick.get().orElse(-1L));

			while(OPEN_FILTER_KEY.consumeClick()) {
				LOGGER.info("{}", BULogger.totalDropped);
				if(client.player != null && client.gui.screen() == null)
					DBFilterUI.open(null);
			}
		});

		ClientLifecycleEvents.CLIENT_STOPPING.register(_ -> {
			if(!config.persistentLog) removeFolders();
		});

		record Key(long pos, UpdateCollector.UpdateType type, ResourceKey<Level> dim) {}

		ClientPlayNetworking.registerGlobalReceiver(UpdateCollector.BatchPayload.TYPE, (payload, _) -> {
			if(config.networkLogging) LOGGER.info("Client received {} entries [ {} ]", payload.entries().size(), payload.type());

			ThisTick.update(payload.tick());

			final Set<Key> seen = config.logDuplicates ?
					null :
					new HashSet<>(payload.entries().size());

			for(UpdateCollector.UpdateEntry entry : payload.entries()) {
				final Key key = new Key(entry.pos(), entry.type(), entry.dim());
				if(seen != null && !seen.add(key)) continue;

				if(config.isRenderingEnabled)
					Tracker.add(entry.pos(), entry.type(), payload.tick(), config.lingerTicks, entry.dim());

				if(config.logToFile) BULogger.enqueue(entry.dim(), payload.tick(), entry);
			}
		});
	}

	private static void removeFolders() {
		final Path singleplayerDir = FabricLoader.getInstance().getGameDir()
				.resolve("logs")
				.resolve(BlockUpdateViewer.MOD_ID)
				.resolve("integrated");
		final Path multiplayerDir = FabricLoader.getInstance().getGameDir()
				.resolve("logs")
				.resolve(BlockUpdateViewer.MOD_ID)
				.resolve("multiplayer");

		try {
			deleteDir(singleplayerDir);
			deleteDir(multiplayerDir);
		} catch(IOException e) {
			LOGGER.error("Failed to delete files. Are you running Minecraft as a Flatpak ?");
		}

		if(config.verboseLogging) LOGGER.info("Cleared all log directories");
	}

	private static void deleteDir(Path dir) throws IOException {
		if(!Files.exists(dir)) return;

		Files.walkFileTree(dir, new SimpleFileVisitor<Path>() {
			@Override
			public @NonNull FileVisitResult visitFile(@NonNull Path file, @NonNull BasicFileAttributes attrs) throws IOException {
				Files.delete(file);
				return FileVisitResult.CONTINUE;
			}

			@Override
			public @NonNull FileVisitResult postVisitDirectory(@NonNull Path dir, IOException e) throws IOException {
				if(e == null) {
					Files.delete(dir);
					return FileVisitResult.CONTINUE;
				} else {
					throw e;
				}
			}
		});
	}

	public static Path resolveLogPath(Minecraft client) {
		final IntegratedServer integrated = client.getSingleplayerServer();

		if(integrated != null) {
			// Singleplayer
			final String worldName = sanitize(integrated.getWorldData().getLevelName());

			final Path dir = FabricLoader.getInstance().getGameDir()
					.resolve("logs")
					.resolve(BlockUpdateViewer.MOD_ID)
					.resolve("integrated");

			try {
				Files.createDirectories(dir);
			} catch(IOException e) {
				LOGGER.error("Failed to create integrated log directory. Are you running Minecraft as a Flatpak ?", e);
			}

			if(config.verboseLogging) LOGGER.info("Created folder for integrated server {}", worldName);
			return dir.resolve(worldName + ".sqlite");
		} else {
			// Multiplayer
			final String serverAddress = sanitize(client.getCurrentServer() != null ? client.getCurrentServer().ip : "unknown_server");

			final Path dir = FabricLoader.getInstance().getGameDir()
					.resolve("logs")
					.resolve(BlockUpdateViewer.MOD_ID)
					.resolve("multiplayer");

			try {
				Files.createDirectories(dir);
			} catch(IOException e) {
				LOGGER.error("Failed to create server log directory", e);
			}
			if(config.verboseLogging) LOGGER.info("Created folder for server {}", serverAddress);
			return dir.resolve(serverAddress + ".sqlite");
		}
	}

	private static String sanitize(String input) {
		return input.replaceAll("[^a-zA-Z0-9.-]", "_");
		// regex meaning "NOT a~z, A~Z, 0~9, '.', or '-'"
	}
}