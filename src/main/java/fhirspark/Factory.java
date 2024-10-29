package fhirspark;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.util.thread.ThreadPool;
import spark.embeddedserver.jetty.JettyServerFactory;

public class Factory implements JettyServerFactory {
    @Override
    public Server create(int i, int i1, int i2) {
        Server server = new CustomJetty().create(i, i1, i2);
        server.setAttribute("org.eclipse.jetty.server.Request.maxFormContentSize", 1024 * 1024 * 10);
        return server;
    }

    @Override
    public Server create(ThreadPool threadPool) {
        return null;
    }
}
