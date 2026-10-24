package mod.cdv.tnpc.travel;

/** Pure, overflow-safe travel accounting. One payment for the whole journey. */
public final class TravelMath {
    private TravelMath() {}
    public static long credit(long balance, long elapsed) {
        if (balance < 0 || elapsed < 0) throw new IllegalArgumentException("Negative credit");
        return elapsed > Long.MAX_VALUE - balance ? Long.MAX_VALUE : balance + elapsed;
    }
    public static long cost(double distance, double blocksPerPoint, int actionCost, int ticksPerPoint) {
        if (!Double.isFinite(distance) || distance < 0 || !Double.isFinite(blocksPerPoint)
            || blocksPerPoint <= 0 || actionCost < 1 || ticksPerPoint < 1)
            throw new IllegalArgumentException("Invalid travel cost parameters");
        double ticks = Math.ceil(distance / blocksPerPoint * actionCost * ticksPerPoint);
        return ticks >= Long.MAX_VALUE ? Long.MAX_VALUE : Math.max(1L, (long)ticks);
    }
}
