package mod.cdv.tnpc.entity.ai;

import mod.cdv.tnpc.data.NpcBehaviorConfig.PerceptionSettings;

/** Local awareness memory. All times use world game ticks; no attribute mutations. */
public final class NpcAwareness {
    public enum State { CALM, RESTING, ALERT }
    private boolean resting;
    private long alertUntil;

    public void beginRest() { resting = true; }
    public void endRest() { resting = false; }
    public void alert(long now, int duration) { alertUntil = Math.max(alertUntil, now + duration); }
    public boolean isAlert(long now, boolean hasTarget) { return hasTarget || now < alertUntil; }
    public State state(long now, boolean hasTarget) {
        return isAlert(now, hasTarget) ? State.ALERT : resting ? State.RESTING : State.CALM;
    }
    public boolean canRest(long now, boolean hasTarget, PerceptionSettings settings) {
        return !hasTarget && (!settings.enabled() || !isAlert(now, false));
    }
    public double detectionRange(long now, boolean hasTarget, double followRange, PerceptionSettings settings) {
        if (!settings.enabled()) return followRange;
        double range = switch (state(now, hasTarget)) {
            case RESTING -> settings.restingRange();
            case CALM -> settings.calmRange();
            case ALERT -> settings.alertRange();
        };
        return Math.min(followRange, range);
    }
    public long alertUntil() { return alertUntil; }
    public void restore(long until) { alertUntil = Math.max(0, until); resting = false; }
}
