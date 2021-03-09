package net.sf.dz3.view.http.v2;

import static org.junit.Assert.assertEquals;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.ThreadContext;
import org.junit.Test;

import net.sf.dz3.device.model.ThermostatSignal;
import net.sf.dz3.device.model.impl.ThermostatModel;
import net.sf.dz3.device.sensor.impl.NullSensor;
import net.sf.dz3.view.http.common.QueueFeeder;
import net.sf.jukebox.datastream.signal.model.DataSample;

public class JsonRendererTest {

    private final Logger logger = LogManager.getLogger(getClass());

    @Test
    public void testRender0() {

        ThreadContext.push("testRender");

        try {

            ThermostatModel t = new ThermostatModel("json-thermostat", new NullSensor("null-address", 100), 20, 1, 0.000002,0, 2);
            Map<String, Object> context = new HashMap<String, Object>();
            BlockingQueue<ZoneSnapshot> queue = new LinkedBlockingQueue<ZoneSnapshot>();

            context.put(QueueFeeder.QUEUE_KEY, queue);

            ThermostatRenderer tr = new ThermostatRenderer(t, context, null);

            DataSample<Double> demand = new DataSample<Double>("source-d", "signature-d", new Double(0), null);
            ThermostatSignal ts = new ThermostatSignal(true, true, true, true, demand);

            tr.consume(new DataSample<ThermostatSignal>("source-ts", "signature-ts", ts, null));

            assertEquals("wrong queue size", 1, queue.size());

            logger.debug("queue head: " + queue.peek());

            assertEquals("wrong toString()", "json-thermostat: Cooling, CALLING, signal=0.0, current=0.0, setpoint=20.0, on hold", queue.peek().toString());

        } finally {
            ThreadContext.pop();
        }
    }
}
