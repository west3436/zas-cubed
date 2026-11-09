package io.github.west3436.zas.atmos.client;

import io.github.west3436.zas.atmos.firelock.FirelockDoorBlock;
import io.github.west3436.zas.atmos.firelock.FirelockDoorBlockEntity;
import io.github.west3436.zas.atmos.registry.AtmosBlocks;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.jetbrains.annotations.Nullable;

/**
 * The model of a door with a firelock (CONT-137): the door it was, from its block entity's model data
 * ({@link FirelockDoorBlockEntity#DOOR}), with the firelock overlay from the block's own blockstate
 * over it. The overlay's open frame has see-through middles, so it is drawn cutout.
 *
 * <p>A renderer that brings no model data, as Ponder's doesn't, gets the door from the block entity
 * through {@link #getModelData}. Until a door is known, before the block entity's data arrives or
 * with no block entity at all, an iron door is drawn, as the block entity's own default is.
 */
final class FirelockDoorModel extends BakedModelWrapper<BakedModel> {
    private static final ChunkRenderTypeSet OVERLAY = ChunkRenderTypeSet.of(RenderType.cutout());

    private FirelockDoorModel(BakedModel overlay) {
        super(overlay);
    }

    /** Wraps the baked model of each of the firelock door's states. */
    static void wrap(ModelEvent.ModifyBakingResult event) {
        for (BlockState state :
                AtmosBlocks.FIRELOCK_DOOR.get().getStateDefinition().getPossibleStates()) {
            event.getModels()
                    .computeIfPresent(BlockModelShaper.stateToModelLocation(state), (k, m) -> new FirelockDoorModel(m));
        }
    }

    /** The door under the firelock: the model data's, or an iron door until it's known. */
    private static Block door(ModelData data) {
        Block door = data.get(FirelockDoorBlockEntity.DOOR);
        return door == null ? Blocks.IRON_DOOR : door;
    }

    private static @Nullable BlockState door(@Nullable BlockState state, ModelData data) {
        return state == null ? null : FirelockDoorBlock.doorState(state, door(data));
    }

    private static BakedModel model(BlockState door) {
        return Minecraft.getInstance().getBlockRenderer().getBlockModel(door);
    }

    /** Fills in the door from the block entity when the renderer brought no model data with it. */
    @Override
    public ModelData getModelData(BlockAndTintGetter level, BlockPos pos, BlockState state, ModelData data) {
        if (data.get(FirelockDoorBlockEntity.DOOR) == null
                && level.getBlockEntity(pos) instanceof FirelockDoorBlockEntity be) {
            data = data.derive().with(FirelockDoorBlockEntity.DOOR, be.door()).build();
        }
        return super.getModelData(level, pos, state, data);
    }

    @Override
    public List<BakedQuad> getQuads(
            @Nullable BlockState state,
            @Nullable Direction side,
            RandomSource rand,
            ModelData data,
            @Nullable RenderType renderType) {
        List<BakedQuad> out = new ArrayList<>();
        if (renderType == null || renderType == RenderType.cutout()) {
            out.addAll(originalModel.getQuads(state, side, rand, data, null));
        }
        BlockState door = door(state, data);
        if (door != null) {
            BakedModel m = model(door);
            if (renderType == null
                    || m.getRenderTypes(door, rand, ModelData.EMPTY).contains(renderType)) {
                out.addAll(m.getQuads(door, side, rand, ModelData.EMPTY, renderType));
            }
        }
        return out;
    }

    @Override
    public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource rand, ModelData data) {
        BlockState door = door(state, data);
        return door == null
                ? OVERLAY
                : ChunkRenderTypeSet.union(OVERLAY, model(door).getRenderTypes(door, rand, ModelData.EMPTY));
    }

    @Override
    public TextureAtlasSprite getParticleIcon(ModelData data) {
        return model(door(data).defaultBlockState()).getParticleIcon(ModelData.EMPTY);
    }
}
