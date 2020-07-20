package transaction_manager.raft.sofa_jraft;

import certifier.Timestamp;
import nosql.KeyValueDriver;
import npvs.NPVS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import transaction_manager.messaging.ServersContextMessage;
import transaction_manager.raft.RaftTransactionManager;
import transaction_manager.raft.sofa_jraft.callbacks.CompletableClosure;
import transaction_manager.raft.snapshot.ExtendedState;

import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

public class RaftTransactionManagerImpl extends RaftTransactionManager {
    private static final Logger LOG = LoggerFactory.getLogger(RaftTransactionManagerImpl.class);
    private final AtomicLong leaderTerm = new AtomicLong(-1);
    private final RequestHandler requestHandler;

    public RaftTransactionManagerImpl(long timestep, NPVS<Long> npvs, KeyValueDriver driver, ServersContextMessage scm, RequestHandler requestHandler) {
        super(timestep, npvs, driver, scm);
        this.requestHandler = requestHandler;
    }

    @Override
    public void updateState(Timestamp<Long> startTimestamp, Timestamp<Long> commitTimestamp, CompletableFuture<Timestamp<Long>> cf) {
        LOG.info("Updating state TC: " + commitTimestamp.toPrimitive());
        requestHandler.applyOperation(TransactionManagerOperation.createUpdateState(startTimestamp, commitTimestamp, LocalDateTime.now()),
                new CompletableClosure<Void>(cf));
    }

    @Override
    public boolean isLeader() {
        return this.leaderTerm.get() > 0;
    }


    public void setTerm(long term){
        this.leaderTerm.set(term);
    }

    public ExtendedState getExtendedState(){
        return new ExtendedState(getState(), getNonAckedFlushes());
    }
}