package hbnet;

/** Client adapters close on backlog overflow; authoritative transactions are never dropped silently. */
public final class BoundedEventQueue<E> extends java.util.concurrent.ConcurrentLinkedQueue<E> {
    private final int limit;
    private final Runnable overflow;
    public BoundedEventQueue(int limit,Runnable overflow){if(limit<1||limit>8192)throw new IllegalArgumentException("Invalid queue bound");this.limit=limit;this.overflow=java.util.Objects.requireNonNull(overflow);}
    @Override public synchronized boolean offer(E value){if(size()>=limit){overflow.run();return false;}return super.offer(value);}
    @Override public boolean add(E value){return offer(value);}
}
