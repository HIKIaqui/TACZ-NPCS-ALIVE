package mod.cdv.tnpc.data;
import com.google.gson.*;
import java.util.Set;
/** Independent switches; applicable damage rules use the lowest percentage, never stack. */
public record FriendlyFireSettings(boolean checkEnabled,double corridorRadius,int retryTicks,
    boolean damageEnabled,boolean alliesEnabled,double alliesPercent,
    boolean nearbyEnabled,double nearbyBlocks,double nearbyPercent,boolean groupEnabled,double groupPercent) {
 public static final FriendlyFireSettings DEFAULTS=new FriendlyFireSettings(true,.35,4,true,true,50,true,6,25,true,10);
 public static FriendlyFireSettings parse(JsonObject o,FriendlyFireSettings f) {
  keys(o,Set.of("shotCheck","damageReduction"));
  var c=obj(o,"shotCheck");keys(c,Set.of("enabled","corridorRadius","retryTicks"));
  var d=obj(o,"damageReduction");keys(d,Set.of("enabled","allies","nearbyAllies","companionGroup"));
  var a=obj(d,"allies");keys(a,Set.of("enabled","damagePercent"));
  var n=obj(d,"nearbyAllies");keys(n,Set.of("enabled","withinBlocks","damagePercent"));
  var g=obj(d,"companionGroup");keys(g,Set.of("enabled","damagePercent"));
  double retry=num(c,"retryTicks",f.retryTicks,1,200);
  if(retry!=Math.rint(retry))throw new IllegalArgumentException("Fractional friendly fire retryTicks");
  return new FriendlyFireSettings(bool(c,"enabled",f.checkEnabled),num(c,"corridorRadius",f.corridorRadius,0,2),(int)retry,
   bool(d,"enabled",f.damageEnabled),bool(a,"enabled",f.alliesEnabled),num(a,"damagePercent",f.alliesPercent,0,100),
   bool(n,"enabled",f.nearbyEnabled),num(n,"withinBlocks",f.nearbyBlocks,0,64),num(n,"damagePercent",f.nearbyPercent,0,100),
   bool(g,"enabled",f.groupEnabled),num(g,"damagePercent",f.groupPercent,0,100));
 }
 public double damageMultiplier(boolean nearby,boolean group) {
  if(!damageEnabled)return 1;
  double percent=100;
  if(alliesEnabled)percent=Math.min(percent,alliesPercent);
  if(nearby && nearbyEnabled)percent=Math.min(percent,nearbyPercent);
  if(group && groupEnabled)percent=Math.min(percent,groupPercent);
  return percent/100;
 }
 private static JsonObject obj(JsonObject o,String k){return o.has(k)?o.getAsJsonObject(k):new JsonObject();}
 private static boolean bool(JsonObject o,String k,boolean f){
  if(!o.has(k))return f;var v=o.get(k);
  if(!v.isJsonPrimitive() || !v.getAsJsonPrimitive().isBoolean())throw new IllegalArgumentException("Invalid friendly fire "+k);
  return v.getAsBoolean();
 }
 private static double num(JsonObject o,String k,double f,double min,double max){
  if(!o.has(k))return f;var v=o.get(k);
  if(!v.isJsonPrimitive() || !v.getAsJsonPrimitive().isNumber())throw new IllegalArgumentException("Invalid friendly fire "+k);
  double value=v.getAsDouble();if(!Double.isFinite(value)||value<min||value>max)throw new IllegalArgumentException("Invalid friendly fire "+k);return value;
 }
 private static void keys(JsonObject o,Set<String> keys){for(String k:o.keySet())if(!keys.contains(k))throw new IllegalArgumentException("Unknown friendly fire option: "+k);}
}
