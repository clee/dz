package net.sf.dz3.scheduler.gcal;

import java.text.NumberFormat;
import java.text.ParseException;
import java.util.NoSuchElementException;
import java.util.StringTokenizer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.ThreadContext;

import net.sf.dz3.device.model.ZoneStatus;
import net.sf.dz3.device.model.impl.ZoneStatusImpl;

/**
 * Utility class to parse the {@link ZoneStatus} out of a period free form text.
 * 
 * Eventually, this utility can become very smart in parsing badly formed free form input. However,
 * this will also cause it to bloat quite a bit and become too heavy. It is possible to offload
 * parsing on an online component, but whether this is necessary is yet to be seen. 
 *  
 * @author Copyright &copy; <a href="mailto:vt@freehold.crocodile.org">Vadim Tkachenko</a> 2001-2018
 */
public class StatusParser {
    
    private final Logger logger = LogManager.getLogger(getClass());
    private final NumberFormat numberFormat = NumberFormat.getInstance();

    public ZoneStatus parse(String arguments) {
        
        ThreadContext.push("parse");
        
        arguments = arguments.toLowerCase();
        
        Double setpoint = null;
        Boolean enabled = null;
        Boolean voting = null;
        Integer dumpPriority = null;
        
        try {

            for (StringTokenizer st = new StringTokenizer(arguments.trim(), ",;"); st.hasMoreTokens(); ) {

                String token = st.nextToken().trim();

                logger.info("Token: '" + token + "'");
                
                if ("on".equals(token) || "enabled".equals(token)) {
                    
                    enabled = true;
                    continue;
                }
                
                if ("off".equals(token) || "disabled".equals(token)) {
                    
                    enabled = false;
                    continue;
                }
                
                if ("voting".equals(token)) {
                    
                    voting = true;
                    continue;
                }
                
                if ("non-voting".equals(token) || "not voting".equals(token)) {
                    
                    voting = false;
                    continue;
                }
                
                if (token.startsWith("setpoint") || token.startsWith("temperature")) {
                    
                    StringTokenizer st2 = new StringTokenizer(token, " =:");
                    
                    // Result is not needed
                    st2.nextToken();

                    try {

                        setpoint = parseSetpoint(st2.nextToken());

                    } catch (NoSuchElementException ex) {

                        // This indicates a problem with setpoint syntax
                        throw new IllegalArgumentException("can't parse '" + arguments + "' (malformed setpoint '" + token + "')", ex);
                    }
                }
                
                if (token.startsWith("dump")) {
                    
                    dumpPriority = parseDumpPriority(token);
                }
            }
            
            // Fill in defaults
            
            if (enabled == null) {
                
                enabled = true;
            }
            
            if (voting == null) {
                
                voting = true;
            }
            
            if (dumpPriority == null) {
                
                dumpPriority = 0;
            }
            
            // There is no default for setpoint
            
            if (setpoint == null) {
                
                throw new IllegalArgumentException("Could not parse setpoint out of '" + arguments + "'");
            }

            return new ZoneStatusImpl(setpoint, dumpPriority, enabled, voting);
        
        } finally {
            ThreadContext.pop();
        }
    }

    private Double parseSetpoint(String setpoint) {
        
        try {
        
            Number n = numberFormat.parse(setpoint);

            Double value = n.doubleValue();

            // Default temperature unit is Celsius, you'll have to explicitly specify
            // Fahrenheit if you want it

            if (setpoint.indexOf("f") >= 0) {

                // Need to convert to Celsius
                logger.debug("Temperature unit is Fahhrenheit");
                value = ((value - 32) * 5) / 9;
            }

            return value;

        } catch (ParseException ex) {

            throw new IllegalArgumentException("Could not parse setpoint out of '" + setpoint + "'");
        }
    }

    private Integer parseDumpPriority(String dumpPriority) {
        
        // ["dumpPriority" || "dump priority"] [= : " "] <value>
        
        for (StringTokenizer st = new StringTokenizer(dumpPriority, " =:"); st.hasMoreTokens(); ) {

            String token = st.nextToken();
            
            try {

                return numberFormat.parse(token).intValue();

            } catch (ParseException ex) {

                // Well, this didn't work, let's go to next
                logger.debug("Couldn't parse '" + token + "' as a number");
            }
        }
        
        throw new IllegalArgumentException("Could not parse dump priority out of '" + dumpPriority + "'");
    }

}
