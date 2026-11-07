package io.github.west3436.zas.hud;

import io.github.west3436.zas.ZasMod;
import io.netty.buffer.ByteBuf;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * A player's own atmosphere HUD state (CLIENT-001, CLIENT-008, CLIENT-009, DATA-014), sent to that
 * player only: each reading's alert level and, while it has one, the value it was judged on, and
 * while the HUD is {@linkplain #shown shown}, its internals and the pressure of their tank. What
 * isn't shown is dropped, so changes to it send nothing: a reading with no alert has the value 0,
 * and a hidden HUD has internals off and no tank.
 *
 * <p>Levels are {@link HudLevels#NONE}, {@link HudLevels#WARNING} or {@link HudLevels#DANGER}.
 * Pressure and temperature are signed: negative for low pressure and cold, positive for high
 * pressure and heat.
 *
 * <p>While the player holds an item in {@link HudLevels#SHOWS_READINGS}, such as an analyzer, every
 * reading keeps its value, alert or not, and the HUD {@linkplain #shows shows} all four (CLIENT-008,
 * CONT-034). Then O₂ and toxins have the value {@link #NO_BREATH} while it has no breath.
 *
 * @param pressure the pressure level, −2 to 2
 * @param oxygen the O₂ level, 0 to 2
 * @param temperature the temperature level, −2 to 2
 * @param toxins the worst toxic gas's level, 0 to 2
 * @param pressureKpa the pressure that reaches the player, in kPa (LIFE-020)
 * @param oxygenKpa the O₂ partial pressure of its breath, in kPa
 * @param temperatureK the temperature around it, in K
 * @param toxinGas the toxic gas with the worst level, while there is one
 * @param toxinKpa that gas's partial pressure in the breath, in kPa
 * @param triggered whether something other than an alert shows the HUD ({@link HudLevels#triggered})
 * @param internals its internals: {@link #INTERNALS_OFF}, {@link #INTERNALS_ON} or {@link
 *     #INTERNALS_FAILING}
 * @param tankKpa the pressure of the tank they draw from, in kPa, or {@link #NO_TANK}
 * @param readings whether every reading is shown, alert or not ({@link HudLevels#showsReadings})
 */
public record AtmosHudPayload(
        int pressure,
        int oxygen,
        int temperature,
        int toxins,
        float pressureKpa,
        float oxygenKpa,
        float temperatureK,
        Optional<ResourceLocation> toxinGas,
        float toxinKpa,
        boolean triggered,
        int internals,
        float tankKpa,
        boolean readings)
        implements CustomPacketPayload {

    public static final Type<AtmosHudPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ZasMod.MOD_ID, "atmos_hud"));

    /** Internals switched off. */
    public static final int INTERNALS_OFF = 0;

    /** Internals switched on, and the player breathes from the tank. */
    public static final int INTERNALS_ON = 1;

    /** Internals switched on, but it breathes the air: no seal, no tank, or a tank that can't deliver. */
    public static final int INTERNALS_FAILING = 2;

    /** {@link #tankKpa} when the player has no tank. */
    public static final float NO_TANK = -1;

    /** No alerts and no trigger: the HUD is hidden. */
    public static final AtmosHudPayload NONE = new AtmosHudPayload(0, 0, 0, 0, 0, 0, 0, Optional.empty(), 0);

    /**
     * {@link #oxygenKpa} and {@link #toxinKpa} while every reading is shown but the player has no
     * breath: its eyes are in a fluid, where vanilla's breathing applies (LIFE-022).
     */
    public static final float NO_BREATH = -1;

    public static final StreamCodec<ByteBuf, AtmosHudPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeByte(p.pressure);
                buf.writeByte(p.oxygen);
                buf.writeByte(p.temperature);
                buf.writeByte(p.toxins);
                buf.writeFloat(p.pressureKpa);
                buf.writeFloat(p.oxygenKpa);
                buf.writeFloat(p.temperatureK);
                ByteBufCodecs.optional(ResourceLocation.STREAM_CODEC).encode(buf, p.toxinGas);
                buf.writeFloat(p.toxinKpa);
                buf.writeBoolean(p.triggered);
                buf.writeByte(p.internals);
                buf.writeFloat(p.tankKpa);
                buf.writeBoolean(p.readings);
            },
            buf -> new AtmosHudPayload(
                    buf.readByte(),
                    buf.readByte(),
                    buf.readByte(),
                    buf.readByte(),
                    buf.readFloat(),
                    buf.readFloat(),
                    buf.readFloat(),
                    ByteBufCodecs.optional(ResourceLocation.STREAM_CODEC).decode(buf),
                    buf.readFloat(),
                    buf.readBoolean(),
                    buf.readByte(),
                    buf.readFloat(),
                    buf.readBoolean()));

    /**
     * Clamps the levels to their ranges, zeroes the value of every reading with no alert unless
     * every reading is shown, and drops the internals and tank of a hidden HUD.
     */
    public AtmosHudPayload {
        pressure = clamp(pressure, -2, 2);
        oxygen = clamp(oxygen, 0, 2);
        temperature = clamp(temperature, -2, 2);
        toxins = clamp(toxins, 0, 2);
        if (pressure == 0 && !readings) {
            pressureKpa = 0;
        }
        if (oxygen == 0 && !readings) {
            oxygenKpa = 0;
        }
        if (temperature == 0 && !readings) {
            temperatureK = 0;
        }
        Objects.requireNonNull(toxinGas, "toxinGas");
        if (toxins == 0 && !readings) {
            toxinGas = Optional.empty();
            toxinKpa = 0;
        }
        internals = clamp(internals, INTERNALS_OFF, INTERNALS_FAILING);
        if (!(tankKpa >= 0)) {
            tankKpa = NO_TANK;
        }
        if (!triggered && !readings && pressure == 0 && oxygen == 0 && temperature == 0 && toxins == 0) {
            internals = INTERNALS_OFF;
            tankKpa = NO_TANK;
        }
    }

    /** The readings alone: no trigger, internals off, no tank, and only their alerts shown. */
    public AtmosHudPayload(
            int pressure,
            int oxygen,
            int temperature,
            int toxins,
            float pressureKpa,
            float oxygenKpa,
            float temperatureK,
            Optional<ResourceLocation> toxinGas,
            float toxinKpa) {
        this(
                pressure,
                oxygen,
                temperature,
                toxins,
                pressureKpa,
                oxygenKpa,
                temperatureK,
                toxinGas,
                toxinKpa,
                false,
                INTERNALS_OFF,
                NO_TANK,
                false);
    }

    /** These readings, with every reading shown or not as they are, with a trigger, internals and a tank. */
    public AtmosHudPayload withIndicator(boolean triggered, int internals, float tankKpa) {
        return new AtmosHudPayload(
                pressure,
                oxygen,
                temperature,
                toxins,
                pressureKpa,
                oxygenKpa,
                temperatureK,
                toxinGas,
                toxinKpa,
                triggered,
                internals,
                tankKpa,
                readings);
    }

    /** Whether any reading is at warning or worse. */
    public boolean any() {
        return pressure != 0 || oxygen != 0 || temperature != 0 || toxins != 0;
    }

    /**
     * Whether the HUD is shown: a reading is at warning or worse, something else shows it, or every
     * reading is shown (CLIENT-001).
     */
    public boolean shown() {
        return triggered || readings || any();
    }

    /**
     * Whether the HUD shows a reading (CLIENT-008, CONT-034): at warning or worse always, and while
     * every reading is shown, at any level it has a value for.
     *
     * @param level the reading's level
     * @param value its value: {@link #NO_BREATH} for none
     */
    public boolean shows(int level, float value) {
        return level != HudLevels.NONE || readings && value >= 0;
    }

    /**
     * Whether the player should be sent this state when it last got {@code sent} (DATA-014): a
     * reading crossed a threshold, the worst toxic gas changed, the HUD was shown or hidden, every
     * reading began or stopped being shown, the internals changed, or a shown value changed by its
     * display resolution (1 kPa, 1 K, 0.1 kPa partial pressure, 1 kPa for the tank). A trigger that
     * comes or goes while an alert shows the HUD anyway changes nothing shown.
     *
     * @param sent the state last sent, or null before the first
     */
    public boolean differsFrom(AtmosHudPayload sent) {
        if (sent == null) {
            return true;
        }
        return pressure != sent.pressure
                || oxygen != sent.oxygen
                || temperature != sent.temperature
                || toxins != sent.toxins
                || !toxinGas.equals(sent.toxinGas)
                || shown() != sent.shown()
                || readings != sent.readings
                || internals != sent.internals
                || (tankKpa < 0) != (sent.tankKpa < 0)
                || moved(pressureKpa, sent.pressureKpa, 1)
                || moved(temperatureK, sent.temperatureK, 1)
                || moved(oxygenKpa, sent.oxygenKpa, 0.1)
                || moved(toxinKpa, sent.toxinKpa, 0.1)
                || moved(tankKpa, sent.tankKpa, 1);
    }

    /** Whether a value moved by a resolution, allowing for float rounding (6.1f − 6f < 0.1). */
    private static boolean moved(float value, float sent, double resolution) {
        return Math.abs((double) value - sent) >= resolution * 0.999;
    }

    @Override
    public Type<AtmosHudPayload> type() {
        return TYPE;
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }
}
