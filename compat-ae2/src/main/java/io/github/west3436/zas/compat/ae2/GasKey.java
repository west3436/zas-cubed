package io.github.west3436.zas.compat.ae2;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import com.google.common.collect.Interner;
import com.google.common.collect.Interners;
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
 * <p><b>One key for each gas.</b> AE2 files keys by their primary key and compares those by
 * identity, as it does a fluid's {@code Fluid}: a filter matches stored gas only if both give the
 * same object. Keys come from saved data, packets, windows and EMI, each with its own id instance,
 * so {@link #of} hands out one key for each gas id, and that key is its own primary key.
 *
 * <p>A key may name a gas the server no longer defines, as a filter saved before a data pack was
 * removed does. It still loads, so the filter isn't lost, but no gas of it goes into a block.
 */
public final class GasKey extends AEKey {
    /** How a key is saved: the gas's id. */
    public static final MapCodec<GasKey> MAP_CODEC =
            ResourceLocation.CODEC.fieldOf("id").xmap(GasKey::of, GasKey::gas);

    /**
     * The keys in use, one for each gas id. Held weakly: a key nothing holds any more goes, so ids a
     * client makes up don't pile up, and any key still held is the one {@link #of} returns.
     */
    private static final Interner<GasKey> KEYS = Interners.newWeakInterner();

    private final ResourceLocation gas;

    private GasKey(ResourceLocation gas) {
        this.gas = Objects.requireNonNull(gas, "gas");
    }

    /** The key of a gas: the same object for the same id, however the id was made. */
    public static GasKey of(ResourceLocation gas) {
        return KEYS.intern(new GasKey(gas));
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

    /** The key itself, which {@link #of} makes the one key of its gas. */
    @Override
    public Object getPrimaryKey() {
        return this;
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
