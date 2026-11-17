package io.github.west3436.zas.compat.sable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.DataInputStream;
import java.io.File;
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
 * What the adapter's compiled classes refer to (COMPAT-003). It reads every class reference and
 * type descriptor in each class file's constant pool:
 *
 * <ul>
 *   <li>the classes of Sable's the adapter names are listed here, and each use of them is covered
 *       by the adapter's GameTests. A new one has to be added here, with its GameTest. Most are in
 *       Sable's {@code api} package or Sable Companion; the rest are the sub-level, its plot and what
 *       Sable holds for an unloaded one, which its API hands out;
 *   <li>it names nothing of Create's or of the Simulated Project's;
 *   <li>the classes asked whether the adapter is on name nothing of Sable's, so loading them never
 *       loads Sable.
 * </ul>
 */
class AdapterReferencesTest {
    private static final String PACKAGE = "io/github/west3436/zas/compat/sable/";

    /** Sable's classes the adapter may name. */
    private static final Set<String> SABLE_CLASSES = Set.of(
            "dev/ryanhcode/sable/api/SubLevelAssemblyHelper",
            "dev/ryanhcode/sable/api/SubLevelAssemblyHelper$AssemblyTransform",
            "dev/ryanhcode/sable/api/sublevel/ServerSubLevelContainer",
            "dev/ryanhcode/sable/api/sublevel/SubLevelContainer",
            "dev/ryanhcode/sable/api/sublevel/SubLevelObserver",
            "dev/ryanhcode/sable/companion/math/BoundingBox3d",
            "dev/ryanhcode/sable/companion/math/BoundingBox3dc",
            "dev/ryanhcode/sable/companion/math/Pose3d",
            "dev/ryanhcode/sable/companion/math/Pose3dc",
            "dev/ryanhcode/sable/sublevel/ServerSubLevel",
            "dev/ryanhcode/sable/sublevel/SubLevel",
            "dev/ryanhcode/sable/sublevel/plot/LevelPlot",
            "dev/ryanhcode/sable/sublevel/plot/PlotChunkHolder",
            "dev/ryanhcode/sable/sublevel/plot/ServerLevelPlot",
            "dev/ryanhcode/sable/sublevel/storage/HoldingSubLevel",
            "dev/ryanhcode/sable/sublevel/storage/SubLevelRemovalReason",
            "dev/ryanhcode/sable/sublevel/storage/holding/SubLevelHoldingChunkMap",
            "dev/ryanhcode/sable/sublevel/storage/serialization/SubLevelData");

    /** Internal-name prefixes any class of the adapter may reference, besides those. */
    private static final List<String> ALLOWED = List.of(
            "java/",
            "net/minecraft/",
            "net/neoforged/",
            "com/mojang/",
            "org/slf4j/",
            "org/jetbrains/annotations/",
            "org/joml/",
            // The mixins and their plugin.
            "org/spongepowered/asm/",
            "org/objectweb/asm/",
            "com/llamalad7/mixinextras/",
            "org/apache/maven/artifact/versioning/",
            // The API, and ZAS³'s internals for the chunks Sable keeps itself and where a point
            // resolves (COMPAT-001 allows them).
            "io/github/west3436/zas/");

    /** Classes that must load without Sable. */
    private static final Set<String> NO_SABLE =
            Set.of(PACKAGE + "SableCompat", PACKAGE + "SableVersions", PACKAGE + "mixin/SableMixinPlugin");

    private static final Pattern DESCRIPTOR_TYPE = Pattern.compile("L([\\w/$]+)[;<]");

    @Test
    void compat_003_the_adapter_names_only_the_listed_sable_classes() throws IOException {
        Set<String> bad = new TreeSet<>();
        Set<String> sable = new TreeSet<>();
        Map<String, List<String>> classes = adapterClasses();
        classes.forEach((name, references) -> {
            for (String reference : references) {
                if (reference.startsWith("dev/ryanhcode/sable/")) {
                    sable.add(reference);
                    if (!SABLE_CLASSES.contains(reference)) {
                        bad.add(name + " -> " + reference);
                    }
                } else if (ALLOWED.stream().noneMatch(reference::startsWith)) {
                    bad.add(name + " -> " + reference);
                }
            }
        });
        assertTrue(classes.size() > 5, "the adapter's classes weren't found: " + classes.keySet());
        assertEquals(Set.of(), bad, "the adapter references types outside what it may name");
        assertEquals(SABLE_CLASSES, sable, "the Sable classes the adapter names");
    }

    @Test
    void compat_003_what_loads_without_sable_names_none_of_its_classes() throws IOException {
        Map<String, List<String>> classes = adapterClasses();
        Set<String> bad = new TreeSet<>();
        for (String name : NO_SABLE) {
            // Nested and anonymous classes load with their outer class's code.
            List<String> found = classes.keySet().stream()
                    .filter(c -> c.equals(name) || c.startsWith(name + "$"))
                    .toList();
            assertFalse(found.isEmpty(), name + " isn't among the adapter's classes");
            for (String c : found) {
                classes.get(c).stream()
                        .filter(r -> r.startsWith("dev/ryanhcode/"))
                        .forEach(r -> bad.add(c + " -> " + r));
            }
        }
        assertEquals(Set.of(), bad);
    }

    /** A sanity check on the scanner, using this test's own class file, which references JUnit. */
    @Test
    void compat_003_the_scanner_catches_a_reference() throws IOException {
        try (InputStream in = AdapterReferencesTest.class.getResourceAsStream("AdapterReferencesTest.class")) {
            assertTrue(references(in).stream().anyMatch(r -> r.startsWith("org/junit/")));
        }
    }

    /** The adapter's main classes by internal name, each with the types it refers to. */
    private static Map<String, List<String>> adapterClasses() throws IOException {
        String property = System.getProperty("zas.sable.classes");
        assertTrue(property != null && !property.isBlank(), "zas.sable.classes is not set by the build");
        Map<String, List<String>> classes = new TreeMap<>();
        for (String entry : property.split(File.pathSeparator)) {
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
