package za.co.neroland.neroevents.client.widget;

import java.util.Objects;

import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import org.jetbrains.annotations.Nullable;

/**
 * Base of every interactive NeroEvents widget (the NeroSecurity widget pattern). It owns the rule the "no
 * manual needed" test checks hardest: <b>a disabled control always says why</b> — {@link #disable(Component)}
 * takes the reason and shows it as the tooltip. The screen hull <em>holds</em> intent widgets while it waits for
 * the server's answer; navigation widgets opt out with {@link #setIntent(boolean) setIntent(false)}. Widgets are
 * display-only: the server re-checks every request. CLIENT-ONLY.
 */
public abstract class EventWidget extends AbstractWidget {

    private static final Component FALLBACK_WHY = Component.translatable("console.neroevents.why.unavailable");

    private boolean enabled = true;
    private @Nullable Component why;
    private @Nullable Component help;
    private boolean intent = true;
    private boolean held;
    private @Nullable Component heldWhy;
    private @Nullable Component shownTip;

    protected EventWidget(int x, int y, int width, int height, Component message) {
        super(x, y, width, height, message);
    }

    public final void enable() {
        this.enabled = true;
        this.why = null;
        sync();
    }

    /** Disables the widget; {@code why} becomes its tooltip (one plain sentence). */
    public final void disable(@Nullable Component why) {
        this.enabled = false;
        this.why = why == null ? FALLBACK_WHY : why;
        sync();
    }

    public final void setEnabled(boolean value, Component whyIfDisabled) {
        if (value) {
            enable();
        } else {
            disable(whyIfDisabled);
        }
    }

    public final boolean isEnabledByScreen() {
        return enabled;
    }

    public final @Nullable Component why() {
        return enabled ? null : why;
    }

    /** The tooltip while enabled ({@code null} for none). */
    public final void setHelp(@Nullable Component value) {
        this.help = value;
        sync();
    }

    public final void setIntent(boolean value) {
        this.intent = value;
        if (!value && held) {
            setHeld(false, null);
        }
    }

    public final boolean isIntent() {
        return intent;
    }

    /** Called by the screen hull only. */
    public final void setHeld(boolean value, @Nullable Component reason) {
        boolean next = value && intent;
        if (next == this.held && Objects.equals(reason, this.heldWhy)) {
            return;
        }
        this.held = next;
        this.heldWhy = reason;
        sync();
    }

    protected final void sync() {
        this.active = enabled && !held;
        Component tip;
        if (!enabled) {
            tip = why;
        } else if (held) {
            tip = heldWhy;
        } else {
            tip = help;
        }
        if (!Objects.equals(tip, shownTip)) {
            shownTip = tip;
            setTooltip(tip == null ? null : Tooltip.create(tip));
        }
    }

    /** {@code true} when the mouse is over (or keyboard focus is on) an active widget. */
    protected final boolean hot() {
        return this.active && isHoveredOrFocused();
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
