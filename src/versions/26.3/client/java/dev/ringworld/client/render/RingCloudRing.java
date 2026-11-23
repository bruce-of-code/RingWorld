package dev.ringworld.client.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.textures.AddressMode;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import dev.ringworld.client.ClientRingState;
import dev.ringworld.world.RingCloudBounds;
import dev.ringworld.world.RingGeometry;
import java.util.Optional;
import java.util.OptionalDouble;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * Distant cloud ring (experiment branch, 2026-11-22).
 *
 * <p>Why: vanilla clouds stop at the cloud range (a few hundred blocks), while the Atlas
 * draws terrain all the way around the ring, so the far side of the ring had no clouds at
 * all. This draws one flat, translucent sheet at the cloud deck over the full circumference
 * and band width, cut from vanilla's {@code clouds.png} with vanilla's 12-block cells,
 * 0.03-block-per-tick drift and 3.96-block Z offset, so it continues the live 3-D clouds'
 * pattern beyond their range. It fades in where the live clouds end and thins toward the far
 * side.
 *
 * <p>It is drawn in the sky pass right after the Atlas surface and shares its projection:
 * vertices are the same global cylinder, the vertex shader mirrors {@code ring_surface.vsh}
 * (including the default distortion branch and the far-depth clamp), and the model-view
 * setup mirrors {@link RingSurfaceTextureRenderer#render}. Colour, range and time come from
 * the frame vanilla's cloud renderer last prepared ({@link RingCloudFrame}).
 *
 * <p>Known simplifications: a single sheet (no thickness); canonical X, so the pattern has a
 * straight discontinuity at the X seam (as vanilla's own pattern would at any 3072-block
 * boundary it can't tile across); skipped under deferred shader packs and underwater.
 */
public final class RingCloudRing {
    private static final Identifier CLOUDS = Identifier.withDefaultNamespace("textures/environment/clouds.png");
    private static final RenderPipeline PIPELINE = RenderPipeline.builder(RenderPipelines.GUI_TEXTURED_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("ringworld", "pipeline/ring_clouds"))
            .withVertexShader(Identifier.fromNamespaceAndPath("ringworld", "core/ring_clouds"))
            .withFragmentShader(Identifier.fromNamespaceAndPath("ringworld", "core/ring_clouds"))
            .withShaderDefine("RINGWORLD_REVERSED_DEPTH")
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT)).withCull(false)
            .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, true))
            .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES).build();
    /** Vanilla CloudRenderer constants (26.3). */
    private static final float BLOCKS_PER_TICK = 0.03F;
    private static final long TICKS_PER_CELL = 400L;
    private static final float Z_OFFSET = 3.96F;
    /** Blocks of circumference per mesh segment. */
    private static final int SEGMENT_BLOCKS = 8;

    private static GpuBuffer vertexBuffer;
    private static int vertexCount;
    private static RingGeometry meshGeometry;
    private static float meshY = Float.NaN;

    private RingCloudRing() { }

    public static RenderPipeline pipeline() { return PIPELINE; }

    public static void render(RenderTarget target, PoseStack matrices, RingSkyView view) {
        if (!RingCloudFrame.visible()) return;
        ClientLevel world = view.world();
        RingGeometry geometry = view.geometry();
        if (world == null || geometry == null || geometry != ClientRingState.geometry(world)) return;
        if (view.fluid() == FogType.WATER) return;
        Minecraft client = Minecraft.getInstance();
        if (RingSurfaceGpu.useDeferredAtlas(client, target)) return;
        var texture = client.getTextureManager().getTexture(CLOUDS);
        GpuTextureView clouds = texture == null ? null : texture.getTextureView();
        if (clouds == null) return;

        // Mid-height of vanilla's 4-block deck.
        float deckY = RingCloudBounds.baseHeight(world.getMinY(), ClientRingState.wallHeightBlocks()) + 2.0F;
        ensureMesh(geometry, deckY);
        if (vertexBuffer == null) return;

        Vec3 camera = view.eye();
        double cameraAngle = Math.PI * 2.0 * geometry.wrapX(camera.x) / geometry.circumferenceBlocks();
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        try {
            modelView.mul(matrices.last().pose());
            if (!dev.ringworld.client.RingDistortionTuning.enabled()) {
                modelView.translate(0.0F, (float)geometry.physicalRadiusAt(camera.y), (float)-camera.z);
                modelView.rotateZ((float)-cameraAngle);
            }
            int argb = RingCloudFrame.color();
            float alpha = ((argb >>> 24) & 0xFF) / 255.0F;
            Vector4f color = new Vector4f(((argb >> 16) & 0xFF) / 255.0F, ((argb >> 8) & 0xFF) / 255.0F,
                    (argb & 0xFF) / 255.0F, alpha <= 0.0F ? 0.8F : alpha);
            int textureWidth = clouds.getWidth(0);
            float drift = ((RingCloudFrame.gameTime() % (textureWidth * TICKS_PER_CELL))
                    + RingCloudFrame.partialTick()) * BLOCKS_PER_TICK;
            float range = RingCloudFrame.rangeChunks() * 16.0F;
            Matrix4f params = new Matrix4f()
                    .translation((float)camera.x, (float)camera.y, (float)camera.z)
                    .m00(range * 0.85F).m01(range)
                    .m10(drift).m11(Z_OFFSET);
            GpuBufferSlice transforms = RenderSystem.getDynamicUniforms().writeTransform(
                    modelView, color,
                    new Vector3f((float)cameraAngle, (float)camera.z, RingSurfaceGpu.farBackgroundDepth()),
                    params);
            var alternateUniforms = view.primary() ? null
                    : ((RingRenderUniforms.SkyViewSource)client.gameRenderer).ringworld$skyViewBuffer(world, view);
            CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
            try (RenderPass pass = encoder.createRenderPass(() -> "RingWorld distant clouds",
                    target.getColorTextureView(), Optional.empty(), target.getDepthTextureView(), OptionalDouble.empty())) {
                pass.setPipeline(RenderSystem.getCompiledPipeline(PIPELINE));
                RenderSystem.bindDefaultUniforms(pass);
                pass.setUniform("DynamicTransforms", transforms);
                if (alternateUniforms != null) pass.setUniform(RingShaderContract.UNIFORM, alternateUniforms);
                pass.setUniform("Sampler0", clouds, RenderSystem.getSamplerCache().getSampler(
                        AddressMode.REPEAT, AddressMode.REPEAT, FilterMode.NEAREST, FilterMode.NEAREST, false));
                pass.setVertexBuffer(0, vertexBuffer.slice());
                pass.draw(vertexCount, 1, 0, 0);
            }
            encoder.submit();
        } finally {
            modelView.popMatrix();
        }
    }

    /** One strip around the whole ring at the deck's physical radius, band-wide in Z. */
    private static void ensureMesh(RingGeometry geometry, float deckY) {
        if (vertexBuffer != null && geometry.equals(meshGeometry) && deckY == meshY) return;
        release();
        int circumference = geometry.circumferenceBlocks();
        int segments = Math.max(256, circumference / SEGMENT_BLOCKS);
        float radius = (float)geometry.physicalRadiusAt(deckY);
        float minZ = geometry.minWidthZ();
        float maxZ = geometry.maxWidthZ() + 1.0F;
        int vertices = segments * 6;
        var format = DefaultVertexFormat.POSITION_TEX_COLOR;
        try (ByteBufferBuilder allocator = ByteBufferBuilder.exactlySized(vertices * format.getVertexSize())) {
            BufferBuilder builder = new BufferBuilder(allocator, PrimitiveTopology.TRIANGLES, format);
            for (int i = 0; i < segments; i++) {
                float x0 = (float)((double)i * circumference / segments);
                float x1 = (float)((double)(i + 1) * circumference / segments);
                double a0 = Math.PI * 2.0 * x0 / circumference, a1 = Math.PI * 2.0 * x1 / circumference;
                float sx0 = (float)(radius * Math.sin(a0)), sy0 = (float)(-radius * Math.cos(a0));
                float sx1 = (float)(radius * Math.sin(a1)), sy1 = (float)(-radius * Math.cos(a1));
                vertex(builder, sx0, sy0, minZ, x0);
                vertex(builder, sx1, sy1, minZ, x1);
                vertex(builder, sx1, sy1, maxZ, x1);
                vertex(builder, sx0, sy0, minZ, x0);
                vertex(builder, sx1, sy1, maxZ, x1);
                vertex(builder, sx0, sy0, maxZ, x0);
            }
            try (MeshData data = builder.buildOrThrow()) {
                vertexBuffer = RenderSystem.getDevice().createBuffer(() -> "RingWorld distant clouds",
                        GpuBuffer.USAGE_VERTEX, data.vertexBuffer());
            }
        }
        vertexCount = vertices;
        meshGeometry = geometry;
        meshY = deckY;
    }

    private static void vertex(BufferBuilder builder, float x, float y, float z, float intrinsicX) {
        builder.addVertex(x, y, z).setUv(intrinsicX, z).setColor(0xFFFFFFFF);
    }

    public static void release() {
        if (vertexBuffer != null) vertexBuffer.close();
        vertexBuffer = null;
        vertexCount = 0;
        meshGeometry = null;
        meshY = Float.NaN;
    }
}
