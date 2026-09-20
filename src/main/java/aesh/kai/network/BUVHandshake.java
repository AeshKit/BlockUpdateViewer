package aesh.kai.network;

import aesh.kai.BlockUpdateViewer;import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

public record BUVHandshake() implements CustomPacketPayload {
    public static final Identifier ID =
            Identifier.fromNamespaceAndPath(BlockUpdateViewer.MOD_ID, "handshake");

    public static final Type<BUVHandshake> TYPE = new Type<>(ID);

    public static final StreamCodec<RegistryFriendlyByteBuf, BUVHandshake> CODEC =
            StreamCodec.unit(new BUVHandshake());

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}