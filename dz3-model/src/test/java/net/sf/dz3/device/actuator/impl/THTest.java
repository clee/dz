package net.sf.dz3.device.actuator.impl;

import com.homeclimatecontrol.jukebox.datastream.signal.model.DataSample;
import net.sf.dz3.device.actuator.Damper;
import net.sf.dz3.device.model.Thermostat;
import net.sf.dz3.device.model.ThermostatSignal;
import net.sf.dz3.device.model.Unit;
import net.sf.dz3.device.model.UnitSignal;
import net.sf.dz3.device.model.impl.AbstractDamperController;
import net.sf.dz3.device.model.impl.BalancingDamperController;
import net.sf.dz3.device.model.impl.SimpleDamperController;
import net.sf.dz3.device.model.impl.ThermostatModel;
import net.sf.dz3.device.sensor.Switch;
import net.sf.dz3.device.sensor.impl.NullSwitch;
import net.sf.dz3.instrumentation.Marker;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.ThreadContext;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

/**
 * Set of test cases to replicate https://github.com/home-climate-control/dz/issues/172.
 */
class THTest {

    private final Logger logger = LogManager.getLogger(getClass());
    private final Random rg = new Random();

    private static final String POSITION = "parked position";
    private static final String STATE = "switch state";

    @Test
    void testSyncFastSimple()
            throws IOException, NoSuchMethodException, SecurityException,
            InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException {

        testSync("fast/simple", SimpleDamperController.class, 0, 0);
    }

    @Test
    void testSyncSlowSimple()
            throws IOException, NoSuchMethodException, SecurityException,
            InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException {

        testSync("slow/simple", SimpleDamperController.class, 1, 5);
    }

    @Test
    void testSyncFastBalancing()
            throws IOException, NoSuchMethodException, SecurityException,
            InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException {

        testSync("fast/balancing", BalancingDamperController.class, 0, 0);
    }

    @Test
    void testSyncSlowBalancing()
            throws IOException, NoSuchMethodException, SecurityException,
            InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException {

        testSync("slow/balancing", BalancingDamperController.class, 1, 5);
    }

    private void testSync(String marker, Class<? extends AbstractDamperController> controllerClass, long minDelay, int maxDelay)
            throws IOException, NoSuchMethodException, SecurityException,
            InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException {

        var m = new Marker(marker);
        ThreadContext.push(marker);

        try {

            var tsLivingRoom = mock(ThermostatModel.class);
            var tsKitchen = mock(ThermostatModel.class);
            var tsWestBathroom = mock(ThermostatModel.class);
            var tsWest = mock(ThermostatModel.class);

            doReturn("thermostat-livingroom").when(tsLivingRoom).getName();
            doReturn("thermostat-kitchen").when(tsKitchen).getName();
            doReturn("thermostat-west_bathroom").when(tsWestBathroom).getName();
            doReturn("thermostat-west").when(tsWest).getName();

            Object lock = "lock";

            Switch switchLivingRoom = new NullSwitch("switch_livingroom_damper", minDelay, maxDelay, lock);
            Switch switchKitchen = new NullSwitch("switch_kitchen_damper", minDelay, maxDelay, lock);
            Switch switchWestBathroom = new NullSwitch("switch_west_bathroom_damper", minDelay, maxDelay, lock);
            Switch switchWestDamper = new NullSwitch("switch_west_damper", minDelay, maxDelay, lock);
            Switch switchWestBoosterFan = new NullSwitch("switch_west_boosterfan", minDelay, maxDelay, lock);

            var switches = new LinkedHashSet<Switch>();

            switches.add(switchLivingRoom);
            switches.add(switchKitchen);
            switches.add(switchWestBathroom);
            switches.add(switchWestDamper);
            switches.add(switchWestBoosterFan);

            var damperLivingRoom = new SwitchDamper("damper_livingroom", switchLivingRoom, 0.8, 1.0, 10);
            var damperKitchen = new SwitchDamper("damper_kitchen", switchKitchen, 0.8, 1.0, 10);
            var damperWestBathroom = new SwitchDamper("damper_west_bathroom", switchWestBathroom, 0.8, 1.0, 10);

            var damperWest = new SwitchDamper("damper_west", switchWestDamper, 0.8, 1.0, 10);
            var damperWestBoosterFan = new SwitchDamper("damper_west_boosterfan", switchWestBoosterFan, 0.8, 0.0, 10, true);

            var west = new LinkedHashSet<Damper>();

            west.add(damperWest);
            west.add(damperWestBoosterFan);

            var damperMultiplexerWest = new DamperMultiplexer("damper_multiplexer_west", west);

            var dampers = new LinkedHashSet<Damper>();

            dampers.add(damperLivingRoom);
            dampers.add(damperKitchen);
            dampers.add(damperWestBathroom);
            dampers.add(damperWest);
            dampers.add(damperWestBoosterFan);
            dampers.add(damperMultiplexerWest);

            // TreeMap will not work here because of the way Mockito works
            // Note 'Thermostat' here vs. 'ThermostatModel' for the mock
            var ts2damper = new LinkedHashMap<Thermostat, Damper>();

            ts2damper.put(tsLivingRoom, damperLivingRoom);
            ts2damper.put(tsKitchen, damperKitchen);
            ts2damper.put(tsWestBathroom, damperWestBathroom);
            ts2damper.put(tsWest, damperMultiplexerWest);

            var u = mock(Unit.class);

            Constructor<? extends AbstractDamperController> c = controllerClass.getDeclaredConstructor(Unit.class, Map.class);
            var dc = c.newInstance(u, ts2damper);

            logger.info("Damper map: {}", Arrays.asList(dc.getDamperMap()));

            // VT: NOTE: It may be a better idea to inject fixed time; let's see if this works
            var timestamp = System.currentTimeMillis();

            // This will wait until all the movements are complete - unlike real life scenario;
            // that'll come later

            dc.stateChanged(tsWest, new ThermostatSignal(
                    true, false, true, true,
                    new DataSample<>(timestamp, "sensor_west", "sensor_west", 3.0625, null)));

            // The unit is off, dampers are parked

            logStatus(dampers, switches);

            assertThat(damperLivingRoom.getPosition()).as(POSITION).isEqualTo(damperLivingRoom.getParkPosition());
            assertThat(damperKitchen.getPosition()).as(POSITION).isEqualTo(damperKitchen.getParkPosition());
            assertThat(damperWestBathroom.getPosition()).as(POSITION).isEqualTo(damperWestBathroom.getParkPosition());
            assertThat(damperMultiplexerWest.getPosition()).as(POSITION).isEqualTo(damperMultiplexerWest.getParkPosition());
            assertThat(damperWest.getPosition()).as(POSITION).isEqualTo(damperWest.getParkPosition());
            assertThat(damperWestBoosterFan.getPosition()).as(POSITION).isEqualTo(damperWestBoosterFan.getParkPosition());

            assertThat(switchLivingRoom.getState()).as(STATE).isTrue();
            assertThat(switchKitchen.getState()).as(STATE).isTrue();
            assertThat(switchWestBathroom.getState()).as(STATE).isTrue();
            assertThat(switchWestDamper.getState()).as(STATE).isTrue();
            assertThat(switchWestBoosterFan.getState()).as(STATE).isTrue();

            // The above stateChanged() also changed the state of the Unit to "running",
            // next stateChanged() will be handled differently

            // For a good measure, let's advance the timestamp between signals
            timestamp += 50 + rg.nextInt(100);
            dc.consume(new DataSample<>(timestamp, "unit", "unit", new UnitSignal(0, true, 0), null));

            timestamp += 50 + rg.nextInt(100);
            dc.consume(new DataSample<>(timestamp, "unit", "unit", new UnitSignal(3.0625, true, 0), null));

            timestamp += 50 + rg.nextInt(100);
            dc.consume(new DataSample<>(timestamp, "unit", "unit", new UnitSignal(6.875, true, 0), null));

            timestamp += 50 + rg.nextInt(100);
            dc.consume(new DataSample<>(timestamp, "unit", "unit", new UnitSignal(10.3125, true, 0), null));

            timestamp += 50 + rg.nextInt(100);
            dc.consume(new DataSample<>(timestamp, "unit", "unit", new UnitSignal(13.6875, true, 0), null));


            // After that, the demand rises by small increments until the whole thing blows up
            // The count in the crash log is 9, let's make sure it's exceeded

            var demand = 13.6875;
            for (int count = 0; count < 50; count++) {

                timestamp += 50 + rg.nextInt(100);
                demand += rg.nextDouble()/10;

                dc.consume(new DataSample<>(timestamp, "unit", "unit", new UnitSignal(demand, true, 0), null));
            }

            // To be continued...

            dc.powerOff();

            assertThat(damperLivingRoom.getPosition()).as(POSITION).isEqualTo(damperLivingRoom.getParkPosition());
            assertThat(damperKitchen.getPosition()).as(POSITION).isEqualTo(damperKitchen.getParkPosition());
            assertThat(damperWestBathroom.getPosition()).as(POSITION).isEqualTo(damperWestBathroom.getParkPosition());
            assertThat(damperMultiplexerWest.getPosition()).as(POSITION).isEqualTo(damperMultiplexerWest.getParkPosition());
            assertThat(damperWest.getPosition()).as(POSITION).isEqualTo(damperWest.getParkPosition());
            assertThat(damperWestBoosterFan.getPosition()).as(POSITION).isEqualTo(damperWestBoosterFan.getParkPosition());

            assertThat(switchLivingRoom.getState()).as(STATE).isTrue();
            assertThat(switchKitchen.getState()).as(STATE).isTrue();
            assertThat(switchWestBathroom.getState()).as(STATE).isTrue();
            assertThat(switchWestDamper.getState()).as(STATE).isTrue();
            assertThat(switchWestBoosterFan.getState()).as(STATE).isTrue();

            logger.info("Damper map: {}", Arrays.asList(dc.getDamperMap()));

        } finally {
            m.close();
            ThreadContext.pop();
        }
    }

    private void logStatus(Set<Damper> dampers, Set<Switch> switches) {
        ThreadContext.push("position");

        dampers.stream().forEach(d -> {
            try {
                logger.info("{}: {}", d.getName(), d.getPosition());
            } catch (IOException ex) {
                // This damper won't throw it
            }
        });

        ThreadContext.pop();
        ThreadContext.push("state");

        switches.stream().forEach(s -> {
            try {
                logger.info("{}: {}", s.getAddress(), s.getState());
            } catch (IOException ex) {
                // This damper won't throw it
            }
        });

        ThreadContext.pop();
    }
}
