package io.github.west3436.zas.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import io.github.west3436.zas.api.atmosphere.AtmosphereSample;
import io.github.west3436.zas.api.atmosphere.Atmospheres;
import io.github.west3436.zas.api.atmosphere.ZoneId;
import io.github.west3436.zas.api.atmosphere.ZoneInfo;
import io.github.west3436.zas.api.gas.GasMixture;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.api.mutation.GasMutations;
import io.github.west3436.zas.api.mutation.MutationResult;
import io.github.west3436.zas.api.mutation.SourceId;
import io.github.west3436.zas.config.ZasServerConfig;
import io.github.west3436.zas.data.DatapackErrors;
import io.github.west3436.zas.engine.conservation.ConservationReport;
import io.github.west3436.zas.engine.conservation.LedgerCategory;
import io.github.west3436.zas.engine.gas.GasRegistry;
import io.github.west3436.zas.engine.scheduler.AtmosSnapshot;
import io.github.west3436.zas.engine.scheduler.GeometryAudit;
import io.github.west3436.zas.engine.scheduler.ZoneView;
import io.github.west3436.zas.gas.ServerGasRegistry;
import io.github.west3436.zas.geometry.Geometries;
import io.github.west3436.zas.geometry.GeometryTable;
import io.github.west3436.zas.provider.PointResolver;
import io.github.west3436.zas.simulation.AtmosService;
import io.github.west3436.zas.simulation.DimensionAtmos;
import io.github.west3436.zas.simulation.ProfileReport;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * The {@code /zas} operator commands (OPS-003, OPS-004, OPS-009, OPS-010, OPS-011, GEO-032, and
 * with {@link PresetCommand} OPS-007, OPS-014 and OPS-015), documented in {@code docs/commands.md}.
 *
 * <p>Every command needs the permission level in {@code [commands] permission_level} of the server
 * config, 2 by default (OPS-011). Other {@code /zas} subcommands registered elsewhere, such as the
 * client's {@code /zas debug}, merge into the same root and should use {@link #permitted}.
 *
 * <p>Commands act on the dimension they run in ({@code execute in} changes it). They read the
 * published zone table and change gas only through the API (API-012), attributed to {@code
 * command:<player>}; edits complete when the next atmos tick is applied, and report back then.
 * Commands that look inside the engine run on its worker between atmos ticks and report back when
 * they finish. Nothing here waits for the worker or loads a chunk.
 */
public final class ZasCommands {
    /** The most blocks a fill, vent or audit may cover, as vanilla's {@code /fill}. */
    public static final int MAX_REGION_BLOCKS = 32_768;

    /** The most sections an audit may cover. */
    public static final int MAX_AUDIT_SECTIONS = 512;

    /** How many audit findings are listed in chat. */
    public static final int LISTED_FINDINGS = 10;

    /** How many zones {@code /zas zones} lists. */
    public static final int LISTED_ZONES = 10;

    private static final DynamicCommandExceptionType UNKNOWN_GAS =
            new DynamicCommandExceptionType(id -> Component.translatable("commands.zas.error.unknown_gas", id));
    private static final DynamicCommandExceptionType UNKNOWN_ZONE =
            new DynamicCommandExceptionType(id -> Component.translatable("commands.zas.error.unknown_zone", id));
    private static final SimpleCommandExceptionType NOT_RUNNING =
            new SimpleCommandExceptionType(Component.translatable("commands.zas.error.not_running"));
    private static final SimpleCommandExceptionType NO_ZONE_HERE =
            new SimpleCommandExceptionType(Component.translatable("commands.zas.error.no_zone_here"));
    private static final DynamicCommandExceptionType REGION_TOO_BIG =
            new DynamicCommandExceptionType(max -> Component.translatable("commands.zas.error.region_too_big", max));

    private static final SuggestionProvider<CommandSourceStack> GASES = (ctx, builder) -> {
        GasRegistry registry = ServerGasRegistry.current();
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < registry.size(); i++) {
            ids.add(registry.definition(i).id());
        }
        return SharedSuggestionProvider.suggest(ids, builder);
    };

    private ZasCommands() {}

    /** Registers the commands. Called once, from the mod constructor. */
    public static void register(IEventBus gameBus) {
        gameBus.addListener((RegisterCommandsEvent e) -> register(e.getDispatcher()));
    }

    /** Whether a source may run {@code /zas} commands (OPS-011). */
    public static boolean permitted(CommandSourceStack source) {
        return source.hasPermission(ZasServerConfig.permissionLevel());
    }

    /** The attribution of a command's gas edits (OPS-009): {@code command:<player>}, or {@code command:server}. */
    public static SourceId sourceOf(CommandSourceStack source) {
        String name = source.getEntity() == null ? "server" : source.getTextName();
        StringBuilder path = new StringBuilder();
        for (char c : name.toLowerCase(Locale.ROOT).toCharArray()) {
            path.append(ResourceLocation.validPathChar(c) ? c : '_');
        }
        return SourceId.of("command", path.isEmpty() ? "unknown" : path.toString());
    }

    static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> root =
                Commands.literal("zas").requires(ZasCommands::permitted);

        // OPS-003: inspection.
        root.then(Commands.literal("at")
                .executes(ctx -> at(ctx.getSource(), eyes(ctx.getSource())))
                .then(Commands.literal("point")
                        .then(Commands.argument("point", Vec3Argument.vec3(false))
                                .executes(ctx -> at(ctx.getSource(), Vec3Argument.getVec3(ctx, "point")))))
                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                        .executes(ctx -> at(ctx.getSource(), BlockPosArgument.getBlockPos(ctx, "pos")))));
        root.then(Commands.literal("zone")
                .then(Commands.literal("at")
                        .executes(ctx -> zoneAt(ctx.getSource(), eyes(ctx.getSource())))
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .executes(ctx -> zoneAt(ctx.getSource(), BlockPosArgument.getBlockPos(ctx, "pos")))))
                .then(Commands.argument("id", LongArgumentType.longArg(1))
                        .executes(ctx -> zone(ctx.getSource(), LongArgumentType.getLong(ctx, "id")))));
        root.then(Commands.literal("zones").executes(ctx -> zones(ctx.getSource())));

        // OPS-009: gas editing.
        root.then(Commands.literal("gas")
                .then(Commands.literal("add")
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .then(gasArgument()
                                        .then(Commands.argument("moles", DoubleArgumentType.doubleArg(0))
                                                .executes(ctx -> add(ctx, -1))
                                                .then(Commands.argument("temperature", DoubleArgumentType.doubleArg(0))
                                                        .executes(ctx -> add(
                                                                ctx,
                                                                DoubleArgumentType.getDouble(ctx, "temperature"))))))))
                .then(Commands.literal("remove")
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .then(gasArgument()
                                        .then(Commands.argument("moles", DoubleArgumentType.doubleArg(0))
                                                .executes(ZasCommands::remove)))))
                .then(Commands.literal("set")
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .then(gasArgument()
                                        .then(Commands.argument("moles", DoubleArgumentType.doubleArg(0))
                                                .executes(ZasCommands::set)))))
                .then(Commands.literal("fill")
                        .then(Commands.argument("from", BlockPosArgument.blockPos())
                                .then(Commands.argument("to", BlockPosArgument.blockPos())
                                        .then(gasArgument()
                                                .then(Commands.argument("moles", DoubleArgumentType.doubleArg(0))
                                                        .executes(ZasCommands::fill))))))
                .then(Commands.literal("vent")
                        .then(Commands.argument("from", BlockPosArgument.blockPos())
                                .then(Commands.argument("to", BlockPosArgument.blockPos())
                                        .executes(ctx -> vent(ctx, null))
                                        .then(gasArgument().executes(ctx -> vent(ctx, gas(ctx))))))));

        // OPS-010, GEO-032: audit and the invariant checker.
        root.then(Commands.literal("audit")
                .then(Commands.argument("from", BlockPosArgument.blockPos())
                        .then(Commands.argument("to", BlockPosArgument.blockPos())
                                .executes(ctx -> audit(ctx, false))
                                .then(Commands.literal("repair").executes(ctx -> audit(ctx, true))))));
        root.then(Commands.literal("checker")
                .executes(ctx -> checkerStatus(ctx.getSource()))
                .then(Commands.literal("on").executes(ctx -> checkerSwitch(ctx.getSource(), true)))
                .then(Commands.literal("off").executes(ctx -> checkerSwitch(ctx.getSource(), false)))
                .then(Commands.literal("config").executes(ctx -> checkerSwitch(ctx.getSource(), null)))
                .then(Commands.literal("report").executes(ctx -> checkerReport(ctx.getSource())))
                .then(Commands.literal("run").executes(ctx -> checkerRun(ctx.getSource()))));

        // OPS-004: the profiler.
        root.then(Commands.literal("profile")
                .executes(ctx -> profile(ctx.getSource()))
                .then(Commands.literal("reset").executes(ctx -> profileReset(ctx.getSource()))));

        // OPS-008: datapack problems.
        root.then(Commands.literal("datapacks").executes(ctx -> datapacks(ctx.getSource())));

        // OPS-007, OPS-014, OPS-015: the preset and the options it sets.
        root.then(PresetCommand.node());

        dispatcher.register(root);
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, ResourceLocation>
            gasArgument() {
        return Commands.argument("gas", ResourceLocationArgument.id()).suggests(GASES);
    }

    // ---- Helpers ------------------------------------------------------------------------

    private static AtmosService service() throws CommandSyntaxException {
        AtmosService s = AtmosService.current();
        if (s == null) {
            throw NOT_RUNNING.create();
        }
        return s;
    }

    private static ResourceLocation gas(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ResourceLocation id = ResourceLocationArgument.getId(ctx, "gas");
        if (ServerGasRegistry.current().indexOf(id.toString()) < 0) {
            throw UNKNOWN_GAS.create(id.toString());
        }
        return id;
    }

    private static void reply(CommandSourceStack source, Component message) {
        source.sendSuccess(() -> message, false);
    }

    private static void replyAll(CommandSourceStack source, List<Component> lines) {
        for (Component line : lines) {
            reply(source, line);
        }
    }

    /** Runs {@code then} on the server thread once {@code future} completes; failures are reported. */
    private static <T> void whenDone(CommandSourceStack source, CompletableFuture<T> future, Consumer<T> then) {
        MinecraftServer server = source.getServer();
        future.whenComplete((value, error) -> server.execute(() -> {
            if (error != null) {
                Throwable cause =
                        error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
                source.sendFailure(
                        Component.translatable("commands.zas.error.failed", String.valueOf(cause.getMessage())));
            } else {
                then.accept(value);
            }
        }));
    }

    /** A number to six significant digits, without exponent or trailing zeros. */
    public static String num(double v) {
        if (!Double.isFinite(v)) {
            return Double.toString(v);
        }
        return new BigDecimal(v).round(new MathContext(6)).stripTrailingZeros().toPlainString();
    }

    private static String pos(BlockPos p) {
        return p.getX() + " " + p.getY() + " " + p.getZ();
    }

    private static Component gasName(ResourceLocation id) {
        GasRegistry registry = ServerGasRegistry.current();
        int index = registry.indexOf(id.toString());
        return index < 0
                ? Component.literal(id.toString())
                : Component.translatableWithFallback(registry.definition(index).nameKey(), id.toString());
    }

    private static List<Component> mixtureLines(GasMixture mixture) {
        List<Component> out = new ArrayList<>();
        for (Map.Entry<ResourceLocation, Double> e : mixture.moles().entrySet()) {
            out.add(Component.translatable(
                    "commands.zas.gas_line",
                    gasName(e.getKey()),
                    e.getKey().toString(),
                    num(e.getValue()),
                    num(mixture.partialPressure(e.getKey()))));
        }
        return out;
    }

    private static Component outcome(MutationResult r) {
        return Component.translatable(
                "commands.zas.outcome." + r.outcome().name().toLowerCase(Locale.ROOT));
    }

    // ---- Inspection (OPS-003) -----------------------------------------------------------

    /** Where {@code /zas at} with no position looks: the eyes of the entity running it (API-015). */
    private static Vec3 eyes(CommandSourceStack source) {
        return source.getEntity() != null ? source.getEntity().getEyePosition() : source.getPosition();
    }

    /** {@code /zas at <pos>}: a block position's largest cell (API-015). */
    private static int at(CommandSourceStack source, BlockPos pos) {
        Component where = cell(PointResolver.resolve(source.getLevel(), pos));
        return at(source, Atmospheres.at(source.getLevel(), pos), where);
    }

    /** {@code /zas at} and {@code /zas at point <point>}: the cell holding an exact point (API-015). */
    private static int at(CommandSourceStack source, Vec3 point) {
        Component where = Component.translatable(
                "commands.zas.at.point",
                num(point.x) + " " + num(point.y) + " " + num(point.z),
                cell(PointResolver.resolve(source.getLevel(), point)));
        return at(source, Atmospheres.at(source.getLevel(), point), where);
    }

    /** A resolved cell: its block, and which of the block's cells it is when there are several. */
    private static Component cell(PointResolver.Cell cell) {
        if (cell.cells() > 1 && cell.sub() >= 0) {
            return Component.translatable("commands.zas.at.sub_cell", pos(cell.pos()), cell.sub(), cell.cells());
        }
        return Component.literal(pos(cell.pos()));
    }

    private static int at(CommandSourceStack source, AtmosphereSample s, Component where) {
        reply(
                source,
                Component.translatable(
                        "commands.zas.at.header",
                        where,
                        source.getLevel().dimension().location().toString(),
                        Component.translatable(
                                "commands.zas.state." + s.state().name().toLowerCase(Locale.ROOT)),
                        s.zone().map(id -> Long.toString(id.value())).orElse("-")));
        reply(source, Component.translatable("commands.zas.at.values", num(s.pressure()), num(s.temperature())));
        replyAll(source, mixtureLines(s.mixture()));
        return 1;
    }

    private static int zoneAt(CommandSourceStack source, BlockPos pos) throws CommandSyntaxException {
        ZoneInfo zone = Atmospheres.zoneAt(source.getLevel(), pos).orElseThrow(NO_ZONE_HERE::create);
        replyAll(source, zoneLines(zone));
        return 1;
    }

    private static int zoneAt(CommandSourceStack source, Vec3 point) throws CommandSyntaxException {
        ZoneInfo zone = Atmospheres.zoneAt(source.getLevel(), point).orElseThrow(NO_ZONE_HERE::create);
        replyAll(source, zoneLines(zone));
        return 1;
    }

    private static int zone(CommandSourceStack source, long id) throws CommandSyntaxException {
        ZoneInfo zone = Atmospheres.zone(source.getLevel(), new ZoneId(id)).orElseThrow(() -> UNKNOWN_ZONE.create(id));
        replyAll(source, zoneLines(zone));
        return 1;
    }

    /** A zone's details (API-011). */
    static List<Component> zoneLines(ZoneInfo zone) {
        List<Component> out = new ArrayList<>();
        out.add(Component.translatable(
                "commands.zas.zone.header",
                zone.id().value(),
                zone.cellCount(),
                num(zone.volume()),
                Component.translatable(zone.breached() ? "commands.zas.zone.breached" : "commands.zas.zone.enclosed"),
                Component.translatable(zone.paused() ? "commands.zas.zone.paused" : "commands.zas.zone.active")));
        out.add(Component.translatable(
                "commands.zas.zone.values",
                num(zone.mixture().pressure()),
                num(zone.mixture().temperature()),
                num(zone.mixture().totalMoles())));
        out.add(Component.translatable(
                "commands.zas.zone.edges",
                zone.edges().zoneEdges(),
                zone.edges().ambientEdges(),
                zone.edges().openFaces()));
        out.addAll(mixtureLines(zone.mixture()));
        return out;
    }

    private static int zones(CommandSourceStack source) throws CommandSyntaxException {
        DimensionAtmos d = service().dimension(source.getLevel());
        AtmosSnapshot snapshot = d == null ? null : d.published();
        List<ZoneView> zones = snapshot == null ? List.of() : snapshot.zones();
        long cells = 0;
        for (ZoneView z : zones) {
            cells += z.cellCount();
        }
        reply(
                source,
                Component.translatable(
                        "commands.zas.zones.header",
                        source.getLevel().dimension().location().toString(),
                        zones.size(),
                        cells,
                        snapshot == null ? -1 : snapshot.tick()));
        for (int i = 0; i < Math.min(LISTED_ZONES, zones.size()); i++) {
            ZoneView z = zones.get(i);
            reply(
                    source,
                    Component.translatable(
                            "commands.zas.zones.line", z.id(), z.cellCount(), num(z.pressure()), num(z.temperature())));
        }
        return zones.size();
    }

    // ---- Gas editing (OPS-009) ----------------------------------------------------------

    private static int add(CommandContext<CommandSourceStack> ctx, double temperature) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        service();
        ServerLevel level = source.getLevel();
        BlockPos pos = BlockPosArgument.getLoadedBlockPos(ctx, level, "pos");
        ResourceLocation gas = gas(ctx);
        double moles = DoubleArgumentType.getDouble(ctx, "moles");
        double t = temperature >= 0 ? temperature : Atmospheres.at(level, pos).temperature();
        SourceId by = sourceOf(source);
        CompletableFuture<MutationResult> f =
                GasMutations.add(level, PointResolver.pointIn(level, pos), new GasParcel(Map.of(gas, moles), t), by);
        reply(source, Component.translatable("commands.zas.gas.queued", by.toString()));
        whenDone(
                source,
                f,
                r -> source.sendSuccess(
                        () -> Component.translatable(
                                "commands.zas.gas.added",
                                num(r.moved().moles(gas)),
                                gasName(gas),
                                pos(pos),
                                outcome(r),
                                by.toString()),
                        true));
        return 1;
    }

    private static int remove(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        service();
        ServerLevel level = source.getLevel();
        BlockPos pos = BlockPosArgument.getLoadedBlockPos(ctx, level, "pos");
        ResourceLocation gas = gas(ctx);
        double moles = DoubleArgumentType.getDouble(ctx, "moles");
        SourceId by = sourceOf(source);
        CompletableFuture<MutationResult> f =
                GasMutations.remove(level, PointResolver.pointIn(level, pos), Map.of(gas, moles), by);
        reply(source, Component.translatable("commands.zas.gas.queued", by.toString()));
        whenDone(
                source,
                f,
                r -> source.sendSuccess(
                        () -> Component.translatable(
                                "commands.zas.gas.removed",
                                num(r.moved().moles(gas)),
                                gasName(gas),
                                pos(pos),
                                outcome(r),
                                by.toString()),
                        true));
        return 1;
    }

    /**
     * Sets the moles of a gas in the zone at a point, as of the published table, by queueing the
     * difference as an addition or a removal. Unzoned points have nothing to set; {@code add}
     * forms a zone.
     */
    private static int set(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        service();
        ServerLevel level = source.getLevel();
        BlockPos pos = BlockPosArgument.getLoadedBlockPos(ctx, level, "pos");
        ResourceLocation gas = gas(ctx);
        double target = DoubleArgumentType.getDouble(ctx, "moles");
        ZoneInfo zone = Atmospheres.zoneAt(level, pos).orElseThrow(NO_ZONE_HERE::create);
        double now = zone.mixture().moles(gas);
        double delta = target - now;
        SourceId by = sourceOf(source);
        Vec3 point = PointResolver.pointIn(level, pos);
        if (delta == 0) {
            reply(source, Component.translatable("commands.zas.gas.unchanged", gasName(gas), num(target)));
            return 1;
        }
        CompletableFuture<MutationResult> f = delta > 0
                ? GasMutations.add(
                        level,
                        point,
                        new GasParcel(Map.of(gas, delta), zone.mixture().temperature()),
                        by)
                : GasMutations.remove(level, point, Map.of(gas, -delta), by);
        reply(source, Component.translatable("commands.zas.gas.queued", by.toString()));
        whenDone(
                source,
                f,
                r -> source.sendSuccess(
                        () -> Component.translatable(
                                "commands.zas.gas.set",
                                gasName(gas),
                                zone.id().value(),
                                num(now),
                                num(
                                        delta > 0
                                                ? now + r.moved().moles(gas)
                                                : now - r.moved().moles(gas)),
                                outcome(r),
                                by.toString()),
                        true));
        return 1;
    }

    /** Adds a gas to every block position in a region that holds gas: {@code moles} per position. */
    private static int fill(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        service();
        ServerLevel level = source.getLevel();
        BlockPos from = BlockPosArgument.getLoadedBlockPos(ctx, level, "from");
        BlockPos to = BlockPosArgument.getLoadedBlockPos(ctx, level, "to");
        checkRegion(from, to);
        ResourceLocation gas = gas(ctx);
        double moles = DoubleArgumentType.getDouble(ctx, "moles");
        SourceId by = sourceOf(source);
        GeometryTable table = Geometries.table();
        List<CompletableFuture<MutationResult>> futures = new ArrayList<>();
        for (BlockPos p : BlockPos.betweenClosed(from, to)) {
            if (!level.isLoaded(p) || table.geometry(level.getBlockState(p)).volume() <= 0) {
                continue;
            }
            double t = Atmospheres.at(level, p).temperature();
            futures.add(
                    GasMutations.add(level, PointResolver.pointIn(level, p), new GasParcel(Map.of(gas, moles), t), by));
        }
        int count = futures.size();
        reply(source, Component.translatable("commands.zas.gas.fill_queued", count, by.toString()));
        whenDone(source, CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)), ignored -> {
            double moved = 0;
            for (CompletableFuture<MutationResult> f : futures) {
                moved += f.join().moved().moles(gas);
            }
            double total = moved;
            source.sendSuccess(
                    () -> Component.translatable(
                            "commands.zas.gas.filled", num(total), gasName(gas), count, by.toString()),
                    true);
        });
        return count;
    }

    /**
     * Empties the zones with cells in a region: removes all their gas, or one gas, as of the
     * published table. Unzoned positions are open air and have nothing to vent.
     */
    private static int vent(CommandContext<CommandSourceStack> ctx, ResourceLocation only)
            throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        AtmosService service = service();
        ServerLevel level = source.getLevel();
        BlockPos from = BlockPosArgument.getLoadedBlockPos(ctx, level, "from");
        BlockPos to = BlockPosArgument.getLoadedBlockPos(ctx, level, "to");
        checkRegion(from, to);
        DimensionAtmos d = service.dimension(level);
        AtmosSnapshot snapshot = d == null ? null : d.published();
        Map<Long, BlockPos> zones = new LinkedHashMap<>();
        if (snapshot != null) {
            for (BlockPos p : BlockPos.betweenClosed(from, to)) {
                // The zone of the cell a removal at this position acts on: its largest (API-015).
                ZoneView z = snapshot.zoneAt(
                        p.asLong(), PointResolver.resolve(level, p).sub());
                if (z != null && !zones.containsKey(z.id())) {
                    zones.put(z.id(), p.immutable());
                }
            }
        }
        SourceId by = sourceOf(source);
        List<CompletableFuture<MutationResult>> futures = new ArrayList<>();
        for (Map.Entry<Long, BlockPos> e : zones.entrySet()) {
            ZoneView z = snapshot.zone(e.getKey());
            Map<ResourceLocation, Double> moles = new TreeMap<>();
            for (int i = 0; i < z.gasCount(); i++) {
                ResourceLocation id =
                        ResourceLocation.parse(snapshot.registry().definition(i).id());
                if (z.moles(i) > 0 && (only == null || only.equals(id))) {
                    moles.put(id, z.moles(i));
                }
            }
            if (!moles.isEmpty()) {
                futures.add(GasMutations.remove(level, PointResolver.pointIn(level, e.getValue()), moles, by));
            }
        }
        int count = futures.size();
        reply(source, Component.translatable("commands.zas.gas.vent_queued", count, by.toString()));
        whenDone(source, CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)), ignored -> {
            double moved = 0;
            for (CompletableFuture<MutationResult> f : futures) {
                moved += f.join().moved().totalMoles();
            }
            double total = moved;
            source.sendSuccess(
                    () -> Component.translatable("commands.zas.gas.vented", num(total), count, by.toString()), true);
        });
        return count;
    }

    private static void checkRegion(BlockPos from, BlockPos to) throws CommandSyntaxException {
        long blocks = (long) (Math.abs(from.getX() - to.getX()) + 1)
                * (Math.abs(from.getY() - to.getY()) + 1)
                * (Math.abs(from.getZ() - to.getZ()) + 1);
        if (blocks > MAX_REGION_BLOCKS) {
            throw REGION_TOO_BIG.create(MAX_REGION_BLOCKS);
        }
    }

    // ---- Audit and the invariant checker (OPS-010, GEO-032) ------------------------------

    private static int audit(CommandContext<CommandSourceStack> ctx, boolean repair) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        AtmosService service = service();
        ServerLevel level = source.getLevel();
        BlockPos a = BlockPosArgument.getBlockPos(ctx, "from");
        BlockPos b = BlockPosArgument.getBlockPos(ctx, "to");
        GeometryAudit.Region region =
                GeometryAudit.Region.spanning(a.getX(), a.getY(), a.getZ(), b.getX(), b.getY(), b.getZ());
        if (region.sectionCount() > MAX_AUDIT_SECTIONS) {
            throw REGION_TOO_BIG.create(MAX_AUDIT_SECTIONS * 4096);
        }
        DimensionAtmos d = service.dimensionFor(level);
        reply(source, Component.translatable("commands.zas.audit.started", region.sectionCount()));
        whenDone(source, d.audit(region, LISTED_FINDINGS).orTimeout(30, TimeUnit.SECONDS), result -> {
            replyAll(source, auditLines(result));
            if (repair) {
                long[] stale = result.report().staleSections();
                d.resend(stale);
                source.sendSuccess(() -> Component.translatable("commands.zas.audit.repaired", stale.length), true);
            }
        });
        return 1;
    }

    /** An audit's result as chat lines. */
    static List<Component> auditLines(DimensionAtmos.AuditResult result) {
        GeometryAudit.Report r = result.report();
        List<Component> out = new ArrayList<>();
        out.add(Component.translatable(
                r.clean() ? "commands.zas.audit.clean" : "commands.zas.audit.differences",
                r.total(),
                r.sectionsChecked(),
                r.zonesChecked()));
        if (result.unloadedSections() > 0
                || result.pendingSections() > 0
                || r.zonesUnresolved() > 0
                || r.zonesPaused() > 0
                || r.heldPositions() > 0) {
            out.add(Component.translatable(
                    "commands.zas.audit.skipped",
                    result.unloadedSections(),
                    result.pendingSections(),
                    r.zonesUnresolved(),
                    r.zonesPaused(),
                    r.heldPositions()));
        }
        for (GeometryAudit.Kind kind : GeometryAudit.Kind.values()) {
            if (r.count(kind) > 0) {
                out.add(Component.translatable(
                        "commands.zas.audit.kind",
                        Component.translatable("zas.audit." + kind.name().toLowerCase(Locale.ROOT)),
                        r.count(kind)));
            }
        }
        for (GeometryAudit.Finding f : r.findings()) {
            out.add(Component.translatable(
                    "commands.zas.audit.finding",
                    Component.translatable("zas.audit." + f.kind().name().toLowerCase(Locale.ROOT)),
                    f.x() + " " + f.y() + " " + f.z(),
                    f.zoneId() == 0 ? "-" : Long.toString(f.zoneId())));
        }
        if (r.staleSections().length > 0) {
            out.add(Component.translatable("commands.zas.audit.repairable", r.staleSections().length));
        }
        return out;
    }

    private static int checkerStatus(CommandSourceStack source) {
        reply(
                source,
                Component.translatable(
                        ZasServerConfig.conservationChecker()
                                ? "commands.zas.checker.status_on"
                                : "commands.zas.checker.status_off",
                        Component.translatable(
                                ZasServerConfig.conservationCheckerOverridden()
                                        ? "commands.zas.checker.by_command"
                                        : "commands.zas.checker.by_config")));
        return ZasServerConfig.conservationChecker() ? 1 : 0;
    }

    private static int checkerSwitch(CommandSourceStack source, Boolean on) {
        ZasServerConfig.overrideConservationChecker(on);
        source.sendSuccess(
                () -> Component.translatable(
                        on == null
                                ? "commands.zas.checker.config"
                                : on ? "commands.zas.checker.on" : "commands.zas.checker.off"),
                true);
        return checkerStatus(source);
    }

    private static int checkerReport(CommandSourceStack source) throws CommandSyntaxException {
        DimensionAtmos d = service().dimension(source.getLevel());
        ConservationReport r = d == null ? null : d.lastConservation();
        if (r == null) {
            reply(source, Component.translatable("commands.zas.checker.no_report"));
            return 0;
        }
        replyAll(source, conservationLines(r, d.registry()));
        return r.conserved() ? 1 : 0;
    }

    private static int checkerRun(CommandSourceStack source) throws CommandSyntaxException {
        DimensionAtmos d = service().dimensionFor(source.getLevel());
        reply(source, Component.translatable("commands.zas.checker.queued"));
        whenDone(source, d.checkConservation(), r -> replyAll(source, conservationLines(r, d.registry())));
        return 1;
    }

    /** An invariant checker report as chat lines (CORE-073). */
    static List<Component> conservationLines(ConservationReport r, GasRegistry registry) {
        List<Component> out = new ArrayList<>();
        out.add(Component.translatable(
                r.conserved() ? "commands.zas.checker.conserved" : "commands.zas.checker.violations",
                r.tick(),
                r.violations().size()));
        for (int gas = 0; gas < registry.size(); gas++) {
            List<String> parts = new ArrayList<>();
            for (LedgerCategory c : LedgerCategory.values()) {
                if (gas < r.byCategory()[c.ordinal()].length) {
                    double v = r.recorded(c, gas);
                    if (v != 0) {
                        parts.add(c.name().toLowerCase(Locale.ROOT) + " " + num(v));
                    }
                }
            }
            if (!parts.isEmpty()) {
                out.add(Component.translatable(
                        "commands.zas.checker.ledger", registry.definition(gas).id(), String.join(", ", parts)));
            }
        }
        for (ConservationReport.Violation v : r.violations()) {
            out.add(Component.translatable(
                    "commands.zas.checker.violation",
                    v.gasId(),
                    num(v.unexplained()),
                    num(v.expected()),
                    num(v.actual())));
        }
        return out;
    }

    // ---- The profiler (OPS-004) ---------------------------------------------------------

    private static int profile(CommandSourceStack source) throws CommandSyntaxException {
        AtmosService service = service();
        DimensionAtmos d = service.dimensionFor(source.getLevel());
        whenDone(source, ProfileReport.of(service, d), report -> replyAll(source, report.lines()));
        return 1;
    }

    private static int profileReset(CommandSourceStack source) throws CommandSyntaxException {
        service().dimensionFor(source.getLevel()).profiler().reset();
        reply(source, Component.translatable("commands.zas.profile.reset"));
        return 1;
    }

    // ---- Datapacks (OPS-008) ------------------------------------------------------------

    private static int datapacks(CommandSourceStack source) {
        List<DatapackErrors.Problem> problems = DatapackErrors.recent();
        reply(source, Component.translatable("commands.zas.datapacks.header", problems.size()));
        for (DatapackErrors.Problem p : problems) {
            reply(source, Component.literal(p.toString()));
        }
        return problems.size();
    }
}
