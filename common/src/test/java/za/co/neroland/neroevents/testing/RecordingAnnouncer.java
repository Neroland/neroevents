package za.co.neroland.neroevents.testing;

import java.util.ArrayList;
import java.util.List;

import za.co.neroland.neroevents.event.EventView;
import za.co.neroland.neroevents.event.announce.Announcer;

/** Records every announcement as {@code kind:reason}. */
public final class RecordingAnnouncer implements Announcer {

    public final List<String> lines = new ArrayList<>();

    @Override
    public void announced(EventView view) {
        lines.add("announced");
    }

    @Override
    public void started(EventView view) {
        lines.add("started");
    }

    @Override
    public void resolved(EventView view) {
        lines.add("resolved:" + view.outcome());
    }

    @Override
    public void cancelled(EventView view, String reasonKey) {
        lines.add("cancelled:" + reasonKey);
    }

    @Override
    public void skipped(EventView view, String reasonKey) {
        lines.add("skipped:" + reasonKey);
    }

    @Override
    public void note(EventView view, String key, Object... args) {
        lines.add("note:" + key);
    }
}
