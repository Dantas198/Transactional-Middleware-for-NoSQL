package transaction_manager.messaging;

public class ErrorMessage extends ContentMessage<Throwable> {
    public ErrorMessage(Throwable body) {
        super(body);
    }
}
