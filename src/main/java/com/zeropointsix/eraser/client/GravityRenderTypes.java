package com.zeropointsix.eraser.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.OptionalDouble;
import net.minecraft.client.renderer.RenderType;
import org.lwjgl.opengl.GL11;

public final class GravityRenderTypes extends RenderType {
    // Vanilla NO_DEPTH_TEST is a no-op, so it can inherit the terrain's enabled depth test.
    private static final DepthTestStateShard THROUGH_WALL = new DepthTestStateShard("gravity_through_wall", GL11.GL_ALWAYS) {
        private boolean enabled;

        @Override
        public void setupRenderState() {
            enabled = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
            RenderSystem.disableDepthTest();
        }

        @Override
        public void clearRenderState() {
            if (enabled) RenderSystem.enableDepthTest();
            else RenderSystem.disableDepthTest();
        }
    };

    public static final RenderType LINES = create("gravity_lines", DefaultVertexFormat.POSITION_COLOR_NORMAL,
            VertexFormat.Mode.LINES, 256, false, false, CompositeState.builder()
                    .setShaderState(RENDERTYPE_LINES_SHADER)
                    .setLineState(new LineStateShard(OptionalDouble.of(1.5)))
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setDepthTestState(THROUGH_WALL).setWriteMaskState(COLOR_WRITE)
                    .setOutputState(MAIN_TARGET)
                    .setCullState(NO_CULL).createCompositeState(false));
    public static final RenderType FILL = create("gravity_fill", DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS, 256, false, true, CompositeState.builder()
                    .setShaderState(POSITION_COLOR_SHADER)
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setDepthTestState(THROUGH_WALL).setWriteMaskState(COLOR_WRITE)
                    .setOutputState(MAIN_TARGET)
                    .setCullState(NO_CULL).createCompositeState(false));

    private GravityRenderTypes(String name, VertexFormat format, VertexFormat.Mode mode, int size,
            boolean crumbling, boolean sorting, Runnable setup, Runnable clear) {
        super(name, format, mode, size, crumbling, sorting, setup, clear);
    }
}
