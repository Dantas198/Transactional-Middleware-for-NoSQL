package transaction_manager;
import io.atomix.cluster.messaging.ManagedMessagingService;
import io.atomix.cluster.messaging.MessagingConfig;
import io.atomix.cluster.messaging.impl.NettyMessagingService;
import io.atomix.utils.net.Address;
import io.atomix.utils.serializer.Serializer;
import io.atomix.utils.serializer.SerializerBuilder;
import jraft.rpc.TransactionCommitRequest;
import jraft.rpc.TransactionStartRequest;
import jraft.rpc.UpdateTimestampRequest;
import npvs.NPVSServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import transaction_manager.utils.BitWriteSet;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TransactionManagerServer {
    private static final Logger LOG = LoggerFactory.getLogger(TransactionManagerServer.class);
    private ManagedMessagingService mms;
    private ExecutorService e;
    private Serializer s;
    private TransactionManager transactionManager;

    public TransactionManagerServer(int myPort, int npvsPort, String databaseURI, String databaseName, String databaseCollectionName) {
        e = Executors.newFixedThreadPool(1);
        s = new SerializerBuilder()
            .addType(BitWriteSet.class)
            .addType(TransactionCommitRequest.class)
            .addType(TransactionStartRequest.class)
            .addType(UpdateTimestampRequest.class)
            .addType(TransactionImpl.class)
            .build();

        mms = new NettyMessagingService(
            "transaction_manager",
            Address.from(myPort),
            new MessagingConfig());

        this.transactionManager = new TransactionManagerImpl(myPort, npvsPort, databaseURI, databaseName, databaseCollectionName);
    }

    void start() {
        mms.start();
        mms.registerHandler("start", (a,b) -> {
            TransactionStartRequest tsr = s.decode(b);
            LOG.debug("new start transaction request message with id: {}", tsr.getId());
            return s.encode(transactionManager.startTransaction());
        }, e);

        mms.registerHandler("commit", (a,b) -> {
            TransactionCommitRequest tcr = s.decode(b);
            LOG.debug("new commit request message with id: {}", tcr.getId());
            transactionManager.tryCommit(tcr.getTransactionContentMessage());
            return s.encode(0);
        } ,e);

        mms.registerHandler("get_server_context", (a,b) -> {
            LOG.debug("context request arrived");
            return s.encode(transactionManager.getServersContext());
        } ,e);
    }
}
