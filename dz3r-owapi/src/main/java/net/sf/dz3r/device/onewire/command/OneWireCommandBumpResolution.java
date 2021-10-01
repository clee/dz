package net.sf.dz3r.device.onewire.command;

import com.dalsemi.onewire.OneWireException;
import com.dalsemi.onewire.adapter.DSPortAdapter;
import com.dalsemi.onewire.container.TemperatureContainer;
import net.sf.dz3.instrumentation.Marker;
import net.sf.dz3r.device.onewire.event.OneWireNetworkEvent;
import org.apache.logging.log4j.ThreadContext;
import reactor.core.publisher.FluxSink;

public class OneWireCommandBumpResolution extends OneWireCommand {

    public final String address;

    public OneWireCommandBumpResolution(FluxSink<OneWireCommand> commandSink, String address) {
        super(commandSink);
        this.address = address;
    }

    @Override
    protected void execute(DSPortAdapter adapter, OneWireCommand command, FluxSink<OneWireNetworkEvent> eventSink) throws OneWireException {
        ThreadContext.push("bumpResolution");
        var m = new Marker("bumpResolution");
        try {

            var device = adapter.getDeviceContainer(address);

            if (!(device instanceof TemperatureContainer)) {
                logger.warn("{} ({}): not a temperature container", address, device.getName());
                return;
            }

            var tc = (TemperatureContainer) device;

            var state = tc.readDevice();

            if (!tc.hasSelectableTemperatureResolution()) {
                logger.warn("{} ({}): doesn't support selectable resolution", address, device.getName());
                return;
            }

            var resolutions = tc.getTemperatureResolutions();
            var sb = new StringBuilder();

            for (double v : resolutions) {
                sb.append(v).append(" ");
            }

            logger.debug("{} ({}): temperature resolutions available: {}, setting best", address, device.getName(), sb);

            tc.setTemperatureResolution(resolutions[resolutions.length - 1], state);
            tc.writeDevice(state);

        } finally {
            m.close();
            ThreadContext.pop();
        }
    }
}
