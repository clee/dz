package net.sf.dz3.view.swing.thermostat;

import static org.junit.Assert.assertTrue;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Clock;
import java.time.Duration;
import java.util.Date;
import java.util.List;
import java.util.Map.Entry;
import java.util.TreeMap;

import javax.imageio.ImageIO;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.ThreadContext;
import org.junit.BeforeClass;
import org.junit.Test;

import net.sf.dz3.instrumentation.Marker;
import net.sf.jukebox.datastream.signal.model.DataSample;
import net.sf.jukebox.util.Interval;

public class ChartBenchmark {

    private final static Logger logger = LogManager.getLogger(ChartBenchmark.class);

    private static TreeMap<Long, Double> series = new TreeMap<>();
    private static Clock testClock;

    @BeforeClass
    public static void loadSeries() throws IOException, URISyntaxException {

        Marker m = new Marker("load");
        try {

            Path source = Paths.get(ClassLoader.getSystemResource("./chart/series1").toURI());
            logger.info("reading {}", source);

            List<String> raw = Files.readAllLines(source);
            logger.info("{} entries read", raw.size());

            raw.stream()
            .map(line -> line.split(" "))
            .forEach(kv -> {
                series.put(Long.parseLong(kv[0]), Double.parseDouble(kv[1]));
            });

            long last = series.lastKey();
            long span = last - series.firstKey();

            logger.info("series time span is {}", Interval.toTimeInterval(span));

            long now = Clock.systemUTC().instant().toEpochMilli();
            long offset = now - last;

            testClock = Clock.offset(Clock.systemUTC(), Duration.ofMillis(-offset));

            logger.info("last sample is at {}", new Date(last));
            logger.info("test clock time is {}", testClock.instant());

        } finally {
            m.close();
        }
    }

    /**
     * Chart length in milliseconds.
     *
     * Hardcoded to 3 hours (current Swing and Android chart length).
     */
    private static long chartLengthMillis = 1000L * 60 * 60 * 3;

    @SuppressWarnings("deprecation")
    @Test
    public void benchmark2009() throws IOException {

        benchmark("2009", new Chart2009(testClock, chartLengthMillis));
        assertTrue(true);
    }

    @Test
    public void benchmark2016() throws IOException {

        benchmark("2016", new Chart2016(testClock, chartLengthMillis));
        assertTrue(true);
    }

    private void benchmark(String marker, AbstractChart target) throws IOException {
        ThreadContext.push(marker);
        Marker m = new Marker(marker);

        try {

            // WVGA854, see Console#screenSizes
            Dimension size = new Dimension(480, 854);
            target.setSize(size);

            BufferedImage image = new BufferedImage(size.width, size.height, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = image.createGraphics();
            target.printAll(g);

            double setpoint = 28.75;

            for (Entry<Long, Double> kv: series.entrySet()) {

                TintedValueAndSetpoint payload = new TintedValueAndSetpoint(kv.getValue(), 0.5, false, setpoint);
                DataSample<TintedValueAndSetpoint> sample = new DataSample<TintedValueAndSetpoint>(
                        kv.getKey(),
                        "source", "signature", payload, null);

                target.consume(sample);
            }

            target.printAll(g);
            g.dispose();

            ImageIO.write(image, "png", new File("/tmp/panel-" + marker + ".png"));

        } finally {
            m.close();
            ThreadContext.pop();
        }
    }
}
