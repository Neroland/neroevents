package za.co.neroland.neroevents.event;

/**
 * One population band: an online-count range, how much slower (or faster) events come, and how many
 * may run at once.
 *
 * @param name               the band's name as datapack {@code conditions.bands} refers to it ({@code 2-4}, {@code 10+})
 * @param minOnline          the lowest online count in the band
 * @param maxOnline          the highest online count, or {@code -1} for an open top band
 * @param intervalMultiplier multiplies the base interval between automatic events
 * @param concurrentCap      the most events running at once in this band
 */
public record PopulationBand(String name, int minOnline, int maxOnline, double intervalMultiplier,
        int concurrentCap) {

    public boolean contains(int online) {
        return online >= minOnline && (maxOnline < 0 || online <= maxOnline);
    }
}
