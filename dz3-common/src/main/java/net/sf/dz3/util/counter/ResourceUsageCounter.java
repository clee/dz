package net.sf.dz3.util.counter;

import net.sf.jukebox.datastream.signal.model.DataSink;
import net.sf.jukebox.datastream.signal.model.DataSource;
import net.sf.jukebox.jmx.JmxAttribute;
import net.sf.jukebox.jmx.JmxAware;

/**
 * Resource usage counter.
 * 
 * Allows to track resource usage and issue notifications.
 * 
 * Note that there is no abstraction for directly influencing the consumed resource -
 * this is intended to be provided via {@link DataSink} framework, to enforce loose coupling.
 * 
 * @author Copyright &copy; <a href="mailto:vt@freehold.crocodile.org">Vadim Tkachenko</a> 2001-2010
 */
public interface ResourceUsageCounter extends DataSink<Double>, DataSource<Double>, JmxAware {
    
    /**
     * @return Human readable name for the user interface.
     */
    @JmxAttribute(description="Device")
    String getName();

    /**
     * Get the currently set {@link #setThreshold(long) usage threshold}.
     * @return Usage threshold.
     */
    @JmxAttribute(description = "Usage Threshold")
    long getThreshold();
    
    /**
     * Notification will be issued after this many units consumed.
     * 
     * @param units Value to serve as a notification threshold.
     */
    void setThreshold(long units);
    
    /**
     * Get usage relative to the {@link #getThreshold() threshold}.
     * 
     * @return A non-negative number representing relative usage. Value of 1 or above
     * means the {@link #getThreshold() threshold} had been reached. 
     */
    @JmxAttribute(description = "Relative Usage")
    double getUsageRelative();
    
    /**
     * Get the usage in units consumed.
     * 
     * @return A value in the same units as the {@link #getThreshold() threshold} set.
     */
    @JmxAttribute(description = "Absolute Usage")
    long getUsageAbsolute();
    
    /**
     * Reset the usage counter.
     * 
     * Sets {@link #getUsageAbsolute()} and {@link #getUsageRelative()} to 0.
     */
    void reset();
}
