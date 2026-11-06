package io.github.west3436.zas.life;

import io.github.west3436.zas.api.life.BreathSource;
import io.github.west3436.zas.api.life.Breathability;
import io.github.west3436.zas.api.life.Breathing;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.entity.living.LivingBreatheEvent;
import net.neoforged.neoforge.event.entity.living.LivingDrownEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.Nullable;

/**
 * The local player's breathing on the client (LIFE-023): the state the server last synced
 * ({@link BreathSyncPayload}), and NeoForge's breathing hook run with it, so the air bar drains
 * as the server's does rather than refilling between the server's updates.
 *
 * <p>While the server finds the player short of O₂ and the player's eyes aren't in a fluid, the
 * hook here sets {@code canBreathe} to false and drains the air at the synced fraction of the
 * vanilla rate, carrying fractions between ticks, as {@link Breathers} does on the server. Once
 * the meter is empty it stops vanilla's drowning, as the server does, so no bubbles show; the
 * server deals the hypoxia damage. Otherwise it leaves the hook to vanilla: in breathable air, and
 * with the eyes in a fluid, where the server applies vanilla's rule too (LIFE-022). The exception
 * is a player the server finds breathing from its tank, for whom eyes in a fluid change nothing
 * (LIFE-047). The server's air value still arrives with the entity's data and corrects any drift.
 *
 * <p>Holds no client-only classes, so the payload registration that names its handler loads on a
 * dedicated server too. Client thread only.
 */
public final class ClientBreath {
    /** The local player's. */
    public static final ClientBreath LOCAL = new ClientBreath();

    private BreathSyncPayload state = BreathSyncPayload.NONE;
    private double carry;

    /** A fresh state with nothing synced; tests make their own. */
    public ClientBreath() {}

    /** The state the server last synced, or {@link BreathSyncPayload#NONE}. */
    public BreathSyncPayload state() {
        return state;
    }

    /** Takes a state the server synced. */
    public void accept(BreathSyncPayload payload) {
        state = payload;
        if (!payload.hypoxic()) {
            carry = 0;
        }
    }

    /** Forgets the synced state, as when leaving a server. */
    public void clear() {
        accept(BreathSyncPayload.NONE);
    }

    /**
     * The breathing verdict for the local player (API-014): the server's, without its breath gas,
     * while the player's eyes are where the server last checked them, in a fluid or out of one, or
     * wherever they are while it breathes from its tank (LIFE-047); otherwise, and before any sync,
     * vanilla's.
     */
    public Breathability verdict(LivingEntity player) {
        Breathability vanilla = Breathing.vanilla(player);
        Breathability synced = state.verdict();
        if (synced == null) {
            return vanilla;
        }
        boolean sameSide = (synced.source() == BreathSource.FLUID) == (vanilla.source() == BreathSource.FLUID);
        return sameSide || fromTank() ? synced : vanilla;
    }

    /**
     * NeoForge's breathing hook for the local player: drains the air as the server does while it
     * finds the player short of O₂ and the eyes aren't in a fluid, or that breath is from its tank,
     * and lets it breathe with its eyes in a fluid while its tank gives it enough (LIFE-047).
     *
     * @param eyesInFluid whether the player's eyes are in a fluid now, where vanilla's rule applies
     * @return whether it changed the event
     */
    public boolean breathe(LivingBreatheEvent e, boolean eyesInFluid) {
        if (eyesInFluid && fromTank() && state.verdict().canBreathe()) {
            e.setCanBreathe(true);
            return true;
        }
        if (!predicts(eyesInFluid)) {
            return false;
        }
        e.setCanBreathe(false);
        if (e.getEntity().getAirSupply() > 0) {
            e.setConsumeAirAmount(drainThisTick(e.getConsumeAirAmount()));
        }
        return true;
    }

    /**
     * NeoForge's drowning hook for the local player: while the server finds it short of O₂, stops
     * vanilla's drowning and holds the meter at empty, as the server does.
     *
     * @param eyesInFluid whether the player's eyes are in a fluid now
     */
    public void drown(LivingDrownEvent e, boolean eyesInFluid) {
        if (!predicts(eyesInFluid)) {
            return;
        }
        e.setCanceled(true);
        if (e.isDrowning()) {
            e.getEntity().setAirSupply(0);
        }
    }

    private boolean predicts(boolean eyesInFluid) {
        return state.hypoxic() && (!eyesInFluid || fromTank());
    }

    /** Whether the server last found the player breathing from its tank. */
    private boolean fromTank() {
        return state.verdict() != null && state.verdict().source() == BreathSource.INTERNALS;
    }

    /** As {@link BreathState#drainThisTick}: the vanilla amount times the synced drain, with carry. */
    private int drainThisTick(int consume) {
        carry += consume * state.drain();
        int whole = (int) carry;
        carry -= whole;
        return whole;
    }

    // ---- Hooks ---------------------------------------------------------------------------

    /** Whether an entity is the local player, whose breathing the client predicts. */
    public static boolean isLocal(@Nullable LivingEntity entity) {
        return entity instanceof Player p && p.level().isClientSide() && p.isLocalPlayer();
    }

    /** Whether an entity's eyes are in a fluid, by vanilla's rule. */
    static boolean eyesInFluid(LivingEntity entity) {
        return Breathing.vanilla(entity).source() == BreathSource.FLUID;
    }

    static void handle(BreathSyncPayload payload, IPayloadContext context) {
        LOCAL.accept(payload);
    }
}
