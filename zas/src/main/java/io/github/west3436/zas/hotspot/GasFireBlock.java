package io.github.west3436.zas.hotspot;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * {@code zas:gas_fire}: a hotspot burning in gas, where there is no vanilla fire block (FIRE-018).
 * It has no collision and no outline, so it never blocks movement or a click on what is behind it;
 * it gives full light, and damages and sets alight an entity inside it as vanilla fire does
 * (FIRE-013). It needs no support, so it burns in mid-air.
 *
 * <p>Only the engine places and removes it (architecture §5, {@link FireIntents}): a gas fire left
 * outside a zone is removed on the next atmos tick, and one whose gas won't burn goes out. It has no
 * item and drops nothing. It is in {@code #minecraft:fire}, so mobs path around it and an entity
 * leaving it keeps burning as it would leaving vanilla fire. Adjacent gas fires hide the faces
 * between them, so a room on fire shows no grid.
 */
public final class GasFireBlock extends Block {
    public static final MapCodec<GasFireBlock> CODEC = simpleCodec(GasFireBlock::new);

    /** Vanilla fire's damage per hit, as {@code Blocks.FIRE}'s. */
    static final float DAMAGE = 1.0F;

    public GasFireBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<GasFireBlock> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    protected boolean skipRendering(BlockState state, BlockState adjacent, Direction direction) {
        return adjacent.is(this) || super.skipRendering(state, adjacent, direction);
    }

    /** As {@code BaseFireBlock#entityInside}: sets the entity alight, then hurts it as fire does. */
    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (!entity.fireImmune()) {
            if (entity.getRemainingFireTicks() < 0) {
                entity.setRemainingFireTicks(entity.getRemainingFireTicks() + 1);
            } else if (entity instanceof ServerPlayer) {
                entity.setRemainingFireTicks(
                        entity.getRemainingFireTicks() + level.getRandom().nextInt(1, 3));
            }
            if (entity.getRemainingFireTicks() >= 0) {
                entity.igniteForSeconds(8.0F);
            }
        }
        entity.hurt(level.damageSources().inFire(), DAMAGE);
        super.entityInside(state, level, pos, entity);
    }

    /** Now and then a wisp of smoke. Its sound is a loop for the whole fire: {@code client.GasFireLoops}. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(8) == 0 && !level.getBlockState(pos.above()).is(this)) {
            level.addParticle(
                    ParticleTypes.SMOKE,
                    pos.getX() + random.nextDouble(),
                    pos.getY() + 1.0,
                    pos.getZ() + random.nextDouble(),
                    0,
                    0.02,
                    0);
        }
    }
}
