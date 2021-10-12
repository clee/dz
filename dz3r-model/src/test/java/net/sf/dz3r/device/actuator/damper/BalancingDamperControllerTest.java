package net.sf.dz3r.device.actuator.damper;

import net.sf.dz3r.model.Thermostat;
import net.sf.dz3r.model.Zone;
import net.sf.dz3r.model.ZoneSettings;
import net.sf.dz3r.signal.Signal;
import net.sf.dz3r.signal.hvac.ThermostatStatus;
import net.sf.dz3r.signal.hvac.UnitControlSignal;
import net.sf.dz3r.signal.hvac.ZoneStatus;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CountDownLatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class BalancingDamperControllerTest {

    private final Logger logger = LogManager.getLogger();
    private final Random rg = new SecureRandom();

    /**
     * Make sure that thermostats with negative demand don't cause damper control signals
     * out of acceptable range.
     */
    @Test
    void testBoundaries1() {

        assertThatCode(() -> {

            var zoneSettings = new ZoneSettings(20);
            var z1 = new Zone(new Thermostat("Z1", 20, 1, 0, 0, 1), zoneSettings);
            var z2 = new Zone(new Thermostat("Z2", 20, 1, 0, 0, 1), zoneSettings);

            var d1 = new NullDamper("d1");
            var d2 = new NullDamper("d2");

            var park1 = rg.nextDouble();
            var park2 = rg.nextDouble();

            d1.setParkPosition(park1);
            d2.setParkPosition(park2);

            try (BalancingDamperController damperController = new BalancingDamperController(Map.of(
                    z1, d1,
                    z2, d2
            ))) {

                var unitFlux = Flux
                        .just(new Signal<UnitControlSignal, Void>(Instant.now(), new UnitControlSignal(0d, 0d)));

                var status1 = new ZoneStatus(zoneSettings, new ThermostatStatus(50, true));
                var status2 = new ZoneStatus(zoneSettings, new ThermostatStatus(-50, true));
                var signalFlux = Flux.just(
                        new Signal<>(Instant.now(), status1, "Z1", Signal.Status.OK, null),
                        new Signal<>(Instant.now(), status2, "Z2", Signal.Status.OK, null)
                ).log();

                // This combination will not produce anything other than park() three times
                int eventCount = 2;
                var gate = new CountDownLatch(eventCount);

                damperController
                        .compute(unitFlux, signalFlux)
                        .take(2)
                        .doOnNext(ignored -> gate.countDown())
                        .blockLast();

                logger.info("waiting for the gate...");
                gate.await();
                logger.info("past the gate");

                logger.info("d1: {}", d1);
                logger.info("d2: {}", d2);

                assertThat(d1.get()).isEqualTo(park1);
                assertThat(d2.get()).isEqualTo(park2);
            }

        }).doesNotThrowAnyException();
    }

    /**
     * Make sure that thermostats with negative demand don't cause damper control signals
     * out of acceptable range.
     */
    @Test
    void testBoundaries2() {

        assertThatCode(() -> {

            var zoneSettings = new ZoneSettings(20);
            var z1 = new Zone(new Thermostat("Z1", 20, 1, 0, 0, 1), zoneSettings);
            var z2 = new Zone(new Thermostat("Z2", 20, 1, 0, 0, 1), zoneSettings);

            var d1 = new NullDamper("d1");
            var d2 = new NullDamper("d2");

            try (BalancingDamperController damperController = new BalancingDamperController(Map.of(
                    z1, d1,
                    z2, d2
            ))) {

                var unitFlux = Flux
                        .just(new Signal<UnitControlSignal, Void>(Instant.now(), new UnitControlSignal(1d, 1d)));

                var status1 = new ZoneStatus(zoneSettings, new ThermostatStatus(50, true));
                var status2 = new ZoneStatus(zoneSettings, new ThermostatStatus(-50, true));
                var signalFlux = Flux.just(
                        new Signal<>(Instant.now(), status1, "Z1", Signal.Status.OK, null),
                        new Signal<>(Instant.now(), status2, "Z2", Signal.Status.OK, null)
                ).log();


                int eventCount = 2;
                var gate = new CountDownLatch(eventCount);

                damperController
                        .compute(unitFlux, signalFlux)
                        .take(eventCount)
                        .doOnNext(ignored -> gate.countDown())
                        .blockLast();

                logger.info("waiting for the gate...");
                gate.await();
                logger.info("past the gate");

                logger.info("d1: {}", d1);
                logger.info("d2: {}", d2);

                assertThat(d1.get()).isEqualTo(1d);
                assertThat(d2.get()).isEqualTo(0d);
            }

        }).doesNotThrowAnyException();
    }

    /**
     * Make sure that zero demand from all thermostats doesn't cause NaN sent to dampers.
     */
    @Test
    void testNaN() {

        assertThatCode(() -> {

            var zoneSettings = new ZoneSettings(20);
            var z1 = new Zone(new Thermostat("Z1", 20, 1, 0, 0, 1), zoneSettings);

            var d1 = new NullDamper("d1");

            try (BalancingDamperController damperController = new BalancingDamperController(Map.of(
                    z1, d1
            ))) {

                var unitFlux = Flux
                        .just(new Signal<UnitControlSignal, Void>(Instant.now(), new UnitControlSignal(1d, 1d)));

                var status1 = new ZoneStatus(zoneSettings, new ThermostatStatus(-50, true));
                var signalFlux = Flux.just(
                        new Signal<>(Instant.now(), status1, "Z1", Signal.Status.OK, null)
                ).log();


                int eventCount = 2;
                var gate = new CountDownLatch(eventCount);

                damperController
                        .compute(unitFlux, signalFlux)
                        .take(eventCount)
                        .doOnNext(ignored -> gate.countDown())
                        .blockLast();

                logger.info("waiting for the gate...");
                gate.await();
                logger.info("past the gate");

                logger.info("d1: {}", d1);

                // VT: NOTE: This test case matches the "existing code", but it is bizarre - a running unit with a
                // single zone shouldn't ever get anything other than 1.0. Bug report coming up.

                assertThat(d1.get()).isEqualTo(0d);
            }

        }).doesNotThrowAnyException();
    }
}
