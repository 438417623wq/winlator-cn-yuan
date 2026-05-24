package com.winlator.widget;

import android.annotation.SuppressLint;
import android.content.Context;
import android.opengl.GLSurfaceView;
import android.view.Choreographer;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.winlator.renderer.GLRenderer;
import com.winlator.xserver.XServer;

@SuppressLint("ViewConstructor")
public class XServerView extends GLSurfaceView {
    private final GLRenderer renderer;
    private final Choreographer.FrameCallback interpolationFrameCallback = this::doInterpolationFrame;
    private final Choreographer.FrameCallback renderFrameCallback = this::doRenderFrame;
    private boolean frameInterpolationEnabled = false;
    private boolean renderRequestScheduled = false;
    private boolean renderSchedulePostPending = false;
    private boolean interpolationFrameScheduled = false;
    private boolean pendingRenderRequest = false;
    private long lastRenderFrameTimeNs = 0;

    public XServerView(Context context, XServer xServer) {
        super(context);
        setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setEGLContextClientVersion(3);
        setEGLConfigChooser(8, 8, 8, 8, 0, 0);
        setPreserveEGLContextOnPause(true);
        renderer = new GLRenderer(this, xServer);
        setRenderer(renderer);
        setRenderMode(RENDERMODE_WHEN_DIRTY);
    }

    public GLRenderer getRenderer() {
        return renderer;
    }

    @Override
    public void requestRender() {
        int fpsLimit = renderer != null ? renderer.getFpsLimit() : 0;
        if (fpsLimit <= 0) {
            super.requestRender();
            return;
        }

        pendingRenderRequest = true;
        postScheduleRenderFrame();
    }

    public void setFrameInterpolationEnabled(boolean enabled) {
        if (frameInterpolationEnabled == enabled) return;
        frameInterpolationEnabled = enabled;
        setRenderMode(RENDERMODE_WHEN_DIRTY);
        requestRender();
        if (enabled) scheduleInterpolationFrame();
        else cancelInterpolationFrame();
    }

    public boolean isFrameInterpolationEnabled() {
        return frameInterpolationEnabled;
    }

    public void resetFramePacing() {
        lastRenderFrameTimeNs = 0;
        renderRequestScheduled = false;
        renderSchedulePostPending = false;
        pendingRenderRequest = false;
        if (frameInterpolationEnabled) scheduleInterpolationFrame();
    }

    public void requestContentRender() {
        requestRender();
    }

    private void postScheduleRenderFrame() {
        if (renderRequestScheduled || renderSchedulePostPending) return;
        renderSchedulePostPending = true;
        post(() -> {
            renderSchedulePostPending = false;
            scheduleRenderFrame();
        });
    }

    private void scheduleRenderFrame() {
        if (renderRequestScheduled) return;
        renderRequestScheduled = true;
        Choreographer.getInstance().postFrameCallback(renderFrameCallback);
    }

    private void doRenderFrame(long frameTimeNanos) {
        renderRequestScheduled = false;
        if (!pendingRenderRequest) return;

        int fpsLimit = renderer != null ? renderer.getFpsLimit() : 0;
        long targetIntervalNs = fpsLimit > 0 ? 1000000000L / fpsLimit : 0L;
        if (targetIntervalNs == 0L || lastRenderFrameTimeNs == 0L || frameTimeNanos - lastRenderFrameTimeNs >= targetIntervalNs) {
            pendingRenderRequest = false;
            lastRenderFrameTimeNs = frameTimeNanos;
            super.requestRender();
        }
        else scheduleRenderFrame();
    }

    private void scheduleInterpolationFrame() {
        if (!frameInterpolationEnabled || interpolationFrameScheduled) return;
        interpolationFrameScheduled = true;
        Choreographer.getInstance().postFrameCallback(interpolationFrameCallback);
    }

    private void cancelInterpolationFrame() {
        if (!interpolationFrameScheduled) return;
        Choreographer.getInstance().removeFrameCallback(interpolationFrameCallback);
        interpolationFrameScheduled = false;
    }

    private void doInterpolationFrame(long frameTimeNanos) {
        interpolationFrameScheduled = false;
        if (!frameInterpolationEnabled) return;

        requestRender();
        scheduleInterpolationFrame();
    }
}
