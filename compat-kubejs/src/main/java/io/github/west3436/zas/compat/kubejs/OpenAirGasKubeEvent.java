package io.github.west3436.zas.compat.kubejs;

import dev.latvian.mods.kubejs.level.KubeLevelEvent;
import io.github.west3436.zas.api.event.OpenAirGasEvent;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.api.mutation.SourceId;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * Gas went to the open air or came from it (COMPAT-050): the data of the API's {@link
 * OpenAirGasEvent}, as the properties {@code level}, {@code server}, {@code pos}, {@code added},
 * {@code gas} and {@code source}.
 */
public final class OpenAirGasKubeEvent implements KubeLevelEvent {
    private final OpenAirGasEvent event;

    OpenAirGasKubeEvent(OpenAirGasEvent event) {
        this.event = event;
    }

    /** The level the gas moved in. */
    @Override
    public ServerLevel getLevel() {
        return event.level();
    }

    /** The block position of the mutation. */
    public BlockPos getPos() {
        return event.pos();
    }

    /** True for gas added to the open air, false for gas taken from it. */
    public boolean isAdded() {
        return event.added();
    }

    /** The gas that went to the ambient, or came from it: its moles by gas id, and its temperature. */
    public GasParcel getGas() {
        return event.gas();
    }

    /** Who made the mutation. */
    public SourceId getSource() {
        return event.source();
    }
}
