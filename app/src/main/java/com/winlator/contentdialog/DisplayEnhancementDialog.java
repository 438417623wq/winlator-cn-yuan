package com.winlator.contentdialog;

import android.content.SharedPreferences;
import android.graphics.drawable.ColorDrawable;
import android.view.View;
import android.view.Window;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.Toast;

import androidx.preference.PreferenceManager;

import com.winlator.R;
import com.winlator.XServerDisplayActivity;
import com.winlator.core.AppUtils;
import com.winlator.renderer.GLRenderer;
import com.winlator.renderer.effects.DisplayEnhancementEffect;
import com.winlator.renderer.effects.FrameSmoothingEffect;
import com.winlator.widget.SeekBar;

public class DisplayEnhancementDialog extends ContentDialog {
    public static final String PREF_UPSCALE_MODE = "display_enhancement_upscale_mode";
    public static final String PREF_FRAME_SMOOTHING_MODE = "display_enhancement_frame_smoothing_mode";
    public static final String PREF_STYLE_MODE = "display_enhancement_style_mode";
    public static final String PREF_SHOW_FPS = "display_enhancement_show_fps";
    public static final String PREF_DISPLAY_FPS_LIMIT = "display_enhancement_display_fps_limit";
    public static final String PREF_GAME_FPS_LIMIT = "display_enhancement_game_fps_limit";

    private final XServerDisplayActivity activity;
    private final SharedPreferences preferences;
    private final Spinner sPictureStyle;
    private final Spinner sFrameInterpolation;
    private final SeekBar sbDisplayFpsLimit;
    private final SeekBar sbGameFpsLimit;
    private final Switch swShowFPS;
    private final Button btSyncGameFpsLimit;
    private boolean loading = true;

    public DisplayEnhancementDialog(XServerDisplayActivity activity) {
        super(activity, R.layout.display_enhancement_dialog);
        this.activity = activity;
        this.preferences = PreferenceManager.getDefaultSharedPreferences(activity);

        setTitle(null);
        findViewById(R.id.LLBottomBar).setVisibility(View.GONE);
        getContentView().setPadding(0, 0, 0, 0);
        getContentView().setBackgroundColor(0xff26282e);

        Window window = getWindow();
        if (window != null) window.setBackgroundDrawable(new ColorDrawable(0xff26282e));

        FrameLayout frameLayout = findViewById(R.id.FrameLayout);
        frameLayout.getLayoutParams().width = AppUtils.getPreferredDialogWidth(activity);
        frameLayout.getLayoutParams().height = (int)(AppUtils.getScreenHeight() * 0.82f);

        sPictureStyle = findViewById(R.id.SPictureStyle);
        sFrameInterpolation = findViewById(R.id.SFrameInterpolation);
        sbDisplayFpsLimit = findViewById(R.id.SBDisplayFpsLimit);
        sbGameFpsLimit = findViewById(R.id.SBGameFpsLimit);
        swShowFPS = findViewById(R.id.SWShowFPS);
        btSyncGameFpsLimit = findViewById(R.id.BTSyncGameFpsLimit);

        setupSpinner(sPictureStyle, R.array.display_enhancement_style_entries, preferences.getInt(PREF_STYLE_MODE, 0));
        setupSpinner(sFrameInterpolation, R.array.display_enhancement_frame_interpolation_entries, preferences.getInt(PREF_FRAME_SMOOTHING_MODE, 0));
        sbDisplayFpsLimit.setValue(preferences.getInt(PREF_DISPLAY_FPS_LIMIT, 0));
        sbGameFpsLimit.setValue(preferences.getInt(PREF_GAME_FPS_LIMIT, 0));
        swShowFPS.setChecked(preferences.getBoolean(PREF_SHOW_FPS, false));
        loading = false;

        findViewById(R.id.BTBack).setOnClickListener((v) -> dismiss());
        findViewById(R.id.LLShowFPS).setOnClickListener((v) -> swShowFPS.setChecked(!swShowFPS.isChecked()));
        btSyncGameFpsLimit.setOnClickListener((v) -> {
            loading = true;
            sbGameFpsLimit.setValue(sbDisplayFpsLimit.getValue());
            loading = false;
            saveAndApply();
        });

        android.widget.AdapterView.OnItemSelectedListener listener = new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                saveAndApply();
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
            }
        };
        sPictureStyle.setOnItemSelectedListener(listener);
        sFrameInterpolation.setOnItemSelectedListener(listener);
        sbDisplayFpsLimit.setOnValueChangeListener((seekBar, value) -> saveAndApply());
        sbGameFpsLimit.setOnValueChangeListener((seekBar, value) -> saveAndApply());
        swShowFPS.setOnCheckedChangeListener((buttonView, isChecked) -> saveAndApply());
    }

    private void setupSpinner(Spinner spinner, int entriesResId, int position) {
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(activity, entriesResId, android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setSelection(Math.max(0, Math.min(position, adapter.getCount() - 1)));
    }

    private void saveAndApply() {
        if (loading) return;

        int previousGameFpsLimit = preferences.getInt(PREF_GAME_FPS_LIMIT, 0);
        int styleMode = sPictureStyle.getSelectedItemPosition();
        int frameSmoothingMode = sFrameInterpolation.getSelectedItemPosition();
        boolean showFPS = swShowFPS.isChecked();
        int displayFpsLimit = Math.round(sbDisplayFpsLimit.getValue());
        int gameFpsLimit = Math.round(sbGameFpsLimit.getValue());

        preferences.edit()
            .putInt(PREF_STYLE_MODE, styleMode)
            .putInt(PREF_FRAME_SMOOTHING_MODE, frameSmoothingMode)
            .putBoolean(PREF_SHOW_FPS, showFPS)
            .putInt(PREF_DISPLAY_FPS_LIMIT, displayFpsLimit)
            .putInt(PREF_GAME_FPS_LIMIT, gameFpsLimit)
            .apply();

        apply(activity, frameSmoothingMode, styleMode, showFPS, displayFpsLimit, gameFpsLimit);
        if (previousGameFpsLimit != gameFpsLimit) {
            Toast.makeText(activity, "游戏帧率限制已写入 DXVK 配置，重启游戏后生效", Toast.LENGTH_SHORT).show();
        }
    }

    public static void applySaved(XServerDisplayActivity activity) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(activity);
        int displayFpsLimit = preferences.getInt(PREF_DISPLAY_FPS_LIMIT, preferences.getInt("display_enhancement_fps_limit", 0));
        int gameFpsLimit = preferences.getInt(PREF_GAME_FPS_LIMIT, 0);
        apply(
            activity,
            preferences.getInt(PREF_FRAME_SMOOTHING_MODE, 0),
            preferences.getInt(PREF_STYLE_MODE, 0),
            preferences.getBoolean(PREF_SHOW_FPS, false),
            displayFpsLimit,
            gameFpsLimit
        );
    }

    private static void apply(XServerDisplayActivity activity, int frameSmoothingMode, int styleMode, boolean showFPS, int displayFpsLimit, int gameFpsLimit) {
        GLRenderer renderer = activity.getXServerView().getRenderer();

        DisplayEnhancementEffect enhancementEffect = renderer.effectComposer.getEffect(DisplayEnhancementEffect.class);
        FrameSmoothingEffect smoothingEffect = renderer.effectComposer.getEffect(FrameSmoothingEffect.class);
        if (enhancementEffect != null) renderer.effectComposer.removeEffect(enhancementEffect);
        if (smoothingEffect != null) renderer.effectComposer.removeEffect(smoothingEffect);

        if (frameSmoothingMode > 0) {
            if (smoothingEffect == null) smoothingEffect = new FrameSmoothingEffect();
            smoothingEffect.setMode(frameSmoothingMode == 2 ? FrameSmoothingEffect.MODE_SMART : FrameSmoothingEffect.MODE_STANDARD);
            smoothingEffect.setStrength(frameSmoothingMode == 2 ? 0.16f : 0.10f);
            renderer.effectComposer.addEffect(smoothingEffect);
        }

        boolean needsEnhancement = styleMode > 0;
        if (needsEnhancement) {
            if (enhancementEffect == null) enhancementEffect = new DisplayEnhancementEffect();
            enhancementEffect.setSharpness(styleMode == DisplayEnhancementEffect.STYLE_SHARP ? 0.35f : 0.0f);
            enhancementEffect.setStyle(styleMode);
            renderer.effectComposer.addEffect(enhancementEffect);
        }

        activity.setDisplayEnhancementFPSVisible(showFPS);
        activity.setDisplayEnhancementFpsLimits(gameFpsLimit, displayFpsLimit);
        renderer.setFpsLimit(displayFpsLimit);
        activity.getXServerView().setFrameInterpolationEnabled(frameSmoothingMode > 0);
        DXVKConfigDialog.setRuntimeFrameLimit(activity, gameFpsLimit);
    }

}
