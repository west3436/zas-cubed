package io.github.west3436.zas.example;

import io.github.west3436.zas.api.capability.IGasHandler;
import io.github.west3436.zas.api.gas.GasMixture;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.api.gas.GasProperties;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A block that stores gas, which others can fill and drain (API-021). ZAS³'s content mod's pipes and
 * devices connect to it (API-025) and call it on the server thread (API-026). It can trust neither to
 * ask for what it has, so it answers with what it really took or gave: that's what keeps gas conserved.
 */
public final class GasTankBlockEntity extends BlockEntity implements IGasHandler {
    /** Its volume, in L, from which others work out its pressure. */
    public static final double VOLUME = 1000;

    /** The most it holds, in moles: about 10 atm at room temperature. */
    public static final double CAPACITY_MOLES = 400;

    private GasParcel gas = GasParcel.EMPTY;

    public GasTankBlockEntity(BlockPos pos, BlockState state) {
        super(ExampleContent.GAS_TANK_ENTITY.get(), pos, state);
    }

    @Override
    public GasMixture contents() {
        return new GasMixture(gas.moles(), gas.temperature(), VOLUME);
    }

    @Override
    public GasParcel fill(GasParcel offered, Action action) {
        double room = Math.max(0, CAPACITY_MOLES - gas.totalMoles());
        double share = offered.isEmpty() ? 0 : Math.min(1, room / offered.totalMoles());
        Map<ResourceLocation, Double> taken = new TreeMap<>();
        offered.moles().forEach((id, moles) -> taken.put(id, moles * share));
        GasParcel took = new GasParcel(taken, offered.temperature());
        if (action == Action.EXECUTE && !took.isEmpty()) {
            // Mixed as ZAS³ mixes gas, conserving thermal energy.
            gas = GasProperties.mix(gas, took);
            setChanged();
            if (level != null) {
                level.playSound(null, worldPosition, ExampleContent.TANK_HISS.get(), SoundSource.BLOCKS, 0.5f, 1.0f);
            }
        }
        return took;
    }

    @Override
    public GasParcel drain(Map<ResourceLocation, Double> moles, Action action) {
        Map<ResourceLocation, Double> given = new TreeMap<>();
        Map<ResourceLocation, Double> left = new TreeMap<>(gas.moles());
        moles.forEach((id, wanted) -> {
            double give = Math.min(wanted, gas.moles(id));
            given.put(id, give);
            left.merge(id, -give, Double::sum);
        });
        GasParcel gave = new GasParcel(given, gas.temperature());
        if (gave.isEmpty()) {
            return GasParcel.EMPTY;
        }
        if (action == Action.EXECUTE) {
            gas = new GasParcel(left, gas.temperature());
            setChanged();
        }
        return gave;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        CompoundTag moles = new CompoundTag();
        gas.moles().forEach((id, n) -> moles.putDouble(id.toString(), n));
        tag.put("moles", moles);
        tag.putDouble("temperature", gas.temperature());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        CompoundTag saved = tag.getCompound("moles");
        Map<ResourceLocation, Double> moles = new TreeMap<>();
        for (String id : saved.getAllKeys()) {
            moles.put(ResourceLocation.parse(id), saved.getDouble(id));
        }
        gas = new GasParcel(moles, tag.getDouble("temperature"));
    }
}
