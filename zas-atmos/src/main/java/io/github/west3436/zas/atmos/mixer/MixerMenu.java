package io.github.west3436.zas.atmos.mixer;

import io.github.west3436.zas.atmos.inline.SidePortMenu;
import io.github.west3436.zas.atmos.registry.AtmosMenus;
import net.minecraft.world.inventory.SimpleContainerData;

/**
 * A mixer's GUI (CONT-077, CONT-074): the base's, with its fractions and buttons to step them. The
 * mixer clamps them to 0–100 %.
 */
public final class MixerMenu extends SidePortMenu {
    /** The back input's largest step down; the smaller step down, the smaller up and the largest up follow. */
    public static final int MIX_DOWN_BIG = LAST_BUTTON + 1;

    /** Sets the back input's share back to a new mixer's. */
    public static final int MIX_DEFAULT = MIX_DOWN_BIG + 4;

    public static final int LAST_MIX_BUTTON = MIX_DEFAULT;

    /** The back input's share, in percent. */
    public static final int DATA_BACK_PERCENT = SidePortMenu.DATA_COUNT;

    public static final int DATA_COUNT = DATA_BACK_PERCENT + 1;

    private final MixerBlockEntity mixer;

    /** The client's menu. */
    public MixerMenu(int id) {
        super(AtmosMenus.MIXER.get(), id, new SimpleContainerData(DATA_COUNT), DATA_COUNT, null);
        this.mixer = null;
    }

    /** The server's menu, on a mixer. */
    public MixerMenu(int id, MixerBlockEntity mixer) {
        super(AtmosMenus.MIXER.get(), id, mixer.data(), DATA_COUNT, mixer);
        this.mixer = mixer;
    }

    @Override
    protected boolean deviceButton(int id) {
        if (id < MIX_DOWN_BIG || id > LAST_MIX_BUTTON) {
            return false;
        }
        if (id == MIX_DEFAULT) {
            mixer.setBackPercent(MixerBlockEntity.DEFAULT_BACK_PERCENT);
        } else {
            mixer.stepBackPercent(id - MIX_DOWN_BIG);
        }
        return true;
    }
}
