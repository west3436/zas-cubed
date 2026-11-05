package io.github.west3436.zas.atmos.filter;

import io.github.west3436.zas.atmos.config.AtmosServerConfig;
import io.github.west3436.zas.atmos.device.AtmosTicks;
import io.github.west3436.zas.atmos.gas.GasStore;
import io.github.west3436.zas.atmos.inline.InlineStatus;
import io.github.west3436.zas.atmos.inline.SidePortEntity;
import io.github.west3436.zas.atmos.pipe.PipeBlockEntity;
import io.github.west3436.zas.atmos.pipe.PipeNetwork;
import io.github.west3436.zas.atmos.registry.AtmosBlockEntities;
import io.github.west3436.zas.atmos.scrubber.GasSelection;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A filter's block entity (CONT-013): gas comes in at the back. The gases it selects, by name or by
 * gas tag, leave by the side outlet and the rest by the front, up to its target, at most 100 L/s at
 * the input's conditions. New, it is off and selects what a scrubber does: CO₂ and {@code
 * #zas:toxic}.
 */
public class FilterBlockEntity extends SidePortEntity {
    private static final String SELECTION = "Selection";

    private GasSelection selection = GasSelection.DEFAULT;

    public FilterBlockEntity(BlockPos pos, BlockState state) {
        super(AtmosBlockEntities.FILTER.get(), pos, state);
    }

    /** What it selects. */
    public GasSelection selection() {
        return selection;
    }

    /** Sets what it selects. Server side. */
    public void setSelection(GasSelection selection) {
        if (!selection.equals(this.selection)) {
            this.selection = selection;
            setChanged();
        }
    }

    /** What its GUI offers now: see {@link FilterOption#offered}. Server side. */
    public List<FilterOption> offered() {
        return FilterOption.offered(
                selection, port(BACK).network().gas().moles().keySet());
    }

    @Override
    protected InlineStatus work(ServerLevel level) {
        PipeNetwork[] networks = networks();
        if (oneNetwork(networks)) {
            return InlineStatus.SAME_NETWORK;
        }
        GasStore in = networks[BACK].gas();
        GasStore out = networks[FRONT].gas();
        GasStore side = networks[SIDE].gas();
        double n = FilterMath.moles(in, out, side, selection, target(), AtmosTicks.SECONDS);
        if (n < PipeBlockEntity.MIN_FLOW) {
            if (runsDry(in)) {
                return InlineStatus.INPUT_EMPTY;
            }
            return side.pressure() >= out.pressure() ? InlineStatus.SIDE_TARGET_REACHED : InlineStatus.TARGET_REACHED;
        }
        FilterMath.move(n, in, out, side, selection);
        return InlineStatus.MOVING;
    }

    @Override
    protected int drawWhenOn() {
        return AtmosServerConfig.get(AtmosServerConfig.FILTER_DRAW);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put(SELECTION, selection.save());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        selection = GasSelection.load(tag.getCompound(SELECTION));
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new FilterMenu(id, this);
    }
}
