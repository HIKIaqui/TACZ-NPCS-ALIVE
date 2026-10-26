package mod.cdv.tnpc.client;
import java.util.UUID;
public interface TradeScreenAccessor {
    void tnpc$refresh(String json);
    UUID tnpc$tradeNpc();
    String tnpc$tradeFile();
}
