package za.co.neroland.neroevents.protect;

/**
 * A protection source's answer. There is deliberately no ALLOW: a source can only refuse or stay out of
 * it, so no source can override another's refusal.
 */
public enum Verdict {
    DENY, ABSTAIN
}
