package aesh.kai.client.render;

import aesh.kai.client.log.ThisTick;
import aesh.kai.network.UpdateCollector;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.PolygonMode;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.StagedVertexBuffer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.*;

import static aesh.kai.BlockUpdateViewer.LOGGER;
import static aesh.kai.BlockUpdateViewer.MOD_ID;
import static aesh.kai.client.BlockUpdateViewerClient.config;
import static net.minecraft.client.renderer.RenderPipelines.PARTICLE_SNIPPET;
import static net.minecraft.core.BlockPos.*;

public final class Renderer {

    private record BoxState(int posX, int posY, int posZ, int color, BlockState block, int points) {}

    private static final StagedVertexBuffer stagedBuffer = new StagedVertexBuffer(() -> "Buffer", RenderType.SMALL_BUFFER_SIZE);

    private static final Vector4f COLOR_MODULATOR = new Vector4f(1f, 1f, 1f, 1f);
    private static final Vector3f MODEL_OFFSET = new Vector3f();
    private static final Matrix4f TEXTURE_MATRIX = new Matrix4f();

    private record SectionKey(int sx, int sy, int sz) {}
    private static final Map<SectionKey, List<BoxState>> bySection = new HashMap<>();

    private static final Identifier ICON_TEXTURE = Identifier.fromNamespaceAndPath(MOD_ID, "textures/orb.png");

    public static final RenderPipeline ICON_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(PARTICLE_SNIPPET)
                    .withLocation(Identifier.fromNamespaceAndPath(MOD_ID, "pipeline/orb_particle"))
                    .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                    .build()
    );

    private static GpuTextureView iconTextureView;
    private static GpuSampler iconSampler;

    private static void ensureIconTexture() {
        if(iconTextureView == null) {
            AbstractTexture tex = Minecraft.getInstance().getTextureManager().getTexture(ICON_TEXTURE);
            iconTextureView = tex.getTextureView();
            iconSampler = tex.getSampler();
        }
    }




    public static void init() {
        LevelExtractionEvents.END_EXTRACTION.register(Renderer::extractFromTracker);
        LevelRenderEvents.AFTER_TRANSLUCENT_TERRAIN.register(Renderer::renderUpdates);
    }

    private static void extractFromTracker(LevelExtractionContext ctx) {
        final Level level = Minecraft.getInstance().level;
        if(ThisTick.get().isEmpty() || level == null) return;

        final Map<UpdateCollector.UpdateType, Integer> COLORS = getColors();

        bySection.clear();
        Tracker.forEach((pos, typeMask, expiry) -> {
            if(expiry <= ThisTick.get().orElse(Long.MAX_VALUE)) return;

//                    COLORS.get(UpdateCollector.UpdateType.fromBit(typeMask));

            BoxState box = new BoxState(getX(pos), getY(pos), getZ(pos), blendColors(COLORS, typeMask),
                    level.getBlockState(new BlockPos(getX(pos), getY(pos), getZ(pos))), typeMask);
            SectionKey key = new SectionKey(box.posX >> 4, box.posY >> 4, box.posZ >> 4);
            bySection.computeIfAbsent(key, _ -> new ArrayList<>()).add(box);
        });
    }

    private static int blendColors(Map<UpdateCollector.UpdateType, Integer> colors, int mask) {
        int a = 0, r = 0, g = 0, b = 0, count = 0;
        for(UpdateCollector.UpdateType type : UpdateCollector.UpdateType.values()) {
            if((mask & type.bit) != 0) {
                final int c = colors.get(type);
                a += (c >>> 24) & 0xFF;
                r += (c >>> 16) & 0xFF;
                g += (c >>> 8) & 0xFF;
                b += c & 0xFF;
                count++;
            }
        }
        return ((a / count) << 24) | ((r / count) << 16) | ((g / count) << 8) | (b / count);
    }

    private static Map<UpdateCollector.UpdateType, Integer> getColors() {
        Map<UpdateCollector.UpdateType, Integer> colors = new EnumMap<>(UpdateCollector.UpdateType.class);
        colors.put(UpdateCollector.UpdateType.PP,
                config.colorPP | (((int) Math.ceil(config.PPOpacityPercent * 2.55)) << 24));
        colors.put(UpdateCollector.UpdateType.NC,
                config.colorNC | (((int) Math.ceil(config.NCOpacityPercent * 2.55)) << 24));
        colors.put(UpdateCollector.UpdateType.COMPARATOR,
                config.colorComparator | (((int) Math.ceil(config.ComparatorOpacityPercent * 2.55)) << 24));
        return colors;
    }

    private static void renderUpdates(LevelRenderContext ctx) {
//        if(config.renderMode == BUVConfig.RenderMode.SOLID)
        renderBoxes(ctx);
    }

    private static void renderBoxes(LevelRenderContext ctx) {
        final Frustum frustum = ctx.gameRenderer().mainCamera().getCullFrustum();
        final Minecraft client = Minecraft.getInstance();
        if(client.level == null) return;

        final PoseStack matrices = ctx.poseStack();
        final Vec3 camera = ctx.levelState().cameraRenderState.pos;

        matrices.pushPose();
        matrices.translate(-camera.x, -camera.y, -camera.z);

        final float scaleDown = 1F - (config.boxSizePercent * 0.01F);
        final boolean CULL = config.culling;
        final boolean OCCLUSION_CULL = config.occlusionCulling;
        final int CHUNK_X = client.player.getBlockX() >> 4;
        final int CHUNK_Z = client.player.getBlockZ() >> 4;
        final int CHUNK_Y = client.player.getBlockY() >> 4;
        final int RENDER_DIST = client.options.getEffectiveRenderDistance();

        List<BoxState> boxesToDraw = new ArrayList<>();
        List<BoxState> iconsToDraw = new ArrayList<>();
//        List<BoxState> linesToDraw = new ArrayList<>();

        for(var entry : bySection.entrySet()) {
            final SectionKey s = entry.getKey();
            final AABB SECTION_BOUNDS = new AABB(
                    s.sx() << 4, s.sy() << 4, s.sz() << 4,
                    (s.sx() + 1) << 4, (s.sy() + 1) << 4, (s.sz() + 1) << 4);

            if(CULL && ( isTooFar(s, CHUNK_X, CHUNK_Z, RENDER_DIST) || !frustum.isVisible(SECTION_BOUNDS) ))
                continue;
            if(OCCLUSION_CULL && isOutsideRange(s, CHUNK_X, CHUNK_Z, CHUNK_Y, config.occlusionCullStart - 1) && isOccluded(client.level, camera, SECTION_BOUNDS))
                continue;

//            for(var box : entry.getValue()) {
//                if(!box.block.isAir()) {
//                    linesToDraw.add(box);
//                } else {
//                    boxesToDraw.add(box);
//                }
//            }
            // Maybe in future version. Right now lines are laggy and I can't find out why
            // Well, it's the iteration in shape.forAllEdges, but I don't really have an alternative
            for(BoxState box : entry.getValue()) {
                boxesToDraw.add(box);
                // Multiple update types on this block -> also draw the billboarded icon.
                // Collected here (not while writing box vertices) because the icon uses a
                // different vertex format (position+uv+color+light vs position+color), so
                // it needs its own StagedVertexBuffer.Draw / vertex builder entirely.
                if(Integer.bitCount(box.points) > 1) {
                    iconsToDraw.add(box);
                }
            }
        }

        StagedVertexBuffer.Draw drawBox = null;
//        StagedVertexBuffer.Draw drawLine = null;
        final RenderPipeline filledBoxPipeline = RenderPipelines.DEBUG_FILLED_BOX;
//        final RenderPipeline linePipeline = RenderPipelines.LINES_DEPTH_BIAS;

        StagedVertexBuffer.Draw drawTex = null;

        boolean pushToBuffer = false;

        if(!boxesToDraw.isEmpty()) {
            drawBox = stagedBuffer.appendDraw(
                    filledBoxPipeline.getVertexFormatBinding(0),
                    filledBoxPipeline.getPrimitiveTopology(),
                    RenderSystem.getProjectionType().vertexSorting()
            );

            final var builder = stagedBuffer.getVertexBuilder(drawBox);
            for(BoxState box : boxesToDraw) {
                drawBox(
                        matrices.last().pose(), builder,
                        box.posX + scaleDown, box.posY + scaleDown, box.posZ + scaleDown,
                        box.posX + 1 - scaleDown, box.posY + 1 - scaleDown, box.posZ + 1 - scaleDown,
                        box.color
                );
            }

            pushToBuffer = true;
        }

        if(!iconsToDraw.isEmpty()) {
            ensureIconTexture();

            drawTex = stagedBuffer.appendDraw(
                    ICON_PIPELINE.getVertexFormatBinding(0),
                    ICON_PIPELINE.getPrimitiveTopology(),
                    RenderSystem.getProjectionType().vertexSorting()
            );

            final var texBuilder = stagedBuffer.getVertexBuilder(drawTex);

            // Camera-facing basis: rotate the local +X/+Y axes by the camera's
            // orientation so the quad we build from them always faces the camera,
            // the same trick vanilla particle billboarding uses.
            final Quaternionf camRot = new Quaternionf(ctx.levelState().cameraRenderState.orientation);
            final Vector3f right = new Vector3f(1, 0, 0).rotate(camRot);
            final Vector3f up = new Vector3f(0, 1, 0).rotate(camRot);

            for(BoxState box : iconsToDraw) {
                drawBillboard(
                        matrices.last().pose(), texBuilder, right, up,
                        box.posX + 0.5f, box.posY + 0.3f, box.posZ + 0.5f
                );
            }

            pushToBuffer = true;
        }

//        if(!linesToDraw.isEmpty()) {
//            drawLine = stagedBuffer.appendDraw(
//                    Objects.requireNonNull(linePipeline.getVertexFormatBinding(0)),
//                    linePipeline.getPrimitiveTopology(),
//                    null
//            );
//
//            final var lineBuilder = stagedBuffer.getVertexBuilder(drawLine);
//            for(BoxState box : linesToDraw) {
//                drawOutline(matrices.last().pose(), lineBuilder, box, client.level);
//            }
//        pushToBuffer = true;
//        }

        if(pushToBuffer) {
            matrices.popPose();
            stagedBuffer.upload();
        }

        if(drawBox != null) {
            StagedVertexBuffer.ExecuteInfo info = stagedBuffer.getExecuteInfo(drawBox);
            if(info != null) draw(client, info, filledBoxPipeline);
        }

        if(drawTex != null) {
            StagedVertexBuffer.ExecuteInfo info = stagedBuffer.getExecuteInfo(drawTex);
            if(info != null) draw(client, info, ICON_PIPELINE, iconTextureView, iconSampler);
        }

//        if(drawLine != null) {
//            StagedVertexBuffer.ExecuteInfo info = stagedBuffer.getExecuteInfo(drawLine);
//            if(info != null) draw(client, info, linePipeline);
//        }

        stagedBuffer.endFrame();
    }

    private static boolean isTooFar(SectionKey section, int playerChunkX, int playerChunkZ, int renderDistance) {
        // Chebyshev distance on chunk coordinates
        final int dX = Math.abs(section.sx() - playerChunkX);
        final int dZ = Math.abs(section.sz() - playerChunkZ);

        return Math.max(dX, dZ) > renderDistance;
    }

    private static boolean isOutsideRange(SectionKey section, int playerChunkX, int playerChunkZ, int playerChunkY, int range) {
        final int dX = Math.abs(section.sx() - playerChunkX);
        final int dY = Math.abs(section.sy() - playerChunkY);
        final int dZ = Math.abs(section.sz() - playerChunkZ);

        return Math.max(Math.max(dX, dY), dZ) > range;
    }

    private static boolean isOccluded(Level level, Vec3 eye, AABB bounds) {
        Vec3 center = bounds.getCenter();

        Vec3[] samples = {
                center,
                new Vec3(eye.x < center.x ? bounds.minX : bounds.maxX, center.y, center.z),
                new Vec3(center.x, eye.y < center.y ? bounds.minY : bounds.maxY, center.z),
                new Vec3(center.x, center.y, eye.z < center.z ? bounds.minZ : bounds.maxZ)
        };

        for(Vec3 target : samples) {
            ClipContext ctx = new ClipContext(eye, target, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, CollisionContext.empty());
            if(level.clip(ctx).getType() == HitResult.Type.MISS) {
                return false;
            }
        }
        return true;
    }

    // This method is pretty much copied verbatim from the Fabric API docs
    // https://docs.fabricmc.net/develop/rendering/world
    private static void drawBox(Matrix4fc m, VertexConsumer buffer,
                                float minX, float minY, float minZ,
                                float maxX, float maxY, float maxZ,
                                int color)
    {
        // +Z Face
        buffer.addVertex(m, minX, minY, maxZ).setColor(color);
        buffer.addVertex(m, maxX, minY, maxZ).setColor(color);
        buffer.addVertex(m, maxX, maxY, maxZ).setColor(color);
        buffer.addVertex(m, minX, maxY, maxZ).setColor(color);

        // -Z Face
        buffer.addVertex(m, minX, minY, minZ).setColor(color);
        buffer.addVertex(m, maxX, minY, minZ).setColor(color);
        buffer.addVertex(m, maxX, maxY, minZ).setColor(color);
        buffer.addVertex(m, minX, maxY, minZ).setColor(color);

        // -X Face
        buffer.addVertex(m, minX, minY, minZ).setColor(color);
        buffer.addVertex(m, minX, minY, maxZ).setColor(color);
        buffer.addVertex(m, minX, maxY, maxZ).setColor(color);
        buffer.addVertex(m, minX, maxY, minZ).setColor(color);

        // +X Face
        buffer.addVertex(m, maxX, minY, maxZ).setColor(color);
        buffer.addVertex(m, maxX, minY, minZ).setColor(color);
        buffer.addVertex(m, maxX, maxY, minZ).setColor(color);
        buffer.addVertex(m, maxX, maxY, maxZ).setColor(color);

        // +Y Face
        buffer.addVertex(m, minX, maxY, maxZ).setColor(color);
        buffer.addVertex(m, maxX, maxY, maxZ).setColor(color);
        buffer.addVertex(m, maxX, maxY, minZ).setColor(color);
        buffer.addVertex(m, minX, maxY, minZ).setColor(color);

        // -Y Face
        buffer.addVertex(m, minX, minY, minZ).setColor(color);
        buffer.addVertex(m, maxX, minY, minZ).setColor(color);
        buffer.addVertex(m, maxX, minY, maxZ).setColor(color);
        buffer.addVertex(m, minX, minY, maxZ).setColor(color);
    }

    // right & up are camera's local x & y-axis rotated in world-space accordingly
    private static void drawBillboard(Matrix4fc m, VertexConsumer buffer,
                                      Vector3f right, Vector3f up,
                                      float cX, float cY, float cZ)
    {
        final float[][] corners = {
                {-1, -1, 0, 1},
                { 1, -1, 1, 1},
                { 1,  1, 1, 0},
                {-1,  1, 0, 0},
        };

        for(float[] c : corners) {
            final float x = cX + (right.x * c[0] + up.x * c[1]) * 0.1F;
            final float y = cY + (right.y * c[0] + up.y * c[1]) * 0.1F;
            final float z = cZ + (right.z * c[0] + up.z * c[1]) * 0.1F;

            buffer.addVertex(m, x, y, z)
                    .setColor(0xFF_FFFFFF)
                    .setUv(c[2], c[3])
                    .setLight(LightCoordsUtil.FULL_BRIGHT);
        }
    }

//    private static void drawOutline(Matrix4fc m, VertexConsumer buffer, BoxState box, Level level) {
//        VoxelShape shape = box.block.getShape(level, new BlockPos(box.posX, box.posY, box.posZ));
//        float lineWidth = Minecraft.getInstance().getWindow().getAppropriateLineWidth();
//
//        shape.forAllEdges((x0, y0, z0, x1, y1, z1) -> {
//            final float dx = (float)(x1 - x0);
//            final float dy = (float)(y1 - y0);
//            final float dz = (float)(z1 - z0);
//
//            buffer.addVertex(m, (float) (box.posX + x0), (float) (box.posY + y0), (float) (box.posZ + z0))
//                    .setColor(box.color)
//                    .setNormal(dx, dy, dz)
//                    .setLineWidth(lineWidth);
//
//            buffer.addVertex(m, (float) (box.posX + x1), (float) (box.posY + y1), (float) (box.posZ + z1))
//                    .setColor(box.color)
//                    .setNormal(dx, dy, dz)
//                    .setLineWidth(lineWidth);
//        });
//    }

    private static void draw(Minecraft client, StagedVertexBuffer.ExecuteInfo info, RenderPipeline pipeline) {
        draw(client, info, pipeline, null, null);
    }

    private static void draw(Minecraft client, StagedVertexBuffer.ExecuteInfo info, RenderPipeline pipeline,
                             GpuTextureView texture, GpuSampler sampler) {
        GpuBufferSlice dynamicTransforms = RenderSystem.getDynamicUniforms()
                .writeTransform(RenderSystem.getModelViewMatrixCopy(), COLOR_MODULATOR, MODEL_OFFSET, TEXTURE_MATRIX);

        RenderTarget mainTarget = client.gameRenderer.mainRenderTarget();
        GpuTextureView colorTexture = mainTarget.getColorTextureView();

        try(RenderPass renderPass = RenderSystem.getDevice()
                .createCommandEncoder()
                .createRenderPass(() -> MOD_ID + " render pipeline", colorTexture, Optional.empty(), mainTarget.getDepthTextureView(), OptionalDouble.empty()))
        {
            renderPass.setPipeline(pipeline);

            RenderSystem.bindDefaultUniforms(renderPass);
            renderPass.setUniform("DynamicTransforms", dynamicTransforms);

            if(texture != null) {
                renderPass.bindTexture("Sampler0", texture, sampler);
            }

            renderPass.setVertexBuffer(0, info.vertexBuffer().slice());
            renderPass.setIndexBuffer(info.indexBuffer(), info.indexType());

            renderPass.drawIndexed(info.indexCount(), 1, info.firstIndex(), info.baseVertex(), 0);
        } catch(Exception e) {
            LOGGER.warn("Exception caught during draw", e);
        }
    }

    public static void close() {
        stagedBuffer.close();
    }
}