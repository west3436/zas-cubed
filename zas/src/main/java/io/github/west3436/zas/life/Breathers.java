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
import io.github.west3436.zas.config.ZasServerConfig;
import java.util.function.Supplier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.event.entity.living.LivingBreatheEvent;
import net.neoforged.neoforge.event.entity.living.LivingDrownEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import org.jetbrains.annotations.Nullable;

/**
 * Breathing on the server (LIFE-001 to LIFE-009, LIFE-040 to LIFE-042, SPACE-002): each breathing
 * entity's breath check, its air meter, hypoxia and respiration.
 *
 * <p><b>The breath check</b> runs every {@code check_interval_ticks} (2 s by default, LIFE-007)
 * for each player and each living entity in {@code zas:breathers}, staggered by entity id so the
 * entities spread over the interval, and again at once whenever the eyes enter or leave a fluid.
 * It takes the verdict ({@link BreathingRules#assess}), applies the gases' hazard profiles for the
 * seconds since the last check ({@link GasHazards}) and adds the O2 used to its zone's respiration
 * ({@link Respiration}). Rates are per second, so the interval changes smoothness, not speed.
 *
 * <p><b>Unresolved atmosphere.</b> Where the atmosphere at the eyes is {@code UNRESOLVED}, such
 * as for the moment a loading room's zone isn't back yet, its mixture is only a last-known guess,
 * so a check holds the state instead (LIFE-048): the last verdict, its air drain and the gas
 * exposures stay, and nothing is charged or breathed. Before any verdict, checks run every tick
 * until the atmosphere resolves, and the air meter is left to vanilla.
 *
 * <p><b>The air meter</b> follows NeoForge's breathing hook, which runs on every living entity's
 * tick. This listens at normal priority to {@link LivingBreatheEvent}. While the last check found
 * too little O2 it sets {@code canBreathe} to false and scales the air consumed to the vanilla
 * underwater rate × (1 − pO2 / minimum), carrying fractions between ticks (LIFE-003). Otherwise it
 * leaves the event alone, so vanilla refills the meter in breathable air (LIFE-009), drowning
 * stays vanilla's under water (LIFE-022), and another mod's "can't breathe" stands (SPACE-002).
 * Once the meter is empty it runs at the vanilla rate, so {@link LivingDrownEvent} reports
 * drowning once a second as in vanilla; for a hypoxic entity this cancels that and deals 2 HP of
 * {@link LifeDamage#HYPOXIA} instead of drowning damage (LIFE-008).
 *
 * <p><b>Client.</b> The hook also runs on the client, where this does nothing: until the client
 * breathing sync (LIFE-023, #36), the local player's air bar refills between the server's
 * updates.
 */
public final class Breathers {
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, ZasMod.MOD_ID);

    /** Each breathing entity's state between checks; not saved. */
    public static final Supplier<AttachmentType<BreathState>> STATE = ATTACHMENTS.register(
            "breath", () -> AttachmentType.builder(BreathState::new).build());

    private Breathers() {}

    /**
     * Registers the attachment, the breathing listeners, respiration's flush and the hazard
     * profiles. Called once, from the mod constructor.
     */
    public static void register(IEventBus modBus, IEventBus gameBus) {
        ATTACHMENTS.register(modBus);
        HazardProfiles.register(gameBus);
        gameBus.addListener(EventPriority.NORMAL, LivingBreatheEvent.class, Breathers::onBreathe);
        gameBus.addListener(EventPriority.NORMAL, LivingDrownEvent.class, Breathers::onDrown);
        gameBus.addListener((ServerTickEvent.Post e) -> {
            if (e.getServer().getTickCount() % ZasServerConfig.scheduler().periodTicks() == 0) {
                Respiration.flush(e.getServer());
            }
        });
        gameBus.addListener((ServerStoppedEvent e) -> Respiration.clear());
    }

    private static void onBreathe(LivingBreatheEvent e) {
        LivingEntity entity = e.getEntity();
        if (!(entity.level() instanceof ServerLevel level) || !BreathingRules.breathes(entity)) {
            return;
        }
        BreathState state = beat(entity, level, level.getGameTime());
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
            return;
        }
        BreathState state = entity.getExistingDataOrNull(STATE);
        if (state == null || !state.hypoxic()) {
            return;
        }
        e.setCanceled(true);
        if (e.isDrowning()) {
            entity.setAirSupply(0);
            if (e.getDamageAmount() > 0) {
                entity.hurt(LifeDamage.source(entity, LifeDamage.HYPOXIA), e.getDamageAmount());
            }
        }
    }

    /**
     * One tick of a breathing entity's breathing at game time {@code now}: its breath check if one
     * is due, or if its eyes entered or left a fluid since the last.
     *
     * @return its state
     */
    private static BreathState beat(LivingEntity entity, ServerLevel level, long now) {
        BreathState state = entity.getData(STATE);
        int interval = BreathingConfig.checkIntervalTicks();
        boolean inFluid = Breathing.vanilla(entity).source() == BreathSource.FLUID;
        if (due(state, entity, now, interval) || (state.lastCheck() >= 0 && state.inFluid() != inFluid)) {
            check(entity, level, state, now, interval);
        }
        return state;
    }

    /** Whether an entity's check is due: on its own tick of the interval, or if it missed one. */
    private static boolean due(BreathState state, LivingEntity entity, long now, int interval) {
        long last = state.lastCheck();
        if (last < 0) {
            return true;
        }
        if (now <= last) {
            return false;
        }
        return Math.floorMod(now + entity.getId(), interval) == 0 || now - last >= 2L * interval;
    }

    /**
     * Runs one breath check at game time {@code now}.
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
            }
            return;
        }
        state.checked(verdict, a.drain(), verdict.source() == BreathSource.FLUID, now);
        if (verdict.reason() == BreathReason.EXEMPT || verdict.breath().isEmpty() || sample == null) {
            state.clearExposure();
            return;
        }
        GasHazards.apply(entity, verdict.breath().get(), seconds, state, interval + GasHazards.EFFECT_GRACE_TICKS);
        if (sample.state() == AtmosphereState.ZONED && sample.zone().isPresent()) {
            Respiration.breathe(
                    level,
                    sample.zone().get().value(),
                    entity.getEyePosition(),
                    BreathingConfig.respirationMolesPerSecond() * seconds);
        }
    }

    /**
     * The breathing verdict for an entity where it is now (API-014): the breathing rules' verdict,
     * or while the atmosphere at its eyes is unresolved, the verdict its checks are holding, if
     * they have one (LIFE-048). Server thread.
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
     * touches the air meter: the breath check, if one is due. A test drives the cadence with it.
     *
     * @return the entity's state after it
     */
    public static BreathState tickAt(LivingEntity entity, long now) {
        return beat(entity, (ServerLevel) entity.level(), now);
    }

    /**
     * For GameTests: a breath check at game time {@code now}, whether or not one is due.
     *
     * @return the entity's state after it
     */
    public static BreathState checkAt(LivingEntity entity, long now) {
        BreathState state = entity.getData(STATE);
        check(entity, (ServerLevel) entity.level(), state, now, BreathingConfig.checkIntervalTicks());
        return state;
    }

    /**
     * For GameTests: applies the hazard profiles to an entity as a breath check would if it had
     * breathed {@code breath} for {@code seconds}, with its exposure carried over from earlier calls.
     */
    public static void breatheForTest(LivingEntity entity, GasMixture breath, double seconds) {
        GasHazards.apply(
                entity,
                breath,
                seconds,
                entity.getData(STATE),
                BreathingConfig.checkIntervalTicks() + GasHazards.EFFECT_GRACE_TICKS);
    }
}
