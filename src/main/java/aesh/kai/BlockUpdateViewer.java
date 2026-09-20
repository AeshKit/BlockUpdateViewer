package aesh.kai;

import aesh.kai.network.BUVHandshake;
import aesh.kai.network.UpdateCollector;
import net.fabricmc.api.ModInitializer;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class BlockUpdateViewer implements ModInitializer {
	public static final String MOD_ID = "block-update-viewer";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static final Set<UUID> BUV_CLIENTS = ConcurrentHashMap.newKeySet();

	@Override
	public void onInitialize() {
		PayloadTypeRegistry.serverboundPlay().register(BUVHandshake.TYPE, BUVHandshake.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(UpdateCollector.BatchPayload.TYPE, UpdateCollector.BatchPayload.CODEC);

		ServerPlayNetworking.registerGlobalReceiver(BUVHandshake.TYPE, (_, context) -> {
			BUV_CLIENTS.add(context.player().getUUID());
			LOGGER.info("Received handshake from {}", context.player().getPlainTextName());
		});

		ServerPlayConnectionEvents.DISCONNECT.register((handler, _) ->
				BUV_CLIENTS.remove(handler.player.getUUID()));

		UpdateCollector.register();

		LOGGER.info("Block Update Viewer Init");
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}