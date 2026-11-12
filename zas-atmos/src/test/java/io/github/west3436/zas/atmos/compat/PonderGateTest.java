package io.github.west3436.zas.atmos.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.west3436.zas.atmos.compat.PonderGate.Verdict;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;

/**
 * The decision whether the Ponder scenes are on (CLIENT-023, COMPAT-002, NFR-022), on the version
 * texts Ponder's builds really carry: its mod metadata takes the jar's version, such as {@code
 * 1.0.82+mc1.21.1}.
 */
class PonderGateTest {
    /** The Ponder that Create 6.0.10 nests, the oldest supported. */
    private static final String CREATES = "1.0.82+mc1.21.1";

    /** The Ponder that the ZAS³ Ponder addon nests. */
    private static final String ADDONS = "1.0.87+mc1.21.1";

    @Test
    void the_scenes_are_on_with_a_supported_ponder() {
        assertEquals(Verdict.ON, PonderGate.decide(true, CREATES, false));
        assertEquals(Verdict.ON, PonderGate.decide(true, ADDONS, false));
    }

    @Test
    void without_ponder_they_are_off() {
        assertEquals(Verdict.ABSENT, PonderGate.decide(true, null, false));
    }

    /** Create 6.0.9 nests Ponder 1.0.81, one below the range. */
    @Test
    void compat_002_with_an_older_ponder_they_are_off() {
        assertEquals(Verdict.UNSUPPORTED, PonderGate.decide(true, "1.0.81+mc1.21.1", false));
    }

    /** Releases of the addon up to 1.0.0 register their own copy of the scenes. */
    @Test
    void beside_an_addon_with_its_own_scenes_they_stand_down() {
        assertEquals(Verdict.ADDON_HAS_SCENES, PonderGate.decide(true, ADDONS, true));
    }

    /** With no items to explain there is nothing to ask Ponder for, whatever is installed. */
    @Test
    void cont_155_with_the_content_off_they_are_off() {
        assertEquals(Verdict.CONTENT_OFF, PonderGate.decide(false, CREATES, false));
        assertEquals(Verdict.CONTENT_OFF, PonderGate.decide(false, ADDONS, true));
        assertEquals(Verdict.CONTENT_OFF, PonderGate.decide(false, null, false));
    }

    /** Numbers compare as numbers: 1.0.100 is above 1.0.82. */
    @Test
    void compat_002_ponder_1_from_creates_copy_up_is_supported() {
        for (String version :
                new String[] {CREATES, "1.0.83+mc1.21.1", ADDONS, "1.0.100+mc1.21.1", "1.1.0+mc1.21.1", "1.0.82"}) {
            assertTrue(PonderGate.supports(version, PonderGate.SUPPORTED), version);
        }
    }

    @Test
    void compat_002_an_older_ponder_and_ponder_2_are_not() {
        for (String version : new String[] {
            "1.0.81+mc1.21.1", "1.0.59+mc1.21.1", "1.0.9+mc1.21.1", "0.9.0", "2.0.0+mc1.21.1", "2.0.0", "3.1.4+mc1.21.1"
        }) {
            assertFalse(PonderGate.supports(version, PonderGate.SUPPORTED), version);
        }
    }

    /** A bare version would let every version through, so it is refused. */
    @Test
    void compat_002_a_range_must_have_bounds() {
        assertThrows(IllegalArgumentException.class, () -> PonderGate.supports(CREATES, "1.0.82"));
        assertThrows(IllegalArgumentException.class, () -> PonderGate.supports(CREATES, "[1.0.82"));
    }

    /**
     * The gate is loaded in every game, so it may name nothing of Ponder's or Flywheel's: a class it
     * named would be looked for when the gate is linked, and isn't there without them.
     */
    @Test
    void nfr_022_the_gate_names_no_class_of_ponders() throws IOException {
        String constants;
        try (InputStream in = PonderGate.class.getResourceAsStream("PonderGate.class")) {
            // Latin-1 reads any byte, and a class file spells the names it uses in ASCII.
            constants = new String(in.readAllBytes(), StandardCharsets.ISO_8859_1);
        }
        for (String theirs : new String[] {"net/createmod", "net.createmod", "dev/engine_room", "dev.engine_room"}) {
            assertFalse(constants.contains(theirs), "PonderGate names " + theirs);
        }
    }

    /** The gate spells the entry class as text, so nothing else says when the class moves. */
    @Test
    void the_entry_class_is_where_the_gate_looks_for_it() throws IOException {
        Path file = Path.of(System.getProperty("zas_atmos.ponderClasses"))
                .resolve(PonderGate.ENTRY.replace('.', '/') + ".class");
        assertTrue(Files.isRegularFile(file), "the Ponder scenes' build has no " + file);
        ClassNode entry = new ClassNode();
        new ClassReader(Files.readAllBytes(file)).accept(entry, ClassReader.SKIP_CODE);
        assertTrue(Modifier.isPublic(entry.access), PonderGate.ENTRY + " isn't public");
        assertTrue(
                entry.methods.stream()
                        .anyMatch(method -> method.name.equals("init")
                                && method.desc.equals("()V")
                                && Modifier.isPublic(method.access)
                                && Modifier.isStatic(method.access)),
                PonderGate.ENTRY + " has no public static void init()");
    }
}
