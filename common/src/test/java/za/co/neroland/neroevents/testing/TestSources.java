package za.co.neroland.neroevents.testing;

import za.co.neroland.neroevents.protect.Footprint;
import za.co.neroland.neroevents.protect.ProtectionEvaluation;
import za.co.neroland.neroevents.protect.ProtectionSource;
import za.co.neroland.neroevents.protect.Verdict;

/** Scriptable protection sources for tests. */
public final class TestSources {

    private TestSources() {
    }

    /** Denies everything (except the whole-dimension sentinel, like every real source). */
    public static final class DenyAllSource implements ProtectionSource {
        private final String id;
        public int calls;

        public DenyAllSource(String id) {
            this.id = id;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public Verdict check(ProtectionEvaluation ctx, Footprint footprint) {
            calls++;
            if (footprint.wholeDimension()) {
                return Verdict.ABSTAIN;
            }
            ctx.note("deny all");
            return Verdict.DENY;
        }
    }

    /** Denies any footprint whose XZ box intersects the given box. */
    public static final class DenyInBoxSource implements ProtectionSource {
        private final String id;
        private final int minX;
        private final int minZ;
        private final int maxX;
        private final int maxZ;

        public DenyInBoxSource(String id, int minX, int minZ, int maxX, int maxZ) {
            this.id = id;
            this.minX = minX;
            this.minZ = minZ;
            this.maxX = maxX;
            this.maxZ = maxZ;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public Verdict check(ProtectionEvaluation ctx, Footprint footprint) {
            if (!footprint.wholeDimension() && footprint.intersectsXZ(minX, minZ, maxX, maxZ)) {
                ctx.note("box");
                return Verdict.DENY;
            }
            return Verdict.ABSTAIN;
        }
    }

    /** Always throws. */
    public static final class ThrowingSource implements ProtectionSource {
        private final String id;

        public ThrowingSource(String id) {
            this.id = id;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public Verdict check(ProtectionEvaluation ctx, Footprint footprint) {
            throw new IllegalStateException("boom");
        }
    }

    /** Abstains and counts calls. */
    public static final class CountingSource implements ProtectionSource {
        private final String id;
        public int calls;

        public CountingSource(String id) {
            this.id = id;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public Verdict check(ProtectionEvaluation ctx, Footprint footprint) {
            calls++;
            return Verdict.ABSTAIN;
        }
    }
}
