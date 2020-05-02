package net.sf.dz3.device.actuator.servomaster;

import java.io.IOException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.ThreadContext;

import net.sf.dz3.device.actuator.Damper;
import net.sf.servomaster.device.model.ServoController;

/**
 *
 * @author Copyright &copy; <a href="mailto:vt@homeclimatecontrol.com"> Vadim Tkachenko</a> 2001-2020
 */
public class DamperFactory {

    private final Logger logger = LogManager.getLogger(getClass());
    private final ServoController theController;

    public DamperFactory(String className, String portName) {

        ThreadContext.push("DamperFactory()");

        try {

            Class<?> controllerClass = Class.forName(className);
            logger.debug("found class: {}", controllerClass.getCanonicalName());

            if (!ServoController.class.isAssignableFrom(controllerClass)) {
                throw new IllegalArgumentException("Not a servo controller: " + controllerClass.getName());
            }

            Object controllerObject = controllerClass.newInstance();

            theController = (ServoController) controllerObject;
            logger.info("Controller instantiated: {}", theController.getMeta());

            theController.init(portName);

        } catch (ClassNotFoundException|SecurityException|InstantiationException|IllegalAccessException ex) {

            throw new IllegalArgumentException("can't instantitate '" + className + "'", ex);

        } catch (IOException ex) {

            throw new IllegalArgumentException("don't know how to handle", ex);

        } finally {
            ThreadContext.pop();
        }
    }

    /**
     * Get an instance of a straight (not reversed) damper with no calibration.
     *
     * @param name Human readable name.
     * @param id Controller specific servo ID.
     *
     * @return Damper instance.
     * @throws IOException if things go wrong.
     */
    public Damper getDamper(String name, String id) throws IOException {
        return getDamper(name, id, true, false, null, null);
    }

    /**
     * Get an instance of a damper with range calibration only.
     *
     * @param name Human readable name.
     * @param id Controller specific servo ID.
     * @param crawl {@code true} if the damper needs to be crawling.
     * @param reverse {@code true} if the damper needs to be reversed.
     * @param rangeCalibration Range calibration object.
     *
     * @return Damper instance.
     * @throws IOException if things go wrong.
     */
    public Damper getDamper(
            String name,
            String id,
            boolean crawl,
            boolean reverse,
            RangeCalibration rangeCalibration) throws IOException {

        return getDamper(name, id, crawl, reverse, rangeCalibration, null);
    }

    /**
     * Get an instance of a damper with limit calibration only.
     *
     * @param name Human readable name.
     * @param id Controller specific servo ID.
     * @param crawl {@code true} if the damper needs to be crawling.
     * @param reverse {@code true} if the damper needs to be reversed.
     * @param limitCalibration Limit calibration object.
     *
     * @return Damper instance.
     * @throws IOException if things go wrong.
     */
    public Damper getDamper(
            String name,
            String id,
            boolean crawl,
            boolean reverse,
            LimitCalibration limitCalibration) throws IOException {

        return getDamper(name, id, crawl, reverse, null, limitCalibration);
    }

    private Damper getDamper(
            String name,
            String id,
            boolean crawl,
            boolean reverse,
            RangeCalibration rangeCalibration,
            LimitCalibration limitCalibration) throws IOException {

        return new ServoDamper(name, theController.getServo(id), crawl, reverse, rangeCalibration, limitCalibration);
    }
}
