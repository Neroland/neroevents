package za.co.neroland.neroevents.map;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.neroevents.event.EventBounds;
import za.co.neroland.neroevents.event.EventEngine;
import za.co.neroland.neroevents.event.EventInstance;
import za.co.neroland.neroevents.event.EventView;
import za.co.neroland.neroevents.storm.ActiveStorms;
import za.co.neroland.neroevents.war.FactionWarPayload;
import za.co.neroland.neroevents.war.WarScoring;
import za.co.neroland.neroevents.war.WarSides;

/**
 * What the event layers read, behind one seam so tests can seed it without a server. The live source reads the
 * running engine's in-memory live list (it never creates or restores an engine) and the active-storm table. Each
 * entry carries the instance's public {@link EventView}; nothing here hands the provider an instance's true anchor.
 */
public interface EventsMapSource {

    /** One war objective: its position and which side holds it ({@code null} while unclaimed). */
    record Beacon(int index, BlockPos pos, @Nullable WarSides.Side owner) {
    }

    /** A live instance as the map may use it: its public view, its declared radius, its war objectives. */
    record Live(EventView view, int radius, List<Beacon> beacons) {
    }

    List<Live> live();

    /** Dimensions with an active storm. */
    Set<Identifier> stormDimensions();

    /** The live source: the running engine, if any. */
    EventsMapSource LIVE = new EventsMapSource() {
        @Override
        public List<Live> live() {
            EventEngine engine = EventEngine.current();
            if (engine == null) {
                return List.of();
            }
            long now = engine.now();
            List<Live> out = new ArrayList<>();
            for (EventInstance instance : engine.live()) {
                EventBounds bounds = instance.definition().bounds();
                int radius = bounds.kind() == EventBounds.Kind.RADIUS ? bounds.radius() : 0;
                List<Beacon> beacons = new ArrayList<>();
                if (instance.payloadState() instanceof FactionWarPayload.State war) {
                    for (WarScoring.Objective objective : war.objectives()) {
                        beacons.add(new Beacon(objective.index(), objective.pos(), objective.owner()));
                    }
                }
                out.add(new Live(instance.view(now), radius, beacons));
            }
            return out;
        }

        @Override
        public Set<Identifier> stormDimensions() {
            return ActiveStorms.all().keySet();
        }
    };
}
