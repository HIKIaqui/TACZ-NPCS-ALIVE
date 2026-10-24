package mod.cdv.tnpc.offline;

/** Pure accounting: all values are world ticks; consecutive active ticks earn nothing. */
public final class OfflineWallet {
    private long lastTick = -1, balance, grantSeen;
    public long lastTick() { return lastTick; }
    public long balance() { return balance; }
    public long grantSeen() { return grantSeen; }
    public void restore(long last, long savedBalance, long seen) {
        lastTick = Math.max(-1, last); balance = Math.max(0, savedBalance); grantSeen = Math.max(0, seen);
    }
    public void observe(long now, long globalGrant, boolean earn) {
        if (lastTick >= 0 && now > lastTick && earn) add(now - lastTick - 1);
        if (globalGrant > grantSeen) add(globalGrant - grantSeen);
        grantSeen = globalGrant;
        lastTick = now;
    }
    public void initialize(long now, long globalGrant) { lastTick = now; grantSeen = globalGrant; }
    public void add(long ticks) {
        if (ticks > 0) balance = ticks > Long.MAX_VALUE - balance ? Long.MAX_VALUE : balance + ticks;
    }
    public boolean spend(long ticks) {
        if (ticks <= 0 || ticks > balance) return false;
        balance -= ticks; return true;
    }
}
