package za.co.neroland.neroevents.client.screen;

import java.util.List;
import java.util.function.LongSupplier;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.neroevents.client.EventColours;
import za.co.neroland.neroevents.client.widget.ConfirmPanel;
import za.co.neroland.neroevents.client.widget.EventWidget;
import za.co.neroland.neroevents.client.widget.Feedback;
import za.co.neroland.neroevents.client.widget.Hull;
import za.co.neroland.neroevents.client.widget.WidgetText;
import za.co.neroland.neroevents.platform.Services;

/**
 * The procedural hull every NeroEvents menu screen sits on (a port of NeroSecurity's {@code SecurityScreen}):
 * panel, one-line title, subtitle, body, and the 14-px footer that reports the last action with a mark.
 *
 * <p>Intents: {@link #sendIntent} sends a payload and holds every intent widget, tooltip "Waiting for the
 * server…", until the snapshot version changes; with no answer within {@link #AWAIT_TIMEOUT_TICKS} the hold is
 * released with "The server didn't answer — try again". The screen never decides a permission: it mirrors the
 * snapshot, and the server re-checks every payload. CLIENT-ONLY.
 */
public abstract class EventScreen<T extends AbstractContainerMenu> extends AbstractContainerScreen<T>
        implements ConfirmPanel.Host {

    public static final int PAD = 8;
    public static final int FOOTER_HEIGHT = 14;
    public static final int AWAIT_TIMEOUT_TICKS = 100;
    private static final int TOOLTIP_WIDTH = 200;
    private static final int OFFSCREEN = -100_000;

    private final int panelWidth;
    private final int panelHeight;
    private final int titleY;
    private final int subtitleY;
    private final LongSupplier versionSource;

    private Component titleLine;
    private @Nullable Component subtitle;
    private int titleRightInset;
    private @Nullable Feedback feedback;
    private long seenVersion = Long.MIN_VALUE;
    private long awaitingVersion = Long.MIN_VALUE;
    private int awaitTicks;
    private @Nullable ConfirmPanel confirm;
    private int frameMouseX;
    private int frameMouseY;

    protected EventScreen(T menu, Inventory inventory, Component title, int panelWidth, int panelHeight, int titleY,
            int subtitleY, LongSupplier versionSource) {
        super(menu, inventory, title, panelWidth, panelHeight);
        this.panelWidth = panelWidth;
        this.panelHeight = panelHeight;
        this.titleY = titleY;
        this.subtitleY = subtitleY;
        this.versionSource = versionSource;
        this.titleLine = title;
    }

    public final int left() {
        return leftPos;
    }

    public final int top() {
        return topPos;
    }

    public final void setTitleLine(Component title) {
        this.titleLine = title;
    }

    public final Component titleLine() {
        return titleLine;
    }

    public final void setSubtitle(@Nullable Component value) {
        this.subtitle = value;
    }

    /** Pixels reserved right of the title (a gear). */
    public final void setTitleRightInset(int px) {
        this.titleRightInset = Math.max(0, px);
    }

    // --- footer --------------------------------------------------------------------------------------------

    public final void feedbackOk(Component text) {
        this.feedback = Feedback.ok(text);
    }

    public final void feedbackDeny(Component text) {
        this.feedback = Feedback.deny(text);
    }

    public final void feedbackHint(Component text) {
        this.feedback = Feedback.hint(text);
    }

    public final void clearFeedback() {
        this.feedback = null;
    }

    // --- intents -------------------------------------------------------------------------------------------

    /** Sends {@code payload} and holds intent widgets until the next snapshot. */
    protected final void sendIntent(CustomPacketPayload payload) {
        Services.NETWORK.sendToServer(payload);
        this.awaitingVersion = versionSource.getAsLong();
        this.awaitTicks = 0;
        feedbackHint(Component.translatable("console.neroevents.deny.waiting"));
        applyHold();
    }

    public final boolean isAwaiting() {
        return awaitingVersion != Long.MIN_VALUE;
    }

    /** Mirror the snapshot into the widgets (after init, every snapshot, every tick). */
    protected void refreshWidgets() {
    }

    /** Runs once per new snapshot, before {@link #refreshWidgets()}. */
    protected void onSnapshot(boolean answeredIntent) {
    }

    private void applyHold() {
        boolean held = isAwaiting();
        Component why = held ? Component.translatable("console.neroevents.deny.waiting") : null;
        for (GuiEventListener child : children()) {
            if (child instanceof EventWidget widget) {
                widget.setHeld(held, why);
            }
        }
    }

    private void pollSnapshot() {
        long version = versionSource.getAsLong();
        if (version != seenVersion) {
            boolean first = seenVersion == Long.MIN_VALUE;
            seenVersion = version;
            boolean answered = isAwaiting() && !first;
            awaitingVersion = Long.MIN_VALUE;
            applyHold();
            onSnapshot(answered);
        } else if (isAwaiting() && ++awaitTicks >= AWAIT_TIMEOUT_TICKS) {
            awaitingVersion = Long.MIN_VALUE;
            applyHold();
            feedbackHint(Component.translatable("console.neroevents.deny.no_answer"));
        }
    }

    protected final <W extends AbstractWidget> W add(W widget) {
        return addRenderableWidget(widget);
    }

    protected final void addAll(List<? extends AbstractWidget> widgets) {
        for (AbstractWidget widget : widgets) {
            addRenderableWidget(widget);
        }
    }

    // --- confirm panel -------------------------------------------------------------------------------------

    public final void openConfirm(ConfirmPanel panel) {
        this.confirm = panel;
        setFocused(null);
        panel.layout(this.font);
    }

    @Override
    public final void closeConfirm(ConfirmPanel panel) {
        if (this.confirm == panel) {
            this.confirm = null;
        }
    }

    @Override
    public final int hostWidth() {
        return this.width;
    }

    @Override
    public final int hostHeight() {
        return this.height;
    }

    // --- lifecycle -----------------------------------------------------------------------------------------

    @Override
    protected final void init() {
        super.init();
        initWidgets();
        ConfirmPanel open = confirm;
        if (open != null) {
            open.layout(this.font);
        }
        applyHold();
        refreshWidgets();
    }

    protected abstract void initWidgets();

    @Override
    protected void containerTick() {
        super.containerTick();
        pollSnapshot();
        applyHold();
        refreshWidgets();
    }

    // --- drawing -------------------------------------------------------------------------------------------

    @Override
    public final void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        ConfirmPanel modal = confirm;
        this.frameMouseX = modal != null ? OFFSCREEN : mouseX;
        this.frameMouseY = modal != null ? OFFSCREEN : mouseY;
        drawHull(g);
        extractPanel(g, frameMouseX, frameMouseY, partialTick);
        super.extractContents(g, frameMouseX, frameMouseY, partialTick);
        if (modal != null) {
            g.nextStratum();
            modal.extract(g, this.font, mouseX, mouseY, partialTick);
        }
    }

    protected abstract void extractPanel(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick);

    private void drawHull(GuiGraphicsExtractor g) {
        int x = leftPos;
        int y = topPos;
        Hull.panel(g, x, y, panelWidth, panelHeight);
        int titleRoom = panelWidth - 2 * PAD - titleRightInset;
        WidgetText.drawFitted(g, font, titleLine, x + PAD, y + titleY, titleRoom, EventColours.TITLE);
        if (WidgetText.overflows(font, titleLine, titleRoom)) {
            explain(g, x + PAD, y + titleY, titleRoom, 9, titleLine);
        }
        Component sub = subtitle;
        if (sub != null) {
            int room = panelWidth - 2 * PAD;
            WidgetText.drawFitted(g, font, sub, x + PAD, y + subtitleY, room, EventColours.SUBTLE);
            if (WidgetText.overflows(font, sub, room)) {
                explain(g, x + PAD, y + subtitleY, room, 9, sub);
            }
        }
        Hull.footer(g, font, x, y, panelWidth, panelHeight, FOOTER_HEIGHT, feedback);
        Feedback shown = feedback;
        if (shown != null) {
            explain(g, x + PAD, y + panelHeight - FOOTER_HEIGHT, panelWidth - 2 * PAD, FOOTER_HEIGHT, shown.text());
        }
    }

    /** No vanilla labels: the hull draws the title. */
    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
    }

    /** A hover sentence over an absolute rectangle (suppressed while a confirm panel is open). */
    protected final void explain(GuiGraphicsExtractor g, int x, int y, int w, int h, @Nullable Component text) {
        if (text == null) {
            return;
        }
        int mx = frameMouseX;
        int my = frameMouseY;
        if (mx >= x && mx < x + w && my >= y && my < y + h) {
            List<FormattedCharSequence> lines = font.split(text, TOOLTIP_WIDTH);
            g.setTooltipForNextFrame(font, lines, mx, my);
        }
    }

    // --- input ---------------------------------------------------------------------------------------------

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        ConfirmPanel modal = confirm;
        if (modal != null) {
            return modal.mouseClicked(event, doubleClick);
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (confirm != null) {
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (confirm != null) {
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (confirm != null) {
            return true;
        }
        if (onScroll(x, y, scrollY)) {
            return true;
        }
        return super.mouseScrolled(x, y, scrollX, scrollY);
    }

    /** Wheel over the body; return true when consumed. */
    protected boolean onScroll(double x, double y, double scrollY) {
        return false;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        ConfirmPanel modal = confirm;
        if (modal != null) {
            return modal.keyPressed(event);
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (confirm != null) {
            return true;
        }
        return super.charTyped(event);
    }
}
