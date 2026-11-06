package io.github.west3436.zas.atmos.canister;

import io.github.west3436.zas.api.atmosphere.AtmosphereSample;
import io.github.west3436.zas.api.atmosphere.Atmospheres;
import io.github.west3436.zas.api.gas.GasMixture;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.api.mutation.SourceId;
import io.github.west3436.zas.atmos.ZasAtmosMod;
import io.github.west3436.zas.atmos.device.ZonePort;
import io.github.west3436.zas.atmos.failure.CanisterFailure;
import io.github.west3436.zas.atmos.failure.ContainerFailure;
import io.github.west3436.zas.atmos.gas.GasStore;
import io.github.west3436.zas.atmos.gear.GasTankItem;
import io.github.west3436.zas.atmos.pipe.PipeBlockEntity;
import io.github.west3436.zas.atmos.pipe.PipeNodeEntity;
import io.github.west3436.zas.atmos.pipe.PipeVolumes;
import io.github.west3436.zas.atmos.port.ConnectorPortBlock;
import io.github.west3436.zas.atmos.registry.AtmosBlockEntities;
import io.github.west3436.zas.atmos.registry.AtmosComponents;
import io.github.west3436.zas.atmos.registry.AtmosSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * A canister's block entity (CONT-017, CONT-084): a 1,000 L network node, joined to a connector
 * port's network when it stands on one (CONT-083). With its valve open, it releases its network's
 * gas into its cell's zone, up to the release pressure. A gas tank held against it is filled the
 * same way, to the release pressure (CONT-039).
 *
 * <p>Mined with the right tool, it keeps its gas: the item carries it as {@link
 * AtmosComponents#GAS}, and placing the item puts it back. Broken in creative, its gas is
 * destroyed with it. Removed any other way, it releases its gas into its cell, as a pipe does
 * (CONT-009).
 *
 * <p>Too much pressure or heat, or an explosion, makes it rupture or explode ({@link
 * CanisterFailure}).
 */
public class CanisterBlockEntity extends PipeNodeEntity implements MenuProvider {
    /** The pressure a canister is rated for, kPa (90 atm); above it, it ruptures (CONT-101). */
    public static final double RATED_KPA = ContainerFailure.CANISTER_RATED_KPA;

    /** Who a canister's releases are made by. */
    public static final SourceId VALVE = SourceId.of(ZasAtmosMod.MOD_ID, "canister_valve");

    private static final String RELEASE_KEY = "Release";

    private final ZonePort port = new ZonePort(VALVE);
    private double releasePressure = CanisterRelease.DEFAULT_KPA;
    private boolean keepContents;
    private boolean voidContents;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case CanisterMenu.DATA_RELEASE -> (int) Math.round(releasePressure);
                case CanisterMenu.DATA_OPEN -> valveOpen() ? 1 : 0;
                case CanisterMenu.DATA_PRESSURE ->
                    clampShort(GasMixture.GAS_CONSTANT * gasNow().temperature() * gasNow().totalMoles() / nodeVolume());
                case CanisterMenu.DATA_MOLES -> clampShort(gasNow().totalMoles());
                case CanisterMenu.DATA_PORT ->
                    level != null && level.getBlockState(worldPosition.below()).getBlock() instanceof ConnectorPortBlock
                            ? 1
                            : 0;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {}

        @Override
        public int getCount() {
            return CanisterMenu.DATA_COUNT;
        }
    };

    public CanisterBlockEntity(BlockPos pos, BlockState state) {
        super(AtmosBlockEntities.CANISTER.get(), pos, state, PipeVolumes.CANISTER);
    }

    private static int clampShort(double v) {
        return (int) Math.max(0, Math.min(Short.MAX_VALUE, Math.round(v)));
    }

    /** The canister's own share of the gas, as its GUI shows it: of its network if it is in one. */
    private GasParcel gasNow() {
        return share();
    }

    /** Whether the valve is open: the block state's. */
    public boolean valveOpen() {
        BlockState state = getBlockState();
        return state.hasProperty(CanisterBlock.OPEN) && state.getValue(CanisterBlock.OPEN);
    }

    /** Opens or closes the valve, with its sound (CONT-126). */
    public void setValveOpen(boolean open) {
        if (level != null && valveOpen() != open) {
            level.setBlock(worldPosition, getBlockState().setValue(CanisterBlock.OPEN, open), Block.UPDATE_ALL);
            AtmosSounds.play(level, worldPosition, open ? AtmosSounds.CANISTER_OPEN : AtmosSounds.CANISTER_CLOSE);
        }
    }

    /** The release pressure, kPa. */
    public double releasePressure() {
        return releasePressure;
    }

    /** Sets the release pressure, clamped to 10–1,013 kPa (CONT-084). */
    public void setReleasePressure(double kPa) {
        releasePressure = CanisterRelease.clamp(kPa);
        setChanged();
    }

    /**
     * Fills a gas tank held against the canister (CONT-039): from its network, to the release
     * pressure as a release into a room is (CONT-084), but against the tank's volume. The gas mixes
     * into the tank conserving thermal energy.
     *
     * @param tank the tank's stack, changed in place
     * @return the moles moved, 0 if the tank is at or above the release pressure or the canister
     */
    public double fillTank(ItemStack tank) {
        if (!(tank.getItem() instanceof GasTankItem)) {
            return 0;
        }
        GasStore gas = network().gas();
        if (gas.isEmpty()) {
            return 0;
        }
        GasMixture in = GasTankItem.contents(tank);
        double n = CanisterRelease.moles(
                gas.totalMoles(),
                gas.volume(),
                in.totalMoles(),
                in.volume(),
                in.pressure(),
                gas.temperature(),
                releasePressure);
        if (n < PipeBlockEntity.MIN_FLOW) {
            return 0;
        }
        GasParcel out = gas.takeFraction(n / gas.totalMoles());
        GasTankItem.add(tank, out);
        setChanged();
        return out.totalMoles();
    }

    /** Called when the canister is about to drop as an item carrying its gas. */
    void keepContents() {
        keepContents = true;
    }

    /** Called when a creative player is about to break the canister: its gas is destroyed with it. */
    void voidContents() {
        voidContents = true;
    }

    @Override
    protected boolean keepsShareWhenRemoved() {
        return keepContents;
    }

    @Override
    protected boolean voidsShareWhenRemoved() {
        return voidContents;
    }

    @Override
    public boolean readyToStep() {
        return !port.busy();
    }

    @Override
    protected void atmosTick(ServerLevel level) {
        cellWatch().step();
        GasStore gas = network().gas();
        if (CanisterFailure.check(level, worldPosition, gas)) {
            return;
        }
        if (!valveOpen()) {
            reportStopped();
            return;
        }
        if (gas.isEmpty()) {
            return;
        }
        AtmosphereSample sample = Atmospheres.at(level, worldPosition);
        if (!cellWatch().trust(sample)) {
            return;
        }
        GasMixture zone = sample.mixture();
        double n = CanisterRelease.moles(
                gas.totalMoles(),
                gas.volume(),
                zone.totalMoles(),
                zone.volume(),
                zone.pressure(),
                gas.temperature(),
                releasePressure);
        if (n < PipeBlockEntity.MIN_FLOW) {
            return;
        }
        GasParcel out = gas.takeFraction(n / gas.totalMoles());
        reportMoved(out.totalMoles());
        port.add(
                level,
                Vec3.atCenterOf(worldPosition),
                out,
                r -> deliver(new GasParcel(ZonePort.untaken(out, r), out.temperature())));
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        GasParcel gas = share();
        if (!gas.isEmpty()) {
            components.set(AtmosComponents.GAS.get(), gas);
        }
    }

    @Override
    protected void applyImplicitComponents(DataComponentInput input) {
        super.applyImplicitComponents(input);
        GasParcel gas = input.get(AtmosComponents.GAS.get());
        if (gas != null) {
            stored().set(gas);
        }
    }

    @Override
    public void removeComponentsFromTag(CompoundTag tag) {
        super.removeComponentsFromTag(tag);
        tag.remove("Gas");
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putDouble(RELEASE_KEY, releasePressure);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains(RELEASE_KEY)) {
            releasePressure = CanisterRelease.clamp(tag.getDouble(RELEASE_KEY));
        }
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.zas_atmos.canister");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new CanisterMenu(id, data, ContainerLevelAccess.create(level, worldPosition), this, player);
    }
}
