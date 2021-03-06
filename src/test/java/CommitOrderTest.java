import certifier.MonotonicTimestamp;
import org.junit.Test;
import transaction_manager.control.CommitOrderHandler;

import java.util.concurrent.CompletableFuture;

import static org.junit.Assert.assertEquals;

public class CommitOrderTest {
    CommitOrderHandler codh = new CommitOrderHandler();
    long ts = 0;


    public void updateState(long tc){
        ts += tc;
        System.out.println("state updated tc: " + tc);
    }

    public CompletableFuture<Void> deliverPipeline(long tc){
        return codh.deliver(new MonotonicTimestamp(tc))
                .thenAccept(x -> updateState(tc))
                .thenAccept(x -> codh.completeDeliveries());
    }


    @Test
    public void checkOrderDelivery(){
        CompletableFuture<?>[] futures = new CompletableFuture<?>[5];

        futures[0] = deliverPipeline(1);
        futures[1] = deliverPipeline(5);
        futures[2] = deliverPipeline(4);
        futures[3] = deliverPipeline(2);
        futures[4] = deliverPipeline(3);

        CompletableFuture.allOf(futures).thenAccept(x -> assertEquals("Should be equals", 5, ts));
    }
}
