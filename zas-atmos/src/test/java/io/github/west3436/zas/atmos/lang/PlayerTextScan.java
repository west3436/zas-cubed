package io.github.west3436.zas.atmos.lang;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.Frame;
import org.objectweb.asm.tree.analysis.SourceInterpreter;
import org.objectweb.asm.tree.analysis.SourceValue;

/**
 * Finds the strings in compiled classes that can reach a player as text (CLIENT-007).
 *
 * <p>Everything that shows text takes a {@code Component}, apart from a few draw calls, so a string
 * only becomes text at the calls in {@link #TEXT} and {@link #TRANSLATED}. From each of those the scan
 * follows the string back through locals, parameters, return values, fields, arrays, lambdas'
 * captures, concatenation and {@code String.format}, as far as the scanned classes go, and collects
 * the string constants it ends at. It reads classes rather than sources because the compiler has
 * already settled which {@code append} is a component's, and has put constants in place.
 *
 * <p>It doesn't follow strings through collections, streams or a {@code StringBuilder}. A string from
 * outside the scanned classes, such as an id's {@code toString()} or an enum's {@code name()}, counts
 * as a composed value.
 */
final class PlayerTextScan {
    /** A string in the code, and the source line it's on. */
    record Found(String text, String where) implements Comparable<Found> {
        @Override
        public int compareTo(Found other) {
            int byPlace = where.compareTo(other.where);
            return byPlace != 0 ? byPlace : text.compareTo(other.text);
        }

        @Override
        public String toString() {
            return where + ": \"" + text + "\"";
        }
    }

    private static final String COMPONENT = "net/minecraft/network/chat/Component";

    /** The calls that make text of a string they're given, as owner.name. */
    private static final Set<String> TEXT = Set.of(
            COMPONENT + ".literal",
            COMPONENT + ".nullToEmpty",
            "net/minecraft/network/chat/MutableComponent.append",
            "net/minecraft/network/chat/FormattedText.of",
            "net/minecraft/client/gui/GuiGraphics.drawString",
            "net/minecraft/client/gui/GuiGraphics.drawCenteredString",
            "net/minecraft/client/gui/Font.drawInBatch",
            "net/minecraft/client/renderer/debug/DebugRenderer.renderFloatingText",
            "com/mojang/brigadier/LiteralMessage.<init>");

    /** The calls whose first argument is a translation key, with any others shown as text. */
    private static final Set<String> TRANSLATED = Set.of(
            COMPONENT + ".translatable",
            COMPONENT + ".translatableWithFallback",
            "net/minecraft/client/resources/language/I18n.get");

    /** A conversion in a format string, such as {@code %,.1f}, which isn't language. */
    private static final Pattern FORMAT_CONVERSION =
            Pattern.compile("%(\\d+\\$)?[-#+ 0,(<]*\\d*(\\.\\d+)?[tT]?[a-zA-Z%]");

    private static final int MAX_CONSTANTS = 4096;

    private record Method(ClassNode owner, MethodNode node, Frame<SourceValue>[] frames) {
        boolean isStatic() {
            return (node.access & Opcodes.ACC_STATIC) != 0;
        }
    }

    /** An instruction in a method. */
    private record Site(Method method, AbstractInsnNode insn) {
        private Frame<SourceValue> frame() {
            return method.frames[method.node.instructions.indexOf(insn)];
        }

        boolean reachable() {
            return frame() != null;
        }

        /** What's on the stack as this instruction starts, counting down from the top. */
        SourceValue stack(int fromTop) {
            Frame<SourceValue> frame = frame();
            return frame.getStack(frame.getStackSize() - 1 - fromTop);
        }

        SourceValue local(int index) {
            return frame().getLocal(index);
        }

        String where() {
            String file = method.owner.name.replaceAll("[^/]*$", "") + method.owner.sourceFile;
            for (AbstractInsnNode at = insn; at != null; at = at.getPrevious()) {
                if (at instanceof LineNumberNode line) {
                    return file + ":" + line.line;
                }
            }
            return file;
        }
    }

    private final Set<String> notFollowed;
    private final Map<String, ClassNode> classes = new HashMap<>();
    private final Map<String, List<Method>> methods = new HashMap<>();
    private final Map<String, List<Site>> calls = new HashMap<>();
    private final Map<String, List<Site>> fieldWrites = new HashMap<>();
    private final Map<AbstractInsnNode, List<Site>> arrayWrites = new HashMap<>();
    private final Set<Found> text = new TreeSet<>();
    private final Set<Found> keys = new TreeSet<>();

    /**
     * Scans classes. Those in one scan can pass strings to each other.
     *
     * @param notFollowed methods, as owner.name, whose return values count as composed values
     */
    PlayerTextScan(List<byte[]> classFiles, Set<String> notFollowed) {
        this.notFollowed = notFollowed;
        List<Site> sites = new ArrayList<>();
        for (byte[] file : classFiles) {
            ClassNode node = new ClassNode();
            new ClassReader(file).accept(node, ClassReader.SKIP_FRAMES);
            classes.put(node.name, node);
            for (MethodNode method : node.methods) {
                Method analysed = analyse(node, method);
                methods.computeIfAbsent(method.name + method.desc, k -> new ArrayList<>())
                        .add(analysed);
                for (AbstractInsnNode insn : method.instructions) {
                    Site site = new Site(analysed, insn);
                    if (insn.getOpcode() >= 0 && site.reachable()) {
                        sites.add(site);
                    }
                }
            }
        }
        sites.forEach(this::index);
        sites.forEach(this::indexArrayWrite);
        sites.forEach(this::collect);
    }

    /** Scans a directory of classes. */
    static PlayerTextScan of(Path classes, Set<String> notFollowed) {
        try (Stream<Path> walk = Files.walk(classes)) {
            List<byte[]> files = new ArrayList<>();
            for (Path file : (Iterable<Path>) walk::iterator) {
                if (file.toString().endsWith(".class")) {
                    files.add(Files.readAllBytes(file));
                }
            }
            return new PlayerTextScan(files, notFollowed);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Every string with a letter in it that can become text. */
    Set<Found> withLetters() {
        return text;
    }

    /** Every translation key the code can name, as far as the code spells it out. */
    Set<Found> keys() {
        return keys;
    }

    // ---- Indexing ---------------------------------------------------------------------------

    private static Method analyse(ClassNode owner, MethodNode method) {
        try {
            return new Method(owner, method, new Analyzer<>(new SourceInterpreter()).analyze(owner.name, method));
        } catch (AnalyzerException e) {
            throw new IllegalStateException(owner.name + "." + method.name, e);
        }
    }

    private void index(Site site) {
        switch (site.insn()) {
            case MethodInsnNode call ->
                calls.computeIfAbsent(call.name + call.desc, k -> new ArrayList<>())
                        .add(site);
            case InvokeDynamicInsnNode indy -> {
                if (lambda(indy) instanceof Handle target) {
                    calls.computeIfAbsent(target.getName() + target.getDesc(), k -> new ArrayList<>())
                            .add(site);
                }
            }
            case FieldInsnNode field -> {
                if (field.getOpcode() == Opcodes.PUTFIELD || field.getOpcode() == Opcodes.PUTSTATIC) {
                    fieldWrites
                            .computeIfAbsent(field.name + field.desc, k -> new ArrayList<>())
                            .add(site);
                }
            }
            default -> {}
        }
    }

    /** Lets an array's creation find what's stored in it. */
    private void indexArrayWrite(Site site) {
        if (site.insn().getOpcode() == Opcodes.AASTORE) {
            Set<Site> copies = new HashSet<>();
            findArrays(site, site.stack(2), copies);
            for (Site origin : copies) {
                if (origin.insn().getOpcode() == Opcodes.ANEWARRAY) {
                    arrayWrites
                            .computeIfAbsent(origin.insn(), k -> new ArrayList<>())
                            .add(site);
                }
            }
        }
    }

    private void findArrays(Site at, SourceValue value, Set<Site> seen) {
        for (AbstractInsnNode insn : value.insns) {
            Site site = new Site(at.method(), insn);
            if (seen.add(site)) {
                switch (insn.getOpcode()) {
                    case Opcodes.DUP, Opcodes.ASTORE, Opcodes.CHECKCAST -> findArrays(site, site.stack(0), seen);
                    case Opcodes.ALOAD -> findArrays(site, site.local(((VarInsnNode) insn).var), seen);
                    default -> {}
                }
            }
        }
    }

    private void collect(Site site) {
        if (site.insn() instanceof MethodInsnNode call) {
            String name = call.owner + "." + call.name;
            Type[] args = Type.getArgumentTypes(call.desc);
            boolean translated = TRANSLATED.contains(name);
            if (!translated && !TEXT.contains(name)) {
                return;
            }
            for (int i = 0; i < args.length; i++) {
                SourceValue arg = site.stack(args.length - 1 - i);
                if (translated && i == 0) {
                    origins(site, arg).forEach(origin -> keys.addAll(constants(origin, new HashSet<>())));
                } else if (isString(args[i]) || args[i].getDescriptor().equals("[Ljava/lang/Object;")) {
                    origins(site, arg).forEach(origin -> addText(origin, new HashSet<>()));
                }
            }
        }
    }

    private static boolean isString(Type type) {
        return type.getDescriptor().equals("Ljava/lang/String;");
    }

    /** The target of a lambda or method reference, which takes what it captures as its first arguments. */
    private static Object lambda(InvokeDynamicInsnNode indy) {
        return indy.bsm.getOwner().equals("java/lang/invoke/LambdaMetafactory") ? indy.bsmArgs[1] : null;
    }

    private static boolean isConcat(AbstractInsnNode insn) {
        return insn instanceof InvokeDynamicInsnNode indy
                && indy.bsm.getOwner().equals("java/lang/invoke/StringConcatFactory");
    }

    private static boolean isFormat(AbstractInsnNode insn) {
        return insn instanceof MethodInsnNode call
                && call.owner.equals("java/lang/String")
                && (call.name.equals("format") || call.name.equals("formatted"));
    }

    private boolean related(String a, String b) {
        return ancestors(a).contains(b) || ancestors(b).contains(a);
    }

    private Set<String> ancestors(String name) {
        Set<String> out = new HashSet<>();
        List<String> open = new ArrayList<>(List.of(name));
        while (!open.isEmpty()) {
            String next = open.removeLast();
            if (out.add(next) && classes.get(next) instanceof ClassNode node) {
                if (node.superName != null) {
                    open.add(node.superName);
                }
                open.addAll(node.interfaces);
            }
        }
        return out;
    }

    // ---- Following a value back -------------------------------------------------------------

    /** The string constants, concatenations and formats a value can come from. */
    private Set<Site> origins(Site at, SourceValue value) {
        Set<Site> out = new LinkedHashSet<>();
        walk(at, value, new HashSet<>(), out);
        return out;
    }

    private void walk(Site at, SourceValue value, Set<Site> seen, Set<Site> out) {
        for (AbstractInsnNode insn : value.insns) {
            Site site = new Site(at.method(), insn);
            if (seen.add(site)) {
                step(site, seen, out);
            }
        }
    }

    private void step(Site site, Set<Site> seen, Set<Site> out) {
        AbstractInsnNode insn = site.insn();
        if (isConcat(insn) || isFormat(insn) || (insn instanceof LdcInsnNode ldc && ldc.cst instanceof String)) {
            out.add(site);
            return;
        }
        switch (insn.getOpcode()) {
            case Opcodes.DUP, Opcodes.ASTORE, Opcodes.CHECKCAST -> walk(site, site.stack(0), seen, out);
            case Opcodes.AALOAD -> walk(site, site.stack(1), seen, out);
            case Opcodes.ALOAD -> {
                int var = ((VarInsnNode) insn).var;
                walk(site, site.local(var), seen, out);
                parameter(site.method(), var, seen, out);
            }
            case Opcodes.ANEWARRAY -> {
                for (Site write : arrayWrites.getOrDefault(insn, List.of())) {
                    walk(write, write.stack(0), seen, out);
                }
            }
            case Opcodes.GETFIELD, Opcodes.GETSTATIC -> {
                FieldInsnNode field = (FieldInsnNode) insn;
                for (Site write : fieldWrites.getOrDefault(field.name + field.desc, List.of())) {
                    if (related(((FieldInsnNode) write.insn()).owner, field.owner)) {
                        walk(write, write.stack(0), seen, out);
                    }
                }
            }
            case Opcodes.INVOKEVIRTUAL, Opcodes.INVOKEINTERFACE, Opcodes.INVOKESTATIC, Opcodes.INVOKESPECIAL ->
                call(site, (MethodInsnNode) insn, seen, out);
            default -> {}
        }
    }

    /** What the callers of a method pass it, if a local is one of its parameters. */
    private void parameter(Method method, int local, Set<Site> seen, Set<Site> out) {
        Type[] args = Type.getArgumentTypes(method.node().desc);
        int slot = method.isStatic() ? 0 : 1;
        int index = 0;
        while (index < args.length && slot < local) {
            slot += args[index++].getSize();
        }
        if (index == args.length || slot != local) {
            return;
        }
        for (Site caller : calls.getOrDefault(method.node().name + method.node().desc, List.of())) {
            if (caller.insn() instanceof MethodInsnNode call) {
                if (related(call.owner, method.owner().name)) {
                    walk(caller, caller.stack(args.length - 1 - index), seen, out);
                }
            } else if (caller.insn() instanceof InvokeDynamicInsnNode indy
                    && lambda(indy) instanceof Handle target
                    && target.getOwner().equals(method.owner().name)) {
                int captured = Type.getArgumentTypes(indy.desc).length;
                int position = index + (method.isStatic() ? 0 : 1);
                if (position < captured) {
                    walk(caller, caller.stack(captured - 1 - position), seen, out);
                }
            }
        }
    }

    /** What a call returns, if it returns strings and the scan can see where they come from. */
    private void call(Site site, MethodInsnNode call, Set<Site> seen, Set<Site> out) {
        Type returns = Type.getReturnType(call.desc);
        if (!isString(returns) && !returns.getDescriptor().equals("[Ljava/lang/String;")) {
            return;
        }
        if (call.owner.equals("java/lang/String")) {
            // valueOf, substring, strip and the like: what goes in comes out.
            Type[] args = Type.getArgumentTypes(call.desc);
            for (int i = 0; i < args.length; i++) {
                if (args[i].getSort() == Type.OBJECT || args[i].getSort() == Type.ARRAY) {
                    walk(site, site.stack(args.length - 1 - i), seen, out);
                }
            }
            if (call.getOpcode() != Opcodes.INVOKESTATIC) {
                walk(site, site.stack(args.length), seen, out);
            }
        } else if (!call.owner.equals("java/lang/Object") && !notFollowed.contains(call.owner + "." + call.name)) {
            for (Method target : methods.getOrDefault(call.name + call.desc, List.of())) {
                if (related(call.owner, target.owner().name)) {
                    for (AbstractInsnNode insn : target.node().instructions) {
                        Site end = new Site(target, insn);
                        if (insn.getOpcode() == Opcodes.ARETURN && end.reachable()) {
                            walk(end, end.stack(0), seen, out);
                        }
                    }
                }
            }
        }
    }

    // ---- What the values say ----------------------------------------------------------------

    private void addText(Site origin, Set<Site> visiting) {
        if (!visiting.add(origin)) {
            return;
        }
        switch (origin.insn()) {
            case LdcInsnNode ldc -> addIfLetters((String) ldc.cst, origin);
            case InvokeDynamicInsnNode indy -> {
                concatParts(indy).forEach(part -> addIfLetters(part, origin));
                addDynamicText(origin, indy, visiting);
            }
            case MethodInsnNode format -> {
                // Whichever form of format it is, its arguments are on top and the format is under them.
                origins(origin, origin.stack(1)).forEach(piece -> addFormat(piece, visiting));
                origins(origin, origin.stack(0)).forEach(piece -> addText(piece, visiting));
            }
            default -> {}
        }
    }

    private void addFormat(Site origin, Set<Site> visiting) {
        switch (origin.insn()) {
            case LdcInsnNode ldc ->
                addIfLetters(FORMAT_CONVERSION.matcher((String) ldc.cst).replaceAll(""), origin);
            case InvokeDynamicInsnNode indy -> {
                // A format built in place, as in "%." + decimals + "f": what's built is a number.
                String format = String.join("0", concatParts(indy));
                addIfLetters(FORMAT_CONVERSION.matcher(format).replaceAll(""), origin);
                addDynamicText(origin, indy, visiting);
            }
            default -> addText(origin, visiting);
        }
    }

    private void addDynamicText(Site origin, InvokeDynamicInsnNode indy, Set<Site> visiting) {
        int count = Type.getArgumentTypes(indy.desc).length;
        for (int i = 0; i < count; i++) {
            origins(origin, origin.stack(i)).forEach(piece -> addText(piece, visiting));
        }
    }

    private void addIfLetters(String piece, Site origin) {
        if (piece.codePoints().anyMatch(Character::isLetter)) {
            text.add(new Found(piece, origin.where()));
        }
    }

    /** The whole strings a concatenation or constant can be, if every part of it is known. */
    private Set<Found> constants(Site origin, Set<Site> visiting) {
        if (origin.insn() instanceof LdcInsnNode ldc) {
            return Set.of(new Found((String) ldc.cst, origin.where()));
        }
        if (!(origin.insn() instanceof InvokeDynamicInsnNode indy) || !visiting.add(origin)) {
            return Set.of();
        }
        Set<Found> built = new TreeSet<>(Set.of(new Found("", origin.where())));
        List<String> parts = concatParts(indy);
        int count = Type.getArgumentTypes(indy.desc).length;
        for (int part = 0; part < parts.size(); part++) {
            String fixed = parts.get(part);
            built = extend(built, Set.of(new Found(fixed, "")));
            if (part < count) {
                Set<Found> options = new TreeSet<>();
                for (Site piece : origins(origin, origin.stack(count - 1 - part))) {
                    options.addAll(constants(piece, visiting));
                }
                built = extend(built, options);
            }
        }
        visiting.remove(origin);
        return built;
    }

    private static Set<Found> extend(Set<Found> built, Set<Found> options) {
        Set<Found> out = new TreeSet<>();
        for (Found start : built) {
            for (Found option : options) {
                out.add(new Found(
                        start.text() + option.text(), option.where().isEmpty() ? start.where() : option.where()));
                if (out.size() > MAX_CONSTANTS) {
                    return Set.of();
                }
            }
        }
        return out;
    }

    /**
     * A concatenation's fixed text: the piece before each of its arguments, and the piece after the
     * last. Its constants are written into the pieces.
     */
    private static List<String> concatParts(InvokeDynamicInsnNode indy) {
        int count = Type.getArgumentTypes(indy.desc).length;
        if (!indy.name.equals("makeConcatWithConstants")) {
            return Collections.nCopies(count + 1, "");
        }
        List<String> parts = new ArrayList<>();
        StringBuilder part = new StringBuilder();
        int constant = 1;
        for (char c : ((String) indy.bsmArgs[0]).toCharArray()) {
            if (c == '\u0001') {
                parts.add(part.toString());
                part.setLength(0);
            } else if (c == '\u0002') {
                part.append(indy.bsmArgs[constant++]);
            } else {
                part.append(c);
            }
        }
        parts.add(part.toString());
        return parts;
    }
}
