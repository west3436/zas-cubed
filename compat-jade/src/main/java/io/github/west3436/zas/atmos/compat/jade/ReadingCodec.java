package io.github.west3436.zas.atmos.compat.jade;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A reading as the bytes Jade carries from the server to the client: a pipe of standard air is 45
 * of them, and the largest reading under 200. Jade sends them in its own packet, with its own limit
 * on how often, so the adapter has no packet of its own.
 */
final class ReadingCodec {
    private static final Reading.Lead[] LEADS = Reading.Lead.values();

    private ReadingCodec() {}

    /** A reading's bytes. */
    static byte[] encode(Reading reading) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(64);
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeByte(reading.rows().size());
            for (Reading.Row row : reading.rows()) {
                out.writeByte(row.lead().ordinal());
                if (row.lead().named()) {
                    out.writeUTF(row.id());
                }
                out.writeFloat(row.kpa());
                out.writeFloat(row.kelvin());
            }
            out.writeByte(reading.shares().size());
            for (Reading.Share share : reading.shares()) {
                out.writeUTF(share.gas());
                out.writeFloat(share.fraction());
            }
        } catch (IOException e) {
            // Writing to memory doesn't fail.
            throw new UncheckedIOException(e);
        }
        return bytes.toByteArray();
    }

    /**
     * The reading some bytes hold.
     *
     * @return the reading, or empty if the bytes aren't one, which the client then draws nothing for:
     *     they come over the network, so they aren't trusted
     */
    static Optional<Reading> decode(byte[] bytes) {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            int rowCount = in.readUnsignedByte();
            if (rowCount > Reading.MOST_ROWS) {
                return Optional.empty();
            }
            List<Reading.Row> rows = new ArrayList<>(rowCount);
            for (int i = 0; i < rowCount; i++) {
                int lead = in.readUnsignedByte();
                if (lead >= LEADS.length) {
                    return Optional.empty();
                }
                String id = LEADS[lead].named() ? in.readUTF() : "";
                rows.add(new Reading.Row(LEADS[lead], id, in.readFloat(), in.readFloat()));
            }
            int shareCount = in.readUnsignedByte();
            if (shareCount > Reading.MOST_SHARES) {
                return Optional.empty();
            }
            List<Reading.Share> shares = new ArrayList<>(shareCount);
            for (int i = 0; i < shareCount; i++) {
                shares.add(new Reading.Share(in.readUTF(), in.readFloat()));
            }
            return in.available() == 0 ? Optional.of(new Reading(rows, shares)) : Optional.empty();
        } catch (IOException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
