package io.github.west3436.zas.atmos.tool;

import io.github.west3436.zas.atmos.registry.AtmosComponents;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The fire extinguisher (CONT-037). Held use sprays: every {@link #SPRAY_INTERVAL} ticks it puts out
 * the gas fires and vanilla fire blocks in a short spray in front of the user, and uses a charge. It
 * holds {@link #CHARGES} charges in its {@code zas_atmos:charges} component, shown on its bar, and
 * refills when used on a water source block. The server works out the spray from the user's eye and
 * look direction; the client only starts and stops using it. It doesn't put out burning entities or
 * change the gas.
 */
public class FireExtinguisherItem extends Item {
    /** The charges a full extinguisher holds. */
    public static final int CHARGES = 100;

    /** Ticks between sprays while the use is held: 5 a second, so a full one sprays for 20 s. */
    public static final int SPRAY_INTERVAL = 4;

    /** How far the spray reaches from the user's eye, in blocks, unless a block stops it first. */
    public static final double REACH = 5.0;

    /** Within this distance of the eye the spray is one block wide, and beyond it three. */
    public static final double NARROW = 2.0;

    /** How far apart along the spray's line it samples cells, in blocks. */
    private static final double STEP = 0.5;

    /** The bar's colour: vanilla's water blue. */
    private static final int BAR = 0x3F76E4;

    /** As long as a use can be held, as a bow's. */
    private static final int USE_DURATION = 72_000;

    public FireExtinguisherItem(Properties properties) {
        super(properties.stacksTo(1).component(AtmosComponents.CHARGES.get(), CHARGES));
    }

    /** The charges a stack has left, 0 to {@link #CHARGES}. */
    public static int charges(ItemStack stack) {
        return Mth.clamp(stack.getOrDefault(AtmosComponents.CHARGES.get(), 0), 0, CHARGES);
    }

    /** Sets the charges a stack has left, clamped to 0 to {@link #CHARGES}. */
    public static void setCharges(ItemStack stack, int charges) {
        stack.set(AtmosComponents.CHARGES.get(), Mth.clamp(charges, 0, CHARGES));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        BlockHitResult aimed = getPlayerPOVHitResult(level, player, ClipContext.Fluid.SOURCE_ONLY);
        if (charges(stack) < CHARGES && aimed.getType() == HitResult.Type.BLOCK) {
            BlockPos pos = aimed.getBlockPos();
            FluidState fluid = level.getFluidState(pos);
            if (fluid.is(FluidTags.WATER) && fluid.isSource() && level.mayInteract(player, pos)) {
                if (!level.isClientSide) {
                    setCharges(stack, CHARGES);
                    level.playSound(null, pos, SoundEvents.BUCKET_FILL, SoundSource.PLAYERS, 1.0f, 1.0f);
                }
                return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
            }
        }
        if (charges(stack) == 0) {
            if (!level.isClientSide) {
                empty(level, player);
            }
            return InteractionResultHolder.fail(stack);
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void onUseTick(Level level, LivingEntity user, ItemStack stack, int remaining) {
        if (!(level instanceof ServerLevel server) || (USE_DURATION - remaining) % SPRAY_INTERVAL != 0) {
            return;
        }
        int left = charges(stack);
        if (left == 0) {
            user.stopUsingItem();
            empty(level, user);
            return;
        }
        setCharges(stack, left - 1);
        spray(server, user);
    }

    /**
     * One spray from {@code user}'s eye along its look: puts out every gas fire and vanilla fire block
     * in it and plays the sounds. The spray is a line of boxes along the look, as far as {@link
     * #REACH} or the first block with collision it meets: each box is the cell at that point, and the
     * cells around it, three wide, beyond {@link #NARROW}. Where the line meets a block, the cells
     * around the one in front of the face it meets are in the spray too, so a fire on a floor or
     * wall aimed at goes out.
     *
     * @return how many fires it put out
     */
    public static int spray(ServerLevel level, LivingEntity user) {
        Vec3 eye = user.getEyePosition();
        Vec3 look = user.getLookAngle();
        BlockHitResult hit = level.clip(new ClipContext(
                eye, eye.add(look.scale(REACH)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, user));
        double reach =
                hit.getType() == HitResult.Type.MISS ? REACH : hit.getLocation().distanceTo(eye);
        Map<BlockPos, Integer> boxes = new LinkedHashMap<>();
        for (double d = STEP; d <= reach; d += STEP) {
            boxes.merge(BlockPos.containing(eye.add(look.scale(d))), d < NARROW ? 0 : 1, Math::max);
        }
        Vec3 end = eye.add(look.scale(reach));
        if (hit.getType() == HitResult.Type.BLOCK) {
            boxes.merge(hit.getBlockPos().relative(hit.getDirection()), 1, Math::max);
        }
        int out = 0;
        for (Map.Entry<BlockPos, Integer> box : boxes.entrySet()) {
            int r = box.getValue();
            out += Fires.extinguish(
                    level, box.getKey().offset(-r, -r, -r), box.getKey().offset(r, r, r));
        }
        float pitch = 1.6f + level.random.nextFloat() * 0.4f;
        level.playSound(
                null,
                user.getX(),
                user.getEyeY(),
                user.getZ(),
                SoundEvents.FIRE_EXTINGUISH,
                SoundSource.PLAYERS,
                0.3f,
                pitch);
        if (out > 0) {
            // As vanilla's dowsed fire sounds.
            float fizz = 2.6f + (level.random.nextFloat() - level.random.nextFloat()) * 0.8f;
            level.playSound(null, end.x, end.y, end.z, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.5f, fizz);
        }
        return out;
    }

    /** Tells the user it is empty, with a click. */
    private static void empty(Level level, LivingEntity user) {
        level.playSound(
                null,
                user.getX(),
                user.getY(),
                user.getZ(),
                SoundEvents.DISPENSER_FAIL,
                SoundSource.PLAYERS,
                1.0f,
                1.2f);
        if (user instanceof Player player) {
            player.displayClientMessage(Component.translatable("item.zas_atmos.fire_extinguisher.empty"), true);
        }
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return USE_DURATION;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.NONE;
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        // A spray changes the charges; that shouldn't bob the item in the hand.
        return slotChanged || !ItemStack.isSameItem(oldStack, newStack);
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return true;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13f * charges(stack) / CHARGES);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return BAR;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("item.zas_atmos.fire_extinguisher.charges", charges(stack), CHARGES));
    }
}
