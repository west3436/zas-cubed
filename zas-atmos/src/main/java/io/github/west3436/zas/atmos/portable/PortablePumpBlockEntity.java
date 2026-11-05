package io.github.west3436.zas.atmos.portable;

import io.github.west3436.zas.api.gas.GasMixture;
import io.github.west3436.zas.api.mutation.SourceId;
import io.github.west3436.zas.atmos.ZasAtmosMod;
import io.github.west3436.zas.atmos.config.AtmosServerConfig;
import io.github.west3436.zas.atmos.pipe.PipeBlockEntity;
import io.github.west3436.zas.atmos.registry.AtmosBlockEntities;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A portable pump's block entity (CONT-018): each second it moves gas between its store and the air
 * it works on, a held tank or the zone it stands in ({@link PortablePumpMath}). Releasing, it fills the
 * air from the store to its target pressure; siphoning, it fills the store from the air to it. It
 * draws {@code [power] portable_pump} FE/t (20) from its battery only while it moves gas.
 */
public class PortablePumpBlockEntity extends PortableGasDevice {
    /** Who a portable pump's changes are made by. */
    public static final SourceId SOURCE = SourceId.of(ZasAtmosMod.MOD_ID, "portable_pump");

    private static final String SIPHON = "Siphon";
    private static final String TARGET = "Target";

    private boolean siphon;
    private double target = PortablePumpMath.DEFAULT_TARGET;

    public PortablePumpBlockEntity(BlockPos pos, BlockState state) {
        super(AtmosBlockEntities.PORTABLE_PUMP.get(), pos, state, SOURCE);
    }

    /** Whether it fills its store from the air, rather than the air from its store. */
    public boolean siphon() {
        return siphon;
    }

    /** Sets which way it pumps. */
    public void setSiphon(boolean siphon) {
        this.siphon = siphon;
        setChanged();
    }

    /** Its target pressure, kPa: the air's when it releases, its store's when it siphons. */
    public double target() {
        return target;
    }

    /** Sets its target pressure, clamped to 0–4,500 kPa (CONT-011). */
    public void setTarget(double kPa) {
        target = PortablePumpMath.clampTarget(kPa);
        setChanged();
    }

    @Override
    protected Move plan(GasMixture air) {
        GasMixture store = store().mixture();
        return siphon ? siphon(air, store) : release(air, store);
    }

    private Move release(GasMixture air, GasMixture store) {
        if (store.totalMoles() < PipeBlockEntity.MIN_FLOW) {
            return Move.idle(PortableStatus.NOTHING_TO_MOVE);
        }
        double moles = PortablePumpMath.release(target, store, air);
        return moles < PipeBlockEntity.MIN_FLOW ? Move.idle(PortableStatus.AT_TARGET) : Move.give(moles);
    }

    private Move siphon(GasMixture air, GasMixture store) {
        if (air.totalMoles() < PipeBlockEntity.MIN_FLOW) {
            return Move.idle(PortableStatus.NOTHING_TO_MOVE);
        }
        double moles = PortablePumpMath.siphon(target, air, store);
        if (moles < PipeBlockEntity.MIN_FLOW) {
            return Move.idle(PortableStatus.AT_TARGET);
        }
        double share = moles / air.totalMoles();
        Map<ResourceLocation, Double> take = new TreeMap<>(air.moles());
        take.replaceAll((gas, n) -> n * share);
        return Move.take(take);
    }

    @Override
    protected int drawPerTick() {
        return AtmosServerConfig.get(AtmosServerConfig.PORTABLE_PUMP_DRAW);
    }

    @Override
    protected int menuValue(int index) {
        return switch (index) {
            case PortablePumpMenu.DATA_SIPHON -> siphon ? 1 : 0;
            case PortablePumpMenu.DATA_TARGET -> (int) Math.round(target);
            default -> super.menuValue(index);
        };
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean(SIPHON, siphon);
        tag.putDouble(TARGET, target);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        siphon = tag.getBoolean(SIPHON);
        if (tag.contains(TARGET)) {
            target = PortablePumpMath.clampTarget(tag.getDouble(TARGET));
        }
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new PortablePumpMenu(
                id,
                inventory,
                battery(),
                tank(),
                menuData(PortablePumpMenu.DATA_COUNT),
                ContainerLevelAccess.create(level, worldPosition),
                this);
    }
}
