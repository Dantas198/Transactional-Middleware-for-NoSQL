package transaction_manager.raft;

import certifier.Timestamp;
import com.alipay.remoting.exception.CodecException;
import com.alipay.remoting.serialization.SerializerManager;
import com.alipay.sofa.jraft.Closure;
import com.alipay.sofa.jraft.Iterator;
import com.alipay.sofa.jraft.Status;
import com.alipay.sofa.jraft.core.StateMachineAdapter;
import com.alipay.sofa.jraft.error.RaftError;
import com.alipay.sofa.jraft.error.RaftException;
import com.alipay.sofa.jraft.storage.snapshot.SnapshotReader;
import com.alipay.sofa.jraft.storage.snapshot.SnapshotWriter;
import com.alipay.sofa.jraft.util.Utils;
import nosql.KeyValueDriver;
import npvs.NPVS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import transaction_manager.messaging.ServersContextMessage;
import transaction_manager.messaging.TransactionContentMessage;
import transaction_manager.raft.callbacks.TransactionClosure;
import transaction_manager.raft.snapshot.ExtendedState;
import transaction_manager.raft.snapshot.StateSnapshot;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicLong;

import static transaction_manager.raft.TransactionManagerOperation.*;

public class StateMachine extends StateMachineAdapter {
    private static final Logger LOG = LoggerFactory.getLogger(StateMachine.class);

    private final RaftTransactionManagerImpl transactionManager;
    /**
     * Leader term
     */
    private final AtomicLong leaderTerm = new AtomicLong(-1);


    public StateMachine(long timestep, NPVS<Long> npvs, KeyValueDriver driver, ServersContextMessage scm){
        super();
        this.transactionManager = new RaftTransactionManagerImpl(timestep, npvs, driver, scm);
    }

    public boolean isLeader() {
        return transactionManager.isLeader();
    }

    /**
     * Returns current timestamp.
     */
    //TODO arranjar
    public long getTimestamp() {
        return -1;
    }
    //TODO
    public Timestamp<Long> getCurrentTs(){
        return null;
    }

    public void onApply(final Iterator iter) {
        while (iter.hasNext()) {
            TransactionManagerOperation transactionManagerOperation = null;

            TransactionClosure closure = null;
            if (iter.done() != null) {
                // This task is applied by this node, get value from closure to avoid additional parsing.
                closure = (TransactionClosure) iter.done();
                transactionManagerOperation = closure.getTransactionManagerOperation();
            } else {
                // Have to parse FetchAddRequest from this user log.
                final ByteBuffer data = iter.getData();
                try {
                    transactionManagerOperation = SerializerManager.getSerializer(SerializerManager.Hessian2).deserialize(
                            data.array(), TransactionManagerOperation.class.getName());
                } catch (final CodecException e) {
                    LOG.error("Fail to decode TransactionManagerOperation", e);
                }
            }
            applyOperation(transactionManagerOperation, closure);
            iter.next();
        }
    }

    private void applyOperation(TransactionManagerOperation transactionManagerOperation, TransactionClosure closure){
        if (transactionManagerOperation != null) {
            switch (transactionManagerOperation.getOp()) {
                case START_TXN:
                    transactionManager.startTransaction().thenAccept(res ->{
                        closure.success(res);
                        closure.run(Status.OK());
                    });
                    break;
                case COMMIT:
                    final TransactionContentMessage tcm = transactionManagerOperation.getTcm();
                    transactionManager.tryCommit(tcm).thenAccept(res -> {
                        closure.success(res);
                        closure.run(Status.OK());
                    });
                    break;
                case UPDATE_STATE:
                    final Timestamp<Long> commitTimestamp = transactionManagerOperation.getTimestamp();
                    transactionManager.removeFlush(commitTimestamp);
                    LOG.info("removing TC={} from nonAckedFlushes", commitTimestamp);
            }
        }
    }

    @Override
    public void onSnapshotSave(final SnapshotWriter writer, final Closure done) {
        //TODO colocar locks?
        Utils.runInThread(() -> {
            final StateSnapshot snapshot = new StateSnapshot(writer.getPath() + File.separator + "data");
            if (snapshot.save(this.transactionManager.getExtendedState())) {
                if (writer.addFile("data")) {
                    done.run(Status.OK());
                } else {
                    done.run(new Status(RaftError.EIO, "Fail to add file to writer"));
                }
            } else {
                done.run(new Status(RaftError.EIO, "Fail to save counter snapshot %s", snapshot.getPath()));
            }
        });
    }

    @Override
    public void onError(final RaftException e) {
        LOG.error("Raft error: {}", e, e);
    }

    @Override
    public boolean onSnapshotLoad(final SnapshotReader reader) {
        if (isLeader()) {
            LOG.warn("Leader is not supposed to load snapshot");
            return false;
        }
        if (reader.getFileMeta("data") == null) {
            LOG.error("Fail to find data file in {}", reader.getPath());
            return false;
        }
        final StateSnapshot snapshot = new StateSnapshot(reader.getPath() + File.separator + "data");
        try {
            ExtendedState es = snapshot.load();
            this.transactionManager.setState(es);
            return true;
        } catch (final IOException e) {
            LOG.error("Fail to load snapshot from {}", snapshot.getPath());
            return false;
        }

    }

    @Override
    public void onLeaderStart(final long term) {
        //TODO flush all requests
        this.leaderTerm.set(term);
        super.onLeaderStart(term);
    }

    @Override
    public void onLeaderStop(final Status status) {
        this.leaderTerm.set(-1);
        super.onLeaderStop(status);
    }

}