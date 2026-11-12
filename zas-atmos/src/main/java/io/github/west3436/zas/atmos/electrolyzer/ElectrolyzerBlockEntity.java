package io.github.west3436.zas.atmos.electrolyzer;

import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.api.gas.StandardAir;
import io.github.west3436.zas.atmos.config.AtmosServerConfig;
import io.github.west3436.zas.atmos.device.AtmosTicks;
import io.github.west3436.zas.atmos.inline.InlineDeviceEntity;
import io.github.west3436.zas.atmos.inline.InlineStatus;
import io.github.west3436.zas.atmos.pipe.PipeNetwork;
import io.github.west3436.zas.atmos.pipe.PipePort;
import io.github.west3436.zas.atmos.power.PowerPriority;
import io.github.west3436.zas.atmos.registry.AtmosBlockEntities;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The electrolyzer's block entity (CONT-055, CONT-056): while it is on and powered, and has water, it
 * splits 2 mol/s of water into 1 mol/s of O₂ through its left outlet and 2 mol/s of H₂ through its
 * right, at 20 °C. Each outlet is a port of its own, so the two gases never meet in it.
 *
 * <p>It is an area air controller's part in the last power group, gas production (CONT-064), drawing
 * 80 FE/t while it is on (CONT-065). With too little water left for a whole second it makes what the
 * water pays for, and then nothing. It makes nothing while an outlet's network is at {@link
 * ElectrolyzerMath#MAX_OUTPUT_KPA} or above. Its switch makes no sound, as no requirement gives it one.
 */
public class ElectrolyzerBlockEntity extends InlineDeviceEntity {
    /** The O₂ outlet's port: on the left, facing its front. */
    public static final int OXYGEN = 0;

    /** The H₂ outlet's port: on the right, facing its front. */
    public static final int HYDROGEN = 1;

    /** H₂'s id. */
    public static final ResourceLocation H2 = ResourceLocation.fromNamespaceAndPath("zas", "hydrogen");

    private static final String WATER = "Water";

    private final ElectrolyzerTank tank = new ElectrolyzerTank(this::waterChanged);

    public ElectrolyzerBlockEntity(BlockPos pos, BlockState state) {
        super(AtmosBlockEntities.ELECTROLYZER.get(), pos, state, 2, PowerPriority.AUXILIARY);
    }

    /** Its water tank, which the fluid capability on its back offers. */
    public ElectrolyzerTank tank() {
        return tank;
    }

    /** The O₂ outlet's port. */
    public PipePort oxygen() {
        return port(OXYGEN);
    }

    /** The H₂ outlet's port. */
    public PipePort hydrogen() {
        return port(HYDROGEN);
    }

    @Override
    protected InlineStatus work(ServerLevel level) {
        double share = ElectrolyzerMath.fraction(tank.water(), AtmosTicks.SECONDS);
        if (share <= 0) {
            return InlineStatus.NO_WATER;
        }
        PipeNetwork o2 = oxygen().network();
        PipeNetwork h2 = hydrogen().network();
        if (o2.gas().pressure() >= ElectrolyzerMath.MAX_OUTPUT_KPA
                || h2.gas().pressure() >= ElectrolyzerMath.MAX_OUTPUT_KPA) {
            return InlineStatus.OUTPUT_FULL;
        }
        double seconds = AtmosTicks.SECONDS * share;
        double oxygen = ElectrolyzerMath.O2_PER_SECOND * seconds;
        double hydrogen = ElectrolyzerMath.H2_PER_SECOND * seconds;
        o2.gas().add(new GasParcel(Map.of(StandardAir.OXYGEN, oxygen), StandardAir.TEMPERATURE));
        h2.gas().add(new GasParcel(Map.of(H2, hydrogen), StandardAir.TEMPERATURE));
        tank.use(ElectrolyzerMath.waterPerSecond() * seconds);
        reportMoved(oxygen + hydrogen);
        return InlineStatus.MOVING;
    }

    /** Its lamp and bubbles show only while it makes gas, not while it waits for water or room. */
    @Override
    protected boolean showsRunning(InlineStatus status) {
        return status == InlineStatus.MOVING;
    }

    @Override
    protected int drawWhenOn() {
        return AtmosServerConfig.get(AtmosServerConfig.ELECTROLYZER_DRAW);
    }

    /** None: no requirement gives the electrolyzer a sound (CONT-126, CONT-127). */
    @Override
    protected Supplier<SoundEvent> switchSound(boolean on) {
        return null;
    }

    /** Saves the water, and shows its level on the block. */
    private void waterChanged() {
        setChanged();
        if (level instanceof ServerLevel server && !isRemoved()) {
            BlockState state = getBlockState();
            int shown = ElectrolyzerMath.level(tank.water());
            if (state.hasProperty(ElectrolyzerBlock.WATER) && state.getValue(ElectrolyzerBlock.WATER) != shown) {
                server.setBlock(worldPosition, state.setValue(ElectrolyzerBlock.WATER, shown), Block.UPDATE_CLIENTS);
            }
        }
    }

    @Override
    protected int dataCount() {
        return ElectrolyzerMenu.DATA_COUNT;
    }

    @Override
    protected int dataValue(int index) {
        return index == ElectrolyzerMenu.DATA_WATER ? shownWater() : super.dataValue(index);
    }

    /** The water in its tank as its GUI shows it: mB, rounded up so that any water shows. */
    public int shownWater() {
        return (int) Math.ceil(tank.water() - ElectrolyzerMath.EMPTY_MB);
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new ElectrolyzerMenu(id, this);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putDouble(WATER, tank.water());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        tank.load(tag.getDouble(WATER));
    }
}
