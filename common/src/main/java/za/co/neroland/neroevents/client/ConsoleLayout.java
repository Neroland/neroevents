package za.co.neroland.neroevents.client;

/**
 * Every rectangle on the Event Console and the HUD settings screen, panel-relative, in one pure table so the
 * layout is checked by a unit test (boxes inside the panel, rows that fit their region, buttons that do not
 * overlap) instead of by eye.
 *
 * <p>The console's header has one extra 14-px row: the 162-px title and the five tabs (the Privacy tab alone
 * needs 40 px) cannot share one 300-px line, so the tabs sit on the top row and the title and subtitle move
 * down by {@link #HEADER_SHIFT}; every body rectangle keeps its position relative to the body.
 *
 * <pre>
 *  y   4  [Live][Soon][Past][Mine][Privacy]            tabs at x 150
 *     20  What's happening on this server  (gear)     title
 *     31  subtitle
 *     44  body … (tab content)
 *    230  footer (14 px)
 * </pre>
 */
public final class ConsoleLayout {

    /** A panel-relative rectangle, {@code x1}/{@code y1} exclusive. */
    public record Box(int x0, int y0, int x1, int y1) {

        public int width() {
            return x1 - x0;
        }

        public int height() {
            return y1 - y0;
        }

        public boolean contains(double x, double y) {
            return x >= x0 && x < x1 && y >= y0 && y < y1;
        }

        public boolean overlaps(Box other) {
            return x0 < other.x1 && other.x0 < x1 && y0 < other.y1 && other.y0 < y1;
        }

        public boolean within(Box outer) {
            return x0 >= outer.x0 && y0 >= outer.y0 && x1 <= outer.x1 && y1 <= outer.y1;
        }

        /** This box moved down by {@code dy}. */
        public Box down(int dy) {
            return new Box(x0, y0 + dy, x1, y1 + dy);
        }
    }

    public static final int PAD = 8;
    public static final int FOOTER_H = 14;
    public static final int TEXT_LINE = 10;

    // --- console hull ---------------------------------------------------------------------------------------

    public static final int HEADER_SHIFT = 14;
    public static final int PANEL_W = 300;
    public static final int PANEL_H = 230 + HEADER_SHIFT;
    public static final Box PANEL = new Box(0, 0, PANEL_W, PANEL_H);
    public static final int TITLE_Y = 6 + HEADER_SHIFT;
    public static final int SUBTITLE_Y = 17 + HEADER_SHIFT;
    public static final int BODY_Y = 30 + HEADER_SHIFT;
    public static final Box BODY = new Box(0, BODY_Y, PANEL_W, PANEL_H - FOOTER_H);

    public static final int TABS_X = 150;
    public static final int TABS_Y = 4;
    public static final int TAB_H = 14;
    /** Live, Soon, Past, Mine, Privacy: each wide enough for its word. */
    public static final int[] TAB_W = {26, 28, 26, 26, 40};
    public static final int GEAR_SIZE = 11;
    /** Gear sits right after the title; this is its y. */
    public static final int GEAR_Y = TITLE_Y - 2;

    // --- Live / Soon rows -----------------------------------------------------------------------------------

    public static final int ROW_H = 36;
    public static final int LIVE_ROWS = 4;
    public static final Box ROWS = new Box(PAD, BODY_Y, PANEL_W - PAD, BODY_Y + ROW_H * LIVE_ROWS);
    public static final int BAND_X0 = 8;
    public static final int BAND_X1 = 11;
    public static final int GLYPH_X = 14;
    public static final int GLYPH_DY = 4;
    public static final int TEXT_X = 26;
    public static final int LINE1_DY = 4;
    public static final int LINE2_DY = 15;
    public static final int LINE3_DY = 25;
    /** Right column on line 1 (the countdown); line 3 right-aligns the players and the severity pill at x 292. */
    public static final int RIGHT_X0 = 230;
    public static final int RIGHT_X1 = 292;
    /** Operator cancel cross, on line 1 between the title and the countdown. */
    public static final int CANCEL_X = 214;
    public static final int CANCEL_SIZE = 11;
    /** Line 1 title budget (up to the countdown column). */
    public static final int TITLE_W = RIGHT_X0 - 2 - TEXT_X;
    /** Line 1 title budget when the operator cross is drawn. */
    public static final int TITLE_W_OPERATOR = CANCEL_X - 2 - TEXT_X;
    /** Line 2 (what to do) runs the full row width: nothing else sits on it. */
    public static final int TEXT2_W = RIGHT_X1 - TEXT_X;
    /** Gap between the players count and the severity pill on line 3. */
    public static final int PILL_GAP = 6;
    /** The vote strip under a Soon row while a vote is open. */
    public static final int VOTE_STRIP_H = 24;
    public static final Box YES = new Box(14, 0, 134, 14);
    public static final Box NO = new Box(166, 0, 286, 14);
    public static final int TALLY_DY = 15;

    // --- Past -----------------------------------------------------------------------------------------------

    public static final int SENTENCE_H = 22;
    public static final int SENTENCE_ROWS = 7;
    public static final Box SENTENCES = new Box(PAD, BODY_Y, PANEL_W - PAD, BODY_Y + SENTENCE_H * SENTENCE_ROWS);
    public static final int SENTENCE_TEXT_X = 22;

    // --- Mine -----------------------------------------------------------------------------------------------

    public static final Box WAITING = new Box(8, 30, 292, 78).down(HEADER_SHIFT);
    public static final Box CLAIM = new Box(148, 38, 288, 64).down(HEADER_SHIFT);
    public static final int WAITING_TEXT_X = 12;
    public static final int WAITING_TEXT_Y = 38 + HEADER_SHIFT;
    public static final int WAITING_TEXT_W = CLAIM.x0() - 4 - WAITING_TEXT_X;
    public static final int LEDGER_Y = 82 + HEADER_SHIFT;
    public static final int LEDGER_ROWS = 5;
    public static final Box LEDGER = new Box(PAD, LEDGER_Y, PANEL_W - PAD, LEDGER_Y + SENTENCE_H * LEDGER_ROWS);
    public static final int LEDGER_NOTE_Y = LEDGER.y1() + 2;

    // --- Privacy (the hub's KEPT / YOURS layout) ------------------------------------------------------------

    public static final Box KEPT = new Box(8, 30, 292, 118).down(HEADER_SHIFT);
    public static final int KEPT_LABEL_X = 12;
    public static final int KEPT_VALUE_X = 84;
    public static final int KEPT_VALUE_W = 204;
    public static final int KEPT_FIRST_Y = 38 + HEADER_SHIFT;
    public static final int KEPT_ROW_GAP = 2;
    public static final Box YOURS = new Box(8, 122, 292, 214).down(HEADER_SHIFT);
    public static final int YOURS_BUTTON_Y = 130 + HEADER_SHIFT;
    public static final int YOURS_BUTTON_H = 14;
    public static final int YOURS_BUTTON_W = 88;
    public static final int[] YOURS_BUTTON_X = {12, 106, 200};
    public static final int YOURS_TEXT_Y = 149 + HEADER_SHIFT;
    public static final int YOURS_TEXT_X = 12;
    public static final int YOURS_TEXT_W = 276;

    // --- HUD settings screen (300 x 230, no tabs) -----------------------------------------------------------

    public static final int HUD_PANEL_W = 300;
    public static final int HUD_PANEL_H = 230;
    public static final Box HUD_PANEL = new Box(0, 0, HUD_PANEL_W, HUD_PANEL_H);
    public static final Box MINIATURE_AREA = new Box(8, 30, 140, 190);
    /** The 132 x 160 miniature screen (the whole left box). */
    public static final Box MINIATURE = MINIATURE_AREA;
    public static final int ANCHOR_NAME_Y = 194;
    public static final Box TOGGLES = new Box(144, 30, 292, 190);
    public static final int TOGGLE_H = 14;
    public static final int TOGGLE_GAP = 2;
    public static final int TOGGLE_COUNT = 6;
    public static final int SCALE_Y = 30 + TOGGLE_COUNT * (TOGGLE_H + TOGGLE_GAP) + 4;
    public static final int SCALE_BUTTON_W = 48;
    public static final int RESET_Y = SCALE_Y + TOGGLE_H + 6;
    /** The miniature draws the HUD at a quarter of its size. */
    public static final int MINIATURE_DIVISOR = 4;
    /** Snap radius in miniature pixels. */
    public static final int SNAP_RADIUS = 6;

    private ConsoleLayout() {
    }

    /** The tab at {@code index}. */
    public static Box tab(int index) {
        int x = TABS_X;
        for (int i = 0; i < index; i++) {
            x += TAB_W[i];
        }
        return new Box(x, TABS_Y, x + TAB_W[index], TABS_Y + TAB_H);
    }

    /** The {@code index}-th Live/Soon row slot of plain height (no vote strip). */
    public static Box row(int index) {
        int y = BODY_Y + index * ROW_H;
        return new Box(PAD, y, PANEL_W - PAD, y + ROW_H);
    }

    /** The {@code index}-th Past sentence row. */
    public static Box sentence(int index) {
        int y = BODY_Y + index * SENTENCE_H;
        return new Box(PAD, y, PANEL_W - PAD, y + SENTENCE_H);
    }

    /** The {@code index}-th Mine ledger row. */
    public static Box ledgerRow(int index) {
        int y = LEDGER_Y + index * SENTENCE_H;
        return new Box(PAD, y, PANEL_W - PAD, y + SENTENCE_H);
    }

    /** The privacy action button at {@code index} (0 export, 1 opt out / in, 2 erase). */
    public static Box yoursButton(int index) {
        int x = YOURS_BUTTON_X[index];
        return new Box(x, YOURS_BUTTON_Y, x + YOURS_BUTTON_W, YOURS_BUTTON_Y + YOURS_BUTTON_H);
    }

    /** The HUD settings toggle at {@code index}. */
    public static Box toggle(int index) {
        int y = TOGGLES.y0() + index * (TOGGLE_H + TOGGLE_GAP);
        return new Box(TOGGLES.x0(), y, TOGGLES.x1(), y + TOGGLE_H);
    }

    /** The scale ladder button at {@code index} (0.75 / 1.0 / 1.25). */
    public static Box scaleButton(int index) {
        int x = TOGGLES.x0() + index * (SCALE_BUTTON_W + 2);
        return new Box(x, SCALE_Y, x + SCALE_BUTTON_W, SCALE_Y + TOGGLE_H);
    }

    public static Box resetButton() {
        return new Box(TOGGLES.x0(), RESET_Y, TOGGLES.x1(), RESET_Y + TOGGLE_H);
    }
}
