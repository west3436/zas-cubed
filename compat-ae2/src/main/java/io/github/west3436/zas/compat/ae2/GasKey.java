package io.github.west3436.zas.compat.ae2;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import com.mojang.serialization.MapCodec;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.api.gas.GasProperties;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * One gas as a storable type of AE2's (COMPAT-070): ME storage holds each gas under its own key, so
 * a mixture going in is held as its gases (COMPAT-071). A key is the gas's id and nothing else: gas
 * in ME storage has no temperature or pressure, only an amount, in {@link GasUnits}.
 *
 * <p>A key may name a gas the server no longer defines, as a filter saved before a data pack was
 * removed does. It still loads, so the filter isn't lost, but no gas of it goes into a block.
 */
public final class GasKey extends AEKey {
    /** How a key is saved: the gas's id. */
    public static final MapCodec<GasKey> MAP_CODEC =
            ResourceLocation.CODEC.fieldOf("id").xmap(GasKey::of, GasKey::gas);

    private final ResourceLocation gas;

    private GasKey(ResourceLocation gas) {
        this.gas = Objects.requireNonNull(gas, "gas");
    }

    /** The key of a gas. */
    public static GasKey of(ResourceLocation gas) {
        return new GasKey(gas);
    }

    /** The gas's id. */
    public ResourceLocation gas() {
        return gas;
    }

    @Override
    public AEKeyType getType() {
        return GasKeyType.TYPE;
    }

    /** A gas has nothing secondary to drop. */
    @Override
    public AEKey dropSecondary() {
        return this;
    }

    @Override
    public CompoundTag toTag(HolderLookup.Provider registries) {
        return (CompoundTag)
                MAP_CODEC.codec().encodeStart(NbtOps.INSTANCE, this).getOrThrow();
    }

    @Override
    public Object getPrimaryKey() {
        return gas;
    }

    @Override
    public ResourceLocation getId() {
        return gas;
    }

    @Override
    public void writeToPacket(RegistryFriendlyByteBuf data) {
        data.writeResourceLocation(gas);
    }

    /** The key a {@link #writeToPacket} wrote. */
    static GasKey fromPacket(RegistryFriendlyByteBuf data) {
        return of(data.readResourceLocation());
    }

    @Override
    protected Component computeDisplayName() {
        return GasProperties.name(gas);
    }

    /**
     * Gas can't be an item, so gas held in a block that is broken, such as an ME interface with gas
     * in a slot, is let out where the block was, at the temperature gas leaves ME storage at.
     */
    @Override
    public void addDrops(long amount, List<ItemStack> drops, Level level, BlockPos pos) {
        if (amount > 0 && level instanceof ServerLevel server && GasProperties.isDefined(gas)) {
            GasRelease.release(
                    server,
                    pos,
                    new GasParcel(Map.of(gas, GasUnits.moles(amount)), Ae2Settings.leavingTemperature()),
                    "a broken block's gas");
        }
    }

    @Override
    public boolean hasComponents() {
        return false;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof GasKey other && other.gas.equals(gas);
    }

    @Override
    public int hashCode() {
        return gas.hashCode();
    }

    @Override
    public String toString() {
        return "gas " + gas;
    }
}
