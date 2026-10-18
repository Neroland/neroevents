package za.co.neroland.neroevents.compat.claims;

/**
 * The documented registration point for claim mods other than Open Parties and Claims — FTB Chunks and Flan. It is
 * deliberately empty: <b>neither is integrated</b>, and no code path calls into either.
 *
 * <p>Why: an event is destructive only if every protection source abstains, and a claim mod that NeroEvents cannot
 * read is a claim NeroEvents cannot see. Rather than guess at an API, a server running FTB Chunks or Flan keeps its
 * claims safe with {@code eventExclusionZones} (config or {@code /neroevents zone}) or by turning destructive events
 * off with the {@code destructiveEventsEnabled} kill switch.
 *
 * <p>When one of them gains a stable, owner-free query ("is this chunk claimed?"), it becomes one more façade beside
 * {@code compat/opac}: a {@code <X>Bridge} interface, a single implementing class behind a {@code <id>_compat} build
 * flag and a {@code META-INF/services} line, and a protection source registered at
 * {@code ProtectionSources.ORDER_OPAC} — the claim-mod slot — that denies on any claimed or unreadable chunk.
 */
public final class ClaimProviderSeam {

    private ClaimProviderSeam() {
    }
}
