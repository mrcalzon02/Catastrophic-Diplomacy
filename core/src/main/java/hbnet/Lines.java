package hbnet;

import java.io.*;

/** Bounded legacy game adapter framing; no readLine allocation before length checks. */
public final class Lines {
    private Lines(){}
    public static String read(Reader reader,int limit)throws IOException{
        StringBuilder s=new StringBuilder();for(;;){int c=reader.read();if(c<0){if(s.length()==0)return null;throw Hbnet.failure();}if(c=='\n')return s.toString();if(c=='\r'){if(reader.read()=='\n')return s.toString();throw Hbnet.failure();}if(c<32||c>126||s.length()>=limit)throw Hbnet.failure();s.append((char)c);}
    }
    /** FIFO overflow disconnects; never silently discards authoritative changes. */
    public static final class Outbox implements AutoCloseable {
        private final java.util.concurrent.ArrayBlockingQueue<String> queue=new java.util.concurrent.ArrayBlockingQueue<>(256);
        private final java.net.Socket socket;
        private final Thread worker;
        private volatile boolean closed;
        public Outbox(PrintWriter out,java.net.Socket socket){this.socket=socket;worker=new Thread(()->{try{while(!closed){String value=queue.take();out.println(value);if(out.checkError())break;}}catch(InterruptedException e){Thread.currentThread().interrupt();}finally{close();}},"hbnet-game-output");worker.setDaemon(true);worker.start();}
        public void send(String value){if(closed)return;if(value.length()>16384||!queue.offer(value))close();}
        public void close(){if(closed)return;closed=true;worker.interrupt();try{socket.close();}catch(IOException ignored){}}
    }
}
