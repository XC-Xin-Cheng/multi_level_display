package com.example.layers.layers;

import com.example.layers.LayersAddon;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;

import java.util.HashSet;
import java.util.Set;

/**
 * 一个显示层。showAll 为 true 时显示全部模块；否则只显示 modules 白名单里的模块（按模块名记录）。
 */
public class Layer {
    public String name;
    public boolean showAll;
    public Set<String> modules = new HashSet<>();

    /** 保存的模块状态：statesSaved 为 true 时，enabled 记录上次离开本层时处于开启状态的模块名。 */
    public boolean statesSaved;
    public Set<String> enabled = new HashSet<>();

    public Layer() {
    }

    public Layer(String name, boolean showAll) {
        this.name = name;
        this.showAll = showAll;
    }

    public boolean shows(Module module) {
        return showAll || modules.contains(module.name);
    }

    /** 记录当前各模块的启停状态（离开本层切换到其他层时调用）。 */
    public void captureStates() {
        statesSaved = true;
        enabled.clear();
        for (Module m : Modules.get().getAll()) {
            try {
                if (m.isActive()) enabled.add(m.name);
            } catch (Exception e) {
                LayersAddon.LOG.error("Failed to read state of module {}", m.name, e);
            }
        }
    }

    /** 恢复保存的模块状态（进入本层时调用）；若从未保存过则不做任何事。 */
    public void applyStates() {
        if (!statesSaved) return;

        for (Module m : Modules.get().getAll()) {
            try {
                if (enabled.contains(m.name)) {
                    if (!m.isActive()) m.enable();
                } else if (m.isActive()) {
                    m.disable();
                }
            } catch (Exception e) {
                // 一个有问题的模块（如其他扩展的 onActivate 抛异常）不应中断整个层的切换，更不应崩游戏
                LayersAddon.LOG.error("Failed to apply saved state to module {}", m.name, e);
            }
        }
    }

    /**
     * 勾选/取消勾选一个模块。从“显示全部”状态取消勾选时，先把它转成包含全部模块的白名单，再隐藏该模块。
     */
    public void toggle(Module module) {
        if (showAll) {
            showAll = false;
            for (Module m : Modules.get().getAll()) {
                modules.add(m.name);
            }
        }

        if (!modules.remove(module.name)) modules.add(module.name);
    }
}
