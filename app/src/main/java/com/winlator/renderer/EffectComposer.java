package com.winlator.renderer;

import android.opengl.GLES20;

import com.winlator.renderer.effects.Effect;
import com.winlator.renderer.effects.FrameSmoothingEffect;
import com.winlator.renderer.material.ScreenMaterial;

import java.util.ArrayList;

public class EffectComposer {
    private final GLRenderer renderer;
    private RenderTarget passBufferA = null;
    private RenderTarget passBufferB = null;
    private RenderTarget currentGameFrame = null;
    private RenderTarget previousGameFrame = null;
    private boolean currentGameFrameReady = false;
    private boolean previousGameFrameReady = false;
    private short bufferWidth = 0;
    private short bufferHeight = 0;
    private long lastGameFrameTimeNs = 0;
    private long averageGameFrameIntervalNs = 33333333L;
    private int generatedFrameIndex = 0;
    private final ArrayList<Effect> effects = new ArrayList<>();

    public EffectComposer(GLRenderer renderer) {
        this.renderer = renderer;
    }

    public synchronized void addEffect(Effect effect) {
        if (!effects.contains(effect)) effects.add(effect);
        renderer.xServerView.requestRender();
    }

    public synchronized void removeEffect(Effect effect) {
        effects.remove(effect);
        renderer.xServerView.requestRender();
    }

    public synchronized  <T extends Effect> T getEffect(Class<T> effectClass) {
        for (Effect effect : effects) {
            if (effect.getClass() == effectClass) return (T)effect;
        }
        return null;
    }

    public synchronized boolean hasEffects() {
        return !effects.isEmpty();
    }

    public synchronized boolean hasFrameInterpolation() {
        return getEffect(FrameSmoothingEffect.class) != null;
    }

    private void renderEffect(Effect effect, int sourceTextureId, boolean renderToScreen, RenderTarget target, float interpolationPhase, boolean generatedFrame) {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, renderToScreen ? 0 : target.getFramebuffer());
        GLES20.glViewport(0, 0, renderer.surfaceWidth, renderer.surfaceHeight);
        renderer.viewportNeedsUpdate = true;
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

        ScreenMaterial material = effect.getMaterial();
        material.use();
        renderer.quadVertices.bind(material.programId);
        material.setUniformVec2(material.uniforms.resolution, renderer.surfaceWidth, renderer.surfaceHeight);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, sourceTextureId);
        material.setUniformInt(material.uniforms.screenTexture, 0);
        if (effect instanceof FrameSmoothingEffect) {
            FrameSmoothingEffect frameSmoothingEffect = (FrameSmoothingEffect)effect;
            frameSmoothingEffect.setInterpolationPhase(interpolationPhase);
            frameSmoothingEffect.setGeneratedFrame(generatedFrame);
        }
        effect.prepareRender(material, previousGameFrame, previousGameFrameReady);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, renderer.quadVertices.count());
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        renderer.quadVertices.disable();
    }

    private void initBuffers() {
        if (bufferWidth == renderer.surfaceWidth && bufferHeight == renderer.surfaceHeight && currentGameFrame != null) return;

        passBufferA = new RenderTarget();
        passBufferA.allocateFramebuffer(renderer.surfaceWidth, renderer.surfaceHeight);
        passBufferB = new RenderTarget();
        passBufferB.allocateFramebuffer(renderer.surfaceWidth, renderer.surfaceHeight);
        currentGameFrame = new RenderTarget();
        currentGameFrame.allocateFramebuffer(renderer.surfaceWidth, renderer.surfaceHeight);
        previousGameFrame = new RenderTarget();
        previousGameFrame.allocateFramebuffer(renderer.surfaceWidth, renderer.surfaceHeight);

        currentGameFrameReady = false;
        previousGameFrameReady = false;
        bufferWidth = renderer.surfaceWidth;
        bufferHeight = renderer.surfaceHeight;
    }

    private void captureGameFrame() {
        long now = System.nanoTime();
        if (lastGameFrameTimeNs != 0) {
            long interval = now - lastGameFrameTimeNs;
            if (interval > 5000000L && interval < 250000000L) {
                averageGameFrameIntervalNs = (averageGameFrameIntervalNs * 3 + interval) / 4;
            }
        }
        lastGameFrameTimeNs = now;
        generatedFrameIndex = 0;

        if (currentGameFrameReady) {
            RenderTarget tmp = previousGameFrame;
            previousGameFrame = currentGameFrame;
            currentGameFrame = tmp;
            previousGameFrameReady = true;
        }

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, currentGameFrame.getFramebuffer());
        renderer.drawFrame(false);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        currentGameFrameReady = true;
    }

    private float getInterpolationPhase(boolean capturedGameFrame) {
        if (!previousGameFrameReady) return 1.0f;
        if (!hasFrameInterpolation()) return 1.0f;

        int fpsLimit = renderer.getFpsLimit();
        long displayIntervalNs = fpsLimit > 0 ? 1000000000L / fpsLimit : 16666666L;
        if (!capturedGameFrame) generatedFrameIndex++;
        float phase = (float)(generatedFrameIndex * displayIntervalNs) / (float)Math.max(1L, averageGameFrameIntervalNs);
        if (capturedGameFrame) phase = (float)displayIntervalNs / (float)Math.max(1L, averageGameFrameIntervalNs);
        return Math.max(0.12f, Math.min(1.0f, phase));
    }

    public synchronized void render() {
        initBuffers();

        boolean capturedGameFrame = false;
        if (!currentGameFrameReady || renderer.consumeGameFrameAvailable()) {
            captureGameFrame();
            capturedGameFrame = true;
        }

        float interpolationPhase = getInterpolationPhase(capturedGameFrame);
        boolean generatedFrame = previousGameFrameReady && hasFrameInterpolation();
        int sourceTextureId = currentGameFrame.getTextureId();
        RenderTarget target = passBufferA;
        for (int i = 0; i < effects.size(); i++) {
            Effect effect = effects.get(i);
            boolean renderToScreen = i == effects.size() - 1;
            renderEffect(effect, sourceTextureId, renderToScreen, target, interpolationPhase, generatedFrame);

            if (!renderToScreen) {
                sourceTextureId = target.getTextureId();
                target = target == passBufferA ? passBufferB : passBufferA;
            }
        }

        if (renderer.isCursorVisible()) renderer.renderCursorOnTop();
    }
}
