package com.winlator.renderer.effects;

import android.opengl.GLES20;

import com.winlator.renderer.Texture;
import com.winlator.renderer.material.ScreenMaterial;
import com.winlator.renderer.material.ShaderMaterial;

public class FrameSmoothingEffect extends Effect {
    public static final int MODE_STANDARD = 1;
    public static final int MODE_SMART = 2;

    private float strength = 0.0f;
    private int mode = MODE_SMART;
    private float interpolationPhase = 1.0f;
    private boolean generatedFrame = false;

    @Override
    public ScreenMaterial createMaterial() {
        final ShaderMaterial.Uniform strengthUniform = new ShaderMaterial.Uniform("strength");
        final ShaderMaterial.Uniform modeUniform = new ShaderMaterial.Uniform("mode");
        final ShaderMaterial.Uniform interpolationPhaseUniform = new ShaderMaterial.Uniform("interpolationPhase");
        final ShaderMaterial.Uniform generatedFrameUniform = new ShaderMaterial.Uniform("generatedFrame");

        return new ScreenMaterial() {
            @Override
            protected String getFragmentShader() {
                return String.join("\n",
                    "precision highp float;",

                    "uniform sampler2D screenTexture;",
                    "uniform sampler2D previousFrame;",
                    "uniform vec2 resolution;",
                    "uniform float strength;",
                    "uniform float interpolationPhase;",
                    "uniform int mode;",
                    "uniform bool previousFrameReady;",
                    "uniform bool generatedFrame;",

                    "varying vec2 vUV;",

                    "float colorDistance(vec3 a, vec3 b) {",
                        "vec3 d = a - b;",
                        "return dot(d, d);",
                    "}",

                    "vec2 estimateMotion(vec3 currentColor, vec2 texel) {",
                        "vec2 bestOffset = vec2(0.0);",
                        "float bestDiff = colorDistance(currentColor, texture2D(previousFrame, vUV).rgb);",
                        "for (int y = -2; y <= 2; y++) {",
                            "for (int x = -2; x <= 2; x++) {",
                                "vec2 offset = vec2(float(x), float(y)) * texel;",
                                "vec3 candidate = texture2D(previousFrame, clamp(vUV + offset, vec2(0.0), vec2(1.0))).rgb;",
                                "float diff = colorDistance(currentColor, candidate);",
                                "if (diff < bestDiff) {",
                                    "bestDiff = diff;",
                                    "bestOffset = offset;",
                                "}",
                            "}",
                        "}",
                        "return bestOffset;",
                    "}",

                    "void main() {",
                        "vec4 currentColor = texture2D(screenTexture, vUV);",
                        "if (!previousFrameReady || strength <= 0.0) {",
                            "gl_FragColor = currentColor;",
                            "return;",
                        "}",
                        "vec2 texel = 1.0 / resolution;",
                        "vec2 motion = mode == 2 ? estimateMotion(currentColor.rgb, texel) : vec2(0.0);",
                        "float phase = generatedFrame ? clamp(interpolationPhase, 0.0, 1.0) : 1.0;",
                        "vec4 previousColor = texture2D(previousFrame, clamp(vUV + motion * (1.0 - phase), vec2(0.0), vec2(1.0)));",
                        "float diff = length(currentColor.rgb - previousColor.rgb);",
                        "float lumaDiff = abs(dot(currentColor.rgb - previousColor.rgb, vec3(0.299, 0.587, 0.114)));",
                        "float motionGuard = 1.0 - smoothstep(mode == 2 ? 0.20 : 0.16, mode == 2 ? 0.55 : 0.45, diff);",
                        "float lumaGuard = 1.0 - smoothstep(0.10, 0.28, lumaDiff);",
                        "float sceneCutGuard = 1.0 - smoothstep(0.35, 0.70, diff + lumaDiff);",
                        "float blend = generatedFrame ? phase : strength;",
                        "blend = mix(1.0, blend, motionGuard * lumaGuard * sceneCutGuard);",
                        "gl_FragColor = mix(previousColor, currentColor, blend);",
                    "}"
                );
            }

            @Override
            public void use() {
                super.use();
                setUniformFloat(strengthUniform, strength);
                setUniformFloat(interpolationPhaseUniform, interpolationPhase);
                setUniformInt(modeUniform, mode);
                setUniformBool(generatedFrameUniform, generatedFrame);
            }
        };
    }

    @Override
    public void prepareRender(ScreenMaterial material, Texture previousFrameTexture, boolean previousFrameReady) {
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, previousFrameTexture != null ? previousFrameTexture.getTextureId() : 0);
        material.setUniformInt(new ShaderMaterial.Uniform("previousFrame"), 1);
        material.setUniformBool(new ShaderMaterial.Uniform("previousFrameReady"), previousFrameReady);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
    }

    public float getStrength() {
        return strength;
    }

    public void setStrength(float strength) {
        this.strength = strength;
    }

    public int getMode() {
        return mode;
    }

    public void setMode(int mode) {
        this.mode = mode;
    }

    public void setInterpolationPhase(float interpolationPhase) {
        this.interpolationPhase = interpolationPhase;
    }

    public void setGeneratedFrame(boolean generatedFrame) {
        this.generatedFrame = generatedFrame;
    }
}
