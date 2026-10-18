package za.co.neroland.neroevents.client.widget;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.IntConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import za.co.neroland.neroevents.client.EventColours;

/**
 * A row of tabs (the NeroSecurity pattern): the active tab's word is white with a 2-px underline, the others
 * {@code SUBTLE} with none (shape, not only colour). Tabs are navigation, so the hull never holds them. Each tab
 * has its own width so every word fits, and a tooltip with the long form. CLIENT-ONLY.
 */
public final class TabBar {

    public static final int TAB_HEIGHT = 14;

    private final List<Tab> tabs = new ArrayList<>();
    private final IntConsumer onSelect;
    private int selected;

    public TabBar(int x, int y, int[] widths, List<Component> labels, List<Component> tooltips, IntConsumer onSelect) {
        this.onSelect = onSelect;
        int tx = x;
        for (int i = 0; i < labels.size(); i++) {
            int w = widths[Math.min(i, widths.length - 1)];
            Tab tab = new Tab(tx, y, w, i, labels.get(i));
            tab.setIntent(false);
            if (i < tooltips.size()) {
                tab.setHelp(tooltips.get(i));
            }
            tabs.add(tab);
            tx += w;
        }
    }

    public List<? extends EventWidget> widgets() {
        return Collections.unmodifiableList(tabs);
    }

    public void select(int index) {
        this.selected = index;
    }

    public int selected() {
        return selected;
    }

    public void setVisible(int index, boolean visible) {
        if (index >= 0 && index < tabs.size()) {
            tabs.get(index).visible = visible;
        }
    }

    private final class Tab extends EventWidget {

        private final int index;

        Tab(int x, int y, int w, int index, Component label) {
            super(x, y, w, TAB_HEIGHT, label);
            this.index = index;
        }

        private void press() {
            if (this.active && index != selected) {
                onSelect.accept(index);
            }
        }

        @Override
        public void onClick(MouseButtonEvent event, boolean doubleClick) {
            press();
        }

        @Override
        public boolean keyPressed(KeyEvent event) {
            if (this.active && this.visible && event.isSelection()) {
                playDownSound(Minecraft.getInstance().getSoundManager());
                press();
                return true;
            }
            return false;
        }

        @Override
        protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
            int x = getX();
            int y = getY();
            int w = getWidth();
            int h = getHeight();
            boolean isSelected = index == selected;
            boolean hot = hot();
            if (hot && !isSelected) {
                g.fill(x, y, x + w, y + h, EventColours.BUTTON_HOVER);
            }
            int text = !this.active ? EventColours.DISABLED_TEXT
                    : (isSelected || hot ? EventColours.TITLE : EventColours.SUBTLE);
            WidgetText.drawCentredFitted(g, Minecraft.getInstance().font, getMessage(), x + w / 2, y + 3, w - 2, text);
            if (isSelected) {
                g.fill(x + 3, y + h - 2, x + w - 3, y + h, EventColours.TITLE);
            }
        }
    }
}
