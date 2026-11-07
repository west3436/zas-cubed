package io.github.west3436.zas.atmos.controller;

import io.github.west3436.zas.atmos.config.AtmosServerConfig;
import io.github.west3436.zas.atmos.device.AtmosDevice;
import io.github.west3436.zas.atmos.device.AtmosTicks;
import io.github.west3436.zas.atmos.device.DeviceTicker;
import io.github.west3436.zas.atmos.power.EnergyStore;
import io.github.west3436.zas.atmos.registry.AtmosBlockEntities;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;

/**
 * The central atmospherics controller's work, once per atmos tick (CONT-115 to CONT-122, D-75):
 *
 * <ol>
 *   <li>Keeps its chunk and the 8 around it loaded while {@code [central_controller] force_load} is
 *       true, and lets them go when it turns false ({@link CentralChunks}, CONT-122).
 *   <li>Lets go of the area air controllers it no longer reaches, or that are gone, and claims every
 *       loaded one in its range that no central controller has ({@link CentralClaims}, CONT-116,
 *       CONT-117).
 *   <li>Has each claimed area air controller in contact take FE from the blocks next to it, then works
 *       out its shortfall: what its last step needed that its buffer doesn't hold.
 *   <li>Takes FE from the blocks next to any of the console's four, as much as its own buffer and
 *       its area air controllers' have room for, pays its own draw, the shortfalls in zone priority
 *       order, and splits the rest of what it took in among their buffers ({@link CentralFeed},
 *       CONT-120, CONT-121).
 *   <li>Records each claimed area air controller's readings for its GUI (CONT-123), and shows the
 *       worst danger among them on its screen, or no power.
 * </ol>
 *
 * <p>It is the console's lower left block ({@link CentralControllerBlock}).
 *
 * <p>An area air controller is in contact while its chunk is loaded in full and its block entity is
 * stepping. One that isn't keeps its claim, gets no FE, and is listed with its last readings.
 */
public class CentralControllerBlockEntity extends AtmosDevice implements MenuProvider {
    private final EnergyStore buffer = new EnergyStore(
            () -> AtmosServerConfig.get(AtmosServerConfig.CENTRAL_BUFFER), true, false, this::setChanged);

    /** The range a player chose in its GUI, in chunks; 0 follows the server's maximum (CONT-117). */
    private int range;

    private boolean powered;
    private ControllerStatus status = ControllerStatus.UNPOWERED;

    /** Whether it holds its chunks; null until its first step after loading, as NeoForge may already. */
    private Boolean holding;

    public CentralControllerBlockEntity(BlockPos pos, BlockState state) {
        super(AtmosBlockEntities.CENTRAL_ATMOS_CONTROLLER.get(), pos, state);
    }

    @Override
    protected void atmosTick(ServerLevel level) {
        keepLoaded(level);
        CentralClaims claims = CentralClaims.of(level);
        Map<BlockPos, AreaAirControllerBlockEntity> loaded = new HashMap<>();
        List<AreaAirControllerBlockEntity> inOrder = new ArrayList<>();
        for (AtmosDevice d : DeviceTicker.loaded(level)) {
            if (d instanceof AreaAirControllerBlockEntity c && !c.isRemoved()) {
                loaded.put(c.getBlockPos(), c);
                inOrder.add(c);
            }
        }
        letGo(level, claims, loaded);
        for (AreaAirControllerBlockEntity c : inOrder) {
            if (reaches(c.getBlockPos()) && claimable(level, claims, c.getBlockPos())) {
                claims.claim(c.getBlockPos(), getBlockPos());
            }
        }
        feed(level, claims, loaded);
    }

    // ---- Chunks (CONT-122) ----------------------------------------------------------------

    private void keepLoaded(ServerLevel level) {
        boolean want = CentralChunks.enabled();
        if (holding == null || holding != want) {
            CentralChunks.hold(level, getBlockPos(), want);
            holding = want;
        }
    }

    /** Whether it holds its chunks, as of its last step. */
    public boolean holdingChunks() {
        return Boolean.TRUE.equals(holding);
    }

    // ---- Claims (CONT-116, CONT-117, CONT-119) --------------------------------------------

    /** Its range in chunks now: the one chosen in its GUI, at most the server's maximum (CONT-117). */
    public int range() {
        int max = maxRange();
        return range <= 0 ? max : Math.min(range, max);
    }

    /** The server's maximum range, in chunks: {@code [central_controller] max_range_chunks}. */
    public static int maxRange() {
        return AtmosServerConfig.get(AtmosServerConfig.CENTRAL_MAX_RANGE);
    }

    /**
     * Sets its range, from 1 chunk to the server's maximum (CONT-117). At the maximum it follows the
     * server's maximum from then on. The area air controllers it no longer reaches are let go now.
     *
     * @return whether the range was in bounds and set
     */
    public boolean setRange(int chunks) {
        if (chunks < 1 || chunks > maxRange()) {
            return false;
        }
        range = chunks >= maxRange() ? 0 : chunks;
        setChanged();
        if (level instanceof ServerLevel server) {
            CentralClaims claims = CentralClaims.of(server);
            for (BlockPos p : claims.claimedBy(getBlockPos())) {
                if (!reaches(p)) {
                    claims.release(p);
                }
            }
        }
        return true;
    }

    /**
     * Whether a position is in its range: in its dimension, which its level is, at any height, within
     * {@link #range()} chunks of its own chunk in X and in Z (CONT-116).
     */
    public boolean reaches(BlockPos pos) {
        int dx = SectionPos.blockToSectionCoord(pos.getX())
                - SectionPos.blockToSectionCoord(getBlockPos().getX());
        int dz = SectionPos.blockToSectionCoord(pos.getZ())
                - SectionPos.blockToSectionCoord(getBlockPos().getZ());
        return Math.max(Math.abs(dx), Math.abs(dz)) <= range();
    }

    /**
     * Whether an area air controller can be claimed: no central controller has it, or the one that
     * has it is loaded and gone, as when it was removed without being broken.
     */
    private static boolean claimable(ServerLevel level, CentralClaims claims, BlockPos controller) {
        BlockPos owner = claims.centralOf(controller).orElse(null);
        if (owner == null) {
            return true;
        }
        if (level.isLoaded(owner) && !(level.getBlockEntity(owner) instanceof CentralControllerBlockEntity)) {
            claims.release(controller);
            return true;
        }
        return false;
    }

    /** Lets go of the area air controllers out of its range, and those whose loaded block is gone. */
    private void letGo(ServerLevel level, CentralClaims claims, Map<BlockPos, AreaAirControllerBlockEntity> loaded) {
        for (BlockPos p : claims.claimedBy(getBlockPos())) {
            if (!reaches(p)
                    || (!loaded.containsKey(p)
                            && level.isLoaded(p)
                            && !(level.getBlockEntity(p) instanceof AreaAirControllerBlockEntity))) {
                claims.release(p);
            }
        }
    }

    /**
     * Takes an area air controller from whichever central controller has it, for the multitool
     * (CONT-118).
     *
     * @return false, changing nothing, if it is out of range
     */
    public boolean take(BlockPos controller) {
        if (!(level instanceof ServerLevel server) || !reaches(controller)) {
            return false;
        }
        CentralClaims.of(server).move(controller, getBlockPos());
        return true;
    }

    /** Called by the block when it is removed: releases its area air controllers and its chunks (CONT-119, CONT-122). */
    void removedFromWorld(ServerLevel level) {
        CentralClaims.of(level).releaseAll(getBlockPos());
        CentralChunks.hold(level, getBlockPos(), false);
        holding = false;
    }

    // ---- Power (CONT-115, CONT-120, CONT-121) ---------------------------------------------

    private void feed(ServerLevel level, CentralClaims claims, Map<BlockPos, AreaAirControllerBlockEntity> loaded) {
        List<AreaAirControllerBlockEntity> zones = new ArrayList<>();
        List<CentralFeed.Zone> needs = new ArrayList<>();
        long room = 0;
        for (BlockPos p : claims.claimedBy(getBlockPos())) {
            AreaAirControllerBlockEntity c = inContact(level, loaded, p);
            if (c == null) {
                continue;
            }
            c.pull(level); // the blocks next to it pay first
            EnergyStore b = c.buffer();
            long shortfall = Math.max(0, c.demand() - b.getEnergyStored());
            zones.add(c);
            needs.add(new CentralFeed.Zone(claims.priority(p), shortfall, b.space()));
            room += b.space();
        }

        long intake = pull(level, (long) buffer.space() + room);
        long own = (long) AtmosServerConfig.get(AtmosServerConfig.CENTRAL_DRAW) * AtmosTicks.PERIOD;
        CentralFeed.Plan plan = CentralFeed.plan(buffer.getEnergyStored(), intake, own, needs);
        long left = plan.left();
        for (int i = 0; i < zones.size(); i++) {
            long give = plan.given(i);
            if (give > 0) {
                left += give - zones.get(i).buffer().fill((int) Math.min(Integer.MAX_VALUE, give));
            }
        }
        buffer.set((int) Math.min(Integer.MAX_VALUE, left));
        powered = plan.powered();

        int worst = 0;
        for (BlockPos p : claims.claimedBy(getBlockPos())) {
            AreaAirControllerBlockEntity c = inContact(level, loaded, p);
            if (c != null) {
                claims.record(p, readings(c));
            }
            worst = Math.max(worst, claims.last(p).danger());
        }
        setStatus(level, powered ? ControllerStatus.of(worst) : ControllerStatus.UNPOWERED);
    }

    /** A claimed area air controller if it is in contact: loaded in full, and stepping. */
    static AreaAirControllerBlockEntity inContact(
            ServerLevel level, Map<BlockPos, AreaAirControllerBlockEntity> loaded, BlockPos p) {
        AreaAirControllerBlockEntity c = loaded.get(p);
        return c != null && level.isLoaded(p) ? c : null;
    }

    private static CentralClaims.Readings readings(AreaAirControllerBlockEntity c) {
        double[] values = c.values();
        return new CentralClaims.Readings(
                values.length > 0 ? values[0] : Double.NaN,
                values.length > 1 ? values[1] : Double.NaN,
                c.danger(),
                c.powered());
    }

    /** The console's four blocks, this one first, as {@link CentralControllerBlock#parts} lists them. */
    public List<BlockPos> parts() {
        BlockState state = getBlockState();
        Direction facing = state.hasProperty(CentralControllerBlock.FACING)
                ? state.getValue(CentralControllerBlock.FACING)
                : Direction.NORTH;
        return CentralControllerBlock.parts(getBlockPos(), facing);
    }

    /**
     * Takes up to {@code most} FE from the blocks next to any of the console's four that give it, and
     * are loaded.
     *
     * @return what it took, not yet in its buffer
     */
    private long pull(ServerLevel level, long most) {
        List<BlockPos> parts = parts();
        long got = 0;
        for (BlockPos part : parts) {
            for (Direction dir : Direction.values()) {
                long want = most - got;
                if (want <= 0) {
                    return got;
                }
                BlockPos next = part.relative(dir);
                if (parts.contains(next) || !level.isLoaded(next)) {
                    continue;
                }
                IEnergyStorage source = level.getCapability(Capabilities.EnergyStorage.BLOCK, next, dir.getOpposite());
                if (source != null && source.canExtract()) {
                    got += Math.max(0, source.extractEnergy((int) Math.min(Integer.MAX_VALUE, want), false));
                }
            }
        }
        return got;
    }

    /** Sets its status, and its screen, which the other three blocks of the console copy from this one. */
    private void setStatus(ServerLevel level, ControllerStatus s) {
        status = s;
        BlockState state = getBlockState();
        ConsoleScreen screen = ConsoleScreen.of(s);
        if (state.hasProperty(CentralControllerBlock.SCREEN)
                && state.getValue(CentralControllerBlock.SCREEN) != screen) {
            level.setBlock(getBlockPos(), state.setValue(CentralControllerBlock.SCREEN, screen), Block.UPDATE_ALL);
        }
    }

    // ---- For the GUI and tests ------------------------------------------------------------

    public EnergyStore buffer() {
        return buffer;
    }

    /** Whether it paid its own draw at its last step. */
    public boolean powered() {
        return powered;
    }

    /** No power, or the highest danger among its area air controllers' last readings. */
    public ControllerStatus status() {
        return status;
    }

    // ---- Saving and the menu -------------------------------------------------------------

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Energy", buffer.getEnergyStored());
        tag.putInt("Range", range);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        buffer.set(tag.getInt("Energy"));
        range = Math.max(0, tag.getInt("Range"));
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.zas_atmos.central_atmos_controller");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new CentralMenu(id, inventory, this);
    }
}
