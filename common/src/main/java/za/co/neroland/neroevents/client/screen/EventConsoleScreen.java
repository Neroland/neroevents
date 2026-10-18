package za.co.neroland.neroevents.client.screen;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.player.Inventory;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.neroevents.NeroEventsCommon;
import za.co.neroland.neroevents.client.ClientConsoleState;
import za.co.neroland.neroevents.client.ConsoleLayout;
import za.co.neroland.neroevents.client.ConsoleText;
import za.co.neroland.neroevents.client.EventColours;
import za.co.neroland.neroevents.client.EventsClient;
import za.co.neroland.neroevents.client.HistorySentences;
import za.co.neroland.neroevents.client.widget.ConfirmPanel;
import za.co.neroland.neroevents.client.widget.EventButton;
import za.co.neroland.neroevents.client.widget.Hull;
import za.co.neroland.neroevents.client.widget.TabBar;
import za.co.neroland.neroevents.client.widget.WidgetText;
import za.co.neroland.neroevents.console.ConsoleIntents;
import za.co.neroland.neroevents.console.ConsolePayloads.ConsoleCancel;
import za.co.neroland.neroevents.console.ConsolePayloads.ConsoleClaim;
import za.co.neroland.neroevents.console.ConsolePayloads.ConsolePrivacy;
import za.co.neroland.neroevents.console.ConsolePayloads.ConsoleSnapshot;
import za.co.neroland.neroevents.console.ConsolePayloads.ConsoleVote;
import za.co.neroland.neroevents.console.ConsolePayloads.EventRow;
import za.co.neroland.neroevents.console.ConsolePayloads.HistoryRow;
import za.co.neroland.neroevents.console.ConsolePayloads.LedgerRow;
import za.co.neroland.neroevents.console.EventConsoleMenu;
import za.co.neroland.neroevents.event.ConsentMode;
import za.co.neroland.neroevents.event.Severity;

/**
 * The Event Console: "What's happening on this server", with five tabs — Live (running now and what to do),
 * Soon (coming up, with big YES / NO buttons while a vote is open), Past (plain sentences), Mine (what you earned
 * and the CLAIM button) and Privacy (what is kept about you, with Export / Opt out / Erase). Everything comes
 * from the server's snapshot; every disabled control says why; nothing shows an id, a UUID, a player name or a
 * coordinate of a non-public event. CLIENT-ONLY.
 */
public class EventConsoleScreen extends EventScreen<EventConsoleMenu> {

    public static final int LIVE = 0;
    public static final int SOON = 1;
    public static final int PAST = 2;
    public static final int MINE = 3;
    public static final int PRIVACY = 4;
    private static final String[] TAB_NAMES = {"live", "soon", "past", "mine", "privacy"};
    private static final int VOTE_SLOTS = 3;
    private static final int ROW_SLOTS = 5;

    private static int lastTab = LIVE;

    private int tab = lastTab;
    private final int[] scroll = new int[TAB_NAMES.length];
    private int snapshotAge;

    private @Nullable TabBar tabs;
    private final EventButton[] yes = new EventButton[VOTE_SLOTS];
    private final EventButton[] no = new EventButton[VOTE_SLOTS];
    private final EventButton[] cancel = new EventButton[ROW_SLOTS];
    private final int[] voteKeys = new int[VOTE_SLOTS];
    private final int[] cancelKeys = new int[ROW_SLOTS];
    private final Component[] cancelTitles = new Component[ROW_SLOTS];
    private @Nullable EventButton claim;
    private @Nullable EventButton export;
    private @Nullable EventButton optToggle;
    private @Nullable EventButton erase;

    /** One visible Live/Soon row: panel-relative top and whether it carries the vote strip. */
    private record Slot(EventRow row, int y, boolean strip) {
    }

    public EventConsoleScreen(EventConsoleMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, ConsoleLayout.PANEL_W, ConsoleLayout.PANEL_H, ConsoleLayout.TITLE_Y,
                ConsoleLayout.SUBTITLE_Y, ClientConsoleState::version);
        setTitleLine(Component.translatable("console.neroevents.title"));
    }

    // --- widgets -------------------------------------------------------------------------------------------

    @Override
    protected void initWidgets() {
        int left = left();
        int top = top();
        List<Component> labels = new ArrayList<>();
        List<Component> tips = new ArrayList<>();
        for (String name : TAB_NAMES) {
            labels.add(Component.translatable("console.neroevents.tab." + name));
            tips.add(Component.translatable("console.neroevents.tab." + name + ".help"));
        }
        TabBar bar = new TabBar(left + ConsoleLayout.TABS_X, top + ConsoleLayout.TABS_Y, ConsoleLayout.TAB_W, labels,
                tips, this::showTab);
        bar.select(tab);
        addAll(bar.widgets());
        this.tabs = bar;

        int titleW = Math.min(font.width(titleLine()), ConsoleLayout.PANEL_W - 2 * PAD - ConsoleLayout.GEAR_SIZE - 4);
        EventButton gear = add(EventButton.of(left + PAD + titleW + 4, top + ConsoleLayout.GEAR_Y,
                ConsoleLayout.GEAR_SIZE, ConsoleLayout.GEAR_SIZE, Component.translatable("console.neroevents.gear"),
                b -> openHudSettings()).icon(EventButton.Icon.GEAR));
        gear.setIntent(false);
        gear.setHelp(Component.translatable("console.neroevents.gear.help", EventsClient.dismissKeyName()));
        setTitleRightInset(ConsoleLayout.GEAR_SIZE + 6);

        for (int i = 0; i < ROW_SLOTS; i++) {
            final int slot = i;
            cancel[i] = add(EventButton.of(0, 0, ConsoleLayout.CANCEL_SIZE, ConsoleLayout.CANCEL_SIZE,
                    Component.translatable("console.neroevents.cancel_event"), b -> confirmCancel(slot))
                    .icon(EventButton.Icon.CROSS).tone(EventButton.Tone.DANGER));
            cancel[i].setHelp(Component.translatable("console.neroevents.cancel_event.help"));
            cancel[i].visible = false;
        }
        for (int i = 0; i < VOTE_SLOTS; i++) {
            final int slot = i;
            yes[i] = add(EventButton.of(0, 0, ConsoleLayout.YES.width(), ConsoleLayout.YES.height(),
                    Component.translatable("console.neroevents.vote.yes"), b -> vote(slot, true))
                    .tone(EventButton.Tone.YES));
            no[i] = add(EventButton.of(0, 0, ConsoleLayout.NO.width(), ConsoleLayout.NO.height(),
                    Component.translatable("console.neroevents.vote.no"), b -> vote(slot, false))
                    .tone(EventButton.Tone.DANGER));
            yes[i].visible = false;
            no[i].visible = false;
        }

        ConsoleLayout.Box c = ConsoleLayout.CLAIM;
        claim = add(EventButton.of(left + c.x0(), top + c.y0(), c.width(), c.height(),
                Component.translatable("console.neroevents.claim"), b -> sendIntent(ConsoleClaim.INSTANCE)));
        ConsoleLayout.Box b0 = ConsoleLayout.yoursButton(0);
        ConsoleLayout.Box b1 = ConsoleLayout.yoursButton(1);
        ConsoleLayout.Box b2 = ConsoleLayout.yoursButton(2);
        export = add(EventButton.of(left + b0.x0(), top + b0.y0(), b0.width(), b0.height(),
                Component.translatable("console.neroevents.privacy.export"),
                b -> sendIntent(new ConsolePrivacy(ConsoleIntents.PrivacyAction.EXPORT.wire()))));
        optToggle = add(EventButton.of(left + b1.x0(), top + b1.y0(), b1.width(), b1.height(),
                Component.translatable("console.neroevents.privacy.optout"), b -> toggleOptOut()));
        EventButton eraseButton = add(EventButton.of(left + b2.x0(), top + b2.y0(), b2.width(), b2.height(),
                Component.translatable("console.neroevents.privacy.erase"), b -> confirmErase())
                .tone(EventButton.Tone.DANGER));
        eraseButton.setIntent(false); // it only opens the chat; nothing to wait for
        eraseButton.setHelp(Component.translatable("console.neroevents.privacy.erase.help"));
        erase = eraseButton;
    }

    private void showTab(int index) {
        this.tab = index;
        lastTab = index;
        TabBar bar = tabs;
        if (bar != null) {
            bar.select(index);
        }
        playUi("block.event_console.tab", 1.1F);
        refreshWidgets();
    }

    private void openHudSettings() {
        EventsClient.setScreen(new EventHudConfigScreen(this));
    }

    private static void playUi(String path, float pitch) {
        try {
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(
                    SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath(NeroEventsCommon.MOD_ID, path)),
                    pitch));
        } catch (RuntimeException | LinkageError ignored) {
            // A missing sound never breaks the screen.
        }
    }

    // --- snapshot -> widgets -------------------------------------------------------------------------------

    @Override
    protected void onSnapshot(boolean answeredIntent) {
        snapshotAge = 0;
        ConsoleSnapshot snap = ClientConsoleState.snapshot();
        if (snap == null || snap.messageKey().isEmpty()) {
            if (answeredIntent) {
                clearFeedback();
            }
            return;
        }
        Component message = snap.messageArg().isEmpty() ? Component.translatable(snap.messageKey())
                : Component.translatable(snap.messageKey(), snap.messageArg());
        if (snap.ok()) {
            feedbackOk(message);
            if (snap.messageKey().startsWith("console.neroevents.vote.")) {
                playUi("event.vote.cast", 1.4F);
            }
        } else {
            feedbackDeny(message);
            playUi("event.deny", 0.6F);
        }
    }

    @Override
    protected void containerTick() {
        snapshotAge++;
        super.containerTick();
    }

    @Override
    protected void refreshWidgets() {
        ConsoleSnapshot snap = ClientConsoleState.snapshot();
        setSubtitle(Component.translatable("console.neroevents.subtitle." + TAB_NAMES[tab]));
        for (EventButton b : cancel) {
            if (b != null) {
                b.visible = false;
            }
        }
        for (int i = 0; i < VOTE_SLOTS; i++) {
            if (yes[i] != null) {
                yes[i].visible = false;
                no[i].visible = false;
            }
        }
        setVisible(claim, tab == MINE);
        setVisible(export, tab == PRIVACY);
        setVisible(optToggle, tab == PRIVACY);
        setVisible(erase, tab == PRIVACY);
        if (snap == null) {
            disableAll(Component.translatable("console.neroevents.deny.waiting"));
            return;
        }
        if (tab == LIVE || tab == SOON) {
            refreshRows(snap);
        } else if (tab == MINE) {
            refreshMine(snap);
        } else if (tab == PRIVACY) {
            refreshPrivacy(snap);
        }
    }

    private static void setVisible(@Nullable EventButton button, boolean visible) {
        if (button != null) {
            button.visible = visible;
        }
    }

    private void disableAll(Component why) {
        for (EventButton b : new EventButton[] {claim, export, optToggle}) {
            if (b != null) {
                b.disable(why);
            }
        }
    }

    private void refreshRows(ConsoleSnapshot snap) {
        boolean live = tab == LIVE;
        List<Slot> slots = slots(live ? snap.live() : snap.soon(), live, snap);
        int voteSlot = 0;
        for (int i = 0; i < slots.size() && i < ROW_SLOTS; i++) {
            Slot slot = slots.get(i);
            EventRow row = slot.row();
            int y = top() + slot.y();
            if (snap.operator()) {
                EventButton x = cancel[i];
                x.setX(left() + ConsoleLayout.CANCEL_X);
                x.setY(y + 3);
                x.visible = true;
                x.enable();
                cancelKeys[i] = row.key();
                cancelTitles[i] = ConsoleText.titleLine(row.titleKey(), row.dimension());
            }
            if (slot.strip() && voteSlot < VOTE_SLOTS) {
                voteKeys[voteSlot] = row.key();
                EventButton y1 = yes[voteSlot];
                EventButton n1 = no[voteSlot];
                int stripY = y + ConsoleLayout.ROW_H;
                y1.setX(left() + ConsoleLayout.YES.x0());
                y1.setY(stripY + ConsoleLayout.YES.y0());
                n1.setX(left() + ConsoleLayout.NO.x0());
                n1.setY(stripY + ConsoleLayout.NO.y0());
                y1.visible = true;
                n1.visible = true;
                ConsoleIntents.Deny block = ConsoleIntents.Deny.fromWire(row.voteBlock());
                if (block == null) {
                    y1.enable();
                    n1.enable();
                    y1.setHelp(Component.translatable("console.neroevents.vote.yes.help"));
                    n1.setHelp(Component.translatable("console.neroevents.vote.no.help"));
                } else {
                    y1.disable(Component.translatable(block.key()));
                    n1.disable(Component.translatable(block.key()));
                }
                Component you = Component.translatable(row.myVote() == 1 ? "console.neroevents.vote.you_yes"
                        : "console.neroevents.vote.you_no");
                y1.setPill(row.myVote() == 1 ? you : null);
                n1.setPill(row.myVote() == 0 ? you : null);
                voteSlot++;
            }
        }
    }

    /** The rows that fit the body from the tab's scroll position; Soon rows with an open vote are taller. */
    private List<Slot> slots(List<EventRow> rows, boolean live, ConsoleSnapshot snap) {
        int bottom = live ? ConsoleLayout.ROWS.y1() : ConsoleLayout.PANEL_H - ConsoleLayout.FOOTER_H - 2;
        int first = Math.max(0, Math.min(scroll[tab], Math.max(0, rows.size() - 1)));
        boolean votes = !live && !snap.singlePlayer();
        List<Slot> out = new ArrayList<>();
        int y = ConsoleLayout.BODY_Y;
        for (int i = first; i < rows.size(); i++) {
            EventRow row = rows.get(i);
            boolean strip = votes && row.votingOpen();
            int h = ConsoleLayout.ROW_H + (strip ? ConsoleLayout.VOTE_STRIP_H : 0);
            if (y + h > bottom) {
                break;
            }
            out.add(new Slot(row, y, strip));
            y += h;
        }
        return out;
    }

    private void refreshMine(ConsoleSnapshot snap) {
        EventButton button = claim;
        if (button == null) {
            return;
        }
        var deny = ConsoleIntents.checkClaim(snap.waitingItems(), snap.waitingXp(), snap.roomInInventory(),
                snap.alreadyClaimed());
        if (deny.isPresent()) {
            button.disable(Component.translatable(deny.get().key()));
        } else {
            button.enable();
            button.setHelp(Component.translatable("console.neroevents.claim.help"));
        }
    }

    private void refreshPrivacy(ConsoleSnapshot snap) {
        EventButton ex = export;
        EventButton opt = optToggle;
        if (ex == null || opt == null) {
            return;
        }
        ex.enable();
        ex.setHelp(Component.translatable("console.neroevents.privacy.export.help"));
        opt.setMessage(Component.translatable(snap.optedOut() ? "console.neroevents.privacy.optin"
                : "console.neroevents.privacy.optout"));
        if (!snap.ledgerEnabled()) {
            opt.disable(Component.translatable("console.neroevents.privacy.ledger_off"));
        } else {
            opt.enable();
            opt.setHelp(Component.translatable(snap.optedOut() ? "console.neroevents.privacy.optin.help"
                    : "console.neroevents.privacy.optout.help"));
        }
    }

    // --- actions -------------------------------------------------------------------------------------------

    private void vote(int slot, boolean value) {
        sendIntent(new ConsoleVote(voteKeys[slot], value));
    }

    private void toggleOptOut() {
        ConsoleSnapshot snap = ClientConsoleState.snapshot();
        boolean out = snap != null && snap.optedOut();
        sendIntent(new ConsolePrivacy((out ? ConsoleIntents.PrivacyAction.OPT_IN : ConsoleIntents.PrivacyAction.OPT_OUT)
                .wire()));
    }

    private void confirmCancel(int slot) {
        int key = cancelKeys[slot];
        Component title = cancelTitles[slot] == null ? Component.translatable("console.neroevents.an_event")
                : cancelTitles[slot];
        openConfirm(new ConfirmPanel(this, Component.translatable("console.neroevents.cancel_event.title", title),
                Component.translatable("console.neroevents.cancel_event.body"),
                Component.translatable("console.neroevents.cancel_event.yes"), () -> sendIntent(new ConsoleCancel(key))));
    }

    private void confirmErase() {
        openConfirm(new ConfirmPanel(this, Component.translatable("console.neroevents.privacy.erase.title"),
                Component.translatable("console.neroevents.privacy.erase.body"),
                Component.translatable("console.neroevents.privacy.erase.yes"), this::openEraseChat));
    }

    /** Erase never runs anything here: it opens the chat with Core's erase command typed in. */
    private void openEraseChat() {
        EventsClient.setScreen(new ChatScreen("/neroland data eraseme", false));
    }

    @Override
    protected boolean onScroll(double x, double y, double scrollY) {
        if (!ConsoleLayout.BODY.contains(x - left(), y - top())) {
            return false;
        }
        ConsoleSnapshot snap = ClientConsoleState.snapshot();
        if (snap == null) {
            return false;
        }
        int size;
        int visible;
        switch (tab) {
            case LIVE -> {
                size = snap.live().size();
                visible = ConsoleLayout.LIVE_ROWS;
            }
            case SOON -> {
                size = snap.soon().size();
                visible = 1;
            }
            case PAST -> {
                size = snap.past().size();
                visible = ConsoleLayout.SENTENCE_ROWS;
            }
            case MINE -> {
                size = snap.ledger().size();
                visible = ConsoleLayout.LEDGER_ROWS;
            }
            default -> {
                return false;
            }
        }
        int max = Math.max(0, size - visible);
        scroll[tab] = Math.max(0, Math.min(max, scroll[tab] + (scrollY < 0 ? 1 : -1)));
        refreshWidgets();
        return true;
    }

    // --- drawing -------------------------------------------------------------------------------------------

    @Override
    protected void extractPanel(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        ConsoleSnapshot snap = ClientConsoleState.snapshot();
        if (snap == null) {
            centred(g, Component.translatable("console.neroevents.deny.waiting"));
            return;
        }
        switch (tab) {
            case LIVE -> drawRows(g, snap, true);
            case SOON -> drawRows(g, snap, false);
            case PAST -> drawPast(g, snap);
            case MINE -> drawMine(g, snap);
            default -> drawPrivacy(g, snap);
        }
    }

    private void centred(GuiGraphicsExtractor g, Component text) {
        int w = ConsoleLayout.PANEL_W - 4 * PAD;
        List<net.minecraft.util.FormattedCharSequence> lines = WidgetText.wrap(font, text, w);
        int y = top() + ConsoleLayout.BODY_Y + 40;
        for (net.minecraft.util.FormattedCharSequence line : lines) {
            g.text(font, line, left() + (ConsoleLayout.PANEL_W - font.width(line)) / 2, y, EventColours.TITLE, false);
            y += ConsoleLayout.TEXT_LINE;
        }
    }

    private void drawRows(GuiGraphicsExtractor g, ConsoleSnapshot snap, boolean live) {
        List<EventRow> rows = live ? snap.live() : snap.soon();
        if (rows.isEmpty()) {
            centred(g, Component.translatable(live ? "console.neroevents.live.empty" : "console.neroevents.soon.empty"));
            return;
        }
        if (!live && snap.consent() == ConsentMode.ADMIN.ordinal()) {
            WidgetText.drawFitted(g, font, Component.translatable("console.neroevents.soon.admin_mode"),
                    left() + PAD, top() + ConsoleLayout.PANEL_H - ConsoleLayout.FOOTER_H - 11,
                    ConsoleLayout.PANEL_W - 2 * PAD, EventColours.SUBTLE);
        }
        for (Slot slot : slots(rows, live, snap)) {
            drawRow(g, slot, live, snap.operator());
        }
    }

    private void drawRow(GuiGraphicsExtractor g, Slot slot, boolean live, boolean operator) {
        EventRow row = slot.row();
        int x = left();
        int y = top() + slot.y();
        int h = ConsoleLayout.ROW_H + (slot.strip() ? ConsoleLayout.VOTE_STRIP_H : 0);
        Severity severity = EventColours.severityOf(row.severity());
        g.fill(x + ConsoleLayout.PAD, y + h - 1, x + ConsoleLayout.PANEL_W - PAD, y + h, EventColours.EDGE);
        g.fill(x + ConsoleLayout.BAND_X0, y + 1, x + ConsoleLayout.BAND_X1, y + h - 2,
                EventColours.opaque(EventColours.severity(severity)));
        Hull.severityGlyph(g, x + ConsoleLayout.GLYPH_X, y + ConsoleLayout.GLYPH_DY, severity);

        long remaining = Math.max(0L, (long) row.ticksLeft() - snapshotAge);
        Component title = ConsoleText.titleLine(row.titleKey(), row.dimension());
        int titleW = operator ? ConsoleLayout.TITLE_W_OPERATOR : ConsoleLayout.TITLE_W;
        WidgetText.drawFitted(g, font, title, x + ConsoleLayout.TEXT_X, y + ConsoleLayout.LINE1_DY, titleW,
                EventColours.TITLE);
        if (WidgetText.overflows(font, title, titleW)) {
            explain(g, x + ConsoleLayout.TEXT_X, y + ConsoleLayout.LINE1_DY, titleW, 9, title);
        }
        Component time = live ? ConsoleText.timeLeft(remaining) : ConsoleText.startsIn(remaining);
        Component todo = ConsoleText.whatToDo(row.definitionTodoKey(), row.todoKey(), row.whatToDoKey(), time);
        Component where = where(row);
        Component line2 = where == null ? todo : Component.translatable("console.neroevents.todo_where", todo, where);
        WidgetText.drawFitted(g, font, line2, x + ConsoleLayout.TEXT_X, y + ConsoleLayout.LINE2_DY,
                ConsoleLayout.TEXT2_W, EventColours.TITLE);
        explain(g, x + ConsoleLayout.TEXT_X, y + ConsoleLayout.LINE2_DY, ConsoleLayout.TEXT2_W, 9, line2);

        int rx = x + ConsoleLayout.RIGHT_X1;
        int rw = ConsoleLayout.RIGHT_X1 - ConsoleLayout.RIGHT_X0;
        Component clock = Component.translatable(live ? "console.neroevents.clock.left" : "console.neroevents.clock.in",
                ConsoleText.clock(remaining));
        WidgetText.drawRight(g, font, clock, rx, y + ConsoleLayout.LINE1_DY, rw, EventColours.SUBTLE);
        int pw = Hull.severityPillWidth(font, severity);
        Component players = row.participants() == 1 ? Component.translatable("console.neroevents.players.one")
                : Component.translatable("console.neroevents.players.many", row.participants());
        int playersRight = ConsoleLayout.RIGHT_X1 - pw - ConsoleLayout.PILL_GAP;
        WidgetText.drawRight(g, font, players, x + playersRight, y + ConsoleLayout.LINE3_DY,
                playersRight - ConsoleLayout.TEXT_X, EventColours.SUBTLE);
        Hull.severityPill(g, font, rx - pw, y + ConsoleLayout.LINE3_DY - 1, severity);
        explain(g, rx - pw, y + ConsoleLayout.LINE3_DY - 1, pw, Hull.PILL_H,
                Component.translatable(EventColours.wordKey(severity) + ".help"));

        if (slot.strip()) {
            int stripY = y + ConsoleLayout.ROW_H;
            Component tally = Component.translatable("console.neroevents.vote.tally", row.yes(), row.no(), row.needed());
            WidgetText.drawCentredFitted(g, font, tally, x + ConsoleLayout.PANEL_W / 2, stripY + ConsoleLayout.TALLY_DY,
                    ConsoleLayout.PANEL_W - 4 * PAD, EventColours.TITLE);
        }
    }

    /** "320 m north-east" for a public location in the viewer's dimension; {@code null} otherwise. */
    private @Nullable Component where(EventRow row) {
        if (!row.located()) {
            return null;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || !player.level().dimension().identifier().equals(row.dimension())) {
            return null;
        }
        int dx = row.location().getX() - player.blockPosition().getX();
        int dz = row.location().getZ() - player.blockPosition().getZ();
        return ConsoleText.bearing(dx, dz);
    }

    private void drawPast(GuiGraphicsExtractor g, ConsoleSnapshot snap) {
        List<HistoryRow> rows = snap.past();
        if (rows.isEmpty()) {
            centred(g, Component.translatable("console.neroevents.past.empty"));
            return;
        }
        int first = Math.max(0, Math.min(scroll[PAST], Math.max(0, rows.size() - ConsoleLayout.SENTENCE_ROWS)));
        for (int i = 0; i < ConsoleLayout.SENTENCE_ROWS && first + i < rows.size(); i++) {
            ConsoleLayout.Box box = ConsoleLayout.sentence(i);
            int x = left();
            int y = top() + box.y0();
            Hull.severityGlyph(g, x + 12, y + 2, null);
            Component sentence = ConsoleText.of(HistorySentences.sentence(rows.get(first + i)));
            int w = ConsoleLayout.PANEL_W - PAD - ConsoleLayout.SENTENCE_TEXT_X;
            int need = WidgetText.drawWrapped(g, font, sentence, x + ConsoleLayout.SENTENCE_TEXT_X, y + 2, w,
                    ConsoleLayout.TEXT_LINE, 2, EventColours.TITLE);
            if (need > 2) {
                explain(g, x + ConsoleLayout.SENTENCE_TEXT_X, y, w, ConsoleLayout.SENTENCE_H, sentence);
            }
        }
    }

    private void drawMine(GuiGraphicsExtractor g, ConsoleSnapshot snap) {
        int x = left();
        int y = top();
        ConsoleLayout.Box box = ConsoleLayout.WAITING;
        Hull.section(g, font, x + box.x0(), y + box.y0(), x + box.x1(), y + box.y1(),
                Component.translatable("console.neroevents.mine.crate"));
        Component waiting = snap.waitingItems() <= 0 && snap.waitingXp() <= 0L
                ? Component.translatable("console.neroevents.mine.waiting_none")
                : Component.translatable("console.neroevents.mine.waiting", snap.waitingItems(), snap.waitingXp());
        int used = Math.min(2, WidgetText.drawWrapped(g, font, waiting, x + ConsoleLayout.WAITING_TEXT_X,
                y + ConsoleLayout.WAITING_TEXT_Y, ConsoleLayout.WAITING_TEXT_W, ConsoleLayout.TEXT_LINE, 2,
                EventColours.TITLE));
        WidgetText.drawWrapped(g, font, Component.translatable("console.neroevents.mine.credits_note"),
                x + ConsoleLayout.WAITING_TEXT_X, y + ConsoleLayout.WAITING_TEXT_Y + used * ConsoleLayout.TEXT_LINE + 2,
                ConsoleLayout.WAITING_TEXT_W, ConsoleLayout.TEXT_LINE, 3 - used, EventColours.SUBTLE);

        List<LedgerRow> rows = snap.ledger();
        Component empty = null;
        if (!snap.ledgerEnabled()) {
            empty = Component.translatable("console.neroevents.mine.ledger_off");
        } else if (snap.optedOut()) {
            empty = Component.translatable("console.neroevents.mine.opted_out");
        } else if (rows.isEmpty()) {
            empty = Component.translatable("console.neroevents.mine.none", snap.ledgerDays());
        }
        if (empty != null) {
            WidgetText.drawWrapped(g, font, empty, x + PAD + 4, y + ConsoleLayout.LEDGER_Y + 4,
                    ConsoleLayout.PANEL_W - 2 * PAD - 8, ConsoleLayout.TEXT_LINE, 3, EventColours.TITLE);
        } else {
            int first = Math.max(0, Math.min(scroll[MINE], Math.max(0, rows.size() - ConsoleLayout.LEDGER_ROWS)));
            for (int i = 0; i < ConsoleLayout.LEDGER_ROWS && first + i < rows.size(); i++) {
                ConsoleLayout.Box row = ConsoleLayout.ledgerRow(i);
                int ry = y + row.y0();
                Hull.severityGlyph(g, x + 12, ry + 2, Severity.REWARD);
                Component sentence = ConsoleText.of(HistorySentences.ledger(rows.get(first + i)));
                int w = ConsoleLayout.PANEL_W - PAD - ConsoleLayout.SENTENCE_TEXT_X;
                int need = WidgetText.drawWrapped(g, font, sentence, x + ConsoleLayout.SENTENCE_TEXT_X, ry + 2, w,
                        ConsoleLayout.TEXT_LINE, 2, EventColours.TITLE);
                if (need > 2) {
                    explain(g, x + ConsoleLayout.SENTENCE_TEXT_X, ry, w, ConsoleLayout.SENTENCE_H, sentence);
                }
            }
        }
        WidgetText.drawFitted(g, font, Component.translatable("console.neroevents.mine.kept", snap.ledgerDays()),
                x + PAD, y + ConsoleLayout.LEDGER_NOTE_Y, ConsoleLayout.PANEL_W - 2 * PAD, EventColours.SUBTLE);
    }

    private void drawPrivacy(GuiGraphicsExtractor g, ConsoleSnapshot snap) {
        int x = left();
        int y = top();
        ConsoleLayout.Box kept = ConsoleLayout.KEPT;
        Hull.section(g, font, x + kept.x0(), y + kept.y0(), x + kept.x1(), y + kept.y1(),
                Component.translatable("console.neroevents.privacy.kept"));
        int ry = y + ConsoleLayout.KEPT_FIRST_Y;
        int bottom = y + kept.y1() - 2;
        Component ledger;
        if (!snap.ledgerEnabled()) {
            ledger = Component.translatable("console.neroevents.privacy.row.ledger_off");
        } else if (snap.optedOut()) {
            ledger = Component.translatable("console.neroevents.privacy.row.ledger_opted_out");
        } else {
            ledger = Component.translatable("console.neroevents.privacy.row.ledger", snap.ledgerDays());
        }
        ry += keptRow(g, "console.neroevents.privacy.label.ledger", ledger, ry, bottom);
        ry += keptRow(g, "console.neroevents.privacy.label.crate",
                Component.translatable("console.neroevents.privacy.row.crate", snap.crateDays()), ry, bottom);
        ry += keptRow(g, "console.neroevents.privacy.label.optout",
                Component.translatable(snap.optedOut() ? "console.neroevents.yes" : "console.neroevents.no"), ry, bottom);
        keptRow(g, "console.neroevents.privacy.label.not_kept",
                Component.translatable("console.neroevents.privacy.row.not_kept"), ry, bottom);

        ConsoleLayout.Box yours = ConsoleLayout.YOURS;
        Hull.section(g, font, x + yours.x0(), y + yours.y0(), x + yours.x1(), y + yours.y1(),
                Component.translatable("console.neroevents.privacy.yours"));
        int ty = y + ConsoleLayout.YOURS_TEXT_Y;
        int limit = y + yours.y1() - 2;
        for (String key : new String[] {"console.neroevents.privacy.explain.export",
                "console.neroevents.privacy.explain.optout", "console.neroevents.privacy.explain.erase"}) {
            Component text = Component.translatable(key);
            List<net.minecraft.util.FormattedCharSequence> lines = WidgetText.wrap(font, text,
                    ConsoleLayout.YOURS_TEXT_W);
            int drawn = 0;
            for (net.minecraft.util.FormattedCharSequence line : lines) {
                if (ty + 9 > limit) {
                    break;
                }
                g.text(font, line, x + ConsoleLayout.YOURS_TEXT_X, ty, EventColours.SUBTLE, false);
                ty += ConsoleLayout.TEXT_LINE;
                drawn++;
            }
            if (drawn < lines.size()) {
                explain(g, x + ConsoleLayout.YOURS_TEXT_X, ty - drawn * ConsoleLayout.TEXT_LINE,
                        ConsoleLayout.YOURS_TEXT_W, Math.max(1, drawn) * ConsoleLayout.TEXT_LINE, text);
            }
            ty += 2;
        }
    }

    /** A KEPT label/value row; returns the height used. */
    private int keptRow(GuiGraphicsExtractor g, String labelKey, Component value, int y, int bottom) {
        int x = left();
        g.text(font, Component.translatable(labelKey), x + ConsoleLayout.KEPT_LABEL_X, y, EventColours.SUBTLE, false);
        List<net.minecraft.util.FormattedCharSequence> lines = WidgetText.wrap(font, value, ConsoleLayout.KEPT_VALUE_W);
        int drawn = 0;
        for (net.minecraft.util.FormattedCharSequence line : lines) {
            int ly = y + drawn * ConsoleLayout.TEXT_LINE;
            if (ly + 9 > bottom) {
                break;
            }
            g.text(font, line, x + ConsoleLayout.KEPT_VALUE_X, ly, EventColours.TITLE, false);
            drawn++;
        }
        if (drawn < lines.size()) {
            explain(g, x + ConsoleLayout.KEPT_VALUE_X, y, ConsoleLayout.KEPT_VALUE_W,
                    Math.max(1, drawn) * ConsoleLayout.TEXT_LINE, value);
        }
        return Math.max(1, drawn) * ConsoleLayout.TEXT_LINE + ConsoleLayout.KEPT_ROW_GAP;
    }
}
