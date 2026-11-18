package io.github.west3436.zas.atmos.ttv;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.Locale;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * A remote signaler's setting (CONT-185): a frequency and a code. A primed TTV fitted with a signaler
 * opens when it hears a signal with both the same as its own (CONT-186). Out of range values are
 * clamped as they are made, so a save or a packet can't hold one.
 *
 * @param frequency the frequency, in tenths: 1457 is 145.7
 * @param code the code, 1 to 100
 */
public record Signal(int frequency, int code) {
    /** The lowest frequency, in tenths. */
    public static final int MIN_FREQUENCY = 1201;

    /** The highest frequency, in tenths. */
    public static final int MAX_FREQUENCY = 1599;

    /** A new signaler's frequency, in tenths. */
    public static final int DEFAULT_FREQUENCY = 1457;

    /** The lowest code. */
    public static final int MIN_CODE = 1;

    /** The highest code. */
    public static final int MAX_CODE = 100;

    /** A new signaler's code. */
    public static final int DEFAULT_CODE = 30;

    /** A new signaler's setting. */
    public static final Signal DEFAULT = new Signal(DEFAULT_FREQUENCY, DEFAULT_CODE);

    /** How it is saved. */
    public static final Codec<Signal> CODEC = RecordCodecBuilder.create(i -> i.group(
                    Codec.INT.fieldOf("frequency").forGetter(Signal::frequency),
                    Codec.INT.fieldOf("code").forGetter(Signal::code))
            .apply(i, Signal::new));

    /** How it is sent. */
    public static final StreamCodec<ByteBuf, Signal> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, Signal::frequency, ByteBufCodecs.VAR_INT, Signal::code, Signal::new);

    /** Clamps both to their ranges. */
    public Signal {
        frequency = Math.max(MIN_FREQUENCY, Math.min(MAX_FREQUENCY, frequency));
        code = Math.max(MIN_CODE, Math.min(MAX_CODE, code));
    }

    /** The same code on another frequency, clamped. */
    public Signal withFrequency(int tenths) {
        return new Signal(tenths, code);
    }

    /** The same frequency with another code, clamped. */
    public Signal withCode(int code) {
        return new Signal(frequency, code);
    }

    /** The frequency as a player reads it: {@code 145.7}. */
    public String frequencyText() {
        return String.format(Locale.ROOT, "%d.%d", frequency / 10, frequency % 10);
    }
}
