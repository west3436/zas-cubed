package io.github.west3436.zas.atmos.ttv;

import io.github.west3436.zas.atmos.config.AtmosServerConfig;
import io.github.west3436.zas.atmos.device.AtmosTicks;
import io.github.west3436.zas.atmos.device.DeviceData;
import io.github.west3436.zas.atmos.registry.AtmosEntities;
import io.github.west3436.zas.atmos.registry.AtmosItems;
import io.github.west3436.zas.atmos.registry.AtmosSounds;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * A primed tank transfer valve (CONT-104): what a TTV becomes when its timer starts, lying in the
 * world as primed TNT does, and just as out of reach. No one can pick it up, use it or knock it
 * about, so a timer can't be stopped once it has started.
 *
 * <p>When the timer runs out, the valve opens (CONT-103). A timer of 0 s opens it as soon as it
 * starts. A TTV fitted with a trigger in its timer's place (CONT-184) is primed, armed, the same way,
 * and its trigger opens the valve instead: a remote signaler on the same frequency and code (CONT-186),
 * or, after the arming delay, something living within the proximity sensor's range (CONT-187). Such a
 * TTV waits as long as it takes. Its tanks' gas reacts every atmos tick (CONT-100), and from the moment the valve opens, so
 * a mixture above autoignition burns at once and the tank may fail (CONT-038). A TTV that survives
 * lies with its valve open for {@link #SETTLE_TICKS} game ticks, then drops as an item again, so a
 * dud can be taken away: its timer is over, and the entity has nothing left to do.
 */
public class PrimedTransferValve extends Entity {
    /** Game ticks a TTV whose valve has opened stays an entity before dropping as an item: 5 s. */
    public static final int SETTLE_TICKS = 100;

    private static final EntityDataAccessor<ItemStack> DATA_FIRST =
            SynchedEntityData.defineId(PrimedTransferValve.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<ItemStack> DATA_SECOND =
            SynchedEntityData.defineId(PrimedTransferValve.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<Boolean> DATA_OPEN =
            SynchedEntityData.defineId(PrimedTransferValve.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_TRIGGER =
            SynchedEntityData.defineId(PrimedTransferValve.class, EntityDataSerializers.INT);

    private static final String FIRST = "First";
    private static final String SECOND = "Second";
    private static final String OPEN = "Open";
    private static final String TIMER = "Timer";
    private static final String FUSE = "Fuse";
    private static final String SETTLE = "Settle";
    private static final String TRIGGER = "Trigger";

    /**
     * What brings a saved valve forward, oldest first, for {@link DeviceData#migrate(CompoundTag, List)}
     * (DATA-012). Version 0 is a valve saved before versions were added, and its fields are those of
     * version 1.
     */
    private static final List<Consumer<CompoundTag>> FORMAT_STEPS = List.of(tag -> {});

    private static final int TICKS_PER_SECOND = 20;

    /** The seconds left from which its timer ticks each second. */
    public static final int TICK_EVERY_SECOND_FROM = 30;

    /** How often an armed proximity sensor looks for something living, in game ticks (CONT-187). */
    public static final int PROXIMITY_SCAN_TICKS = 4;

    private TransferValve valve = new TransferValve(ItemStack.EMPTY, ItemStack.EMPTY, false);
    private int timer = TransferValve.DEFAULT_TIMER;
    private int fuse = timer * TICKS_PER_SECOND;
    private int settle = SETTLE_TICKS;
    private int nextStep;

    /** The item fitted in its timer's place, or an empty stack for its timer (CONT-184). */
    private ItemStack trigger = ItemStack.EMPTY;

    /** Game ticks before a watching proximity sensor looks again. */
    private int scanIn;

    public PrimedTransferValve(EntityType<? extends PrimedTransferValve> type, Level level) {
        super(type, level);
        blocksBuilding = true;
    }

    /**
     * Primes a TTV stack: adds its entity to the level at a player's feet, tossed gently the way they
     * face. A timer of 0 s opens its valve at once. The stack itself isn't changed.
     *
     * @param level the level
     * @param player who started the timer
     * @param stack the TTV
     * @return the entity
     */
    public static PrimedTransferValve prime(ServerLevel level, Player player, ItemStack stack) {
        Vec3 look = player.getLookAngle();
        return prime(
                level,
                player.position(),
                new Vec3(look.x * 0.2, 0.2, look.z * 0.2),
                player.getDirection().toYRot(),
                stack);
    }

    /**
     * Primes a placed TTV where it stands (D-86): adds its entity on the block's floor, still, its timer
     * facing the way the block's did. The caller removes the block. The stack isn't changed.
     *
     * @param level the level
     * @param pos where the block stood
     * @param facing the way its timer faced
     * @param stack the TTV's tanks, valve and timer
     * @return the entity
     */
    public static PrimedTransferValve prime(ServerLevel level, BlockPos pos, Direction facing, ItemStack stack) {
        // The model's timer is on its north face, and the renderer turns it by -yRot.
        return prime(
                level,
                Vec3.atBottomCenterOf(pos),
                Vec3.ZERO,
                facing.getOpposite().toYRot(),
                stack);
    }

    private static PrimedTransferValve prime(ServerLevel level, Vec3 at, Vec3 motion, float yRot, ItemStack stack) {
        PrimedTransferValve primed = new PrimedTransferValve(AtmosEntities.PRIMED_TANK_TRANSFER_VALVE.get(), level);
        primed.setPos(at);
        primed.setDeltaMovement(motion);
        primed.setYRot(yRot);
        primed.arm(TransferValveItem.valve(stack), TransferValveItem.timer(stack), TransferValveItem.trigger(stack));
        level.addFreshEntity(primed);
        level.playSound(
                null,
                primed.getX(),
                primed.getY(),
                primed.getZ(),
                AtmosSounds.TTV_ARM.get(),
                SoundSource.BLOCKS,
                1.0f,
                1.0f);
        return primed;
    }

    /**
     * Arms it. A timer counts down from its setting, and at 0 s opens the valve at once. A proximity
     * sensor counts down its arming delay instead, then watches. A signaler only listens.
     */
    private void arm(TransferValve valve, int seconds, ItemStack trigger) {
        this.valve = valve;
        this.trigger = trigger;
        timer = seconds;
        fuse = switch (kind()) {
            case TIMER -> seconds * TICKS_PER_SECOND;
            case PROXIMITY -> AtmosServerConfig.get(AtmosServerConfig.PROXIMITY_ARMING_DELAY) * TICKS_PER_SECOND;
            case SIGNALER -> 0;
        };
        publish();
        if (kind() == TtvTrigger.TIMER && seconds <= 0 && !valve.isOpen()) {
            openValve();
        }
    }

    /** What opens its valve: its timer, or the item fitted in its place. */
    public TtvTrigger kind() {
        return TtvTrigger.of(trigger);
    }

    /** The item fitted in its timer's place, as a copy, or an empty stack. */
    public ItemStack trigger() {
        return trigger.copy();
    }

    /** What clients draw in its timer's place, as far as this side knows. */
    public TtvTrigger shownTrigger() {
        return TtvTrigger.of(entityData.get(DATA_TRIGGER));
    }

    /**
     * Whether it opens for a signal (CONT-186): it is fitted with a signaler set to that frequency and
     * code, and its valve is still shut.
     */
    public boolean hears(Signal signal) {
        return isAlive()
                && !valve.isOpen()
                && kind() == TtvTrigger.SIGNALER
                && RemoteSignalerItem.signal(trigger).equals(signal);
    }

    /** A signal it hears opens its valve. */
    void signalled() {
        if (!valve.isOpen() && isAlive()) {
            openValve();
        }
    }

    /**
     * Whether a proximity sensor fitted to it is watching: armed, and its arming delay over. Before
     * that, nothing sets it off, the player who armed it included.
     */
    public boolean watching() {
        return kind() == TtvTrigger.PROXIMITY && !valve.isOpen() && fuse <= 0;
    }

    /**
     * Whether its timer ticks audibly with this many game ticks left (CONT-127): each second for the
     * last {@value #TICK_EVERY_SECOND_FROM} s, and every 5 s before that, since a timer can run for 10
     * minutes.
     */
    public static boolean ticksAt(int fuse) {
        if (fuse <= 0 || fuse % TICKS_PER_SECOND != 0) {
            return false;
        }
        int seconds = fuse / TICKS_PER_SECOND;
        return seconds <= TICK_EVERY_SECOND_FROM || seconds % 5 == 0;
    }

    /** The timer it was started with, s. */
    public int timer() {
        return timer;
    }

    /** Game ticks until the timer runs out; 0 or less once it has. */
    public int fuse() {
        return fuse;
    }

    /** The valve and its tanks; server side. Clients have {@link #firstTank}, {@link #secondTank} and {@link #valveOpen}. */
    public TransferValve valve() {
        return valve;
    }

    /** The first tank, as far as this side knows. */
    public ItemStack firstTank() {
        return entityData.get(DATA_FIRST);
    }

    /** The second tank, as far as this side knows. */
    public ItemStack secondTank() {
        return entityData.get(DATA_SECOND);
    }

    /** Whether the valve is open, as far as this side knows. */
    public boolean valveOpen() {
        return entityData.get(DATA_OPEN);
    }

    private void openValve() {
        valve.open();
        nextStep = 0;
        publish();
        level().playSound(null, getX(), getY(), getZ(), AtmosSounds.TTV_OPEN.get(), SoundSource.BLOCKS, 1.0f, 1.0f);
    }

    /** Tells clients what they draw: the tanks, whose tints follow their kinds, and the valve. */
    private void publish() {
        if (!ItemStack.matches(firstTank(), valve.first())) {
            entityData.set(DATA_FIRST, valve.first().copy());
        }
        if (!ItemStack.matches(secondTank(), valve.second())) {
            entityData.set(DATA_SECOND, valve.second().copy());
        }
        entityData.set(DATA_OPEN, valve.isOpen());
        entityData.set(DATA_TRIGGER, kind().ordinal());
    }

    @Override
    public void tick() {
        handlePortal();
        applyGravity();
        move(MoverType.SELF, getDeltaMovement());
        setDeltaMovement(getDeltaMovement().scale(0.98));
        if (onGround()) {
            setDeltaMovement(getDeltaMovement().multiply(0.7, -0.5, 0.7));
        }
        updateInWaterStateAndDoFluidPushing();
        if (level() instanceof ServerLevel server) {
            serverTick(server);
        }
    }

    private void serverTick(ServerLevel level) {
        if (!valve.isOpen()) {
            switch (kind()) {
                case TIMER -> {
                    if (--fuse <= 0) {
                        openValve();
                    } else if (ticksAt(fuse)) {
                        level.playSound(
                                null,
                                getX(),
                                getY(),
                                getZ(),
                                AtmosSounds.TTV_TICK.get(),
                                SoundSource.BLOCKS,
                                1.0f,
                                1.0f);
                    }
                }
                case PROXIMITY -> watch(level);
                case SIGNALER -> {
                    // A signal opens it: signalled().
                }
            }
        }
        if (--nextStep <= 0) {
            nextStep = AtmosTicks.PERIOD;
            TransferValve.Step step = valve.step(level, this, this);
            if (step == TransferValve.Step.DESTROYED) {
                discard();
                return;
            }
            if (step == TransferValve.Step.CHANGED) {
                publish();
            }
        }
        if (valve.isOpen() && --settle <= 0) {
            dropAsItem(level);
        }
    }

    /**
     * An armed proximity sensor's step (CONT-187): it counts down its arming delay, ticking once when
     * it starts watching, then looks every {@value #PROXIMITY_SCAN_TICKS} game ticks for something
     * living, not a spectator or an armor stand, whose box is within its range of its own. Its first
     * look is the game tick after its delay ends. Only a primed TTV with a
     * proximity sensor does this, and only until its valve opens.
     */
    private void watch(ServerLevel level) {
        if (fuse > 0) {
            if (--fuse == 0) {
                level.playSound(
                        null, getX(), getY(), getZ(), AtmosSounds.TTV_TICK.get(), SoundSource.BLOCKS, 1.0f, 1.0f);
            }
            return;
        }
        if (--scanIn > 0) {
            return;
        }
        scanIn = PROXIMITY_SCAN_TICKS;
        int range = ProximitySensorItem.range(trigger);
        if (!level.getEntitiesOfClass(
                        LivingEntity.class,
                        getBoundingBox().inflate(range),
                        e -> e.isAlive() && !e.isSpectator() && !(e instanceof ArmorStand))
                .isEmpty()) {
            openValve();
        }
    }

    /** It has done what it was primed for: it's an item again, its valve as the trigger left it. */
    private void dropAsItem(ServerLevel level) {
        ItemStack stack = new ItemStack(AtmosItems.TANK_TRANSFER_VALVE.get());
        TransferValveItem.save(stack, valve);
        TransferValveItem.setTimer(stack, timer);
        TransferValveItem.setTrigger(stack, trigger);
        level.addFreshEntity(new ItemEntity(level, getX(), getY(), getZ(), stack));
        discard();
    }

    // ---- Out of reach ------------------------------------------------------------------------

    /** Crosshairs and projectiles pass through it, so no one can pick it up, use it or hit it. */
    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    protected MovementEmission getMovementEmission() {
        return MovementEmission.NONE;
    }

    @Override
    protected double getDefaultGravity() {
        return 0.04;
    }

    // ---- Saved -------------------------------------------------------------------------------

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_FIRST, ItemStack.EMPTY);
        builder.define(DATA_SECOND, ItemStack.EMPTY);
        builder.define(DATA_OPEN, false);
        builder.define(DATA_TRIGGER, TtvTrigger.TIMER.ordinal());
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        HolderLookup.Provider registries = registryAccess();
        tag.putInt(DeviceData.VERSION_KEY, FORMAT_STEPS.size());
        tag.put(FIRST, valve.first().saveOptional(registries));
        tag.put(SECOND, valve.second().saveOptional(registries));
        tag.putBoolean(OPEN, valve.isOpen());
        tag.putInt(TIMER, timer);
        tag.putInt(FUSE, fuse);
        tag.putInt(SETTLE, settle);
        if (!trigger.isEmpty()) {
            tag.put(TRIGGER, trigger.save(registries));
        }
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        DeviceData.migrate(tag, FORMAT_STEPS);
        HolderLookup.Provider registries = registryAccess();
        valve = new TransferValve(
                ItemStack.parseOptional(registries, tag.getCompound(FIRST)),
                ItemStack.parseOptional(registries, tag.getCompound(SECOND)),
                tag.getBoolean(OPEN));
        timer = TransferValve.clampTimer(tag.getInt(TIMER));
        fuse = tag.getInt(FUSE);
        settle = tag.getInt(SETTLE);
        ItemStack read = ItemStack.parseOptional(registries, tag.getCompound(TRIGGER));
        trigger = TtvTrigger.fits(read) ? read : ItemStack.EMPTY;
        publish();
    }
}
