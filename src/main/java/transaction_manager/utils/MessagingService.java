package transaction_manager.utils;

import io.atomix.cluster.messaging.ManagedMessagingService;
import io.atomix.cluster.messaging.MessagingConfig;
import io.atomix.cluster.messaging.impl.NettyMessagingService;
import io.atomix.utils.net.Address;
import io.atomix.utils.serializer.Serializer;
import io.atomix.utils.serializer.SerializerBuilder;
import transaction_manager.messaging.ContentMessage;
import transaction_manager.messaging.ErrorMessage;
import transaction_manager.messaging.Message;

import java.util.concurrent.*;

//TODO REVER
public class MessagingService {

    private ManagedMessagingService mms;
    private Address server;
    private Serializer s;
    private CompletableFuture<Message> res;
    private ScheduledExecutorService ses;


    public MessagingService(int myPort, int serverPort){
        this.res = new CompletableFuture<>();
        this.server = Address.from(serverPort);
        Address myAddress = Address.from("localhost", myPort);
        this.ses = Executors.newScheduledThreadPool(1);

        this.mms = new NettyMessagingService(
                "server",
                myAddress,
                new MessagingConfig());
        this.mms.start();
        this.s = new SerializerBuilder()
                .withRegistrationRequired(false)
                .build();

        this.mms.registerHandler("reply", (a,b) -> {
            try{
                ContentMessage<?> repm = s.decode(b);
                res.complete(repm);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, ses);
    }

    private ScheduledFuture<?> scheduleTimeout(Message reqm){
        return ses.scheduleAtFixedRate(()->{
            System.out.println("timeout...sending new request");
            mms.sendAsync(server, "request", s.encode(reqm));
        }, 1, 4, TimeUnit.SECONDS);
    }

    //TODO retornar CompletableFuture<R>
    public<R extends Message> R sendAndReceive(Message request) throws ExecutionException, InterruptedException {
        return new Request<R>().sendAndReceive(request);
    }

    @SuppressWarnings("unchecked")
    private class Request<R extends Message> {

        public R sendAndReceive(Message request) throws ExecutionException, InterruptedException {
            res = new CompletableFuture<>();
            mms.sendAsync(server, "request", s.encode(request));
            return (R) res.thenApply(cm -> {
                System.out.println("Received message: "+ cm);
                if(cm instanceof ErrorMessage)
                    throw new CompletionException(((ErrorMessage) cm).getBody());
                return cm;
            }).get();
        };
    }

}
