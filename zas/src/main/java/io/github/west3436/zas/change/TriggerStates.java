package io.github.west3436.zas.change;

import io.github.west3436.zas.ZasMod;
import io.github.west3436.zas.config.ZoneTrigger;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.TagsUpdatedEvent;

/**
 * Which block states are Hardcore zone triggers (CORE-056, D-13): a dense map from block state id
 * to flags, read by the change hook, so a write costs one array read per state to classify.
 *
 * <ul>
 *   <li><b>Combustion</b> ({@link ZoneTrigger#COMBUSTION}, FIRE-024): a state of a block in the
 *       {@value #COMBUSTION_TAG} tag that burns: one with no {@code lit} property, such as fire, or
 *       with {@code lit=true}, such as a lit furnace or campfire. A modded fuel block, such as the
 *       fuel generator (CONT-067), joins by being in the tag with a boolean {@code lit} property.
 *   <li><b>Planting</b> ({@link ZoneTrigger#PLANTING}, CONT-053): every state of a block in the
 *       {@value #PLANTS_TAG} tag, the plants that photosynthesise.
 * </ul>
 *
 * <p>A write is a trigger when it gives the position a flag its old state didn't have: fire
 * appearing, a furnace being lit, a plant being placed. A fire's age changing, a lit furnace
 * turning, a crop growing or leaves updating their distance aren't, so trigger blocks that are
 * already there cost nothing more as they tick. Extinguishing or removing one isn't a trigger
 * either. Trigger blocks already in place when the preset changes stay as they are: zones never
 * come from a scan (CORE-054).
 *
 * <p>The table is rebuilt from the tags whenever the server binds them (start and {@code /reload});
 * until then nothing is a trigger. Reads are safe from any thread.
 */
public final class TriggerStates {
    /** The combustion tag's id. */
    public static final String COMBUSTION_TAG = "zas:combustion";

    /** The plants tag's id. */
    public static final String PLANTS_TAG = "zas:plants";

    /** Blocks whose burning states are combustion zone triggers (CORE-056, FIRE-024). */
    public static final TagKey<Block> COMBUSTION =
            TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(ZasMod.MOD_ID, "combustion"));

    /** Plants: placing one is planting (CORE-056), and they photosynthesise (CONT-053). */
    public static final TagKey<Block> PLANTS =
            TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(ZasMod.MOD_ID, "plants"));

    /** The flag of a combustion state. */
    public static final int COMBUSTION_FLAG = 1;

    /** The flag of a plant state. */
    public static final int PLANTING_FLAG = 2;

    private static volatile byte[] byStateId = new byte[0];
    private static volatile Set<Block> combustionForTest = Set.of();

    private TriggerStates() {}

    /** Registers the tag listener on the game event bus. */
    public static void register(IEventBus gameBus) {
        gameBus.addListener(TriggerStates::onTagsUpdated);
    }

    /**
     * The triggers a write from state id {@code oldId} to {@code nowId} ({@link Block#getId})
     * starts, among those active under the preset now: {@link #COMBUSTION_FLAG}, {@link
     * #PLANTING_FLAG}, both or 0. The options are read only for a write that starts one. Any
     * thread.
     */
    public static int started(int oldId, int nowId) {
        byte[] table = byStateId;
        int fresh = flags(table, nowId) & ~flags(table, oldId);
        if (fresh == 0) {
            return 0;
        }
        if ((fresh & COMBUSTION_FLAG) != 0 && !ZoneTrigger.COMBUSTION.active()) {
            fresh &= ~COMBUSTION_FLAG;
        }
        if ((fresh & PLANTING_FLAG) != 0 && !ZoneTrigger.PLANTING.active()) {
            fresh &= ~PLANTING_FLAG;
        }
        return fresh;
    }

    /** Whether a state is combustion: a block in {@value #COMBUSTION_TAG}, burning. */
    public static boolean isCombustion(BlockState state) {
        return (flags(byStateId, Block.getId(state)) & COMBUSTION_FLAG) != 0;
    }

    /** Whether a state is a plant: a block in {@value #PLANTS_TAG}. */
    public static boolean isPlant(BlockState state) {
        return (flags(byStateId, Block.getId(state)) & PLANTING_FLAG) != 0;
    }

    /**
     * Rebuilds the table with {@code extra} counted as combustion blocks besides the tag's, as a
     * datapack that added them to the tag would; none goes back to the tag alone. For GameTests
     * that stand in for a modded fuel block, such as the generator (CONT-067). Server thread,
     * since tags are read here.
     */
    public static void combustionForTest(Block... extra) {
        combustionForTest = Set.of(extra);
        rebuild();
    }

    private static int flags(byte[] table, int stateId) {
        return stateId >= 0 && stateId < table.length ? table[stateId] : 0;
    }

    private static void onTagsUpdated(TagsUpdatedEvent event) {
        if (event.getUpdateCause() == TagsUpdatedEvent.UpdateCause.SERVER_DATA_LOAD) {
            rebuild();
        }
    }

    /** Builds the table from the bound tags. Only the tagged blocks' states are visited. */
    private static void rebuild() {
        byte[] table = new byte[Block.BLOCK_STATE_REGISTRY.size()];
        for (Holder<Block> holder : BuiltInRegistries.BLOCK.getTagOrEmpty(COMBUSTION)) {
            markBurning(table, holder.value());
        }
        for (Block block : combustionForTest) {
            markBurning(table, block);
        }
        for (Holder<Block> holder : BuiltInRegistries.BLOCK.getTagOrEmpty(PLANTS)) {
            for (BlockState state : holder.value().getStateDefinition().getPossibleStates()) {
                mark(table, state, PLANTING_FLAG);
            }
        }
        byStateId = table;
    }

    /** Marks a block's burning states: all of them without a {@code lit} property, else the lit ones. */
    private static void markBurning(byte[] table, Block block) {
        Property<?> lit = block.getStateDefinition().getProperty("lit");
        for (BlockState state : block.getStateDefinition().getPossibleStates()) {
            if (!(lit instanceof BooleanProperty b) || state.getValue(b)) {
                mark(table, state, COMBUSTION_FLAG);
            }
        }
    }

    private static void mark(byte[] table, BlockState state, int flag) {
        int id = Block.getId(state);
        if (id >= 0 && id < table.length) {
            table[id] |= (byte) flag;
        }
    }
}
