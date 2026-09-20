package aesh.kai.client.render;

import aesh.kai.network.UpdateCollector;
import it.unimi.dsi.fastutil.longs.Long2LongMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import static aesh.kai.BlockUpdateViewer.LOGGER;
import static aesh.kai.client.BlockUpdateViewerClient.config;

public final class Tracker {
    private static final Long2LongOpenHashMap TRACKED = new Long2LongOpenHashMap();

    public static void add(long pos, UpdateCollector.UpdateType type, long receivedTick, int lingerTicks, ResourceKey<Level> dim) {
        final Minecraft client = Minecraft.getInstance();
        if(client.player == null || client.player.level().dimension() != dim) return;

        final long expiry = Math.min(
                (lingerTicks == -1) ? 0x1FFF_FFFF_FFFF_FFFFL : receivedTick + lingerTicks + 1,
                0b00011111_11111111_11111111_11111111_11111111_11111111_11111111_11111111L // 0x1FFF_FFFF_FFFF_FFFFL in bit-notation
        );
        /*
        Using the tick of the block update for storing the types of the block update.
        0x1FFFFFFFFFFFFFFF seconds / 20 = x years      [ Run in Qalculate! ]
        Still allows up to ~3_653_387_788 years of runtime.
         */

        final long existing = TRACKED.get(pos);
        final long existingExpiry = existing >>> 3;
        final int existingMask = (int)(existing & 0b111);
        final int newBit = type.bit;

        final int mask = (existingExpiry > receivedTick) ? (existingMask | newBit) : newBit;

        TRACKED.put(pos, combine(expiry, mask));
    }

    public static void purgeBefore(long tick) {
        if(TRACKED.isEmpty()) return;

        TRACKED.values().removeIf(val -> (val >>> 3) < tick);
    }

    public static void clear() {
        TRACKED.clear();
        if(config.verboseLogging) LOGGER.info("Cleared render tracker");
    }

    public static void forEach(tConsumer consumer) {
        var i = TRACKED.long2LongEntrySet().fastIterator();
        while(i.hasNext()) {
            final Long2LongMap.Entry thisEntry = i.next();
            final long pos = thisEntry.getLongKey();
            final long expiryAndMask = thisEntry.getLongValue();
            final long expiry = expiryAndMask >>> 3;
            final int typeMask = (int)(expiryAndMask & 0b111);
            consumer.accept(pos, typeMask, expiry);
        }
    }

    public static long combine(long bottom29Bits, int typeMask) {
        return (bottom29Bits << 3) | (typeMask & 0b111L);
    }

    @FunctionalInterface
    public interface tConsumer {
        void accept(long pos, int typeMask, long expiryTick);
    }
}