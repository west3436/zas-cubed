package io.github.west3436.zas.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.west3436.zas.debug.ClientDebugOverlay;
import io.github.west3436.zas.debug.DebugLayer;
import io.github.west3436.zas.debug.DebugOverlayPayload;
import io.github.west3436.zas.engine.Face;
import io.github.west3436.zas.engine.FaceMask;
import io.github.west3436.zas.engine.Geometry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.debug.DebugRenderer;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/**
 * Draws the operator debug overlay from the last {@link DebugOverlayPayload} (CLIENT-005,
 * CLIENT-011 to CLIENT-013), after particles, with translucent quads and floating labels:
 *
 * <ul>
 *   <li><b>zones:</b> the outer faces of each zone's cells, tinted by zone id; paused zones grey,
 *       unresolved regions magenta; a label per zone with its id, pressure and temperature
 *   <li><b>edges:</b> each open face out of a zone, coloured by kind (zone direct cyan, zone
 *       boundary blue, ambient direct red, ambient boundary orange), with a label per edge giving
 *       its pressure difference
 *   <li><b>cells:</b> for the block under the crosshair, each face's 16×16 seal mask (red where
 *       covered) and the footprint of each sub-cell in its own colour, with a label
 *   <li><b>hotspots:</b> an orange box and a temperature label per hotspot
 * </ul>
 *
 * <p>The geometry of a payload is built once, when it arrives (at most once per atmos tick), and
 * drawn each frame. Render thread only.
 */
public final class DebugOverlayRenderer {
    private static final int PAUSED = 0x70808080;
    private static final int UNRESOLVED = 0x60FF00FF;
    private static final int ZONE_DIRECT = 0xB000E5FF;
    private static final int ZONE_BOUNDARY = 0xB03060FF;
    private static final int AMBIENT_DIRECT = 0xB0FF3030;
    private static final int AMBIENT_BOUNDARY = 0xB0FF9900;
    private static final int COVERED = 0x90FF2020;
    private static final int HOTSPOT = 0x80FF8000;
    private static final int[] SUB_CELLS = {
        0x8030FF30, 0x80FFFF30, 0x8030FFFF, 0x80FF30FF, 0x803080FF, 0x80FFA030, 0x80A0FF80, 0x80FFFFFF
    };
    private static final double LABEL_RANGE = 48;

    private static DebugOverlayPayload built;
    private static Mesh mesh;
    private static BlockPos probedPos;
    private static Geometry probedGeometry;
    private static Quads probeQuads;

    private DebugOverlayRenderer() {}

    static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        DebugOverlayPayload payload = ClientDebugOverlay.current();
        Minecraft mc = Minecraft.getInstance();
        if (payload == null
                || mc.level == null
                || !mc.level.dimension().location().equals(payload.dimension())) {
            return;
        }
        if (payload != built) {
            mesh = new Mesh(payload);
            built = payload;
        }
        Vec3 camera = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        Matrix4f pose = poseStack.last().pose();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        BlockPos origin = payload.origin();
        float ox = (float) (origin.getX() - camera.x);
        float oy = (float) (origin.getY() - camera.y);
        float oz = (float) (origin.getZ() - camera.z);

        VertexConsumer quads = buffers.getBuffer(RenderType.debugQuads());
        mesh.quads.draw(quads, pose, ox, oy, oz);
        List<Label> labels = new ArrayList<>(mesh.labels);
        if (payload.has(DebugLayer.CELLS)) {
            probe(mc, payload, labels);
            if (probeQuads != null) {
                probeQuads.draw(
                        quads,
                        pose,
                        (float) (probedPos.getX() - camera.x),
                        (float) (probedPos.getY() - camera.y),
                        (float) (probedPos.getZ() - camera.z));
            }
        }
        buffers.endBatch(RenderType.debugQuads());

        for (Label l : labels) {
            if (camera.distanceToSqr(l.x, l.y, l.z) <= LABEL_RANGE * LABEL_RANGE) {
                DebugRenderer.renderFloatingText(
                        poseStack, buffers, l.text, l.x, l.y, l.z, l.color | 0xFF000000, 0.025F, true, 0, true);
            }
        }
        buffers.endBatch();
    }

    // ---- CLIENT-012: the block under the crosshair -------------------------------------------

    private static void probe(Minecraft mc, DebugOverlayPayload payload, List<Label> labels) {
        HitResult hit = mc.hitResult;
        if (!(hit instanceof BlockHitResult block) || hit.getType() != HitResult.Type.BLOCK) {
            probeQuads = null;
            return;
        }
        BlockPos pos = block.getBlockPos();
        Geometry g = payload.probe().geometryAt(pos);
        if (g == null) {
            probeQuads = null;
            return;
        }
        if (!pos.equals(probedPos) || g != probedGeometry) {
            probedPos = pos.immutable();
            probedGeometry = g;
            probeQuads = sealAndSubCells(g);
        }
        String name = mc.level.getBlockState(pos).getBlock().getName().getString();
        List<String> sealed = new ArrayList<>();
        for (Face f : Face.values()) {
            if (g.isFaceFull(f)) {
                sealed.add(f.name().toLowerCase(Locale.ROOT));
            }
        }
        double x = pos.getX() + 0.5;
        double z = pos.getZ() + 0.5;
        labels.add(new Label(
                I18n.get("debug.zas.cell", name, g.cellCount(), format(g.volume())), x, pos.getY() + 1.6, z, 0xFFFFFF));
        labels.add(new Label(
                I18n.get("debug.zas.cell.sealed", sealed.isEmpty() ? "-" : String.join(", ", sealed)),
                x,
                pos.getY() + 1.35,
                z,
                0xFF8080));
    }

    /**
     * Each face's 16×16 grid: covered bits red, free bits in the colour of the sub-cell whose
     * footprint holds them (GEO-003, GEO-004). Runs of one colour along u are merged.
     */
    private static Quads sealAndSubCells(Geometry g) {
        Quads q = new Quads();
        for (Face f : Face.values()) {
            FaceMask cover = g.coverage(f);
            for (int v = 0; v < FaceMask.SIZE; v++) {
                int start = 0;
                int run = colourAt(g, cover, f, 0, v);
                for (int u = 1; u <= FaceMask.SIZE; u++) {
                    int c = u < FaceMask.SIZE ? colourAt(g, cover, f, u, v) : Integer.MIN_VALUE;
                    if (c != run) {
                        if (run != 0) {
                            q.rect(0, 0, 0, f.ordinal(), start / 16F, v / 16F, u / 16F, (v + 1) / 16F, 0.004F, run);
                        }
                        start = u;
                        run = c;
                    }
                }
            }
        }
        return q;
    }

    private static int colourAt(Geometry g, FaceMask cover, Face f, int u, int v) {
        if (cover.get(u, v)) {
            return COVERED;
        }
        for (int i = 0; i < g.cellCount(); i++) {
            if (g.cell(i).footprint(f).get(u, v)) {
                return SUB_CELLS[i % SUB_CELLS.length];
            }
        }
        return 0;
    }

    // ---- The payload's geometry ---------------------------------------------------------------

    /** The quads and labels of one payload, relative to its origin. */
    private static final class Mesh {
        final Quads quads = new Quads();
        final List<Label> labels = new ArrayList<>();

        Mesh(DebugOverlayPayload p) {
            BlockPos o = p.origin();
            if (p.has(DebugLayer.ZONES)) {
                zones(p, o);
            }
            if (p.has(DebugLayer.EDGES)) {
                edges(p, o);
            }
            if (p.has(DebugLayer.HOTSPOTS)) {
                for (DebugOverlayPayload.Hotspot h : p.hotspots()) {
                    for (int f = 0; f < 6; f++) {
                        quads.rect(h.dx(), h.dy(), h.dz(), f, 0, 0, 1, 1, 0.02F, HOTSPOT);
                    }
                    labels.add(new Label(
                            I18n.get("debug.zas.hotspot", format(h.temperature())),
                            o.getX() + h.dx() + 0.5,
                            o.getY() + h.dy() + 1.3,
                            o.getZ() + h.dz() + 0.5,
                            HOTSPOT & 0xFFFFFF));
                }
            }
            if (p.truncated()) {
                labels.add(new Label(
                        I18n.get("debug.zas.truncated"), o.getX() + 0.5, o.getY() + 2.5, o.getZ() + 0.5, 0xFF5555));
            }
        }

        private void zones(DebugOverlayPayload p, BlockPos o) {
            Map<Long, int[]> zonesAt = new HashMap<>();
            for (DebugOverlayPayload.Cell c : p.cells()) {
                zonesAt.merge(key(c.dx(), c.dy(), c.dz()), new int[] {c.zone()}, DebugOverlayRenderer::concat);
            }
            int zoneCount = p.zones().size();
            double[][] sums = new double[zoneCount][4];
            for (DebugOverlayPayload.Cell c : p.cells()) {
                DebugOverlayPayload.Zone zone = p.zones().get(c.zone());
                int colour = zone.paused() ? PAUSED : zoneColour(zone.id());
                for (Face f : Face.values()) {
                    int[] there = zonesAt.get(key(c.dx() + f.dx(), c.dy() + f.dy(), c.dz() + f.dz()));
                    if (there == null || !contains(there, c.zone())) {
                        quads.rect(c.dx(), c.dy(), c.dz(), f.ordinal(), 0, 0, 1, 1, -0.01F, colour);
                    }
                }
                double[] s = sums[c.zone()];
                s[0] += c.dx();
                s[1] += c.dy();
                s[2] += c.dz();
                s[3]++;
            }
            for (int i = 0; i < zoneCount; i++) {
                double[] s = sums[i];
                if (s[3] == 0) {
                    continue;
                }
                DebugOverlayPayload.Zone z = p.zones().get(i);
                String key = z.paused() ? "debug.zas.zone.paused" : "debug.zas.zone";
                labels.add(new Label(
                        I18n.get(key, z.id(), format(z.pressure()), format(z.temperature())),
                        o.getX() + s[0] / s[3] + 0.5,
                        o.getY() + s[1] / s[3] + 0.5,
                        o.getZ() + s[2] / s[3] + 0.5,
                        z.paused() ? 0xC0C0C0 : zoneColour(z.id()) & 0xFFFFFF));
            }

            Map<Long, Boolean> unresolvedAt = new HashMap<>();
            double[] u = new double[4];
            for (DebugOverlayPayload.Unresolved c : p.unresolved()) {
                unresolvedAt.put(key(c.dx(), c.dy(), c.dz()), true);
            }
            for (DebugOverlayPayload.Unresolved c : p.unresolved()) {
                for (Face f : Face.values()) {
                    if (!unresolvedAt.containsKey(key(c.dx() + f.dx(), c.dy() + f.dy(), c.dz() + f.dz()))) {
                        quads.rect(c.dx(), c.dy(), c.dz(), f.ordinal(), 0, 0, 1, 1, -0.01F, UNRESOLVED);
                    }
                }
                u[0] += c.dx();
                u[1] += c.dy();
                u[2] += c.dz();
                u[3]++;
            }
            if (u[3] > 0) {
                labels.add(new Label(
                        I18n.get("debug.zas.unresolved"),
                        o.getX() + u[0] / u[3] + 0.5,
                        o.getY() + u[1] / u[3] + 0.5,
                        o.getZ() + u[2] / u[3] + 0.5,
                        UNRESOLVED & 0xFFFFFF));
            }
        }

        private void edges(DebugOverlayPayload p, BlockPos o) {
            int edgeCount = p.edges().size();
            double[][] sums = new double[edgeCount][4];
            for (DebugOverlayPayload.Face f : p.faces()) {
                DebugOverlayPayload.Edge e = p.edges().get(f.edge());
                quads.rect(f.dx(), f.dy(), f.dz(), f.face(), 0.15F, 0.15F, 0.85F, 0.85F, 0.02F, edgeColour(e));
                Face face = Face.of(f.face());
                double[] s = sums[f.edge()];
                s[0] += f.dx() + 0.5 + face.dx() * 0.5;
                s[1] += f.dy() + 0.5 + face.dy() * 0.5;
                s[2] += f.dz() + 0.5 + face.dz() * 0.5;
                s[3]++;
            }
            for (int i = 0; i < edgeCount; i++) {
                double[] s = sums[i];
                if (s[3] == 0) {
                    continue;
                }
                DebugOverlayPayload.Edge e = p.edges().get(i);
                String dp = String.format(Locale.ROOT, "%+.1f", e.pressureDifference());
                String text = e.ambient()
                        ? I18n.get(e.direct() ? "debug.zas.edge.ambient.direct" : "debug.zas.edge.ambient.boundary", dp)
                        : I18n.get(
                                e.direct() ? "debug.zas.edge.zone.direct" : "debug.zas.edge.zone.boundary",
                                dp,
                                p.zones().get(e.other()).id());
                labels.add(new Label(
                        text,
                        o.getX() + s[0] / s[3],
                        o.getY() + s[1] / s[3] + 0.25,
                        o.getZ() + s[2] / s[3],
                        edgeColour(e) & 0xFFFFFF));
            }
        }
    }

    private static int edgeColour(DebugOverlayPayload.Edge e) {
        if (e.ambient()) {
            return e.direct() ? AMBIENT_DIRECT : AMBIENT_BOUNDARY;
        }
        return e.direct() ? ZONE_DIRECT : ZONE_BOUNDARY;
    }

    /** A colour per zone id, spread around the hue circle by the golden ratio. */
    static int zoneColour(long id) {
        float hue = (float) ((id * 0.6180339887498949) % 1.0);
        return 0x50000000 | Mth.hsvToRgb(hue, 0.7F, 1.0F);
    }

    private static long key(int dx, int dy, int dz) {
        return ((long) (dx & 0xFFFF) << 32) | ((long) (dy & 0xFFFF) << 16) | (dz & 0xFFFF);
    }

    private static int[] concat(int[] a, int[] b) {
        int[] out = Arrays.copyOf(a, a.length + b.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }

    private static boolean contains(int[] values, int v) {
        for (int x : values) {
            if (x == v) {
                return true;
            }
        }
        return false;
    }

    private static String format(double v) {
        return Double.isNaN(v) ? "?" : String.format(Locale.ROOT, "%.1f", v);
    }

    /** A floating label at a world position. */
    private record Label(String text, double x, double y, double z, int color) {}

    /** Coloured quads, four vertices each, relative to some origin. */
    private static final class Quads {
        private float[] xyz = new float[12 * 64];
        private int[] colours = new int[64];
        private int count;

        /**
         * A rectangle on face {@code face} of the block at {@code (x, y, z)}, spanning {@code u0}
         * to {@code u1} and {@code v0} to {@code v1} in the face's axes ({@link Face}), pushed out
         * along the face's normal by {@code push} (in if negative).
         */
        void rect(float x, float y, float z, int face, float u0, float v0, float u1, float v1, float push, int argb) {
            int normal; // 0 = x, 1 = y, 2 = z
            int ua;
            int va;
            switch (Face.of(face)) {
                case DOWN, UP -> {
                    normal = 1;
                    ua = 0;
                    va = 2;
                }
                case NORTH, SOUTH -> {
                    normal = 2;
                    ua = 0;
                    va = 1;
                }
                default -> {
                    normal = 0;
                    ua = 2;
                    va = 1;
                }
            }
            boolean positive = (face & 1) == 1;
            float n = positive ? 1 + push : -push;
            if (count == colours.length) {
                colours = Arrays.copyOf(colours, count * 2);
                xyz = Arrays.copyOf(xyz, count * 24);
            }
            float[] us = {u0, u1, u1, u0};
            float[] vs = {v0, v0, v1, v1};
            float[] base = {x, y, z};
            int at = count * 12;
            for (int i = 0; i < 4; i++) {
                float[] p = base.clone();
                p[normal] += n;
                p[ua] += us[i];
                p[va] += vs[i];
                xyz[at++] = p[0];
                xyz[at++] = p[1];
                xyz[at++] = p[2];
            }
            colours[count++] = argb;
        }

        void draw(VertexConsumer out, Matrix4f pose, float ox, float oy, float oz) {
            for (int q = 0; q < count; q++) {
                int c = colours[q];
                int a = c >>> 24;
                int r = (c >> 16) & 0xFF;
                int g = (c >> 8) & 0xFF;
                int b = c & 0xFF;
                int at = q * 12;
                for (int i = 0; i < 4; i++) {
                    out.addVertex(pose, xyz[at] + ox, xyz[at + 1] + oy, xyz[at + 2] + oz)
                            .setColor(r, g, b, a);
                    at += 3;
                }
            }
        }
    }
}
