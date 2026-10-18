package za.co.neroland.neroevents.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import za.co.neroland.neroevents.client.ConsoleLayout.Box;

/**
 * The console and HUD settings layouts, checked as numbers: every box inside its panel and above the footer, no
 * overlaps where things sit side by side, and both panels fitting a 1280x720 window at GUI scale 2 (640x360).
 */
class ConsoleLayoutTest {

    private static final Box FOOTERLESS = new Box(0, 0, ConsoleLayout.PANEL_W, ConsoleLayout.PANEL_H - ConsoleLayout.FOOTER_H);

    @Test
    void panelsFitAt1280x720GuiScale2() {
        assertTrue(ConsoleLayout.PANEL_W <= 640 - 16 && ConsoleLayout.PANEL_H <= 360 - 16);
        assertTrue(ConsoleLayout.HUD_PANEL_W <= 640 - 16 && ConsoleLayout.HUD_PANEL_H <= 360 - 16);
    }

    @Test
    void tabsFitTheTopRowAndDoNotOverlap() {
        Box last = ConsoleLayout.tab(4);
        assertTrue(last.x1() <= ConsoleLayout.PANEL_W - 2, "the Privacy tab stays inside the panel");
        for (int i = 0; i < 4; i++) {
            assertFalse(ConsoleLayout.tab(i).overlaps(ConsoleLayout.tab(i + 1)));
            assertEquals(ConsoleLayout.tab(i).x1(), ConsoleLayout.tab(i + 1).x0());
        }
        assertTrue(ConsoleLayout.tab(0).y1() <= ConsoleLayout.TITLE_Y, "tabs sit above the title row");
        assertEquals(40, ConsoleLayout.TAB_W[4], "Privacy needs 40 px");
    }

    @Test
    void liveRowsFitTheBody() {
        for (int i = 0; i < ConsoleLayout.LIVE_ROWS; i++) {
            assertTrue(ConsoleLayout.row(i).within(FOOTERLESS), "row " + i);
        }
        assertTrue(ConsoleLayout.TEXT_X + ConsoleLayout.TEXT2_W <= ConsoleLayout.RIGHT_X1);
        assertEquals(266, ConsoleLayout.TEXT2_W, "line 2 runs the full row");
        assertEquals(202, ConsoleLayout.TITLE_W);
        assertEquals(186, ConsoleLayout.TITLE_W_OPERATOR);
        assertTrue(ConsoleLayout.TEXT_X + ConsoleLayout.TITLE_W_OPERATOR < ConsoleLayout.CANCEL_X,
                "the operator title stops before the cross");
        assertTrue(ConsoleLayout.CANCEL_X + ConsoleLayout.CANCEL_SIZE < ConsoleLayout.RIGHT_X0);
        assertTrue(ConsoleLayout.TEXT_X + ConsoleLayout.TITLE_W < ConsoleLayout.RIGHT_X0,
                "the title stops before the countdown");
        assertFalse(ConsoleLayout.YES.overlaps(ConsoleLayout.NO));
        assertTrue(ConsoleLayout.YES.height() + 10 <= ConsoleLayout.VOTE_STRIP_H, "tally fits under the buttons");
    }

    @Test
    void pastAndMineRowsFitTheBody() {
        for (int i = 0; i < ConsoleLayout.SENTENCE_ROWS; i++) {
            assertTrue(ConsoleLayout.sentence(i).within(FOOTERLESS), "sentence " + i);
        }
        assertTrue(ConsoleLayout.CLAIM.within(ConsoleLayout.WAITING), "CLAIM inside its box");
        assertTrue(ConsoleLayout.WAITING.within(FOOTERLESS));
        assertTrue(ConsoleLayout.LEDGER.within(FOOTERLESS));
        assertFalse(ConsoleLayout.LEDGER.overlaps(ConsoleLayout.WAITING));
        assertTrue(ConsoleLayout.LEDGER_NOTE_Y + 9 <= ConsoleLayout.PANEL_H - ConsoleLayout.FOOTER_H);
        // CLAIM is the biggest button on the tab.
        assertTrue(ConsoleLayout.CLAIM.width() > ConsoleLayout.YOURS_BUTTON_W);
    }

    @Test
    void privacyBoxesMirrorTheHubLayout() {
        assertTrue(ConsoleLayout.KEPT.within(FOOTERLESS));
        assertTrue(ConsoleLayout.YOURS.within(FOOTERLESS));
        assertFalse(ConsoleLayout.KEPT.overlaps(ConsoleLayout.YOURS));
        for (int i = 0; i < 3; i++) {
            assertTrue(ConsoleLayout.yoursButton(i).within(ConsoleLayout.YOURS), "button " + i);
            if (i < 2) {
                assertFalse(ConsoleLayout.yoursButton(i).overlaps(ConsoleLayout.yoursButton(i + 1)));
            }
        }
    }

    @Test
    void hudSettingsControlsFitTheirColumn() {
        assertEquals(132, ConsoleLayout.MINIATURE.width());
        assertEquals(160, ConsoleLayout.MINIATURE.height());
        Box body = new Box(0, 0, ConsoleLayout.HUD_PANEL_W, ConsoleLayout.HUD_PANEL_H - ConsoleLayout.FOOTER_H);
        assertTrue(ConsoleLayout.MINIATURE.within(body));
        assertFalse(ConsoleLayout.MINIATURE.overlaps(ConsoleLayout.TOGGLES));
        for (int i = 0; i < ConsoleLayout.TOGGLE_COUNT; i++) {
            assertTrue(ConsoleLayout.toggle(i).within(ConsoleLayout.TOGGLES), "toggle " + i);
        }
        for (int i = 0; i < 3; i++) {
            assertTrue(ConsoleLayout.scaleButton(i).within(ConsoleLayout.TOGGLES), "scale " + i);
        }
        assertTrue(ConsoleLayout.resetButton().within(ConsoleLayout.TOGGLES));
        assertTrue(ConsoleLayout.ANCHOR_NAME_Y + 9 <= ConsoleLayout.HUD_PANEL_H - ConsoleLayout.FOOTER_H);
    }
}
