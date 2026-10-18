package za.co.neroland.neroevents.payload.debris;

import java.io.BufferedReader;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import za.co.neroland.neroevents.NeroEventsCommon;

/**
 * The wreck layout catalog, loaded from {@code data/<namespace>/neroevents/wrecks/<name>.json} (id
 * {@code <namespace>:<name>}) the same way as the event definitions: lazily from the running server's
 * {@link ResourceManager}, cached against the server and that resource-manager instance, re-read when
 * {@code /reload} replaced it. A file that does not decode is ignored with one WARN naming it.
 */
public final class WreckLayouts {

    private static final String DIRECTORY = "neroevents/wrecks";
    private static final String EXTENSION = ".json";
    /** The four shipped variants (used for load-time validation before any server has loaded the catalog). */
    public static final Set<String> SHIPPED = Set.of("wreck_pod_shell", "wreck_strut_field", "wreck_cargo_spill",
            "wreck_hull_rib");
    /** The largest box a layout may have; the footprint of an unknown variant. */
    static final int MAX_X = 7;
    static final int MAX_Y = 5;
    static final int MAX_Z = 7;

    private static MinecraftServer loadedFor;
    private static ResourceManager loadedFrom;
    private static Map<Identifier, WreckLayout> layouts = Map.of();

    private WreckLayouts() {
    }

    /** Re-reads iff the datapacks were reloaded (or this is another server). */
    public static synchronized void refreshIfReloaded(MinecraftServer server) {
        if (server == loadedFor && server.getResourceManager() == loadedFrom) {
            return;
        }
        Map<Identifier, WreckLayout> loaded = new TreeMap<>();
        ResourceManager resources = server.getResourceManager();
        for (Map.Entry<Identifier, Resource> entry : resources
                .listResources(DIRECTORY, path -> path.getPath().endsWith(EXTENSION)).entrySet()) {
            Identifier file = entry.getKey();
            String path = file.getPath();
            Identifier id = Identifier.fromNamespaceAndPath(file.getNamespace(),
                    path.substring(DIRECTORY.length() + 1, path.length() - EXTENSION.length()));
            try (BufferedReader reader = entry.getValue().openAsReader()) {
                JsonElement json = JsonParser.parseReader(reader);
                Optional<WreckLayout> layout = decode(json);
                if (layout.isPresent()) {
                    loaded.put(id, layout.get());
                } else {
                    NeroEventsCommon.LOGGER.warn("[NeroEvents] wreck layout {} does not decode; ignored", id);
                }
            } catch (Exception e) {
                NeroEventsCommon.LOGGER.warn("[NeroEvents] wreck layout {} could not be read ({}); ignored", id,
                        e.getClass().getSimpleName());
            }
        }
        layouts = Map.copyOf(loaded);
        loadedFor = server;
        loadedFrom = resources;
        NeroEventsCommon.LOGGER.info("[NeroEvents] Loaded {} wreck layout(s)", layouts.size());
    }

    /** Decodes one layout file (pure). */
    public static Optional<WreckLayout> decode(JsonElement json) {
        return WreckLayout.CODEC.parse(JsonOps.INSTANCE, json).result();
    }

    /** The layout for {@code id} on {@code server}. */
    public static synchronized Optional<WreckLayout> get(MinecraftServer server, Identifier id) {
        refreshIfReloaded(server);
        return Optional.ofNullable(layouts.get(id));
    }

    /** The last loaded layout for {@code id}, without a server (footprints). */
    public static synchronized Optional<WreckLayout> cached(Identifier id) {
        return Optional.ofNullable(layouts.get(id));
    }

    /** A variant name from a definition: a bare name is in the {@code neroevents} namespace. */
    public static Identifier idOf(String name) {
        Identifier parsed = name.indexOf(':') >= 0 ? Identifier.tryParse(name) : null;
        return parsed != null ? parsed : Identifier.fromNamespaceAndPath(NeroEventsCommon.MOD_ID, name);
    }

    /** Whether a variant is known: shipped, or in the last loaded catalog. */
    public static synchronized boolean known(Identifier id) {
        return layouts.containsKey(id)
                || (NeroEventsCommon.MOD_ID.equals(id.getNamespace()) && SHIPPED.contains(id.getPath()));
    }
}
