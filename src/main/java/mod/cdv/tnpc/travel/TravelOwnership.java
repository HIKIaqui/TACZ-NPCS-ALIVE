package mod.cdv.tnpc.travel;

/** Pure ownership gate, shared by disk-entity admission and its isolated tests. */
public final class TravelOwnership {
    public enum Phase { TRAVELLING, ARRIVING, RESIDENT, DEAD }
    private TravelOwnership() {}
    public static boolean mayJoin(Phase phase, long expectedEpoch, long actualEpoch,
                                  boolean sameDimension, double savedPositionDistanceSquared) {
        return (phase == Phase.ARRIVING || phase == Phase.RESIDENT)
            && expectedEpoch > 0 && expectedEpoch == actualEpoch && sameDimension
            && Double.isFinite(savedPositionDistanceSquared) && savedPositionDistanceSquared >= 0
            && savedPositionDistanceSquared < 1;
    }
}
