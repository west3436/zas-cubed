package io.github.west3436.zas.atmos.compat.jade;

import java.util.List;
import java.util.Objects;

/**
 * What looking at a device shows (COMPAT-030, COMPAT-031): a row for each body of gas it reads, and
 * the shares of the gases if it holds one body. The server makes one from the block entity, and the
 * client draws it; values stay in kPa and K until they are drawn.
 *
 * <p>It names no class of the game or of Jade, so it is tested without either.
 *
 * @param rows a pressure and a temperature each, at most {@value #MOST_ROWS}
 * @param shares the gases of a single body, at most {@value #MOST_SHARES}; empty for a device with
 *     several bodies, for a room's reading and for a vacuum
 */
public record Reading(List<Row> rows, List<Share> shares) {
    /** The most rows a reading has: a filter's or a mixer's three ports. */
    public static final int MOST_ROWS = 3;

    /** The most gases a reading lists. */
    public static final int MOST_SHARES = 4;

    /** What stands in front of a row, saying which body of gas it is. */
    public enum Lead {
        /** The device's one body of gas, or the room it reads. */
        BODY(false),
        /** The port gas comes in at. */
        IN(false),
        /** The port gas goes out at. */
        OUT(false),
        /** The port on the device's side. */
        SIDE(false),
        /** A port that carries one gas, named by the row's id. */
        GAS(true),
        /** A tank, whose item the row's id names. */
        ITEM(true);

        private final boolean named;

        Lead(boolean named) {
            this.named = named;
        }

        /** Whether its rows carry an id. */
        public boolean named() {
            return named;
        }
    }

    /**
     * One body of gas.
     *
     * @param lead which body it is
     * @param id the gas or item a {@linkplain Lead#named named} lead shows; empty for the others
     * @param kpa its pressure in kPa, or NaN if the device reads none
     * @param kelvin its temperature in K, or NaN if the device reads none or holds a vacuum
     */
    public record Row(Lead lead, String id, float kpa, float kelvin) {
        public Row {
            Objects.requireNonNull(lead, "lead");
            Objects.requireNonNull(id, "id");
            if (lead.named() == id.isEmpty()) {
                throw new IllegalArgumentException("a " + lead + " row with the id '" + id + "'");
            }
        }

        /** A row with no id. */
        public Row(Lead lead, float kpa, float kelvin) {
            this(lead, "", kpa, kelvin);
        }

        /** Whether it has a pressure to show. */
        public boolean hasPressure() {
            return !Float.isNaN(kpa);
        }

        /** Whether it has a temperature to show. */
        public boolean hasTemperature() {
            return !Float.isNaN(kelvin);
        }
    }

    /**
     * A gas's share of a body.
     *
     * @param gas the gas's id
     * @param fraction its share of the moles, above 0 and at most 1
     */
    public record Share(String gas, float fraction) {
        public Share {
            Objects.requireNonNull(gas, "gas");
        }
    }

    public Reading {
        rows = List.copyOf(rows);
        shares = List.copyOf(shares);
        if (rows.size() > MOST_ROWS || shares.size() > MOST_SHARES) {
            throw new IllegalArgumentException(rows.size() + " rows and " + shares.size() + " shares");
        }
    }

    /** A reading of one body of gas with no shares listed. */
    public static Reading of(Row row) {
        return new Reading(List.of(row), List.of());
    }
}
