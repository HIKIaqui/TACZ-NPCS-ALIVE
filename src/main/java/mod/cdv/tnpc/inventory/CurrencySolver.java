package mod.cdv.tnpc.inventory;

import java.util.*;

/** Exact bounded search: greatest payable value <= price, then fewest physical coins.
 * The descending order and count order also give deterministic ties. No greedy assumption. */
public final class CurrencySolver {
    public record Coin(String item, long value, int maxCount) {
        public Coin {
            if (value < 1 || value > 1_000_000_000L || maxCount < 1 || maxCount > 999)
                throw new IllegalArgumentException("Invalid currency value/count");
        }
    }
    public record Payment(long value, int coins, Map<String,Integer> counts) {}
    private final List<Coin> coins;
    private final Map<Long,Payment> cache = new LinkedHashMap<>(64,.75f,true) {
        protected boolean removeEldestEntry(Map.Entry<Long,Payment> e) { return size()>1024; }
    };
    public CurrencySolver(List<Coin> input) {
        if(input.isEmpty()) throw new IllegalArgumentException("At least one currency is required");
        var ids=new HashSet<String>();
        for(var c:input) if(!ids.add(c.item())) throw new IllegalArgumentException("Duplicate currency item");
        coins=input.stream().sorted(Comparator.comparingLong(Coin::value).reversed().thenComparing(Coin::item)).toList();
    }
    public synchronized Payment solve(long target) {
        if(target<1) throw new IllegalArgumentException("Price must be positive");
        var previous=cache.get(target); if(previous!=null) return previous;
        var search=new Search(target); search.visit(0,3,0,0,new LinkedHashMap<>());
        cache.put(target,search.best); return search.best;
    }
    private final class Search {
        final long target;
        Payment best=new Payment(0,0,Map.of());
        Search(long target) { this.target=target; }
        void visit(int start,int types,long value,int count,LinkedHashMap<String,Integer> selected) {
            if(value>best.value() || value==best.value() && count<best.coins())
                best=new Payment(value,count,Collections.unmodifiableMap(new LinkedHashMap<>(selected)));
            if(types==0 || start>=coins.size() || value==target) return;
            long remaining=target-value;
            // Optimistic bound permits pruning without excluding any legal combination.
            long[] largest=new long[types];
            for(int i=start;i<coins.size();i++) {
                Coin c=coins.get(i); long capacity=Math.min(c.maxCount(),remaining/c.value())*c.value();
                for(int k=0;k<types;k++) if(capacity>largest[k]) { long swap=largest[k]; largest[k]=capacity; capacity=swap; }
            }
            long upper=value; for(long capacity:largest) upper+=capacity;
            if(upper<best.value()) return;
            for(int i=start;i<coins.size();i++) {
                Coin c=coins.get(i); int max=(int)Math.min(c.maxCount(),remaining/c.value());
                if(best.value()==target && count+(remaining+c.value()-1)/c.value()>=best.coins()) break;
                if(types==1) {
                    if(max>0) { selected.put(c.item(),max); visit(coins.size(),0,value+c.value()*max,count+max,selected); selected.remove(c.item()); }
                } else for(int n=max;n>=1;n--) {
                    selected.put(c.item(),n); visit(i+1,types-1,value+c.value()*n,count+n,selected); selected.remove(c.item());
                }
            }
        }
    }
}
