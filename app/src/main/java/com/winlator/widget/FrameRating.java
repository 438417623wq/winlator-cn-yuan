package com.winlator.widget;

import android.app.ActivityManager;
import android.content.Context;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.winlator.R;
import com.winlator.box64.Box64Utils;
import com.winlator.core.CPUStatus;
import com.winlator.core.StringUtils;

import java.util.Locale;

public class FrameRating extends FrameLayout implements Runnable {
    public enum Mode {DISABLED, SIMPLE, FULL}
    private long lastTime = 0;
    private short frameCount = 0;
    private float lastFPS = 0;
    private long lastDisplayTime = 0;
    private short displayFrameCount = 0;
    private float lastDisplayFPS = 0;
    private final LinearLayout fpsPanel;
    private final LinearLayout displayFPSPanel;
    private final LinearLayout gpuPanel;
    private final LinearLayout ramPanel;
    private final LinearLayout cpuPanel;
    private Mode mode = Mode.SIMPLE;
    private ActivityManager activityManager;
    private ActivityManager.MemoryInfo memoryInfo;
    private String cpuInfo = null;
    private byte tick = 0;
    private boolean showDisplayFPS = false;
    private int gameFpsLimit = 0;
    private int displayFpsLimit = 0;

    public FrameRating(Context context) {
        this(context, null);
    }

    public FrameRating(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public FrameRating(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setClickable(false);
        setFocusable(false);

        View view = LayoutInflater.from(context).inflate(R.layout.frame_rating, this, false);
        fpsPanel = view.findViewById(R.id.LLFPSPanel);
        displayFPSPanel = view.findViewById(R.id.LLDisplayFPSPanel);
        gpuPanel = view.findViewById(R.id.LLGPUPanel);
        ramPanel = view.findViewById(R.id.LLRAMPanel);
        cpuPanel = view.findViewById(R.id.LLCPUPanel);
        addView(view);
        setupPanels();
    }

    private void setupPanels() {
        switch (mode) {
            case DISABLED:
                fpsPanel.setVisibility(GONE);
                displayFPSPanel.setVisibility(GONE);
                gpuPanel.setVisibility(GONE);
                ramPanel.setVisibility(GONE);
                cpuPanel.setVisibility(GONE);
                
                activityManager = null;
                memoryInfo = null;
                break;
            case SIMPLE:
                fpsPanel.setVisibility(VISIBLE);
                displayFPSPanel.setVisibility(showDisplayFPS ? VISIBLE : GONE);
                gpuPanel.setVisibility(GONE);
                ramPanel.setVisibility(GONE);
                cpuPanel.setVisibility(GONE);

                activityManager = null;
                memoryInfo = null;
                break;
            case FULL:
                fpsPanel.setVisibility(VISIBLE);
                displayFPSPanel.setVisibility(showDisplayFPS ? VISIBLE : GONE);
                gpuPanel.setVisibility(VISIBLE);
                ramPanel.setVisibility(VISIBLE);
                cpuPanel.setVisibility(VISIBLE);

                Context context = getContext();
                activityManager = (ActivityManager)context.getSystemService(Context.ACTIVITY_SERVICE);
                memoryInfo = new ActivityManager.MemoryInfo();
                break;
        }
    }

    public Mode getMode() {
        return mode;
    }

    public void setMode(Mode mode) {
        this.mode = mode;
        setupPanels();
    }

    public void setGPUInfo(String gpuInfo) {
        post(() -> ((TextView)gpuPanel.getChildAt(1)).setText(gpuInfo));
    }

    public void setShowDisplayFPS(boolean showDisplayFPS) {
        this.showDisplayFPS = showDisplayFPS;
        setupPanels();
    }

    public void setFpsLimits(int gameFpsLimit, int displayFpsLimit) {
        this.gameFpsLimit = Math.max(0, gameFpsLimit);
        this.displayFpsLimit = Math.max(0, displayFpsLimit);
        post(this);
    }

    public void reset() {
        frameCount = 0;
        lastTime = SystemClock.elapsedRealtime();
        lastFPS = 0;
        displayFrameCount = 0;
        lastDisplayTime = lastTime;
        lastDisplayFPS = 0;
        tick = 2;
    }

    public void update() {
        long time = SystemClock.elapsedRealtime();
        if (time >= lastTime + 500) {
            lastFPS = ((float)(frameCount * 1000) / (time - lastTime));
            post(this);
            lastTime = time;
            frameCount = 0;
        }

        frameCount++;
    }

    public void updateDisplayFrame() {
        if (!showDisplayFPS) return;

        long time = SystemClock.elapsedRealtime();
        if (time >= lastDisplayTime + 500) {
            lastDisplayFPS = ((float)(displayFrameCount * 1000) / (time - lastDisplayTime));
            post(this);
            lastDisplayTime = time;
            displayFrameCount = 0;
        }

        displayFrameCount++;
    }

    @Override
    public void run() {
        if (getVisibility() == GONE) setVisibility(View.VISIBLE);
        ((TextView)fpsPanel.getChildAt(0)).setText(showDisplayFPS ? getLimitLabel("Game", gameFpsLimit) : "FPS:");
        ((TextView)fpsPanel.getChildAt(1)).setText(String.format(Locale.ENGLISH, "%.1f", lastFPS));
        ((TextView)displayFPSPanel.getChildAt(0)).setText(getLimitLabel("Display", displayFpsLimit));
        ((TextView)displayFPSPanel.getChildAt(1)).setText(String.format(Locale.ENGLISH, "%.1f", lastDisplayFPS));

        if (mode == Mode.FULL && ++tick >= 2) {
            tick = 0;
            activityManager.getMemoryInfo(memoryInfo);
            long usedMem = memoryInfo.totalMem - memoryInfo.availMem;
            String ramText = StringUtils.formatBytes(usedMem, false)+"/"+StringUtils.formatBytes(memoryInfo.totalMem);
            ((TextView)ramPanel.getChildAt(1)).setText(ramText);

            if (cpuInfo == null) {
                cpuInfo = "Box64 v"+ Box64Utils.extractBinVersion(cpuPanel.getContext());
            }

            short[] clockSpeeds = CPUStatus.getCurrentClockSpeeds();
            int maxClockSpeed = 0;
            for (short clockSpeed : clockSpeeds) maxClockSpeed = Math.max(maxClockSpeed, clockSpeed);
            ((TextView)cpuPanel.getChildAt(1)).setText(CPUStatus.formatClockSpeed(maxClockSpeed)+" | "+cpuInfo);
        }
    }

    private String getLimitLabel(String name, int limit) {
        return limit > 0 ? String.format(Locale.ENGLISH, "%s(%d):", name, limit) : name+"(off):";
    }
}
