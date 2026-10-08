import id.walt.wallet2.handlers.WalletIssuanceSessionRecord;
import id.walt.wallet2.handlers.WalletIssuanceSessionStore;
import kotlin.coroutines.Continuation;

/** Compiled against waltid-openid4vc-wallet-jvm 1.1.0, never against the candidate. */
public final class ReleasedSessionStore implements WalletIssuanceSessionStore {
    private final java.util.Map<String, WalletIssuanceSessionRecord> records = new java.util.LinkedHashMap<>();
    public Object get(String id, Continuation<? super WalletIssuanceSessionRecord> c) { return records.get(id); }
    public Object list(Continuation<? super java.util.List<WalletIssuanceSessionRecord>> c) {
        return new java.util.ArrayList<>(records.values());
    }
    public Object put(WalletIssuanceSessionRecord record, Continuation<? super kotlin.Unit> c) {
        records.put(record.getId(), record);
        return kotlin.Unit.INSTANCE;
    }
    public Object remove(String id, Continuation<? super Boolean> c) { return records.remove(id) != null; }
}
