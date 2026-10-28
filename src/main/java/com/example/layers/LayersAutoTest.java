package com.example.layers;

import com.example.layers.gui.LayerGui;
import com.example.layers.layers.Layer;
import com.example.layers.layers.Layers;
import net.minecraft.client.texture.NativeImage;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.gui.GuiThemes;
import meteordevelopment.meteorclient.gui.screens.ModulesScreen;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.ScreenshotRecorder;
import org.slf4j.Logger;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 仅在以 -Dlayers.autotest 启动时生效：
 * =true  完整测试（会改动模块状态和层数据，结束后自动恢复现场），仅用于 dev 环境；
 * =safe  只验证挂载和截图，不改任何数据，用于真实游戏环境。
 */
public class LayersAutoTest {
    private int tick;
    private int stage;
    private int stageTick;

    private final boolean safe;
    private Map<String, Boolean> stateSnapshot;
    private int initialLayerCount;
    private int initialActiveIndex;

    private final File screenshotsDir = new File(".").getAbsoluteFile();
    private Set<String> knownScreenshots = Set.of();

    public LayersAutoTest() {
        this.safe = "safe".equalsIgnoreCase(System.getProperty("layers.autotest"));
    }

    public static void init() {
        String mode = System.getProperty("layers.autotest");
        if (mode != null && !mode.isEmpty()) {
            LayersAddon.LOG.info("LAYERS-TEST: auto test enabled, mode={}", "safe".equalsIgnoreCase(mode) ? "safe" : "full");
            MeteorClient.EVENT_BUS.subscribe(new LayersAutoTest());
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        MinecraftClient mc = MeteorClient.mc;
        tick++;

        switch (stage) {
            case 0 -> {
                // 主菜单本身有 TitleScreen，所以只判断不在世界里，等标题界面加载完
                if (mc.world == null && tick > 300) {
                    LayersAddon.LOG.info("LAYERS-TEST: opening ModulesScreen (current screen: {})",
                        mc.currentScreen == null ? "null" : mc.currentScreen.getClass().getSimpleName());
                    mc.setScreen(new ModulesScreen(GuiThemes.get()));
                    enterStage(1);
                }
            }
            case 1 -> {
                if (ticksInStage() >= 30) {
                    Logger log = LayersAddon.LOG;

                    // 记录现场快照（full 模式结束后恢复）
                    stateSnapshot = new HashMap<>();
                    for (Module module : Modules.get().getAll()) {
                        stateSnapshot.put(module.name, module.isActive());
                    }
                    initialLayerCount = Layers.get().layers.size();
                    initialActiveIndex = Layers.get().activeIndex;

                    LayerGui.setSectionsExpanded(true); // 展开分类，截图里能看到批量开关
                    log.info("LAYERS-TEST report[默认层]: {}", LayerGui.selfTest());
                    requestScreenshot();
                    enterStage(2);
                }
            }
            case 2 -> {
                // 等展开状态渲染、官方截图落盘后收集改名
                if (ticksInStage() >= 20) {
                    collectScreenshot("layers-test-1.png");

                    if (safe) {
                        LayersAddon.LOG.info("LAYERS-TEST: safe mode, skipping data-changing stages");
                        enterStage(10);
                        return;
                    }

                    // add() 会自动切换到新层（空层）
                    if (Layers.get().layers.size() < 2) Layers.get().add("Layer");
                    enterStage(3);
                }
            }
            case 3 -> {
                if (ticksInStage() >= 15) {
                    LayersAddon.LOG.info("LAYERS-TEST report[空层]: {} (期望 categoryWindows=0)",
                        LayerGui.selfTest());
                    requestScreenshot();
                    enterStage(4);
                }
            }
            case 4 -> {
                if (ticksInStage() >= 20) {
                    collectScreenshot("layers-test-empty.png");
                    // 勾选列表里第一个模块的显示勾选框，走真实点击路径（action 回调）
                    var checkbox = LayerGui.firstCheckbox();
                    if (checkbox != null) {
                        checkbox.action.run();
                        LayersAddon.LOG.info("LAYERS-TEST report[空层勾选一个模块后]: {} (期望 moduleRows=1)",
                            LayerGui.selfTest());
                    } else {
                        LayersAddon.LOG.error("LAYERS-TEST: firstCheckbox() returned null!");
                    }
                    enterStage(5);
                }
            }
            case 5 -> {
                if (ticksInStage() >= 15) {
                    var checkbox = LayerGui.firstCheckbox();
                    if (checkbox != null) checkbox.action.run();
                    LayersAddon.LOG.info("LAYERS-TEST report[再次取消勾选后]: {} (期望 moduleRows=0)",
                        LayerGui.selfTest());
                    enterStage(6);
                }
            }
            case 6 -> {
                if (ticksInStage() >= 15) {
                    Layers.get().next(); // 环绕切回 Default（showAll）
                    LayersAddon.LOG.info("LAYERS-TEST report[切回 Default]: {} (期望 showAll=true, moduleRows=167)",
                        LayerGui.selfTest());

                    // 在 showAll 层取消勾选：应转成白名单（全部模块 - 1），该模块从列里消失
                    var checkbox = LayerGui.firstCheckbox();
                    if (checkbox != null) checkbox.action.run();
                    LayersAddon.LOG.info("LAYERS-TEST report[Default 取消勾选后]: {} (期望 showAll=false, whitelist={}, moduleRows=166)",
                        LayerGui.selfTest(), Modules.get().getAll().size() - 1);
                    enterStage(7);
                }
            }
            case 7 -> {
                if (ticksInStage() >= 15) {
                    // 再勾选回来：白名单 = 全部模块，行数恢复
                    var checkbox = LayerGui.firstCheckbox();
                    if (checkbox != null) checkbox.action.run();
                    LayersAddon.LOG.info("LAYERS-TEST report[Default 重新勾选后]: {} (期望 whitelist={})",
                        LayerGui.selfTest(), Modules.get().getAll().size());

                    // 重名自动去重 + 删除
                    Layer added = Layers.get().add("Layer");
                    Layer added2 = Layers.get().add("Layer");
                    LayersAddon.LOG.info("LAYERS-TEST naming: add(\"Layer\") x2 -> \"{}\", \"{}\"", added.name, added2.name);
                    Layers.get().remove(added.name);
                    Layers.get().remove(added2.name);
                    LayersAddon.LOG.info("LAYERS-TEST report[清理后]: {}", LayerGui.selfTest());
                    enterStage(8);
                }
            }
            case 8 -> {
                if (ticksInStage() >= 15) {
                    // 状态保存/恢复的不变量：切换层后，模块状态必须等于目标层保存的快照
                    Module sprint = Modules.get().get("sprint");
                    if (sprint != null) {
                        Layers layers = Layers.get();

                        sprint.enable();
                        layers.getActive().captureStates(); // 强制刷新当前层快照，让测试不依赖历史状态
                        LayersAddon.LOG.info("LAYERS-TEST states: sprint enabled on layer \"{}\"", layers.getActive().name);

                        layers.next();
                        boolean expected = layers.getActive().enabled.contains("sprint");
                        LayersAddon.LOG.info("LAYERS-TEST states: switch to \"{}\": isActive={} expected={} -> {}",
                            layers.getActive().name, sprint.isActive(), expected,
                            sprint.isActive() == expected ? "OK" : "MISMATCH");

                        sprint.disable(); // 在新层改变状态，再切回去
                        layers.next();
                        boolean expectedBack = layers.getActive().enabled.contains("sprint");
                        LayersAddon.LOG.info("LAYERS-TEST states: switch to \"{}\": isActive={} expected={} -> {}",
                            layers.getActive().name, sprint.isActive(), expectedBack,
                            sprint.isActive() == expectedBack ? "OK" : "MISMATCH");
                    } else {
                        LayersAddon.LOG.error("LAYERS-TEST states: sprint module not found");
                    }
                    enterStage(9);
                }
            }
            case 9 -> {
                if (ticksInStage() >= 15) {
                    // 批量显示开关：一键隐藏 / 显示整个分类（“功能列表”）的全部模块
                    Category category = null;
                    for (Category c : Modules.loopCategories()) {
                        category = c;
                        break;
                    }

                    if (category != null) {
                        Layer active = Layers.get().getActive();
                        int size = Modules.get().getGroup(category).size();
                        int shown = 0;
                        for (Module m : Modules.get().getGroup(category)) {
                            if (active.shows(m)) shown++;
                        }

                        int before = LayerGui.moduleRowCount();
                        LayerGui.setCategoryVisible(category, false);
                        int afterOff = LayerGui.moduleRowCount();
                        LayerGui.setCategoryVisible(category, true);
                        int afterOn = LayerGui.moduleRowCount();

                        boolean ok = afterOff == before - shown && afterOn == before + (size - shown);
                        LayersAddon.LOG.info("LAYERS-TEST category-toggle [{}]: before={} shown={} afterOff={} (期望 {}) afterOn={} (期望 {}) -> {}",
                            category.name, before, shown, afterOff, before - shown, afterOn, before + (size - shown),
                            ok ? "OK" : "MISMATCH");
                    } else {
                        LayersAddon.LOG.error("LAYERS-TEST category-toggle: no categories found");
                    }
                    enterStage(10);
                }
            }
            case 10 -> {
                if (ticksInStage() >= 20) {
                    requestScreenshot();
                    enterStage(11);
                }
            }
            case 11 -> {
                if (ticksInStage() >= 20) {
                    collectScreenshot("layers-test-2.png");

                    // full 模式：恢复测试前的现场（模块状态、层数据）
                    if (!safe && stateSnapshot != null) {
                        Layers layers = Layers.get();

                        Layer testLayer = layers.byName("Layer");
                        if (testLayer != null) layers.remove(testLayer.name);

                        layers.activeIndex = Math.min(initialActiveIndex, layers.layers.size() - 1);

                        for (Map.Entry<String, Boolean> entry : stateSnapshot.entrySet()) {
                            Module module = Modules.get().get(entry.getKey());
                            if (module == null) continue;
                            try {
                                if (entry.getValue() && !module.isActive()) module.enable();
                                else if (!entry.getValue() && module.isActive()) module.disable();
                            } catch (Exception e) {
                                LayersAddon.LOG.error("LAYERS-TEST: failed to restore module {}", module.name, e);
                            }
                        }

                        layers.save();
                        LayersAddon.LOG.info("LAYERS-TEST: state restored (layers={}, activeIndex={})",
                            layers.layers.size(), layers.activeIndex);
                    }

                    boolean saved = new File("meteor-client/layers.json").exists();
                    LayersAddon.LOG.info("LAYERS-TEST layers.json saved: {}", saved);
                    LayersAddon.LOG.info("LAYERS-TEST: DONE, stopping game");
                    enterStage(12);
                }
            }
            case 12 -> {
                if (ticksInStage() >= 10) {
                    mc.scheduleStop();
                }
            }
        }
    }

    private int ticksInStage() {
        return tick - stageTick;
    }

    private void enterStage(int s) {
        stage = s;
        stageTick = tick;
    }

    private static int countActive(Category category) {
        int count = 0;
        for (Module module : Modules.get().getGroup(category)) {
            if (module.isActive()) count++;
        }
        return count;
    }

    /** 调用官方一站式截图（写入 gameDir/screenshots/，文件名为时间戳）。 */
    private void requestScreenshot() {
        try {
            knownScreenshots = listScreenshots();
            ScreenshotRecorder.saveScreenshot(new File("."), MeteorClient.mc.getFramebuffer(), message -> { });
        } catch (Exception e) {
            LayersAddon.LOG.error("LAYERS-TEST: screenshot error", e);
        }
    }

    /** 找出新生成的截图文件并复制为目标名。 */
    private void collectScreenshot(String targetName) {
        File latest = null;
        File[] files = screenshotsDir.listFiles((dir, n) -> n.endsWith(".png"));
        if (files != null) {
            for (File f : files) {
                if (!knownScreenshots.contains(f.getName()) && (latest == null || f.lastModified() > latest.lastModified())) {
                    latest = f;
                }
            }
        }

        if (latest == null) {
            LayersAddon.LOG.error("LAYERS-TEST: no new screenshot found for {}", targetName);
            return;
        }

        try {
            File out = Path.of(targetName).toAbsolutePath().toFile();
            Files.copy(latest.toPath(), out.toPath(), StandardCopyOption.REPLACE_EXISTING);
            LayersAddon.LOG.info("LAYERS-TEST: screenshot {} -> {}", latest.getName(), out);
        } catch (Exception e) {
            LayersAddon.LOG.error("LAYERS-TEST: screenshot copy failed", e);
        }
    }

    private Set<String> listScreenshots() {
        Set<String> names = new HashSet<>();
        File[] files = screenshotsDir.listFiles((dir, n) -> n.endsWith(".png"));
        if (files != null) {
            for (File f : files) names.add(f.getName());
        }
        return names;
    }
}
