package io.github.west3436.zas.atmos.firealarm;

import org.jetbrains.annotations.Nullable;

/**
 * What a client hears of the fire alarms around it (CONT-128): one sound, however many are alarming.
 * Every alarm in range is one loop to the client's sound manager, placed at the nearest, so a whole
 * station's alarms can't stack, and it counts once under the nearest 8 (CLIENT-015).
 *
 * <p>The sound plays through once, then rests, so it comes round every {@value #PERIOD_TICKS} game
 * ticks, 12 s, rather than playing without a break. Alarms that start after a quiet spell sound at
 * once. One that stops and starts again within a period keeps the same rhythm, so a flickering alarm
 * can't sound any more often.
 *
 * <p>Alarms {@linkplain #report report} themselves each game tick while their block shows the alarm,
 * so one that resets, is broken or unloads simply stops, and the sound fades as soon as none is left.
 *
 * <p>Platform-free. Client thread only.
 */
public final class FireAlarmVoice {
    /** The client's own. */
    public static final FireAlarmVoice LOCAL = new FireAlarmVoice();

    /** How far it is heard, in blocks: the range of {@code device.fire_alarm.alarm}. */
    public static final double RANGE = 32;

    /**
     * How long it asks for the sound each time, in game ticks. The file is 80 ticks long, and asking
     * stops a fade's length, half a second, before its end, so it plays once and doesn't start over.
     */
    public static final int SOUND_TICKS = 70;

    /** How often the sound comes round, in game ticks: 12 s, so it plays for 4 s of each 12. */
    public static final int PERIOD_TICKS = 240;

    /** What to ask the sound manager for. A {@code volume} of 0 fades it out now. */
    public record Request(double x, double y, double z, float volume) {}

    private double earX;
    private double earY;
    private double earZ;
    private double nearest = Double.POSITIVE_INFINITY;
    private double x;
    private double y;
    private double z;
    private long now;
    private long cycleStart;
    private long lastAlarm = -PERIOD_TICKS - 1L;
    private boolean sounding;

    /**
     * An alarm says it is alarming, once per game tick. It is measured from where the listener last
     * was, a tick ago.
     *
     * @param ax where it is
     * @param ay where it is
     * @param az where it is
     */
    public void report(double ax, double ay, double az) {
        double dx = ax - earX;
        double dy = ay - earY;
        double dz = az - earZ;
        double distance = dx * dx + dy * dy + dz * dz;
        if (distance < nearest) {
            nearest = distance;
            x = ax;
            y = ay;
            z = az;
        }
    }

    /**
     * One game tick, after the alarms have reported.
     *
     * @param lx where the listener is
     * @param ly where the listener is
     * @param lz where the listener is
     * @return what to ask for this tick, or null for nothing. The last request of a sounding has
     *     volume 0, which fades it at once rather than when the request would have lapsed.
     */
    public @Nullable Request tick(double lx, double ly, double lz) {
        now++;
        boolean alarm = nearest < RANGE * RANGE;
        nearest = Double.POSITIVE_INFINITY;
        earX = lx;
        earY = ly;
        earZ = lz;
        if (alarm) {
            if (now - lastAlarm > PERIOD_TICKS) {
                cycleStart = now;
            }
            lastAlarm = now;
            if ((now - cycleStart) % PERIOD_TICKS < SOUND_TICKS) {
                sounding = true;
                return new Request(x, y, z, 1f);
            }
        }
        if (sounding) {
            sounding = false;
            return new Request(x, y, z, 0f);
        }
        return null;
    }
}
