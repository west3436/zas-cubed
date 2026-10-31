package io.github.west3436.zas.api;

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
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * The API's compiled classes reference only the JDK, Minecraft, NeoForge and
 * the API itself: never {@code zas}, the engine or an optional mod (API-003,
 * API-044). It reads every class reference and type descriptor in each class
 * file's constant pool, so it catches references from signatures as well as
 * code.
 */
class ApiReferencesTest {
    /** Internal-name prefixes the API may reference. */
    private static final List<String> ALLOWED = List.of(
            "java/",
            "javax/annotation/",
            "net/minecraft/",
            "net/neoforged/",
            "com/mojang/",
            "org/jetbrains/annotations/",
            "io/github/west3436/zas/api/");

    private static final Pattern DESCRIPTOR_TYPE = Pattern.compile("L([\\w/$]+)[;<]");

    @Test
    void api_003_api_044_api_references_only_jdk_minecraft_and_neoforge() throws IOException {
        Set<String> bad = new TreeSet<>();
        int classes = 0;
        for (Path dir : classDirs()) {
            try (Stream<Path> files = Files.walk(dir)) {
                for (Path file : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".class"))::iterator) {
                    classes++;
                    try (InputStream in = Files.newInputStream(file)) {
                        for (String ref : references(in)) {
                            if (ALLOWED.stream().noneMatch(ref::startsWith)) {
                                bad.add(dir.relativize(file) + " -> " + ref);
                            }
                        }
                    }
                }
            }
        }
        assertTrue(classes > 0, "no API classes found");
        assertEquals(Set.of(), bad, "the API references types outside the JDK, Minecraft, NeoForge and itself");
    }

    @Test
    void api_044_scanner_catches_an_internal_reference() throws IOException {
        // A sanity check on the scanner itself, using this test's own class
        // file, which references org.junit (not allowed in the API).
        try (InputStream in = ApiReferencesTest.class.getResourceAsStream("ApiReferencesTest.class")) {
            List<String> refs = references(in);
            assertTrue(refs.stream().anyMatch(r -> r.startsWith("org/junit/")), refs.toString());
            assertFalse(refs.isEmpty());
        }
    }

    private static List<Path> classDirs() {
        String prop = System.getProperty("zas.api.classes");
        assertTrue(prop != null && !prop.isBlank(), "zas.api.classes is not set by the build");
        List<Path> dirs = new ArrayList<>();
        for (String s : prop.split(java.io.File.pathSeparator)) {
            Path p = Path.of(s);
            if (Files.isDirectory(p)) {
                dirs.add(p);
            }
        }
        return dirs;
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
        List<String> refs = new ArrayList<>();
        for (int idx : classIndexes) {
            String name = utf8[idx];
            // Array classes are written as descriptors, e.g. [Ljava/lang/String;
            if (name.startsWith("[")) {
                addDescriptorTypes(name, refs);
            } else {
                refs.add(name);
            }
        }
        for (String s : utf8) {
            if (s != null) {
                addDescriptorTypes(s, refs);
            }
        }
        return refs;
    }

    private static void addDescriptorTypes(String s, List<String> out) {
        Matcher m = DESCRIPTOR_TYPE.matcher(s);
        while (m.find()) {
            String name = m.group(1);
            // Only names that look like packaged types; plain strings in the
            // pool can contain an 'L' followed by text and a semicolon.
            if (name.contains("/")) {
                out.add(name);
            }
        }
    }
}
