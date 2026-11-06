package io.github.west3436.zas.atmos.inline;

import java.util.Map;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The item that places a {@link SidePortBlock} with its side port on the left (CONT-013, CONT-077). It
 * places the same block, whose own item stays the one that puts the port on the right.
 */
public class MirroredDeviceItem extends BlockItem {
    private final SidePortBlock device;

    public MirroredDeviceItem(SidePortBlock device, Properties properties) {
        super(device, properties);
        this.device = device;
    }

    @Override
    protected BlockState getPlacementState(BlockPlaceContext context) {
        BlockState state = device.mirroredStateForPlacement(context);
        return canPlace(context, state) ? state : null;
    }

    /** Leaves the block's own item as it is. */
    @Override
    public void registerBlocks(Map<Block, Item> blockToItemMap, Item item) {}

    /** Its own name, as a block item's would be its block's. */
    @Override
    public String getDescriptionId() {
        return getOrCreateDescriptionId();
    }
}
