package io.github.west3436.zas.client;

import io.github.west3436.zas.api.client.DisplayUnitProvider;
import io.github.west3436.zas.api.client.DisplayUnits;
import io.github.west3436.zas.config.ZasClientConfig;

/** Answers {@link DisplayUnits} from the client config, so other mods' GUIs follow it (CLIENT-006). */
final class ClientDisplayUnits implements DisplayUnitProvider {
    private ClientDisplayUnits() {}

    /** Installs the provider. Called once, from the client entry point. */
    static void register() {
        DisplayUnits.install(new ClientDisplayUnits());
    }

    @Override
    public double pressure(double kpa) {
        return ZasClientConfig.pressureUnit().fromKpa(kpa);
    }

    @Override
    public String pressureSymbol() {
        return ZasClientConfig.pressureUnit().symbol();
    }

    @Override
    public double temperature(double kelvin) {
        return ZasClientConfig.temperatureUnit().fromKelvin(kelvin);
    }

    @Override
    public String temperatureSymbol() {
        return ZasClientConfig.temperatureUnit().symbol();
    }
}
