package npvs;

import certifier.MonotonicTimestamp;
import certifier.Timestamp;
import npvs.messaging.FlushMessage;
import npvs.messaging.NPVSReply;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import transaction_manager.utils.ByteArrayWrapper;

import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public abstract class AbstractNPVS implements NPVS<Long>{
    private static final Logger LOG = LoggerFactory.getLogger(AbstractNPVS.class);
    private final HashSet<Timestamp<Long>> requestsMade;
    private final Timestamp<Long> currentCommitTs;
    private Timestamp<Long> rebootTs;

    public AbstractNPVS(){
        this.requestsMade = new HashSet<>();
        this.currentCommitTs = new MonotonicTimestamp(0);
        this.rebootTs = new MonotonicTimestamp(-1);
    }

    public abstract void evict(Timestamp<Long> lowWaterMark);

    public abstract CompletableFuture<Void> putImpl(Map<ByteArrayWrapper, byte[]> writeMap, Timestamp<Long> ts);

    public abstract CompletableFuture<NPVSReply> getImpl(ByteArrayWrapper key, Timestamp<Long> ts);

    public CompletableFuture<NPVSReply> get(ByteArrayWrapper key, Timestamp<Long> ts){
        if (ts.isBefore(rebootTs))
            return CompletableFuture.completedFuture(NPVSReply.FAIL());
        else
            return getImpl(key, ts);
    }

    @Override
    public CompletableFuture<Void> put(FlushMessage flushMessage) {
        if(requestsMade.contains(flushMessage.getTransactionStartTimestamp())) {
            LOG.info("Duplicate Request arrived. Sending confirmation");
            return CompletableFuture.completedFuture(null);
        }
        Timestamp<Long> incomingCurrentCommit = flushMessage.getCurrentTimestamp();
        if(incomingCurrentCommit.isAfter(currentCommitTs)){
            LOG.info("New currentCommit arrived. Clearing previous requests");
            currentCommitTs.set(incomingCurrentCommit);
            requestsMade.clear();
        }
        return putImpl(flushMessage.getWriteMap(), flushMessage.getCurrentTimestamp());
    }

    public void setRebootTs(Timestamp<Long> rebootTs) {
        this.rebootTs = rebootTs;
    }
}
