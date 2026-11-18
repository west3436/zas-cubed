package io.github.west3436.zas.atmos.ttv;

import java.util.Locale;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.ItemStack;

/**
 * What opens a primed TTV's valve (CONT-104, CONT-184): its own timer, or the item fitted in the
 * timer's place. A TTV holds at most one fitted item; without one, its timer is its trigger.
 */
public enum TtvTrigger implements StringRepresentable {
    /** Its timer runs out (CONT-104). */
    TIMER,
    /** A remote signaler on its frequency and code is used (CONT-186). */
    SIGNALER,
    /** Something living comes within the proximity sensor's range (CONT-187). */
    PROXIMITY;

    /** The trigger an item is fitted as, or {@link #TIMER} for one that isn't a trigger, or none. */
    public static TtvTrigger of(ItemStack fitted) {
        if (fitted.getItem() instanceof RemoteSignalerItem) {
            return SIGNALER;
        }
        if (fitted.getItem() instanceof ProximitySensorItem) {
            return PROXIMITY;
        }
        return TIMER;
    }

    /** The trigger of a TTV stack: its fitted item's, or its timer. */
    public static TtvTrigger ofTtv(ItemStack ttv) {
        return of(TransferValveItem.trigger(ttv));
    }

    /** Whether an item can be fitted to a TTV in its timer's place: one signaler or proximity sensor. */
    public static boolean fits(ItemStack item) {
        return item.getCount() == 1 && of(item) != TIMER;
    }

    /** The trigger with an ordinal, or {@link #TIMER} for one out of range. */
    public static TtvTrigger of(int ordinal) {
        return ordinal >= 0 && ordinal < values().length ? values()[ordinal] : TIMER;
    }

    /** Whether starting it arms the TTV rather than starting a timer. */
    public boolean arms() {
        return this != TIMER;
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
