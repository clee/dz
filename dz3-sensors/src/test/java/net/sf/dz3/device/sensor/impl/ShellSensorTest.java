package net.sf.dz3.device.sensor.impl;

import com.homeclimatecontrol.jukebox.datastream.signal.model.DataSample;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.ThreadContext;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * Shell sensor tests.
 * 
 * These tests, in their current form, are good enough to run on a Unix system, and, unfortunately,
 * not really portable across installations. Analyzing the source will give you an idea of what to do to
 * to make them work on your system - or feel free to make tests generic enough so they can be
 * reused without modification.
 * 
 * @author Copyright &copy; <a href="mailto:vt@homeclimatecontrol.com">Vadim Tkachenko</a> 2009-2018
 */
class ShellSensorTest {

    private final Logger logger = LogManager.getLogger(getClass());
    
    /**
     * @return {@code false} if OS is not within a supported list (just Linux for now).
     */
    private boolean isOsSupported() {
        
        Set<String> supported = new TreeSet<>();
        
        supported.add("Linux");
        
        String os = System.getProperty("os.name");
        
        if (supported.contains(os)) {
            return true;
        }
        
        logger.error("OS not supported: " + os);
        return false;
    }
    
    /**
     * Make sure the {@link ShellSensor} properly executes a shell command given.
     */
    @Test
    public void good() throws IOException {
        
        ThreadContext.push("good");
        
        try {
            
            if (!isOsSupported()) {
                return;
            }

            // VT: NOTE: The test string is Unix specific (shell builtin)
            ShellSensor ss = new ShellSensor("address", 1000, "echo 5.5");

            DataSample<Double> sample = ss.getSensorSignal();        
            logger.info("Sample: " + sample);
            
            assertThat(sample.isError()).isFalse();
            assertThat(sample.sample).isEqualTo(5.5);

        } finally {
            ThreadContext.pop();
        }
    }

    /**
     * Make sure the {@link ShellSensor} properly reports a problem with the shell command given.
     */
    @Test
    public void bad() throws IOException {
        
        ThreadContext.push("bad");
        
        try {

            if (!isOsSupported()) {
                return;
            }

            ShellSensor ss = new ShellSensor("address", 1000, "does.not.exist");

            DataSample<Double> sample = ss.getSensorSignal();        
            logger.info("Sample: " + sample);

            assertThat(sample.isError()).isTrue();

        } finally {
            ThreadContext.pop();
        }
    }

    /**
     * Not a test, really.
     * 
     * Fiddle with arguments and see that pipes are not handled correctly.
     */
    @Test
    public void pipeRaw() throws IOException, InterruptedException {
        
        ThreadContext.push("testPipeRaw");
        
        try {
            
            if (!isOsSupported()) {
                return;
            }

            int rc = -1;
            
            try {
                // VT: NOTE: The test string is Unix specific
                Process p = Runtime.getRuntime().exec(new String[] {"echo", "\"abcd\"|tr \"dcba\" \"0123\""});

                rc = p.waitFor();

            } catch (Throwable t) {

                logger.error("Unexpected exception", t);
                fail("Unexpected exception");

            }

            assertThat(rc).isZero();
            
        } finally {
            ThreadContext.pop();
        }
    }

    /**
     * Make sure that {@link ShellSensor} implementation correctly handles commands with pipes in them.  
     */
    @Test
    public void pipe() throws IOException {
        
        ThreadContext.push("testPipe");
        
        try {

            if (!isOsSupported()) {
                return;
            }

            // VT: NOTE: The test string is Unix specific
            ShellSensor ss = new ShellSensor("address", 1000, "echo \"abcd\"|tr \"dcba\" \"0123\"");

            DataSample<Double> sample = ss.getSensorSignal();        
            logger.info("Sample: " + sample);
            
            assertThat(sample.isError()).isFalse();
            assertThat(sample.sample).isEqualTo(3210.0);

        } catch (Throwable t) {
            
            logger.error("Unexpected exception", t);
            fail("Unexpected exception");
            
        } finally {
            ThreadContext.pop();
        }
    }
}
