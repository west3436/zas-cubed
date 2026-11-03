package io.github.west3436.zas.engine.flow;

import java.util.Objects;

/**
 * One active edge whose pressure difference reached the airflow threshold in an atmos tick
 * (FLOW-001), with its openings: the open pairs of cells between its two sides. Immutable.
 *
 * <p>Opening {@code i} is the pair ({@code cells[i]}, {@code otherCells[i]}): the first on {@code
 * zone}'s side, the second on {@code other}'s, which for an ambient edge is an unzoned cell. Cells
 * are cell ids ({@link io.github.west3436.zas.engine.classify.CellIds}). The pressures are the
 * sides' after the tick's exchange, as the tick's snapshot publishes them.
 *
 * @param zone the zone with the lower id, or the only zone of an ambient edge
 * @param other the other zone, or {@link #AMBIENT}
 * @param pressure {@code zone}'s pressure, kPa
 * @param otherPressure {@code other}'s pressure, or the ambient's, kPa
 * @param cells each opening's cell on {@code zone}'s side
 * @param otherCells each opening's cell on the other side
 */
public record FlowEdge(long zone, long other, double pressure, double otherPressure, long[] cells, long[] otherCells) {
    /** The {@link #other} of an ambient edge. */
    public static final long AMBIENT = -1;

    public FlowEdge {
        Objects.requireNonNull(cells, "cells");
        Objects.requireNonNull(otherCells, "otherCells");
        if (cells.length != otherCells.length) {
            throw new IllegalArgumentException("cells and otherCells differ in length");
        }
        cells = cells.clone();
        otherCells = otherCells.clone();
    }

    @Override
    public long[] cells() {
        return cells.clone();
    }

    @Override
    public long[] otherCells() {
        return otherCells.clone();
    }

    /** The number of openings. */
    public int openings() {
        return cells.length;
    }

    /** The cell of opening {@code i} on {@code zone}'s side. */
    public long cell(int i) {
        return cells[i];
    }

    /** The cell of opening {@code i} on the other side. */
    public long otherCell(int i) {
        return otherCells[i];
    }

    /** The pressure difference, kPa, never negative. */
    public double difference() {
        return Math.abs(pressure - otherPressure);
    }

    /** Whether this is an ambient edge. */
    public boolean isAmbient() {
        return other == AMBIENT;
    }
}
