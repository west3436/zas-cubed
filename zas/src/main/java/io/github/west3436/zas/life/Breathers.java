package io.github.west3436.zas.life;

import io.github.west3436.zas.ZasMod;
import io.github.west3436.zas.api.atmosphere.AtmosphereSample;
import io.github.west3436.zas.api.atmosphere.AtmosphereState;
import io.github.west3436.zas.api.gas.GasMixture;
import io.github.west3436.zas.api.life.BreathReason;
import io.github.west3436.zas.api.life.BreathSource;
import io.github.west3436.zas.api.life.Breathability;
import io.github.west3436.zas.api.life.Breathing;
import io.github.west3436.zas.config.BreathingConfig;
import io.github.west3436.zas.config.HazardConfig;
import io.github.west3436.zas.config.ZasServerConfig;
import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.living.LivingBreatheEvent;
import net.neoforged.neoforge.event.entity.living.LivingDrownEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import org.jetbrains.annotations.Nullable;

/**
 * Breathing and hazards on the server (LIFE-001 to LIFE-015, LIFE-030 to LIFE-042, SPACE-002):
 * each living entity's check, its air meter, hypoxia, respiration, and the pressure and
 * temperature hazards.
 *
 * <p><b>The check</b> runs every {@code check_interval_ticks} (2 s by default, LIFE-007) for each
 * player, each living entity in {@code zas:breathers}, and each other living entity that pressure
 * and temperature can harm (not in {@code zas:atmosphere_immune}, LIFE-015). Entities are staggered
 * by id so they spread over the interval (LIFE-033), and a check runs again at once whenever the
 * eyes enter or leave a fluid. A server tick runs at most {@code max_checks_per_tick} checks; one
 * due beyond that waits for the next tick (LIFE-038). A check takes the verdict ({@link
 * BreathingRules#assess}), applies the gases' hazard profiles for the seconds since the last check
 * ({@link GasHazards}), adds the O2 used to its zone's respiration ({@link Respiration}) and deals
 * the pressure and temperature damage ({@link EnvironmentHazards}). Rates are per second, so the
 * interval changes smoothness, not speed.
 *
 * <p><b>Unresolved atmosphere.</b> Where the atmosphere at the eyes is {@code UNRESOLVED}, such
 * as for the moment a loading room's zone isn't back yet, its mixture is only a last-known guess,
 * so a check holds the state instead (LIFE-048): the last verdict, its air drain and the gas
 * exposures stay, and nothing is charged or breathed. Before any verdict, a breather's checks run
 * every tick until the atmosphere resolves, and its air meter is left to vanilla; anything else
 * waits for its own tick of the interval.
 *
 * <p><b>The air meter</b> follows NeoForge's breathing hook, which runs on every living entity's
 * tick. This listens at normal priority to {@link LivingBreatheEvent}. While the last check found
 * too little O2 it sets {@code canBreathe} to false and scales the air consumed to the vanilla
 * underwater rate × (1 − pO2 / minimum), carrying fractions between ticks (LIFE-003). Otherwise it
 * leaves the event alone, so vanilla refills the meter in breathable air (LIFE-009), drowning
 * stays vanilla's under water (LIFE-022), and another mod's "can't breathe" stands (SPACE-002).
 * Once the meter is empty it runs at the vanilla rate, so {@link LivingDrownEvent} reports
 * drowning once a second as in vanilla; for a hypoxic entity this cancels that and deals 2 HP of
 * {@link LifeDamage#HYPOXIA} instead of drowning damage (LIFE-008), times the hazard's scale.
 *
 * <p><b>Client.</b> The hook also runs on the client, where vanilla alone would refill the local
 * player's air bar between the server's updates. So each player's state is synced to its own
 * client ({@link BreathSyncPayload}) when it changes, at most 4 times a second, and on the client
 * {@link ClientBreath} drains the local player's bar with it as this does on the server (LIFE-023).
 * Other entities on the client are left to vanilla.
 */
public final class Breathers {
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, ZasMod.MOD_ID);

    /** Each checked entity's state between checks; not saved. */
    public static final Supplier<AttachmentType<BreathState>> STATE = ATTACHMENTS.register(
            "breath", () -> AttachmentType.builder(BreathState::new).build());

    /** The per-tick cap on checks over every dimension (LIFE-038). */
    private static final CheckBudget BUDGET = new CheckBudget(BreathingConfig::maxChecksPerTick);

    private Breathers() {}

    /**
     * Registers the attachment, the breathing listeners, respiration's flush, the hazard profiles
     * and the protection data. Called once, from the mod constructor.
     */
    public static void register(IEventBus modBus, IEventBus gameBus) {
        ATTACHMENTS.register(modBus);
        modBus.addListener(Breathers::registerPayloads);
        HazardProfiles.register(gameBus);
        Protections.register(gameBus);
        gameBus.addListener(EventPriority.NORMAL, LivingBreatheEvent.class, Breathers::onBreathe);
        gameBus.addListener(EventPriority.NORMAL, LivingDrownEvent.class, Breathers::onDrown);
        gameBus.addListener((ServerTickEvent.Post e) -> {
            if (e.getServer().getTickCount() % ZasServerConfig.scheduler().periodTicks() == 0) {
                Respiration.flush(e.getServer());
            }
        });
        gameBus.addListener((ServerStoppedEvent e) -> Respiration.clear());
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(BreathSyncPayload.TYPE, BreathSyncPayload.STREAM_CODEC, ClientBreath::handle);
    }

    private static void onBreathe(LivingBreatheEvent e) {
        LivingEntity entity = e.getEntity();
        if (ClientBreath.isLocal(entity)) {
            ClientBreath.LOCAL.breathe(e, ClientBreath.eyesInFluid(entity));
            return;
        }
        if (!(entity.level() instanceof ServerLevel level) || !BreathingRules.checked(entity)) {
            return;
        }
        BreathState state = beat(entity, level, level.getGameTime(), BUDGET);
        if (state.hypoxic()) {
            e.setCanBreathe(false);
            if (entity.getAirSupply() > 0) {
                e.setConsumeAirAmount(state.drainThisTick(e.getConsumeAirAmount()));
            }
        }
    }

    private static void onDrown(LivingDrownEvent e) {
        LivingEntity entity = e.getEntity();
        if (entity.level().isClientSide()) {
            if (ClientBreath.isLocal(entity)) {
                ClientBreath.LOCAL.drown(e, ClientBreath.eyesInFluid(entity));
            }
            return;
        }
        BreathState state = entity.getExistingDataOrNull(STATE);
        if (state == null || !state.hypoxic()) {
            return;
        }
        e.setCanceled(true);
        if (e.isDrowning()) {
            entity.setAirSupply(0);
            double damage =
                    e.getDamageAmount() * HazardConfig.scale(entity.level().dimension(), HazardType.HYPOXIA);
            if (damage > 0) {
                entity.hurt(LifeDamage.source(entity, LifeDamage.HYPOXIA), (float) damage);
            }
        }
    }

    /**
     * One tick of a checked entity at game time {@code now}: its check if one is due, or if its eyes
     * entered or left a fluid since the last, and the budget has room for it.
     *
     * @return its state
     */
    private static BreathState beat(LivingEntity entity, ServerLevel level, long now, CheckBudget budget) {
        BreathState state = entity.getData(STATE);
        int interval = BreathingConfig.checkIntervalTicks();
        boolean inFluid = Breathing.vanilla(entity).source() == BreathSource.FLUID;
        boolean fluidChanged = state.lastCheck() >= 0 && state.inFluid() != inFluid;
        if (due(state, entity, now, interval) || fluidChanged) {
            if (budget.take(now)) {
                check(entity, level, state, now, interval);
            } else {
                state.defer();
            }
        }
        if (entity instanceof ServerPlayer player) {
            sync(player, state, now);
        }
        return state;
    }

    /**
     * Sends a player's breathing state to its client if a sync is due (LIFE-023, DATA-005): to that
     * player only, when it changes, at most 4 times a second.
     */
    private static void sync(ServerPlayer player, BreathState state, long now) {
        BreathSyncPayload payload = state.syncDue(now);
        // Fake players have no client; every real client has the channel (zas is required on both sides).
        if (payload != null
                && !(player instanceof FakePlayer)
                && player.connection.hasChannel(BreathSyncPayload.TYPE)) {
            player.connection.send(payload);
        }
    }

    /**
     * Whether an entity's check is due: on its own tick of the interval, if it missed one, or if the
     * per-tick cap put it off. Before its first verdict, at once, then each tick for a breather,
     * whose meter waits on it, or each interval for anything else.
     */
    private static boolean due(BreathState state, LivingEntity entity, long now, int interval) {
        long last = state.lastCheck();
        boolean ownTick = Math.floorMod(now + entity.getId(), interval) == 0;
        if (last < 0) {
            long attempt = state.lastAttempt();
            if (attempt < 0) {
                return true;
            }
            return now > attempt && (state.deferred() || BreathingRules.breathes(entity) || ownTick);
        }
        if (now <= last) {
            return false;
        }
        return ownTick || state.deferred() || now - last >= 2L * interval;
    }

    /**
     * Runs one check at game time {@code now}.
     *
     * @param interval the check interval in game ticks, which bounds the seconds counted
     */
    private static void check(LivingEntity entity, ServerLevel level, BreathState state, long now, int interval) {
        long last = state.lastCheck();
        double seconds = last < 0 ? 0 : Math.clamp(now - last, 0, interval) / 20.0;
        BreathingRules.Assessment a = BreathingRules.assess(entity);
        Breathability verdict = a.verdict();
        AtmosphereSample sample = a.sample();
        if (holds(verdict, sample)) {
            if (state.verdict() != null) {
                state.held(now);
            } else {
                state.waited(now);
            }
            return;
        }
        state.checked(verdict, a.drain(), verdict.source() == BreathSource.FLUID, now);
        if (verdict.reason() == BreathReason.EXEMPT) {
            state.clearExposure();
            return;
        }
        ResourceKey<Level> dimension = level.dimension();
        Optional<GasMixture> breath = verdict.breath();
        if (breath.isPresent() && HazardConfig.enabled(dimension, HazardType.TOXIC_GAS)) {
            GasHazards.apply(
                    entity,
                    breath.get(),
                    seconds,
                    state,
                    interval + GasHazards.EFFECT_GRACE_TICKS,
                    HazardConfig.scale(dimension, HazardType.TOXIC_GAS));
        } else {
            state.clearExposure();
        }
        if (a.internals() != null) {
            Internals.breathe(level, entity, a.internals().supply(), seconds);
        } else if (breath.isPresent()
                && sample != null
                && sample.state() == AtmosphereState.ZONED
                && sample.zone().isPresent()) {
            Respiration.breathe(
                    level,
                    sample.zone().get().value(),
                    entity.getEyePosition(),
                    BreathingConfig.respirationMolesPerSecond() * seconds);
        }
        if (a.exposure() != null) {
            EnvironmentHazards.apply(entity, a.exposure(), seconds);
        }
    }

    /**
     * The verdict for an entity where it is now (API-014): the rules' verdict, or while the
     * atmosphere at its eyes is unresolved, the verdict its checks are holding, if they have one
     * (LIFE-048). Server thread.
     */
    public static Breathability verdict(LivingEntity entity) {
        BreathingRules.Assessment a = BreathingRules.assess(entity);
        if (holds(a.verdict(), a.sample())) {
            BreathState state = entity.getExistingDataOrNull(STATE);
            if (state != null && state.verdict() != null) {
                return state.verdict();
            }
        }
        return a.verdict();
    }

    /**
     * Whether a check holds the entity's state instead of acting on its verdict: when the
     * atmosphere at the eyes is {@code UNRESOLVED}, whose mixture is only a last-known guess, such
     * as the ambient while a loading room's zone isn't back yet (LIFE-048, API-010). An exempt
     * player has nothing to hold.
     */
    private static boolean holds(Breathability verdict, @Nullable AtmosphereSample sample) {
        return sample != null
                && sample.state() == AtmosphereState.UNRESOLVED
                && verdict.reason() != BreathReason.EXEMPT;
    }

    // ---- For GameTests ---------------------------------------------------------------

    /**
     * For GameTests: what the breathing hook does on a tick at game time {@code now}, before it
     * touches the air meter: the check, if one is due and the server's budget has room. A test
     * drives the cadence with it.
     *
     * @return the entity's state after it
     */
    public static BreathState tickAt(LivingEntity entity, long now) {
        return tickAt(entity, now, BUDGET);
    }

    /**
     * For GameTests: {@link #tickAt(LivingEntity, long)} against a budget of the test's own, so a
     * test of the cap (LIFE-038) leaves the server's alone.
     */
    public static BreathState tickAt(LivingEntity entity, long now, CheckBudget budget) {
        return beat(entity, (ServerLevel) entity.level(), now, budget);
    }

    /**
     * For GameTests: a check at game time {@code now}, whether or not one is due, outside the cap.
     *
     * @return the entity's state after it
     */
    public static BreathState checkAt(LivingEntity entity, long now) {
        BreathState state = entity.getData(STATE);
        check(entity, (ServerLevel) entity.level(), state, now, BreathingConfig.checkIntervalTicks());
        return state;
    }

    /**
     * For GameTests: applies the hazard profiles to an entity as a check would if it had breathed
     * {@code breath} for {@code seconds}, with its exposure carried over from earlier calls.
     */
    public static void breatheForTest(LivingEntity entity, GasMixture breath, double seconds) {
        GasHazards.apply(
                entity,
                breath,
                seconds,
                entity.getData(STATE),
                BreathingConfig.checkIntervalTicks() + GasHazards.EFFECT_GRACE_TICKS,
                HazardConfig.scale(entity.level().dimension(), HazardType.TOXIC_GAS));
    }

    /**
     * For GameTests: deals the pressure and temperature damage a check would if the entity had
     * been in {@code around} for {@code seconds}, with its own protection, in its dimension.
     *
     * @return the exposure
     */
    public static EnvironmentHazards.Exposure exposeForTest(LivingEntity entity, GasMixture around, double seconds) {
        EnvironmentHazards.Exposure exposure = EnvironmentHazards.exposure(entity, around);
        EnvironmentHazards.apply(entity, exposure, seconds);
        return exposure;
    }
}
