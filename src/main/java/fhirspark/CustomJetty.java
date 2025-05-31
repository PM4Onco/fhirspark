package fhirspark;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.util.VirtualThreads;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.eclipse.jetty.util.thread.ThreadPool;
import spark.embeddedserver.VirtualThreadAware;
import spark.embeddedserver.jetty.JettyServerFactory;

class CustomJetty extends VirtualThreadAware.Base implements JettyServerFactory {
    CustomJetty() {
    }

    public Server create(int maxThreads, int minThreads, int threadTimeoutMillis) {
        QueuedThreadPool queuedThreadPool;
        if (maxThreads > 0) {
            int max = maxThreads;
            int min = minThreads > 0 ? minThreads : 8;
            int idleTimeout = threadTimeoutMillis > 0 ? threadTimeoutMillis : '\uea60';
            queuedThreadPool = new QueuedThreadPool(max, min, idleTimeout);
        } else {
            queuedThreadPool = new QueuedThreadPool();
        }

        return this.create(queuedThreadPool);
    }

    public Server create(ThreadPool threadPool) {
        if (threadPool == null) {
            threadPool = new QueuedThreadPool();
        }

        if (this.useVThread && VirtualThreads.areSupported() && threadPool instanceof QueuedThreadPool) {
            ((QueuedThreadPool)threadPool).setVirtualThreadsExecutor(VirtualThreads.getDefaultVirtualThreadsExecutor());
        }

        return new Server(threadPool);
    }
}
