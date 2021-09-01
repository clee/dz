package net.sf.dz3r.view.swing.unit;

import net.sf.dz3r.signal.HvacDeviceStatus;
import net.sf.dz3r.view.swing.AbstractChart;

import java.time.Clock;
import java.time.Duration;
import java.time.temporal.ChronoUnit;

public class UnitChart extends AbstractChart<HvacDeviceStatus, Void> {
    /**
     * Defines how wide the unit chart is the moment it turns on.
     *
     * Runtimes less than 10 minutes are a sure sign of the unit being oversized,
     * and long runtimes are quite variable.
     *
     * Also, defines how much time is added to the chart when it is about to be filled up.
     */
    private static final Duration timeSpanIncrement = Duration.of(15, ChronoUnit.MINUTES);

    protected UnitChart(Clock clock) {
        super(clock, timeSpanIncrement.toMillis());
    }
}
