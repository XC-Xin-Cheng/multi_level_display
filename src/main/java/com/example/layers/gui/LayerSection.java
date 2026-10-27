package com.example.layers.gui;

import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.themes.meteor.MeteorWidget;
import meteordevelopment.meteorclient.gui.themes.meteor.widgets.WMeteorSection;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WSection;
import meteordevelopment.meteorclient.gui.widgets.pressable.WTriangle;

/**
 * 与 WMeteorSection 相同，但 headerWidget（分类批量开关）显示在分类名左侧。
 */
public class LayerSection extends WMeteorSection {
    public LayerSection(String title, boolean expanded, WWidget headerWidget) {
        super(title, expanded, headerWidget);
    }

    @Override
    protected WHeader createHeader() {
        return new LeadingHeader(title);
    }

    protected class LeadingHeader extends WHeader {
        private final WTriangle triangle;

        public LeadingHeader(String title) {
            super(title);
            triangle = new HeaderTriangle();
        }

        @Override
        public void init() {
            triangle.theme = theme;
            triangle.action = this::onClick;

            if (headerWidget != null) add(headerWidget);
            add(theme.horizontalSeparator(title)).expandX();
            add(triangle);
        }

        @Override
        protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
            triangle.rotation = (1 - animProgress) * -90;
            super.onRender(renderer, mouseX, mouseY, delta);
        }
    }

    protected static class HeaderTriangle extends WTriangle implements MeteorWidget {
        @Override
        protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
            renderer.rotatedQuad(x, y, width, height, rotation, GuiRenderer.TRIANGLE, theme().textColor.get());
        }
    }
}
