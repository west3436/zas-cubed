package io.github.west3436.zas.atmos.compat.cc;

import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.peripheral.IPeripheral;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

/**
 * A device as a peripheral (COMPAT-020, COMPAT-021). CC: Tweaked asks for one each time a computer
 * or a modem looks at the block, so it holds nothing but the device: two for one device are equal.
 *
 * <p>Its Lua methods are the public methods a subclass marks. Each asks for {@link #device()} first,
 * so a call that reaches the server after the block was broken is an error, not a read of a dead
 * block entity.
 */
public abstract class DevicePeripheral<T extends BlockEntity> implements IPeripheral {
    private final String type;
    private final T device;

    DevicePeripheral(String type, T device) {
        this.type = type;
        this.device = device;
    }

    @Override
    public final String getType() {
        return type;
    }

    @Override
    public final Object getTarget() {
        return device;
    }

    @Override
    public final boolean equals(@Nullable IPeripheral other) {
        return other instanceof DevicePeripheral<?> peripheral && peripheral.device == device;
    }

    /** The device, whether or not it is still there: for {@code attach} and {@code detach}. */
    final T held() {
        return device;
    }

    /**
     * The device, for a call from a computer.
     *
     * @throws LuaException if its block is gone
     */
    final T device() throws LuaException {
        if (device.isRemoved()) {
            throw new LuaException("the device is gone");
        }
        return device;
    }
}
