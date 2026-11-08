package io.github.west3436.zas.s10;

/** What the bench's mixins call: {@link Clock} for the code they wrap. Server thread or not. */
public final class BenchHooks {
    private BenchHooks() {}

    /** Before a block entity ticks; {@link Clock#OFF} unless it is one of the mods'. */
    public static long enterBlockEntity(Object be) {
        return Clock.blockEntity(be) == null ? Clock.OFF : Clock.enter();
    }

    /** After a block entity ticked. */
    public static void exitBlockEntity(Object be, long started) {
        if (started != Clock.OFF) {
            Clock.exit(Clock.blockEntity(be), started);
        }
    }

    /** Before the M1 change hook. */
    public static long enterBlockWrite() {
        return Clock.enter();
    }

    /** After the M1 change hook. */
    public static void exitBlockWrite(long started) {
        Clock.exit(Clock.CHANGE_HOOK, started);
    }

    /** Before a device steps. */
    public static long enterDevice() {
        return Clock.enterDevice();
    }

    /** After a device stepped. */
    public static void exitDevice(Object device, long started) {
        Clock.exitDevice(Clock.device(device), started);
    }
}
