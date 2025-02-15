package net.sf.dz3.util.counter;

import java.io.IOException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.ThreadContext;

import net.sf.dz3.util.digest.MessageDigestCache;
import com.homeclimatecontrol.jukebox.datastream.logger.impl.DataBroadcaster;
import com.homeclimatecontrol.jukebox.datastream.signal.model.DataSample;
import com.homeclimatecontrol.jukebox.datastream.signal.model.DataSink;
import com.homeclimatecontrol.jukebox.datastream.signal.model.DataSource;

/**
 * Base class for persistent implementations.
 *  
 * @author Copyright &copy; <a href="mailto:vt@homeclimatecontrol.com">Vadim Tkachenko</a> 2001-2020
 */
public abstract class AbstractUsageCounter implements ResourceUsageCounter {
    
    protected final Logger logger;
    
    private final DataBroadcaster<Double> dataBroadcaster = new DataBroadcaster<>();
    
    /**
     * Human readable name for the user interface.
     */
    private final String name;
    
    /**
     * Signature for the data logger.
     */
    private final String signature;
    
    /**
     * Strategy determining what exactly is being counted.
     */
    private final CounterStrategy counter;

    /**
     * The threshold.
     */
    private long threshold;
    
    /**
     * Current usage.
     */
    private long current;
    
    /**
     * Storage keys.
     */
    private final Object[] storageKeys;

    /**
     * Create an instance with the default (time based usage) counter strategy.
     * 
     * @param name Human readable name for the user interface.
     * @param target What to count.
     * @param storageKeys How to store the counter data.
     * 
     * @throws IOException if things go sour.
     */
    public AbstractUsageCounter(String name, DataSource<Double> target, Object[] storageKeys) throws IOException {
        
        this(name, new TimeBasedUsage(System.currentTimeMillis()), target, storageKeys);
    }
    
    /**
     * Create an instance.
     * 
     * @param name Human readable name for the user interface.
     * @param counter Counter to use.
     * @param target What to count.
     * @param storageKeys How to store the counter data.
     * 
     * @throws IOException if things go sour.
     */
    public AbstractUsageCounter(String name, CounterStrategy counter, DataSource<Double> target, Object []storageKeys) throws IOException {
        
        // Kludge to allow to use logger in subclass methods called from the constructor
        logger = LogManager.getLogger(getClass());
        
        if (name == null) {
            throw new IllegalArgumentException("name can't be null");
        }
        
        if (counter == null) {
            throw new IllegalArgumentException("counter can't be null");
        }

        this.name = name;
        this.signature = MessageDigestCache.getMD5(name).substring(0, 19);
        this.counter = counter;
        this.storageKeys = storageKeys;
        
        if (storageKeys != null) {

            logger.debug("storageKeys: " + storageKeys.length + " items");
        
            for (int offset = 0; offset < storageKeys.length; offset++) {
                logger.debug("storageKeys[" + offset + "]: " + storageKeys[offset]);
            }
        
        } else {
            
            logger.info("no storageKeys given");
        }

        CounterState state = load();
        
        this.threshold = state.threshold;
        this.current = state.current;
        
        if (target == null) {
            throw new IllegalArgumentException("null target doesn't make sense");
        }
        
        target.addConsumer(this);
    }

    /**
     * @return Storage keys.
     * @see #storageKeys
     */
    protected final Object[] getStorageKeys() {
        
        return storageKeys;
    }

    @Override
    public final String getName() {
        
        return name;
    }

    @Override
    public long getThreshold() {
        
        return threshold;
    }

    @Override
    public long getUsageAbsolute() {
        
        return current;
    }

    @Override
    public double getUsageRelative() {
        
        return threshold == 0 ? 0 : (double)current / (double)threshold;
    }

    @Override
    public void setThreshold(long units) {
        
        if (units <= 0) {
            throw new IllegalArgumentException("threshold must be positive (" + units + " given)");
        }
        
        threshold = units;
    }

    @Override
    public final synchronized void reset() {

        ThreadContext.push("reset");
        
        try {

            current = 0;

            doReset();
            save();
                
        } catch (IOException ex) {

            logger.error("Failed to save state for '" + getName() + "'", ex);
            
        }finally {
            ThreadContext.pop();
        }
    }

    @Override
    public void consume(DataSample<Double> signal) {
        
        ThreadContext.push("consume@" + Integer.toHexString(hashCode()));
        
        try {
            
            logger.debug("Signal: " + signal);
        
            if (signal == null) {
                throw new IllegalArgumentException("signal can't be null");
            }

            if (signal.isError()) {

                // Interpret this as "not running"
                current += counter.consume(signal.timestamp, 0);
                return;
            }

            current += counter.consume(signal.timestamp, signal.sample);

            save();

            dataBroadcaster.broadcast(new DataSample<Double>(name, signature, Double.valueOf(current), null));

            alert(threshold, current);
        
        } catch (IOException ex) {
            
            logger.error("Failed to save state for '" + getName() + "'", ex);
            
        } finally {
            ThreadContext.pop();
        }
    }

    @Override
    public void addConsumer(DataSink<Double> consumer) {

        dataBroadcaster.addConsumer(consumer);
    }

    @Override
    public void removeConsumer(DataSink<Double> consumer) {

        dataBroadcaster.removeConsumer(consumer);
    }

    /**
     * Object to represent the counter state.
     */
    protected static class CounterState {

        /**
         * Currently set threshold.
         */
        public final long threshold;
        
        /**
         * Currently recorded usage.
         */
        public final long current;
        
        public CounterState(long threshold, long current) {
            
            this.threshold = threshold;
            this.current = current;
        }
        
        @Override
        public String toString() {
            
            StringBuilder sb = new StringBuilder();
            
            sb.append("(threshold=").append(threshold);
            sb.append(", current=").append(current).append(")");
            
            return sb.toString();
        }
    }

    /**
     * Load the previously stored state.
     * 
     * @throws IOException if things go sour.
     */
    protected abstract CounterState load() throws IOException;
    
    /**
     * Store the current state.
     * 
     * @throws IOException if things go sour.
     */
    protected abstract void save() throws IOException;
    
    /**
     * Alert the user about usage.
     * 
     * @param threshold Usage threshold.
     * @param current Current usage.
     */
    protected abstract void alert(long threshold, long current);
    
    /**
     * Perform the reset in concrete implementations.
     * 
     * One good thing to do here would be to reset alerts, if any.
     * 
     * @throws IOException if things go sour.
     */
    protected abstract void doReset() throws IOException;
}
