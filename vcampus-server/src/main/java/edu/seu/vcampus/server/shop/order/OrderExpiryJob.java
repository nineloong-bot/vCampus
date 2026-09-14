package edu.seu.vcampus.server.shop.order;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Proactive reservation recovery on startup and once every minute. */
public final class OrderExpiryJob implements AutoCloseable {
    private final ScheduledExecutorService executor;
    /** Starts recovery immediately; subsequent failures are logged and retried next minute. */
    public OrderExpiryJob(OrderService service) {
        service.expirePending();
        executor=Executors.newSingleThreadScheduledExecutor(task->{
            Thread thread=new Thread(task,"shop-order-expiry");thread.setDaemon(true);return thread;
        });
        executor.scheduleWithFixedDelay(()->{
            try {service.expirePending();}
            catch(RuntimeException error){System.getLogger(OrderExpiryJob.class.getName())
                    .log(System.Logger.Level.WARNING,"Order expiry will retry on the next scheduled run");}
        },60,60,TimeUnit.SECONDS);
    }
    /** Stops the background task during server shutdown. */
    @Override public void close(){executor.shutdownNow();}
}
