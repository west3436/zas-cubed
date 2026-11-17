package io.github.west3436.zas.compat.ae2;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import com.mojang.serialization.MapCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * Gases as a type of stack ME storage holds (COMPAT-070), next to AE2's items and fluids. Amounts are
 * millimoles ({@link GasUnits}), shown as moles.
 *
 * <p>A bus moves {@link #PER_OPERATION} a step for each operation it has, as it moves 125 mB of a
 * fluid. At the Mekanism adapter's default of 1 L of gas to 1 mB, 5 mol is about 120 mB, so gas
 * moves through AE2 at much the rate a fluid does.
 */
public final class GasKeyType extends AEKeyType {
    /** The type's id. */
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("zas", "gas");

    /** The units a bus moves for one operation: 5 mol. */
    public static final int PER_OPERATION = 5 * GasUnits.PER_MOLE;

    /** The units in one byte of a storage cell's room: 64 operations' worth, as for fluids. */
    public static final int PER_BYTE = 64 * PER_OPERATION;

    /** The one instance, which {@link Ae2Compat} registers. */
    public static final GasKeyType TYPE = new GasKeyType();

    private GasKeyType() {
        super(ID, GasKey.class, Component.translatable("zas.ae2.gases"));
    }

    @Override
    public MapCodec<? extends AEKey> codec() {
        return GasKey.MAP_CODEC;
    }

    @Override
    public AEKey readFromPacket(RegistryFriendlyByteBuf input) {
        return GasKey.fromPacket(input);
    }

    @Override
    public int getAmountPerOperation() {
        return PER_OPERATION;
    }

    @Override
    public int getAmountPerByte() {
        return PER_BYTE;
    }

    @Override
    public int getAmountPerUnit() {
        return GasUnits.PER_MOLE;
    }

    @Override
    public String getUnitSymbol() {
        return "mol";
    }
}
