package io.github.west3436.zas.compat.mekanism;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * What the adapter's compiled classes refer to (D-11, COMPAT-003, MEK-001). Gradle already keeps
 * {@code zas}, the engine and {@code zas_atmos} off the compile classpath; this reads every class
 * reference and type descriptor in each class file's constant pool for the rest:
 *
 * <ul>
 *   <li>outside the {@code mixin} package, the only classes of Mekanism's are its API's;
 *   <li>the classes that are loaded before the game's, or asked whether the adapter is on, name
 *       nothing of Mekanism's at all, so loading them never loads Mekanism.
 * </ul>
 */
class AdapterReferencesTest {
    private static final String PACKAGE = "io/github/west3436/zas/compat/mekanism/";
    private static final String MIXIN_PACKAGE = PACKAGE + "mixin/";

    /** Internal-name prefixes any class of the adapter may reference. */
    private static final List<String> ALLOWED = List.of(
            "java/",
            "net/minecraft/",
            "net/neoforged/",
            "com/mojang/",
            "com/google/gson/",
            "org/slf4j/",
            "org/jetbrains/annotations/",
            "org/apache/maven/artifact/versioning/",
            "io/github/west3436/zas/api/",
            PACKAGE,
            "mekanism/api/");

    /** What the mixin package may reference besides: Mixin itself, and any class of Mekanism's. */
    private static final List<String> ALLOWED_IN_MIXINS =
            List.of("org/spongepowered/asm/", "org/objectweb/asm/", "com/llamalad7/mixinextras/", "mekanism/");

    /** Classes that must load without Mekanism. */
    private static final Set<String> NO_MEKANISM = Set.of(
            PACKAGE + "MekanismCompat",
            PACKAGE + "MekanismVersions",
            PACKAGE + "MekanismSettings",
            PACKAGE + "ChemicalUnits",
            PACKAGE + "ChemicalMapping",
            PACKAGE + "ChemicalMap",
            PACKAGE + "ChemicalMapFormat",
            PACKAGE + "ChemicalMapLoader",
            // The venting's cap (MEK-021).
            PACKAGE + "VentBudget",
            MIXIN_PACKAGE + "MekanismMixinPlugin");

    private static final Pattern DESCRIPTOR_TYPE = Pattern.compile("L([\\w/$]+)[;<]");

    @Test
    void mek_001_the_adapter_references_only_the_public_api_and_mekanisms_api() throws IOException {
        Set<String> bad = new TreeSet<>();
        Map<String, List<String>> classes = adapterClasses();
        classes.forEach((name, references) -> {
            boolean mixin = name.startsWith(MIXIN_PACKAGE);
            for (String reference : references) {
                boolean allowed = ALLOWED.stream().anyMatch(reference::startsWith)
                        || mixin && ALLOWED_IN_MIXINS.stream().anyMatch(reference::startsWith);
                if (!allowed) {
                    bad.add(name + " -> " + reference);
                }
            }
        });
        assertTrue(classes.size() > 5, "the adapter's classes weren't found: " + classes.keySet());
        assertEquals(Set.of(), bad, "the adapter references types outside the public API and Mekanism's API");
    }

    @Test
    void mek_001_what_loads_without_mekanism_names_none_of_its_classes() throws IOException {
        Map<String, List<String>> classes = adapterClasses();
        Set<String> bad = new TreeSet<>();
        for (String name : NO_MEKANISM) {
            // Nested and anonymous classes load with their outer class's code.
            List<String> found = classes.keySet().stream()
                    .filter(c -> c.equals(name) || c.startsWith(name + "$"))
                    .toList();
            assertFalse(found.isEmpty(), name + " isn't among the adapter's classes");
            for (String c : found) {
                classes.get(c).stream().filter(r -> r.startsWith("mekanism/")).forEach(r -> bad.add(c + " -> " + r));
            }
        }
        assertEquals(Set.of(), bad);
        // The pure arithmetic names nothing but the JDK and itself (MEK-002).
        assertEquals(
                List.of(),
                classes.get(PACKAGE + "ChemicalUnits").stream()
                        .filter(r -> !r.startsWith("java/") && !r.equals(PACKAGE + "ChemicalUnits"))
                        .toList());
    }

    /** A sanity check on the scanner, using this test's own class file, which references JUnit. */
    @Test
    void mek_001_the_scanner_catches_a_reference() throws IOException {
        try (InputStream in = AdapterReferencesTest.class.getResourceAsStream("AdapterReferencesTest.class")) {
            assertTrue(references(in).stream().anyMatch(r -> r.startsWith("org/junit/")));
        }
    }

    /** The adapter's main classes by internal name, each with the types it refers to. */
    private static Map<String, List<String>> adapterClasses() throws IOException {
        String property = System.getProperty("zas.mekanism.classes");
        assertTrue(property != null && !property.isBlank(), "zas.mekanism.classes is not set by the build");
        Map<String, List<String>> classes = new TreeMap<>();
        for (String entry : property.split(java.io.File.pathSeparator)) {
            Path dir = Path.of(entry);
            if (!Files.isDirectory(dir)) {
                continue;
            }
            try (Stream<Path> files = Files.walk(dir)) {
                for (Path file : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".class"))::iterator) {
                    String name = dir.relativize(file).toString().replace('\\', '/');
                    try (InputStream in = Files.newInputStream(file)) {
                        classes.put(name.substring(0, name.length() - ".class".length()), references(in));
                    }
                }
            }
        }
        return classes;
    }

    /** The internal names of every type a class file refers to. */
    private static List<String> references(InputStream raw) throws IOException {
        DataInputStream in = new DataInputStream(raw);
        if (in.readInt() != 0xCAFEBABE) {
            throw new IOException("not a class file");
        }
        in.readUnsignedShort(); // minor
        in.readUnsignedShort(); // major
        int count = in.readUnsignedShort();
        String[] utf8 = new String[count];
        List<Integer> classIndexes = new ArrayList<>();
        for (int i = 1; i < count; i++) {
            int tag = in.readUnsignedByte();
            switch (tag) {
                case 1 -> utf8[i] = in.readUTF();
                case 7 -> classIndexes.add(in.readUnsignedShort()); // Class
                case 8, 16, 19, 20 -> in.readUnsignedShort(); // String, MethodType, Module, Package
                case 15 -> { // MethodHandle
                    in.readUnsignedByte();
                    in.readUnsignedShort();
                }
                case 3, 4, 9, 10, 11, 12, 17, 18 -> in.readInt(); // Integer, Float, refs, NameAndType, dynamic
                case 5, 6 -> { // Long, Double take two slots
                    in.readLong();
                    i++;
                }
                default -> throw new IOException("unknown constant pool tag " + tag);
            }
        }
        List<String> references = new ArrayList<>();
        for (int index : classIndexes) {
            String name = utf8[index];
            // Array classes are written as descriptors, such as [Ljava/lang/String;
            if (name.startsWith("[")) {
                addDescriptorTypes(name, references);
            } else {
                references.add(name);
            }
        }
        for (String text : utf8) {
            if (text != null) {
                addDescriptorTypes(text, references);
            }
        }
        return references;
    }

    private static void addDescriptorTypes(String text, List<String> out) {
        Matcher m = DESCRIPTOR_TYPE.matcher(text);
        while (m.find()) {
            String name = m.group(1);
            // Only names that look like packaged types: plain strings in the pool can hold an 'L'
            // followed by text and a semicolon.
            if (name.contains("/")) {
                out.add(name);
            }
        }
    }
}
