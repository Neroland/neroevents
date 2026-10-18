package za.co.neroland.neroevents.war;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.neroevents.payload.PendingCredits;
import za.co.neroland.neroevents.war.WarSides.Side;

/**
 * Capture and scoring for one faction war — pure, one {@link #pass} every {@value #PASS_TICKS} ticks.
 *
 * <p>Per objective: players of side A and side B present (neutrals never count). Both ⇒ contested, progress
 * frozen. One side ⇒ if it owns the objective it earns hold points ({@code hold_points_per_100_ticks × 20/100},
 * accumulated in hundredths so whole points are added exactly); otherwise progress grows by
 * {@value #PASS_TICKS} and at {@code capture_ticks} the objective flips, the side scores {@code capture_points}
 * and every present member of that side is credited {@code OBJECTIVE 1/n}. Nobody ⇒ progress decays by
 * {@value #DECAY_PER_PASS}.
 *
 * <p>Resolution: the higher score wins; a tie has no victor. Reputation lines go to credited participants with
 * a frozen side only: the victor side {@code +min(win, cap)} with its own faction, the loser side
 * {@code −min(loss, cap)} with its own; neutrals nothing.
 */
public final class WarScoring {

    /** Ticks between passes. */
    public static final int PASS_TICKS = 20;
    /** Progress lost per pass by an objective nobody stands at. */
    public static final int DECAY_PER_PASS = 10;

    /** The definition's scoring rules. */
    public record Rules(int captureTicks, int capturePoints, int holdPointsPer100Ticks, int winScore) {
    }

    /** One capture objective (a war beacon). Mutable, server thread only. */
    public static final class Objective {
        final int index;
        final BlockPos pos;
        @Nullable Side owner;
        int progress;
        @Nullable Side progressSide;
        boolean contested;
        long lastCaptureAt = -1L;

        public Objective(int index, BlockPos pos) {
            this.index = index;
            this.pos = pos.immutable();
        }

        public int index() {
            return index;
        }

        public BlockPos pos() {
            return pos;
        }

        /** The owning side, or {@code null} while unowned. */
        public @Nullable Side owner() {
            return owner;
        }

        public int progress() {
            return progress;
        }

        /** The side whose presence last moved the progress ({@code null} if none). */
        public @Nullable Side progressSide() {
            return progressSide;
        }

        public boolean contested() {
            return contested;
        }

        public long lastCaptureAt() {
            return lastCaptureAt;
        }

        /** For restore only. */
        void restore(@Nullable Side owner, int progress, long lastCaptureAt) {
            this.owner = owner == Side.NEUTRAL ? null : owner;
            this.progress = Math.max(0, progress);
            this.lastCaptureAt = lastCaptureAt;
        }
    }

    /** One flip that happened in a pass. */
    public record Capture(int index, Side side, List<UUID> capturers) {
    }

    /** One reputation change to apply at resolution. */
    public record ReputationLine(UUID player, Identifier faction, int delta) {
    }

    private final Rules rules;
    private final List<Objective> objectives;
    private int scoreA;
    private int scoreB;
    private int holdHundredthsA;
    private int holdHundredthsB;

    public WarScoring(Rules rules, List<Objective> objectives) {
        this.rules = rules;
        this.objectives = new ArrayList<>(objectives);
    }

    public Rules rules() {
        return rules;
    }

    public List<Objective> objectives() {
        return objectives;
    }

    public int score(Side side) {
        return side == Side.A ? scoreA : side == Side.B ? scoreB : 0;
    }

    /** The fractional hold counter of a side, 0..1 (saved as {@code holdFractionA/B}). */
    public double holdFraction(Side side) {
        return (side == Side.A ? holdHundredthsA : side == Side.B ? holdHundredthsB : 0) / 100.0D;
    }

    /** For restore only. */
    void restoreScores(int a, int b, double holdA, double holdB) {
        scoreA = Math.max(0, a);
        scoreB = Math.max(0, b);
        holdHundredthsA = clampHundredths(holdA);
        holdHundredthsB = clampHundredths(holdB);
    }

    private static int clampHundredths(double fraction) {
        if (!(fraction > 0.0D)) {
            return 0;
        }
        return (int) Math.min(99L, Math.round(fraction * 100.0D));
    }

    /**
     * One pass. {@code presence.get(i)} holds the players standing at objective {@code i} (alive, not
     * spectating — the caller's MC query); sides are resolved (and frozen) through {@code sides}. Capture credit
     * is queued on {@code pending}. Returns the flips of this pass.
     */
    public List<Capture> pass(List<? extends Collection<UUID>> presence, WarSides sides,
            Function<UUID, Set<Identifier>> membership, PendingCredits pending, long now) {
        List<Capture> captures = new ArrayList<>();
        for (int i = 0; i < objectives.size(); i++) {
            Objective o = objectives.get(i);
            Collection<UUID> here = i < presence.size() && presence.get(i) != null ? presence.get(i) : List.of();
            List<UUID> onA = new ArrayList<>();
            List<UUID> onB = new ArrayList<>();
            for (UUID player : here) {
                Side side = sides.sideOf(player, membership);
                if (side == Side.A) {
                    onA.add(player);
                } else if (side == Side.B) {
                    onB.add(player);
                }
            }
            boolean a = !onA.isEmpty();
            boolean b = !onB.isEmpty();
            if (a && b) {
                o.contested = true; // progress frozen
            } else if (a || b) {
                Side side = a ? Side.A : Side.B;
                List<UUID> present = a ? onA : onB;
                o.contested = false;
                if (o.owner == side) {
                    hold(side);
                } else {
                    o.progress += PASS_TICKS;
                    o.progressSide = side;
                    if (o.progress >= rules.captureTicks()) {
                        o.owner = side;
                        o.progress = 0;
                        o.progressSide = null;
                        o.lastCaptureAt = now;
                        add(side, rules.capturePoints());
                        double share = 1.0D / present.size();
                        for (UUID player : present) {
                            pending.objective(player, share);
                        }
                        captures.add(new Capture(o.index, side, List.copyOf(present)));
                    }
                }
            } else {
                o.contested = false;
                o.progress = Math.max(0, o.progress - DECAY_PER_PASS);
                if (o.progress == 0) {
                    o.progressSide = null;
                }
            }
        }
        return captures;
    }

    private void hold(Side side) {
        int gained = rules.holdPointsPer100Ticks() * PASS_TICKS;
        if (side == Side.A) {
            holdHundredthsA += gained;
            add(Side.A, holdHundredthsA / 100);
            holdHundredthsA %= 100;
        } else {
            holdHundredthsB += gained;
            add(Side.B, holdHundredthsB / 100);
            holdHundredthsB %= 100;
        }
    }

    private void add(Side side, int points) {
        if (points <= 0) {
            return;
        }
        if (side == Side.A) {
            scoreA = (int) Math.min(Integer.MAX_VALUE, (long) scoreA + points);
        } else if (side == Side.B) {
            scoreB = (int) Math.min(Integer.MAX_VALUE, (long) scoreB + points);
        }
    }

    /** Whether {@code win_score > 0} and a side has reached it (the war ends early). */
    public boolean winReached() {
        return rules.winScore() > 0 && Math.max(scoreA, scoreB) >= rules.winScore();
    }

    /** The side with the higher score; empty on a tie. */
    public Optional<Side> victor() {
        if (scoreA == scoreB) {
            return Optional.empty();
        }
        return Optional.of(scoreA > scoreB ? Side.A : Side.B);
    }

    /** Objectives owned by the leading side (0 on a tie), for the progress bar. */
    public int ownedByLeader() {
        Optional<Side> leader = victor();
        if (leader.isEmpty()) {
            return 0;
        }
        int owned = 0;
        for (Objective o : objectives) {
            if (o.owner == leader.get()) {
                owned++;
            }
        }
        return owned;
    }

    /**
     * The reputation changes for a war with a victor: each credited participant with a frozen warring side gets
     * {@code +min(win, cap)} (victor side) or {@code −min(loss, cap)} (loser side) with their own side's faction;
     * neutrals and unresolved players get nothing. Zero deltas are dropped.
     */
    public static List<ReputationLine> reputationLines(Collection<UUID> participants, WarSides sides, Side victor,
            int reputationWin, int reputationLoss, int cap) {
        List<ReputationLine> out = new ArrayList<>();
        if (victor == Side.NEUTRAL) {
            return out;
        }
        int limit = Math.max(0, cap);
        int win = Math.min(Math.max(0, reputationWin), limit);
        int loss = Math.min(Math.max(0, reputationLoss), limit);
        for (UUID player : participants) {
            Side side = sides.frozenSide(player).orElse(Side.NEUTRAL);
            if (side == Side.NEUTRAL) {
                continue;
            }
            Identifier faction = sides.faction(side).orElse(null);
            if (faction == null) {
                continue;
            }
            int delta = side == victor ? win : -loss;
            if (delta != 0) {
                out.add(new ReputationLine(player, faction, delta));
            }
        }
        return out;
    }
}
