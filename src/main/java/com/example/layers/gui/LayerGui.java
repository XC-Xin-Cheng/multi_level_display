package com.example.layers.gui;

import com.example.layers.LayersAddon;
import com.example.layers.layers.Layer;
import com.example.layers.layers.Layers;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.utils.Cell;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WContainer;
import meteordevelopment.meteorclient.gui.widgets.containers.WHorizontalList;
import meteordevelopment.meteorclient.gui.widgets.containers.WSection;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.containers.WVerticalList;
import meteordevelopment.meteorclient.gui.widgets.containers.WWindow;
import meteordevelopment.meteorclient.gui.widgets.WLabel;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.gui.widgets.pressable.WCheckbox;
import meteordevelopment.meteorclient.gui.widgets.pressable.WConfirmedMinus;
import meteordevelopment.meteorclient.systems.config.Config;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 在模块列表界面右上角挂载“层”窗口：上一层 / 下一层 / 添加层按钮，
 * 以及一个按分类折叠的模块勾选列表，用来配置当前层显示哪些模块。
 */
public final class LayerGui {
    private static final String WINDOW_ID = "multi_level_display";

    private static WeakReference<WContainer> controller;
    private static WeakReference<List<WWindow>> windows;

    private static WWindow window;
    private static WVerticalList list;
    private static WLabel label;

    /** 是否展开设置列表中的全部分类（自动化测试用，也可供外部调用）。 */
    private static boolean expandSections;

    private LayerGui() {
    }

    public static void attach(WContainer c, List<WWindow> categoryWindows) {
        controller = new WeakReference<>(c);
        windows = new WeakReference<>(categoryWindows);

        buildWindow(c);
        rebuildColumns();
    }

    public static void onLayersChanged() {
        if (controller == null || controller.get() == null) return;
        if (window == null) return;

        refreshLabel();
        refreshList();
        rebuildColumns();
    }

    /**
     * 自动化测试用：分类窗口里的模块行总数（过滤后的可见模块数）。
     */
    public static int moduleRowCount() {
        List<WWindow> ws = windows == null ? null : windows.get();
        if (ws == null) return -1;

        int rows = 0;
        for (WWindow w : ws) {
            if (w.view != null && isCategoryWindow(w)) rows += w.view.cells.size();
        }
        return rows;
    }

    /**
     * 自动化测试用：返回层窗口勾选列表里第一个模块的勾选框（调用它的 action 等同于真实点击）。
     * 返回 null 表示列表里没有模块或界面未挂载。
     */
    public static WCheckbox firstCheckbox() {
        for (Cell<?> cell : rowWidgets()) {
            if (cell.widget() instanceof WCheckbox checkbox) return checkbox;
        }
        return null;
    }

    /** 按顺序给出设置列表里的所有控件（每个模块对应 勾选框/名字 两个）。 */
    private static List<Cell<?>> rowWidgets() {
        List<Cell<?>> cells = new ArrayList<>();
        if (list == null) return cells;

        for (Cell<?> sectionCell : list.cells) {
            if (!(sectionCell.widget() instanceof WSection section)) continue;

            for (Cell<?> contentCell : section.cells) {
                if (!(contentCell.widget() instanceof WTable table)) continue;
                cells.addAll(table.cells);
            }
        }

        return cells;
    }

    /**
     * 自动化测试用：输出当前层窗口与分类窗口的状态快照。
     */
    public static String selfTest() {
        StringBuilder sb = new StringBuilder();

        WContainer c = controller == null ? null : controller.get();
        List<WWindow> ws = windows == null ? null : windows.get();

        sb.append("attach=").append(c != null && ws != null);
        sb.append(", window=").append(window != null);
        sb.append(", list=").append(list != null);
        sb.append(", label=").append(label == null ? "null" : "\"" + label.get() + "\"");

        if (ws != null) {
            int categories = 0;
            int rows = 0;
            for (WWindow w : ws) {
                if (w.view != null && isCategoryWindow(w)) {
                    categories++;
                    rows += w.view.cells.size();
                }
            }
            sb.append("; categoryWindows=").append(categories).append(", moduleRows=").append(rows);
        }

        Layers layers = Layers.get();
        Layer active = layers.getActive();
        sb.append("; layers=").append(layers.layers.size());
        sb.append(", active=").append(active.name);
        sb.append(", showAll=").append(active.showAll);
        sb.append(", whitelist=").append(active.modules.size());
        sb.append("; statesSaved=").append(active.statesSaved);
        sb.append(", enabled=").append(active.enabled.size());

        return sb.toString();
    }

    private static void buildWindow(WContainer c) {
        GuiTheme theme = c.theme;

        window = theme.window("Layers");
        window.id = WINDOW_ID;
        window.padding = 0;
        window.spacing = 0;

        c.add(window);
        List<WWindow> ws = windows.get();
        if (ws != null) ws.add(window);

        window.view.scrollOnlyWhenMouseOver = true;

        WHorizontalList buttons = window.add(theme.horizontalList()).pad(4).widget();

        WButton previous = buttons.add(theme.button("<")).widget();
        previous.action = () -> Layers.get().previous();

        label = buttons.add(theme.label("")).pad(8).widget();
        refreshLabel();

        WButton next = buttons.add(theme.button(">")).widget();
        next.action = () -> Layers.get().next();

        WButton add = buttons.add(theme.button("+")).widget();
        // meteor 的 GUI 自带字体不含 CJK 字形，界面文案用 ASCII；层名可随时用 .layer rename 改
        add.action = () -> Layers.get().add("Layer");

        // 删除当前层（两段式按钮：第一次点击进入确认状态）
        WConfirmedMinus minus = buttons.add(theme.confirmedMinus()).widget();
        // Layers.remove 自带“最后一层不可删”的保护
        minus.action = () -> Layers.get().remove(Layers.get().getActive().name);

        list = window.add(theme.verticalList()).expandX().widget();
        refreshList();
    }

    private static void refreshLabel() {
        if (label == null) return;

        Layers layers = Layers.get();
        Layer active = layers.getActive();
        label.set((layers.activeIndex + 1) + "/" + layers.layers.size() + " " + active.name);
    }

    public static void setSectionsExpanded(boolean expanded) {
        expandSections = expanded;
        if (list != null) refreshList();
    }

    private static void refreshList() {
        if (list == null) return;

        GuiTheme theme = list.theme;
        Layer active = Layers.get().getActive();

        list.clear();

        for (Category category : Modules.loopCategories()) {
            // 分类标题左侧的批量开关：勾上 = 整个分类的模块在本层显示，取消 = 整个分类隐藏
            WHorizontalList headerControls = theme.horizontalList();
            WCheckbox allSwitch = headerControls.add(theme.checkbox(isCategoryFullyVisible(category, active))).widget();
            allSwitch.tooltip = "Show/hide all modules in this category";
            allSwitch.action = () -> {
                setCategoryVisible(category, allSwitch.checked);
                allSwitch.checked = isCategoryFullyVisible(category, active);
            };

            WSection section = list.add(new LayerSection(category.name, expandSections, headerControls)).expandX().widget();
            section.spacing = 0;

            // 表格布局：显示勾选框 / 模块名 两列对齐
            WTable table = section.add(theme.table()).widget();

            for (Module module : Modules.get().getGroup(category)) {
                WCheckbox checkbox = table.add(theme.checkbox(active.shows(module))).widget();
                WLabel moduleLabel = table.add(theme.label(module.title)).widget();
                table.row();

                // 勾选框 / 点名字 = 本层是否显示该模块（单个功能的开关）
                Runnable displayToggle = () -> {
                    active.toggle(module);
                    checkbox.checked = active.shows(module);
                    Layers.get().save();
                    rebuildColumns();
                };

                checkbox.action = displayToggle;
                moduleLabel.action = displayToggle;

                checkbox.tooltip = "Show module in this layer";
            }
        }
    }

    private static boolean isCategoryFullyVisible(Category category, Layer active) {
        if (active.showAll) return true;
        for (Module m : Modules.get().getGroup(category)) {
            if (!active.shows(m)) return false;
        }
        return true;
    }

    /**
     * 一键显示 / 隐藏一个分类（“功能列表”）下的全部模块（只改当前层的显示白名单，不动模块启停）。
     */
    public static void setCategoryVisible(Category category, boolean visible) {
        Layer active = Layers.get().getActive();
        List<Module> group = Modules.get().getGroup(category);

        if (visible) {
            if (!active.showAll) {
                for (Module m : group) active.modules.add(m.name);
            }
        } else if (active.showAll) {
            // 从“显示全部”转为白名单：保留其他分类，隐藏本分类
            active.showAll = false;
            for (Module m : Modules.get().getAll()) {
                if (m.category != category) active.modules.add(m.name);
            }
        } else {
            for (Module m : group) active.modules.remove(m.name);
        }

        Layers.get().save();
        rebuildColumns();

        // 刷新设置列表里的勾选状态
        if (list != null) refreshList();
    }

    /**
     * 按当前层重建分类窗口（分类窗口的 id 就是分类名，搜索/收藏/层窗口不动）。
     */
    private static void rebuildColumns() {
        WContainer c = controller == null ? null : controller.get();
        List<WWindow> ws = windows == null ? null : windows.get();
        if (c == null || ws == null) return;

        GuiTheme theme = c.theme;

        Iterator<WWindow> it = ws.iterator();
        while (it.hasNext()) {
            WWindow w = it.next();
            if (isCategoryWindow(w)) {
                removeCell(c, w);
                it.remove();
            }
        }

        for (Category category : Modules.loopCategories()) {
            List<Module> modules = filterGroup(category);
            if (modules.isEmpty()) continue;
            addCategoryWindow(c, theme, category, modules, ws);
        }
    }

    private static boolean isCategoryWindow(WWindow w) {
        if (w.id == null) return false;
        return !w.id.equals(WINDOW_ID) && !w.id.equals("search") && !w.id.equals("favorites");
    }

    private static void removeCell(WContainer c, WWidget widget) {
        for (Cell<?> cell : c.cells) {
            if (cell.widget() == widget) {
                c.remove(cell);
                return;
            }
        }
    }

    private static List<Module> filterGroup(Category category) {
        Layer active = Layers.get().getActive();

        List<Module> visible = new ArrayList<>();
        for (Module module : Modules.get().getGroup(category)) {
            if (active.shows(module) && !Config.get().hiddenModules.get().contains(module)) {
                visible.add(module);
            }
        }

        return visible;
    }

    private static void addCategoryWindow(WContainer c, GuiTheme theme, Category category, List<Module> modules, List<WWindow> ws) {
        WWindow w = theme.window(category.name);
        w.id = category.name;
        w.padding = 0;
        w.spacing = 0;

        c.add(w);
        ws.add(w);

        w.view.scrollOnlyWhenMouseOver = true;
        w.view.hasScrollBar = false;
        w.view.spacing = 0;

        for (Module module : modules) {
            w.add(theme.module(module)).expandX();
        }
    }
}
