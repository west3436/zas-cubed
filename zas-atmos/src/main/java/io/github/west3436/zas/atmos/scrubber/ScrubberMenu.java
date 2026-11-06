package io.github.west3436.zas.atmos.scrubber;

import io.github.west3436.zas.api.gas.GasTag;
import io.github.west3436.zas.api.gas.GasTags;
import io.github.west3436.zas.api.gas.StandardAir;
import io.github.west3436.zas.atmos.controller.ControllerPreset;
import io.github.west3436.zas.atmos.registry.AtmosBlocks;
import io.github.west3436.zas.atmos.registry.AtmosMenus;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;

/**
 * A scrubber's GUI (CONT-074, CONT-015): its mode and selection, its network's pressure, what it did
 * last atmos tick, whether it has power, and the area air controller that controls it, if one does.
 * The client sends only button ids. The server checks each id, and refuses every button while a
 * controller controls the scrubber.
 *
 * <p>The selection's toggles are the gases and gas tags ZAS³ ships. A datapack's gas is selected
 * through a tag it joins, and an order or saved selection may name others: a toggle changes only its
 * own gas or tag.
 */
public class ScrubberMenu extends AbstractContainerMenu {
    /** Sets the mode, by {@link ScrubberMode} ordinal (ids 0–3). */
    public static final int SET_MODE = 0;

    /** Toggles the selection's option 0; the other options follow. */
    public static final int FIRST_OPTION = 4;

    /** The gases the GUI toggles, the default's first. */
    public static final List<ResourceLocation> GASES = List.of(
            ControllerPreset.CO2,
            ControllerPreset.CO,
            ControllerPreset.N2O,
            StandardAir.OXYGEN,
            StandardAir.NITROGEN,
            ControllerPreset.H2,
            ControllerPreset.CH4,
            ResourceLocation.fromNamespaceAndPath("zas", "water_vapor"));

    /** The gas tags the GUI toggles, after the gases. */
    public static final List<GasTag> TAGS = List.of(GasTags.TOXIC, GasTags.FLAMMABLE, GasTags.OXIDIZER);

    /** How many toggles there are. */
    public static final int OPTIONS = GASES.size() + TAGS.size();

    public static final int LAST_BUTTON = FIRST_OPTION + OPTIONS - 1;

    /**
     * The synced values: mode, selection (a bit per option), power, and control: 0 if no controller
     * controls the scrubber, 1 if one at the synced position does, 2 if one at an unknown position
     * does. Menu data syncs as shorts, so x and z are sent in two halves.
     */
    public static final int DATA_MODE = 0;

    public static final int DATA_SELECTION = 1;
    public static final int DATA_POWERED = 2;
    public static final int DATA_CONTROLLED = 3;
    public static final int DATA_X_LOW = 4;
    public static final int DATA_X_HIGH = 5;
    public static final int DATA_Y = 6;
    public static final int DATA_Z_LOW = 7;
    public static final int DATA_Z_HIGH = 8;
    /** The network's pressure, kPa, rounded and capped at 32,767. */
    public static final int DATA_NETWORK = 9;
    /** The {@link ScrubberStatus}'s ordinal. */
    public static final int DATA_STATUS = 10;

    public static final int DATA_COUNT = 11;

    private final ContainerData data;
    private final ContainerLevelAccess access;
    private final ScrubberBlockEntity scrubber;

    /** The client's menu. */
    public ScrubberMenu(int id, Inventory inventory) {
        this(id, new SimpleContainerData(DATA_COUNT), ContainerLevelAccess.NULL, null);
    }

    /** The server's menu, on a scrubber. */
    public ScrubberMenu(int id, ContainerData data, ContainerLevelAccess access, ScrubberBlockEntity scrubber) {
        super(AtmosMenus.SCRUBBER.get(), id);
        checkContainerDataCount(data, DATA_COUNT);
        this.data = data;
        this.access = access;
        this.scrubber = scrubber;
        addDataSlots(data);
    }

    /** The bits of the options a selection holds. */
    static int mask(GasSelection selection) {
        int mask = 0;
        for (int i = 0; i < GASES.size(); i++) {
            if (selection.gases().contains(GASES.get(i))) {
                mask |= 1 << i;
            }
        }
        for (int i = 0; i < TAGS.size(); i++) {
            if (selection.tags().contains(TAGS.get(i))) {
                mask |= 1 << (GASES.size() + i);
            }
        }
        return mask;
    }

    /** A synced value, by its {@code DATA_} index. */
    public int value(int index) {
        return data.get(index);
    }

    /** The synced mode. */
    public ScrubberMode mode() {
        return ScrubberMode.of(data.get(DATA_MODE));
    }

    /** The synced status. */
    public ScrubberStatus status() {
        return ScrubberStatus.of(data.get(DATA_STATUS));
    }

    /** Whether an option is selected, as synced. */
    public boolean selected(int option) {
        return (data.get(DATA_SELECTION) >> option & 1) != 0;
    }

    /** Whether a controller controls the scrubber, as synced. */
    public boolean controlled() {
        return data.get(DATA_CONTROLLED) != 0;
    }

    /** The synced position of the controller that controls the scrubber, if one does and it is known. */
    public Optional<BlockPos> controller() {
        if (data.get(DATA_CONTROLLED) != 1) {
            return Optional.empty();
        }
        return Optional.of(new BlockPos(
                join(data.get(DATA_X_LOW), data.get(DATA_X_HIGH)),
                (short) data.get(DATA_Y),
                join(data.get(DATA_Z_LOW), data.get(DATA_Z_HIGH))));
    }

    private static int join(int low, int high) {
        return (high << 16) | (low & 0xFFFF);
    }

    /**
     * Applies a button: the server's half of the GUI. Unknown ids are refused, and so is every
     * button while a controller controls the scrubber.
     */
    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (scrubber == null || scrubber.isRemoved() || id < SET_MODE || id > LAST_BUTTON) {
            return false;
        }
        if (id < FIRST_OPTION) {
            return scrubber.setByHand(ScrubberMode.values()[id - SET_MODE], scrubber.selection());
        }
        int option = id - FIRST_OPTION;
        GasSelection s = scrubber.selection();
        return scrubber.setByHand(
                scrubber.mode(),
                option < GASES.size() ? s.toggled(GASES.get(option)) : s.toggled(TAGS.get(option - GASES.size())));
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, AtmosBlocks.SCRUBBER.get());
    }
}
