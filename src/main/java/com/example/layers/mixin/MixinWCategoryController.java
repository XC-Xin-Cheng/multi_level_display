package com.example.layers.mixin;

import com.example.layers.gui.LayerGui;
import meteordevelopment.meteorclient.gui.widgets.containers.WContainer;
import meteordevelopment.meteorclient.gui.widgets.containers.WWindow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * 往 ModulesScreen 的分类窗口控制器里挂载层窗口。
 */
@Mixin(targets = "meteordevelopment.meteorclient.gui.screens.ModulesScreen$WCategoryController")
public abstract class MixinWCategoryController extends WContainer {
    @Shadow
    @Final
    public List<WWindow> windows;

    @Inject(method = "init", at = @At("TAIL"))
    private void onInit(CallbackInfo ci) {
        LayerGui.attach(this, windows);
    }
}
