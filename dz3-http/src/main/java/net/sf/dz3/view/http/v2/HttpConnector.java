package net.sf.dz3.view.http.v2;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.homeclimatecontrol.jukebox.jmx.JmxAttribute;
import com.homeclimatecontrol.jukebox.jmx.JmxDescriptor;
import net.sf.dz3.device.model.impl.ThermostatModel;
import net.sf.dz3.instrumentation.Marker;
import net.sf.dz3.scheduler.Scheduler;
import net.sf.dz3.view.Connector;
import net.sf.dz3.view.ConnectorFactory;
import net.sf.dz3.view.http.common.BufferedExchanger;
import net.sf.dz3.view.http.common.QueueFeeder;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.util.EntityUtils;
import org.apache.logging.log4j.ThreadContext;

import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * HTTP client side interface.
 *
 * This object is supposed to be instantiated via Spring configuration file, with objects
 * that are supposed to be rendered and/or controlled being present in a set passed to the constructor.
 *
 * See {@code net.sf.dz3.view.swing.Console} for more information.
 *
 * {@code init-method="start"} attribute must be used in Spring bean definition, otherwise
 * the connector will not work.
 *
 * Unlike {@link net.sf.dz3.view.http.v1.HttpConnector previous implementation} which turned out to have
 * too much request data transfer and processing overhead, this connector caches data from all sources
 * and then submits them in bigger packets more rarely.
 *
 * @author Copyright &copy; <a href="mailto:vt@homeclimatecontrol.com">Vadim Tkachenko</a> 2001-2021
 */
public class HttpConnector extends Connector<JsonRenderer>{

    private final BlockingQueue<ZoneSnapshot> upstreamQueue = new LinkedBlockingQueue<>();
    private final URL serverContextRoot;
    private final BufferedExchanger<ZoneSnapshot> exchanger;
    private final Gson gson = new Gson();

    /**
     * Create an instance and fill it up with objects to render.
     *
     * @param initSet Objects to display.
     */
    public HttpConnector(URL serverContextRoot, String username, String password, Set<Object> initSet) {

        super(initSet);

        this.serverContextRoot = serverContextRoot;

        exchanger = new ZoneSnapshotExchanger(serverContextRoot, username, password, upstreamQueue);

        Scheduler scheduler = null;

        for (Object maybeScheduler : initSet) {

            if (maybeScheduler instanceof Scheduler) {
                logger.debug("Found scheduler: {}", maybeScheduler);
                scheduler = (Scheduler) maybeScheduler;
            }
        }

        logger.info("Using scheduler: {}", scheduler);

        if (scheduler == null) {
            logger.error("No scheduler found, no schedule deviations will be reported to remote controls");
        }

        register(ThermostatModel.class, new ThermostatFactory(scheduler));
    }

    /**
     * Create an instance and fill it up with objects to render,
     * using custom factory set.
     *
     * @param initSet Objects to display.
     * @param factorySet Set of {@link ConnectorFactory} objects to use for component creation.
     */
    public HttpConnector(URL serverBase, String username, String password, Set<Object> initSet, Set<ConnectorFactory<JsonRenderer>> factorySet) {

        super(initSet, factorySet);

        this.serverContextRoot = serverBase;

        exchanger = new ZoneSnapshotExchanger(serverContextRoot, username, password, upstreamQueue);
    }

    @Override
    protected void activate2() {
        exchanger.start();
    }

    @Override
    protected Map<String, Object> createContext() {
        return Map.of(QueueFeeder.QUEUE_KEY, upstreamQueue);
    }

    @Override
    protected void deactivate2() {
        exchanger.stop();
    }

    @Override
    public JmxDescriptor getJmxDescriptor() {

        var port = serverContextRoot.getPort();

        return new JmxDescriptor(
                "dz",
                getClass().getSimpleName(),
                serverContextRoot.getProtocol()
                + " " + serverContextRoot.getHost()
                + (port == -1 ? "" : " port " + port)
                + " " + serverContextRoot.getPath(),
                "HTTP Client v2");
    }

    @JmxAttribute(description = "Upstream queue size")
    public final int getQueueSize() {
        return upstreamQueue.size();
    }

    @JmxAttribute(description="Maximum age of the buffer before it gets sent, in milliseconds")
    public long getMaxBufferAgeMillis() {
        return exchanger.getMaxBufferAgeMillis();
    }

    /**
     * Set the maximum buffer age.
     *
     * @param maxBufferAgeMillis Maximum buffer age, in milliseconds.
     */
    public void setMaxBufferAgeMillis(long maxBufferAgeMillis) {
        exchanger.setMaxBufferAgeMillis(maxBufferAgeMillis);
    }

    private class ZoneSnapshotExchanger extends BufferedExchanger<ZoneSnapshot> { // NOSONAR Inheritance tree has been considered

        public ZoneSnapshotExchanger(URL serverContextRoot,
                String username, String password,
                BlockingQueue<ZoneSnapshot> upstreamQueue) {

            super(serverContextRoot, username, password, upstreamQueue);
        }

        @Override
        protected final void exchange(List<ZoneSnapshot> buffer) {

            ThreadContext.push("exchange");
            var m = new Marker("exchange");

            try {

                logger.debug("sending {} items: {}", buffer.size(), buffer);

                var encoded = gson.toJson(buffer);

                logger.debug("JSON ({} bytes): {}", encoded.length(), encoded);

                var targetUrl = serverContextRoot; // NOSONAR Readability
                var builder = new URIBuilder(targetUrl.toString());

                builder.addParameter("snapshot", encoded);
                var post = new HttpPost(builder.toString());

                try {

                    var rsp = httpClient.execute(post, context);
                    var rc = rsp.getStatusLine().getStatusCode();

                    if (rc != 200) {

                        logger.error("HTTP rc={}, text follows:", rc);
                        logger.error(EntityUtils.toString(rsp.getEntity())); // NOSONAR Not worth the effort

                        throw new IOException("Request to " + targetUrl + " failed with HTTP code " + rc);
                    }

                    processResponse(EntityUtils.toString(rsp.getEntity()));

                } finally {
                    post.releaseConnection();
                }

            } catch (Throwable t) { // NOSONAR Consequences have been considered

                // VT: NOTE: For now, this is not a recoverable problem, the snapshot is
                // irretrievably lost. Need to see if this matters at all

                logger.error("Buffer exchange failed", t);

            } finally {
                m.close();
                ThreadContext.pop();
            }
        }

        private void processResponse(String rsp) {

            ThreadContext.push("processResponse");

            try {

                logger.debug("JSON: {}", rsp);

                var setType = new TypeToken<Set<ZoneCommand>>(){}.getType();
                Set<ZoneCommand> buffer = gson.fromJson(rsp, setType);

                logger.debug("Commands received: {}", buffer.size());

                if (buffer.isEmpty()) {
                    return;
                }

                for (ZoneCommand zoneCommand : buffer) {
                    executeCommand(zoneCommand);
                }

            } finally {
                ThreadContext.pop();
            }
        }

        private void executeCommand(ZoneCommand command) {

            ThreadContext.push("executeCommand");

            try {

                logger.debug("Command: {}", command);

                for (Object target : getInitSet()) {

                    if (!(target instanceof ThermostatModel)) {
                        continue;
                    }

                    var ts = (ThermostatModel) target;

                    if (ts.getName().equals(command.name)) {

                        logger.debug("Matched: {}", command.name);

                        ts.setSetpoint(command.setpointTemperature);
                        ts.setOn(command.enabled);
                        ts.setOnHold(command.onHold);
                        ts.setVoting(command.voting);
                    }
                }

            } finally {
                ThreadContext.pop();
            }
        }
    }
}
