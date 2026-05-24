package com.winlator.renderer.effects;

import com.winlator.renderer.Texture;
import com.winlator.renderer.material.ScreenMaterial;

public abstract class Effect {
    private ScreenMaterial material;

    protected ScreenMaterial createMaterial() {
        return null;
    }

    public ScreenMaterial getMaterial() {
        if (material == null) material = createMaterial();
        return material;
    }

    public void prepareRender(ScreenMaterial material, Texture previousFrameTexture, boolean previousFrameReady) {
    }
}
