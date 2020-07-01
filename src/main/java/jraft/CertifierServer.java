package jraft;

import java.io.File;
import java.io.IOException;

import certifier.Timestamp;
import jraft.rpc.*;
import org.apache.commons.io.FileUtils;

import com.alipay.sofa.jraft.Node;
import com.alipay.sofa.jraft.RaftGroupService;
import com.alipay.sofa.jraft.conf.Configuration;
import com.alipay.sofa.jraft.entity.PeerId;
import com.alipay.sofa.jraft.option.NodeOptions;
import com.alipay.sofa.jraft.rpc.RaftRpcServerFactory;
import com.alipay.sofa.jraft.rpc.RpcServer;

public class CertifierServer {
    private RaftGroupService raftGroupService;
    private Node node;
    private StateMachine fsm;

    public CertifierServer(final String dataPath, final String groupId, final PeerId serverId,
                           final NodeOptions nodeOptions) throws IOException {
        // Initialize the path.
        FileUtils.forceMkdir(new File(dataPath));

        // Here Raft RPC and business RPC share the same RPC server. They can use different RPC servers, too.
        final RpcServer rpcServer = RaftRpcServerFactory.createRaftRpcServer(serverId.getEndpoint());
        // Register the business processor.

        CertifierService certifierService = new CertifierServiceImpl(this);
        rpcServer.registerProcessor(new RequestProcessor<TransactionCommitRequest, Timestamp>(TransactionCommitRequest.class,
                (req , closure) -> certifierService.commit(req.getBws(), new Timestamp(req.getTimestamp()), closure)));

        rpcServer.registerProcessor(new RequestProcessor<TransactionStartRequest, Timestamp>(TransactionStartRequest.class,
                (req , closure) -> certifierService.getTimestamp(req.isReadOnlySafe(), closure)));

        // Initialize the state machine.
        this.fsm = new StateMachine();
        // Set the state machine to the startup parameters.
        nodeOptions.setFsm(this.fsm);
        // Set the storage path.
        // Required. Specify the log.
        nodeOptions.setLogUri(dataPath + File.separator + "log");
        // Required. Specify the metadata.
        nodeOptions.setRaftMetaUri(dataPath + File.separator + "raft_meta");
        // Recommended. Specify the snapshot.
        nodeOptions.setSnapshotUri(dataPath + File.separator + "snapshot");
        // Initialize the Raft group service framework.
        this.raftGroupService = new RaftGroupService(groupId, serverId, nodeOptions, rpcServer);
        // Startup
        this.node = this.raftGroupService.start();
    }

    public StateMachine getFsm() {
        return this.fsm;
    }

    public Node getNode() {
        return this.node;
    }

    public RaftGroupService RaftGroupService() {
        return this.raftGroupService;
    }

    /**
     * Redirect request to new leader
     */
    public ValueResponse redirect() {
        final ValueResponse response = new ValueResponse();
        response.setSuccess(false);
        if (this.node != null) {
            final PeerId leader = this.node.getLeaderId();
            if (leader != null) {
                response.setRedirect(leader.toString());
            }
        }
        return response;
    }

    public static void main(final String[] args) throws IOException {
        if (args.length != 4) {
            System.out
                    .println("Useage : java com.alipay.sofa.jraft.example.counter.CounterServer {dataPath} {groupId} {serverId} {initConf}");
            System.out
                    .println("Example: java com.alipay.sofa.jraft.example.counter.CounterServer /tmp/server1 counter 127.0.0.1:8081 127.0.0.1:8081,127.0.0.1:8082,127.0.0.1:8083");
            System.exit(1);
        }
        final String dataPath = args[0];
        final String groupId = args[1];
        final String serverIdStr = args[2];
        final String initConfStr = args[3];

        final NodeOptions nodeOptions = new NodeOptions();
        // For testing, adjust the snapshot interval and other parameters
        // Set the election timeout to 1 second
        nodeOptions.setElectionTimeoutMs(1000);
        // Close the CLI service.
        nodeOptions.setDisableCli(false);
        // Snapshot every 30 seconds
        nodeOptions.setSnapshotIntervalSecs(300);
        // Parsing parameters
        final PeerId serverId = new PeerId();
        if (!serverId.parse(serverIdStr)) {
            throw new IllegalArgumentException("Fail to parse serverId:" + serverIdStr);
        }
        final Configuration initConf = new Configuration();
        if (!initConf.parse(initConfStr)) {
            throw new IllegalArgumentException("Fail to parse initConf:" + initConfStr);
        }
        // Set up the initial cluster configuration
        nodeOptions.setInitialConf(initConf);

        // start up
        final CertifierServer certifierServer = new CertifierServer(dataPath, groupId, serverId, nodeOptions);
        System.out.println("Started counter server at port:"
                + certifierServer.getNode().getNodeId().getPeerId().getPort());
    }
}