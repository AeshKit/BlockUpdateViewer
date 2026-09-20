package aesh.kai.network;

import aesh.kai.BlockUpdateViewer;
import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UpdateCollector {
    private static final Map<ResourceKey<Level>, ArrayList<UpdateEntry>> BUFFERS = new HashMap<>();
    private static Long lastFlushedTick = null; // one global tick now, not per-dim

    public static void record(Level level, long pos, UpdateType type) {
        ResourceKey<Level> dim = level.dimension();
        BUFFERS.computeIfAbsent(dim, _ -> new ArrayList<>())
                .add(new UpdateEntry(pos, type, dim));
    }

    public static List<UpdateEntry> drain(ResourceKey<Level> dim) {
        ArrayList<UpdateEntry> list = BUFFERS.get(dim);
        if(list == null || list.isEmpty()) return List.of();
        BUFFERS.put(dim, new ArrayList<>(list.size()));
        return list;
    }

    public enum UpdateType {
        PP(0b001),
        NC(0b010),
        COMPARATOR(0b100);

        public final int bit;

        UpdateType(int bit) {
            this.bit = bit;
        }

        private static final Map<Integer, UpdateType> BY_BIT = new HashMap<>();
        static {
            for(UpdateType t : values()) {
                if(BY_BIT.put(t.bit, t) != null)
                    throw new IllegalStateException("Duplicate UpdateType bit: " + t.bit);
            }
        }

        public static UpdateType fromBit(int bit) {
            UpdateType t = BY_BIT.get(bit);
            if(t == null) throw new IllegalArgumentException("Unknown UpdateType bit: " + bit);
            return t;
        }
    }

    public record UpdateEntry(long pos, UpdateType type, ResourceKey<Level> dim) {
        public static final StreamCodec<ByteBuf, UpdateEntry> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_LONG, UpdateEntry::pos,
                ByteBufCodecs.idMapper(UpdateType::fromBit, t -> t.bit), UpdateEntry::type,
                ResourceKey.streamCodec(Registries.DIMENSION), UpdateEntry::dim,
                UpdateEntry::new
        );
    }

    public record BatchPayload(long tick, List<UpdateEntry> entries) implements CustomPacketPayload {
        public static final Type<BatchPayload> TYPE =
                new Type<>(Identifier.fromNamespaceAndPath(BlockUpdateViewer.MOD_ID, "batch"));
        public static final StreamCodec<RegistryFriendlyByteBuf, BatchPayload> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_LONG, BatchPayload::tick,
                UpdateEntry.CODEC.apply(ByteBufCodecs.list()), BatchPayload::entries,
                BatchPayload::new
        );
        @Override
        public @NonNull Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            long gameTime = server.overworld().getGameTime(); // shared across all dims on the same server
            if(lastFlushedTick != null && lastFlushedTick == gameTime) return;
            lastFlushedTick = gameTime;

            List<UpdateEntry> merged = new ArrayList<>();
            for(ServerLevel level : server.getAllLevels()) {
                merged.addAll(drain(level.dimension()));
            }

            BatchPayload payload = new BatchPayload(gameTime, merged);
            for(ServerPlayer player : server.getPlayerList().getPlayers()) {
                if(BlockUpdateViewer.BUV_CLIENTS.contains(player.getUUID()))
                    ServerPlayNetworking.send(player, payload);
            }
        });
    }
}