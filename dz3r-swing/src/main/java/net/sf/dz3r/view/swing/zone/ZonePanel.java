package net.sf.dz3r.view.swing.zone;

import net.sf.dz3r.model.HvacMode;
import net.sf.dz3r.model.Zone;
import net.sf.dz3r.model.ZoneSettings;
import net.sf.dz3r.signal.Signal;
import net.sf.dz3r.signal.ZoneStatus;
import net.sf.dz3r.view.swing.ColorScheme;
import net.sf.dz3r.view.swing.EntityPanel;
import net.sf.dz3r.view.swing.ScreenDescriptor;
import net.sf.dz3r.view.swing.TemperatureUnit;
import org.apache.logging.log4j.ThreadContext;
import reactor.core.publisher.Flux;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.text.DecimalFormat;
import java.util.Locale;
import java.util.Optional;

public class ZonePanel extends EntityPanel<ZoneStatus, Void> {

    private static final DecimalFormat numberFormat = new DecimalFormat("#0.0;-#0.0");

    private static final String UNDEFINED = "--.-";

    private static final String VOTING = "VOTING";
    private static final String NOT_VOTING = "NOT VOTING";

    private static final String HOLD = "HOLD";
    private static final String ON_HOLD = "ON HOLD";

    private static final String NO_PERIOD = "(no period is active)";

    private final JLabel currentLabel = new JLabel(UNDEFINED, SwingConstants.RIGHT);
    private final JLabel setpointLabel = new JLabel(UNDEFINED + "\u00b0", SwingConstants.RIGHT);
    private final JLabel votingLabel = new JLabel(VOTING, SwingConstants.RIGHT);
    private final JLabel holdLabel = new JLabel(HOLD, SwingConstants.RIGHT);
    private final JLabel periodLabel = new JLabel("", SwingConstants.LEFT);

    /**
     * Font to display the current temperature in Celsius.
     */
    private Font currentFontC;

    /**
     * Font to display the current temperature in Fahrenheit when the temperature is over 100F.
     */
    private Font currentFontF;

    /**
     * Font to display the setpoint with.
     */
    private Font setpointFont;

    private boolean needFahrenheit;

    /**
     * Setpoint change upon a keypress.
     *
     */
    private double setpointDelta = 0.1d;

    private final Zone zone;

    /**
     * VT: FIXME: Need the actual chart
     */
    private final JPanel chart = new JPanel();

    /**
     * @see #consumeSignalValue(ZoneStatus)
     */
    private ZoneStatus zoneStatus;

    /**
     * @see #consumeSensorSignal(Signal)
     */
    private Signal<Double, Void> sensorSignal;

    /**
     * @see #consumeMode(Signal)
     */
    private HvacMode hvacMode;

    public ZonePanel(Zone zone, ScreenDescriptor screenDescriptor, TemperatureUnit defaultUnit) {
        this.zone = zone;

        this.needFahrenheit = defaultUnit == TemperatureUnit.F;

        setFontSize(screenDescriptor);

        initGraphics();
    }


    private void initGraphics() {

        currentLabel.setFont(currentFontC);
        currentLabel.setToolTipText("Current temperature (Left/Right to change zone)");

        setpointLabel.setFont(setpointFont);
        setpointLabel.setToolTipText("Setpoint (Up/Down to change)");

        createLayout(zone.getAddress(), chart);
    }

    @Override
    @SuppressWarnings("squid:S1199")
    protected void createControls(JPanel controls, GridBagLayout layout, GridBagConstraints cs) {

        // VT: NOTE: squid:S1199 - SonarLint is not smart enough to realize that these
        // blocks are for readability

        {
            // Period label is on top left

            cs.gridwidth = 2;
            cs.fill = GridBagConstraints.HORIZONTAL;

            layout.setConstraints(periodLabel, cs);
            controls.add(periodLabel);

            periodLabel.setForeground(Color.GRAY);
        }

        {
            // Current label takes all available space on the left
            // and expands until it meets the setpoint and voting/hold labels

            cs.fill = GridBagConstraints.HORIZONTAL;
            cs.gridx = 0;
            cs.gridy++;
            cs.gridwidth = 1;
            cs.gridheight = 3;
            cs.weightx = 1;
            cs.weighty = 0;

            layout.setConstraints(currentLabel, cs);
            controls.add(currentLabel);
        }

        {
            // Setpoint, hold and voting buttons form a group to the right of the current
            // temperature reading, and take the rest of the row

            cs.fill = GridBagConstraints.VERTICAL;
            cs.gridx++;

            cs.gridheight = 1;
            cs.gridwidth = GridBagConstraints.REMAINDER;

            cs.weightx = 0;

            {
                // Setpoint label takes the rest of the space on the right in the top row

                // It takes more space than voting and hold labels

                cs.weighty = 1;

                layout.setConstraints(setpointLabel, cs);
                controls.add(setpointLabel);
            }

            {
                // Hold label is underneath the setpoint label

                cs.gridy++;

                cs.weighty = 0;

                layout.setConstraints(holdLabel, cs);
                controls.add(holdLabel);
            }

            {
                // Voting label is underneath the hold label

                cs.gridy++;

                layout.setConstraints(votingLabel, cs);
                controls.add(votingLabel);
            }
        }
    }

    @Override
    public void setFontSize(ScreenDescriptor screenDescriptor) {

        this.currentFontC = screenDescriptor.fontCurrentTemperatureC;
        this.currentFontF = screenDescriptor.fontCurrentTemperatureF;
        this.setpointFont = screenDescriptor.fontSetpoint;
    }

    @Override
    protected boolean isBackgroundTransparent() {
        return true;
    }

    @Override
    public void keyTyped(KeyEvent e) {
        // No special handling
    }

    @Override
    public void keyPressed(KeyEvent e) {

        ThreadContext.push("keyPressed");

        try {

            logger.debug("{}", e::toString);

            if (zoneStatus == null) {
                logger.warn("zoneStatus unset, blowups likely, ignored");
                return;
            }

            switch (e.getKeyChar()) {

                case 'c':
                case 'C':
                case 'f':
                case 'F':

                    needFahrenheit = !needFahrenheit;
                    refresh();

                    logger.info("Displaying temperature in {}", (needFahrenheit ? "Fahrenheit" : "Celsius"));

                    break;

                case 'h':
                case 'H':

                    // Toggle hold status

                    zone.setSettings(zone.getSettings().merge(new ZoneSettings(
                            null,
                            null,
                            null,
                            !zoneStatus.settings.hold,
                            null
                    )));

                    refresh();

                    logger.info("Hold status for {} is now {}", zone.getAddress(), zone.getSettings().hold);

                    break;

                case 'v':
                case 'V':

                    // Toggle voting status

                    zone.setSettings(zone.getSettings().merge(new ZoneSettings(
                            null,
                            null,
                            !zoneStatus.settings.voting,
                            null,
                            null
                    )));

                    refresh();

                    logger.info("Voting status for {} is now {}", zone.getAddress(), zone.getSettings().voting);

                    break;

                case 'o':
                case 'O':

                    // Toggle off status

                    zone.setSettings(new ZoneSettings(zone.getSettings(), !zoneStatus.settings.enabled));
                    refresh();

                    logger.info("On status for {} is now {}", zone.getAddress(), zone.getSettings().enabled);

                    break;

                case 's':
                case 'S':

                    // Go back to schedule

                    // Implies taking the zone off hold
                    zone.setSettings(zone.getSettings().merge(new ZoneSettings(
                            null,
                            null,
                            null,
                            false,
                            null
                    )));

                    activateSchedule();
                    refresh();

                    break;

                case '0':
                case '1':
                case '2':
                case '3':
                case '4':
                case '5':
                case '6':
                case '7':
                case '8':
                case '9':

                    // Change dump priority

                    zone.setSettings(zone.getSettings().merge(new ZoneSettings(
                            null,
                            null,
                            null,
                            null,
                            e.getKeyChar() - '0'
                    )));

                    refresh();

                    logger.info("Dump priority for{} is now {}", zone.getAddress(), zone.getSettings().dumpPriority);

                    break;

                case KeyEvent.CHAR_UNDEFINED:

                    switch (e.getKeyCode()) {

                        case KeyEvent.VK_KP_UP:
                        case KeyEvent.VK_UP:

                            raiseSetpoint(getSetpointDeltaModifier(e.isShiftDown(), e.isControlDown()));
                            break;

                        case KeyEvent.VK_KP_DOWN:
                        case KeyEvent.VK_DOWN:

                            lowerSetpoint(getSetpointDeltaModifier(e.isShiftDown(), e.isControlDown()));
                            break;

                        default:
                            // Do nothing
                    }
                    break;

                default:
                    // Do nothing
            }

        } finally {
            ThreadContext.pop();
        }
    }

    private void activateSchedule() {
        logger.error("activateSchedule(): Not Implemented", new UnsupportedOperationException());
    }

    private void refresh() {
        logger.warn("refresh(): NOP?");
    }

    private int getSetpointDeltaModifier(boolean shift, boolean ctrl) {

        if (shift && ctrl) {
            return 100;
        }

        if (shift) {
            return 10;
        }

        if (ctrl) {
            return 50;
        }

        return 1;
    }

    /**
     * Raise the setpoint.
     *
     * @param modifier Multiply the default {@link #setpointDelta} by this number to get the actual delta.
     */
    private void raiseSetpoint(int modifier) {

        // Must operate in visible values to avoid rounding problems

        var setpoint = getDisplayValue(zone.getSettings().setpoint);

        setpoint += setpointDelta * modifier;
        setpoint = getSIValue(Double.parseDouble(numberFormat.format(setpoint)));

        zone.setSettings(new ZoneSettings(zone.getSettings(), setpoint));

        refresh();
    }

    /**
     * Lower the setpoint.
     *
     * @param modifier Multiply the default {@link #setpointDelta} by this number to get the actual delta.
     */
    private void lowerSetpoint(int modifier) {

        // Must operate in visible values to avoid rounding problems

        var setpoint = getDisplayValue(zone.getSettings().setpoint);

        setpoint -= setpointDelta * modifier;
        setpoint = getSIValue(Double.parseDouble(numberFormat.format(setpoint)));

        zone.setSettings(new ZoneSettings(zone.getSettings(), setpoint));

        refresh();
    }

    @Override
    public void keyReleased(KeyEvent e) {
        // No special handling
    }

    @Override
    protected void consumeSignalValue(ZoneStatus zoneStatus) {

        if (zoneStatus == null) {
            logger.warn("null zoneStatus update, ignored");
            return;
        }

        this.zoneStatus = zoneStatus;
    }

    /**
     * Process {@link ZoneStatus} updates (see {@link net.sf.dz3r.view.swing.SwingSink}).
     */
    @Override
    protected void update() {

        var signal = getSignal();

        if (signal.isError()) {
            logger.error("Not Implemented: processing error signal: {}", signal, new UnsupportedOperationException());
            return;
        }

        var setpoint = Optional.ofNullable(zoneStatus.settings.setpoint);
        var voting = Optional.ofNullable(zoneStatus.settings.voting);
        var hold = Optional.ofNullable(zoneStatus.settings.hold);

        setpoint.ifPresent(s -> setpointLabel.setText(String.format(Locale.getDefault(), "%.1f\u00b0", getDisplayValue(s))));

        voting.ifPresent(v -> {
            votingLabel.setText(v ? VOTING : NOT_VOTING);
            votingLabel.setForeground(v ? ColorScheme.getScheme(getMode()).noticeDefault : ColorScheme.getScheme(getMode()).noticeActive);
        });

        hold.ifPresent(h -> {
            holdLabel.setText(h ? ON_HOLD : HOLD);
            holdLabel.setForeground(h ? ColorScheme.getScheme(getMode()).noticeActive : ColorScheme.getScheme(getMode()).noticeDefault);
        });
    }

    public void subscribeSensor(Flux<Signal<Double, Void>> sensorFlux) {
        sensorFlux.subscribe(this::consumeSensorSignal);
    }

    private void consumeSensorSignal(Signal<Double, Void> sensorSignal) {
        this.sensorSignal = sensorSignal;
        logger.info("sensorSignal: {}", sensorSignal);
        updateSensorSignal();
    }

    /**
     * Selectively update only the UI parts affected by a changed sensor signal.
     *
     * We do not process error signal here, it will propagate to {@link #update()}.
     */
    private void updateSensorSignal() {

        if (sensorSignal == null || sensorSignal.isError()) {
            currentLabel.setText(UNDEFINED);
        } else {

            var displayTemperature = String.format(Locale.getDefault(), "%.1f", getDisplayValue(sensorSignal.getValue()));
            currentLabel.setText(displayTemperature);

            var font = needFahrenheit  && displayTemperature.length() > 4 ? currentFontF : currentFontC;
            currentLabel.setFont(font);
        }
    }

    private HvacMode getMode() {
        return hvacMode;
    }

    public void subscribeMode(Flux<Signal<HvacMode, Void>> hvacModeFlux) {
        hvacModeFlux.subscribe(this::consumeMode);
    }

    private void consumeMode(Signal<HvacMode, Void> hvacModeSignal) {
        this.hvacMode = hvacModeSignal.getValue();
        logger.info("hvacMode: {}", hvacMode);
        updateMode();
    }

    /**
     * Selectively update only the UI parts affected by a changed HVAC mode.
     */
    private void updateMode() {

        // The way the lifecycle is built, the only updates are the setpoint and current temperature colors,
        // and only from "unknown" to "mode specific".

        var c = ColorScheme.getScheme(getMode()).setpoint;
        currentLabel.setForeground(c);
        setpointLabel.setForeground(c);
    }

    /**
     * Convert SI value into display value depending on whether the display is
     * currently in {@link #needFahrenheit Fahrenheit}.
     *
     * @param value Value to possibly convert.
     * @return Display value.
     */
    private double getDisplayValue(double value) {
        return needFahrenheit ? (value * 9) / 5d + 32: value;
    }

    /**
     * Convert display value into SI value depending on whether the display is
     * currently in {@link #needFahrenheit Fahrenheit}.
     *
     * @param value Value to possibly convert.
     * @return SI value.
     */
    private double getSIValue(double value) {
        return needFahrenheit ? (value - 32) * (5d / 9d) : value;
    }

    public double getSetpointDelta() {
        return setpointDelta;
    }

    public void setSetpointDelta(double setpointDelta) {
        this.setpointDelta = setpointDelta;
    }

    @Override
    public synchronized void paintComponent(Graphics g) {

        super.paintComponent(g);

        if (zoneStatus == null || sensorSignal == null) {
            // Nothing to do yet
            return;
        }

        var g2d = (Graphics2D) g;
        var d = getSize();

        var signal = zoneStatus.status.demand;
        var mode = getMode();
        var state = resolveState();
        var boundary = new Rectangle(0, 0, d.width, d.height);

        switch (state) {

            case CALLING:
            case ERROR:
            case OFF:

                BackgroundRenderer.drawBottom(state, mode, signal, g2d, boundary, true);
                break;

            case HAPPY:

                BackgroundRenderer.drawTop(mode, signal, g2d, boundary);
                break;
        }
    }

    private Zone.State resolveState() {

        if (getSignal() == null || getSignal().isError()) {
            return Zone.State.ERROR;
        }

        if (!zoneStatus.settings.enabled) {
            return Zone.State.OFF;
        }

        return zoneStatus.status.calling ? Zone.State.CALLING : Zone.State.HAPPY;
    }
}
