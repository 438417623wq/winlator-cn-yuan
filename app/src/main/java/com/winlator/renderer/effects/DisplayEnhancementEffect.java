package com.winlator.renderer.effects;

import com.winlator.renderer.material.ScreenMaterial;
import com.winlator.renderer.material.ShaderMaterial;

public class DisplayEnhancementEffect extends Effect {
    public static final int STYLE_ORIGINAL = 0;
    public static final int STYLE_VIVID = 1;
    public static final int STYLE_SOFT = 2;
    public static final int STYLE_SHADOW_BOOST = 3;
    public static final int STYLE_SHARP = 4;
    public static final int STYLE_HDR = 5;

    private float sharpness = 0.0f;
    private int style = STYLE_ORIGINAL;

    @Override
    public ScreenMaterial createMaterial() {
        final ShaderMaterial.Uniform sharpnessUniform = new ShaderMaterial.Uniform("sharpness");
        final ShaderMaterial.Uniform styleUniform = new ShaderMaterial.Uniform("style");

        return new ScreenMaterial() {
            @Override
            protected String getFragmentShader() {
                return String.join("\n",
                    "precision highp float;",

                    "uniform sampler2D screenTexture;",
                    "uniform vec2 resolution;",
                    "uniform float sharpness;",
                    "uniform int style;",

                    "varying vec2 vUV;",

                    "vec3 adjustSaturation(vec3 color, float saturation) {",
                        "float gray = dot(color, vec3(0.299, 0.587, 0.114));",
                        "return mix(vec3(gray), color, saturation);",
                    "}",

                    "vec3 applyStyle(vec3 color) {",
                        "if (style == 1) {",
                            "color = adjustSaturation(color, 1.18);",
                            "color = (color - 0.5) * 1.08 + 0.5;",
                        "}",
                        "else if (style == 2) {",
                            "color = adjustSaturation(color, 0.88);",
                            "color = mix(color, vec3(dot(color, vec3(0.299, 0.587, 0.114))), 0.06);",
                        "}",
                        "else if (style == 3) {",
                            "float luma = dot(color, vec3(0.299, 0.587, 0.114));",
                            "float lift = (1.0 - smoothstep(0.0, 0.55, luma)) * 0.18;",
                            "color += vec3(lift);",
                            "color = (color - 0.5) * 1.05 + 0.5;",
                        "}",
                        "else if (style == 4) {",
                            "color = adjustSaturation(color, 1.08);",
                            "color = (color - 0.5) * 1.12 + 0.5;",
                        "}",
                        "else if (style == 5) {",
                            "float luma = dot(color, vec3(0.299, 0.587, 0.114));",
                            "vec3 shadowLift = color + vec3((1.0 - smoothstep(0.0, 0.45, luma)) * 0.12);",
                            "vec3 highlightRollOff = shadowLift / (shadowLift + vec3(0.18));",
                            "color = mix(shadowLift, highlightRollOff, 0.32);",
                            "color = adjustSaturation(color, 1.14);",
                            "color = (color - 0.5) * 1.10 + 0.5;",
                        "}",
                        "return clamp(color, 0.0, 1.0);",
                    "}",

                    "void main() {",
                        "vec2 texel = 1.0 / resolution;",
                        "vec3 center = texture2D(screenTexture, vUV).rgb;",
                        "vec3 north = texture2D(screenTexture, vUV + vec2(0.0, -texel.y)).rgb;",
                        "vec3 south = texture2D(screenTexture, vUV + vec2(0.0, texel.y)).rgb;",
                        "vec3 west = texture2D(screenTexture, vUV + vec2(-texel.x, 0.0)).rgb;",
                        "vec3 east = texture2D(screenTexture, vUV + vec2(texel.x, 0.0)).rgb;",
                        "vec3 blur = (north + south + west + east) * 0.25;",
                        "float effectiveSharpness = sharpness + (style == 4 ? 0.35 : 0.0);",
                        "vec3 color = center + (center - blur) * effectiveSharpness;",
                        "color = applyStyle(color);",
                        "gl_FragColor = vec4(color, texture2D(screenTexture, vUV).a);",
                    "}"
                );
            }

            @Override
            public void use() {
                super.use();
                setUniformFloat(sharpnessUniform, sharpness);
                setUniformInt(styleUniform, style);
            }
        };
    }

    public float getSharpness() {
        return sharpness;
    }

    public void setSharpness(float sharpness) {
        this.sharpness = sharpness;
    }

    public int getStyle() {
        return style;
    }

    public void setStyle(int style) {
        this.style = style;
    }
}
