package mod.cdv.tnpc.requests;

import java.util.*;
import java.util.function.ToDoubleFunction;

/** The mean is calculated before removing stocked candidates. */
public final class RequestSelection {
    public static double mean(double[] weights) {
        double total=0; int count=0;
        for(double w:weights) if(Double.isFinite(w) && w>0) { total+=w; count++; }
        return count==0 ? 0 : total/count;
    }
    public static double chance(double mean,double weight) {
        return mean>0 && weight>=mean && Double.isFinite(weight) ? mean/weight : 0;
    }
    public static int needed(int maxStack,long owned) { return (int)Math.max(0,(maxStack>1 ? 8L : 1L)-Math.max(0,owned)); }
    public static <T> T pick(List<T> candidates,ToDoubleFunction<T> weights,double mean,double random) {
        if(!Double.isFinite(random) || random<0 || random>=1) throw new IllegalArgumentException("Random must be [0,1)");
        double total=0; for(T c:candidates) total+=chance(mean,weights.applyAsDouble(c));
        if(total<=0) return null;
        double cursor=random*total; T last=null;
        for(T c:candidates) { double w=chance(mean,weights.applyAsDouble(c)); if(w<=0) continue; last=c; cursor-=w; if(cursor<0) return c; }
        return last;
    }
}
