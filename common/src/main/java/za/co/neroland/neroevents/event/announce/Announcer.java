package za.co.neroland.neroevents.event.announce;

import za.co.neroland.neroevents.event.EventView;

/**
 * How the engine tells players about an event. Built from {@link EventView}s only, so an announcement
 * can never carry a roster or a non-public location. The HUD packet becomes a second announcer later.
 */
public interface Announcer {

    void announced(EventView view);

    void started(EventView view);

    void resolved(EventView view);

    void cancelled(EventView view, String reasonKey);

    void skipped(EventView view, String reasonKey);

    void note(EventView view, String key, Object... args);

    /** Says nothing (tests, and a server with nobody to tell). */
    Announcer SILENT = new Announcer() {
        @Override
        public void announced(EventView view) {
        }

        @Override
        public void started(EventView view) {
        }

        @Override
        public void resolved(EventView view) {
        }

        @Override
        public void cancelled(EventView view, String reasonKey) {
        }

        @Override
        public void skipped(EventView view, String reasonKey) {
        }

        @Override
        public void note(EventView view, String key, Object... args) {
        }
    };
}
