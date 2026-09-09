import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;

/**
 * Minimal JUnit-5-style runner for the local ECJ test loop (no JUnit artifact reachable
 * from this sandbox; CI executes the real JUnit 6 suite). Supports the subset this repo's
 * tests use: @Test, @BeforeEach/@AfterEach, @TempDir fields, and @ParameterizedTest with
 * @ValueSource/@CsvSource/@EnumSource/@MethodSource/@NullAndEmptySource/@NullSource.
 */
public final class LocalTestRunner {
    private static int passed;
    private static int failed;
    private static final List<String> failures = new ArrayList<>();

    public static void main(String[] argv) throws Exception {
        Path classes = Paths.get(argv[0]);
        List<Class<?>> testClasses = new ArrayList<>();
        try (var walk = Files.walk(classes)) {
            walk.filter(p -> p.toString().endsWith(".class"))
                    .map(p -> classes.relativize(p).toString())
                    .map(s -> s.substring(0, s.length() - 6).replace('/', '.'))
                    .filter(s -> s.endsWith("Test") && !s.contains("$"))
                    .sorted()
                    .forEach(name -> {
                        try {
                            Class<?> c = Class.forName(name);
                            testClasses.add(c);
                        } catch (LinkageError e) {
                            System.out.println("[skip-unlinkable] " + name);
                        } catch (ClassNotFoundException e) {
                            System.out.println("[skip-missing] " + name);
                        }
                    });
        }
        for (Class<?> c : testClasses) {
            runClass(c);
        }
        System.out.println();
        System.out.println("RESULT: " + passed + " passed, " + failed + " failed"
                + (failures.isEmpty() ? "" : " — failures:"));
        for (String f : failures) {
            System.out.println("  FAIL " + f);
        }
        System.exit(failed == 0 ? 0 : 1);
    }

    private static void runClass(Class<?> c) {
        List<Method> tests = new ArrayList<>();
        for (Method m : allMethods(c)) {
            if (m.isAnnotationPresent(org.junit.jupiter.api.Test.class)
                    || m.isAnnotationPresent(org.junit.jupiter.params.ParameterizedTest.class)) {
                m.setAccessible(true);
                tests.add(m);
            }
        }
        if (tests.isEmpty()) {
            return;
        }
        for (Method m : tests) {
            for (Object[] args : invocations(c, m)) {
                invoke(c, m, args);
            }
        }
    }

    private static List<Method> allMethods(Class<?> c) {
        List<Method> out = new ArrayList<>();
        for (Class<?> k = c; k != null && k != Object.class; k = k.getSuperclass()) {
            out.addAll(Arrays.asList(k.getDeclaredMethods()));
        }
        return out;
    }

    private static List<Object[]> invocations(Class<?> c, Method m) {
        List<Object[]> sets = new ArrayList<>();
        try {
            var vs = m.getAnnotation(org.junit.jupiter.params.provider.ValueSource.class);
            if (vs != null) {
                List<Object> vals = new ArrayList<>();
                for (int v : vs.ints()) vals.add(v);
                for (long v : vs.longs()) vals.add(v);
                for (double v : vs.doubles()) vals.add(v);
                for (boolean v : vs.booleans()) vals.add(v);
                for (String v : vs.strings()) vals.add(v);
                for (Object v : vals) sets.add(new Object[]{v});
            }
            var csv = m.getAnnotation(org.junit.jupiter.params.provider.CsvSource.class);
            if (csv != null) {
                for (String line : csv.value()) {
                    String[] raw = line.split(String.valueOf(csv.delimiter()), -1);
                    Class<?>[] types = m.getParameterTypes();
                    Object[] args = new Object[types.length];
                    for (int i = 0; i < types.length; i++) {
                        String cell = i < raw.length ? raw[i].trim() : "";
                        args[i] = coerce(cell, types[i]);
                    }
                    sets.add(args);
                }
            }
            var es = m.getAnnotation(org.junit.jupiter.params.provider.EnumSource.class);
            if (es != null) {
                Class<?> declared = es.value();
                if (declared == org.junit.jupiter.params.provider.EnumSource.NullEnum.class) {
                    declared = m.getParameterTypes().length > 0 ? m.getParameterTypes()[0] : Enum.class;
                }
                Set<String> names = new HashSet<>(Arrays.asList(es.names()));
                for (Object k : declared.getEnumConstants()) {
                    Enum<?> e = (Enum<?>) k;
                    if (names.isEmpty() || names.contains(e.name())) {
                        sets.add(new Object[]{e});
                    }
                }
            }
            var ms = m.getAnnotation(org.junit.jupiter.params.provider.MethodSource.class);
            if (ms != null) {
                for (String name : ms.value()) {
                    Method factory = Arrays.stream(allMethods(c).toArray(Method[]::new))
                            .filter(x -> Modifier.isStatic(x.getModifiers()) && x.getName().equals(name))
                            .findFirst().orElseThrow(() -> new IllegalStateException("MethodSource not found: " + name));
                    factory.setAccessible(true);
                    Object result = factory.invoke(null);
                    if (result instanceof java.util.stream.Stream<?> s) {
                        s.forEach(o -> {
                            if (o instanceof org.junit.jupiter.params.provider.Arguments a) sets.add(a.get());
                            else if (o instanceof Object[] arr) sets.add(arr);
                            else sets.add(new Object[]{o});
                        });
                    }
                }
            }
            if (m.getAnnotation(org.junit.jupiter.params.provider.NullAndEmptySource.class) != null
                    && m.getParameterCount() == 1) {
                sets.add(new Object[]{null});
                sets.add(new Object[]{coerce("", m.getParameterTypes()[0])});
            }
            if (m.getAnnotation(org.junit.jupiter.params.provider.NullSource.class) != null
                    && m.getParameterCount() == 1) {
                sets.add(new Object[]{null});
            }
            if (sets.isEmpty() && m.getParameterCount() == 0) {
                sets.add(new Object[0]);
            }
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("arg expansion failed for " + m, e);
        }
        return sets;
    }

    private static Object coerce(String raw, Class<?> type) {
        if (raw == null || (raw.isEmpty() && Number.class.isAssignableFrom(boxOf(type)))) {
            return null; // NullAndEmptySource semantics for object-typed params
        }
        if (type == String.class) return raw.equals("''") || raw.equals("null") ? null : raw;
        if (type == int.class || type == Integer.class) return raw.isEmpty() ? null : Integer.valueOf(raw);
        if (type == long.class || type == Long.class) return Long.valueOf(raw);
        if (type == double.class || type == Double.class) return Double.valueOf(raw);
        if (type == boolean.class || type == Boolean.class) return Boolean.valueOf(raw);
        if (Enum.class.isAssignableFrom(type)) {
            @SuppressWarnings({"unchecked", "rawtypes"}) Object v = Enum.valueOf((Class<? extends Enum>) type, raw);
            return v;
        }
        return raw;
    }

    private static Class<?> boxOf(Class<?> t) {
        if (!t.isPrimitive()) return t;
        if (t == int.class) return Integer.class;
        if (t == long.class) return Long.class;
        if (t == double.class) return Double.class;
        if (t == boolean.class) return Boolean.class;
        return t;
    }

    private static void invoke(Class<?> c, Method m, Object[] args) {
        String id = c.getSimpleName() + "." + m.getName()
                + (m.getParameterCount() > 0 ? Arrays.toString(args) : "");
        try {
            Constructor<?> ctor = c.getDeclaredConstructor();
            ctor.setAccessible(true);
            Object inst = ctor.newInstance();
            for (Field f : c.getDeclaredFields()) {
                if (f.isAnnotationPresent(org.junit.jupiter.api.io.TempDir.class)) {
                    f.setAccessible(true);
                    Path dir = Files.createTempDirectory("lt-tempdir");
                    if (f.getType() == java.io.File.class) f.set(inst, dir.toFile());
                    else f.set(inst, dir);
                }
            }
            for (Method b : filtered(c, org.junit.jupiter.api.BeforeEach.class)) invoke0(inst, b);
            invoke0(inst, m, args);
            for (Method a : filtered(c, org.junit.jupiter.api.AfterEach.class)) invoke0(inst, a);
            passed++;
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            failed++;
            failures.add(id + " -> " + cause);
        } catch (ReflectiveOperationException | java.io.IOException e) {
            failed++;
            failures.add(id + " -> " + e);
        }
    }

    private static List<Method> filtered(Class<?> c, Class<? extends java.lang.annotation.Annotation> anno) {
        List<Method> out = new ArrayList<>();
        for (Method m : allMethods(c)) {
            if (m.isAnnotationPresent(anno)) {
                m.setAccessible(true);
                out.add(m);
            }
        }
        return out;
    }

    private static void invoke0(Object inst, Method m, Object... args) throws ReflectiveOperationException {
        m.invoke(inst, args);
    }
}
