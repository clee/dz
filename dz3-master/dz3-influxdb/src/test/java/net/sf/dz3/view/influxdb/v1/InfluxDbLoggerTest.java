package net.sf.dz3.view.influxdb.v1;

import static org.junit.Assert.fail;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import org.apache.logging.log4j.ThreadContext;
import org.junit.Test;

import net.sf.jukebox.datastream.logger.impl.DataBroadcaster;
import net.sf.jukebox.datastream.signal.model.DataSample;
import net.sf.jukebox.datastream.signal.model.DataSource;

public class InfluxDbLoggerTest {

    @Test
    public void testLogger() throws InterruptedException {

        ThreadContext.push("testLogger");

        try {
            
            String instance = "dz3.test";

            Set<DataSource<Integer>> producers = new HashSet<>();
            DataBroadcaster<Integer> b = new DataBroadcaster<>();

            producers.add(b);

            InfluxDbLogger<Integer> l = new InfluxDbLogger<>(producers, instance, "http://127.0.0.1:8086", null, null);
            
            try {

                if (!l.start().waitFor()) {
                    fail("InfluxDbLogger failed to start, see the logs for the cause");
                }
                
                b.broadcast(new DataSample<Integer>("test-source", "test-signature", new Random().nextInt(), null));
            } finally {
                l.stop();
            }

        } finally {
            ThreadContext.pop();
        }
    }
}
