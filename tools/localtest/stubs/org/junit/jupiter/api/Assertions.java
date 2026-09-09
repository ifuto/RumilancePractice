package org.junit.jupiter.api;
import java.util.Objects;
/** Subset of JUnit 5 Assertions used by this repo's tests (local ECJ test runner; CI runs real JUnit 6). */
public final class Assertions {
    private Assertions() { }
    public static void fail(String m) { throw new AssertionError(m); }
    private static String fmt(Object msg, String def) { return msg == null ? def : String.valueOf(msg); }
    public static void assertTrue(boolean c) { assertTrue(c, null); }
    public static void assertTrue(boolean c, String m) { if (!c) throw new AssertionError(fmt(m,"expected: <true> but was: <false>")); }
    public static void assertFalse(boolean c) { assertFalse(c, null); }
    public static void assertFalse(boolean c, String m) { if (c) throw new AssertionError(fmt(m,"expected: <false> but was: <true>")); }
    public static void assertNull(Object o) { assertNull(o, null); }
    public static void assertNull(Object o, String m) { if (o != null) throw new AssertionError(fmt(m,"expected: <null> but was: <"+o+">")); }
    public static void assertNotNull(Object o) { assertNotNull(o, null); }
    public static void assertNotNull(Object o, String m) { if (o == null) throw new AssertionError(fmt(m,"expected non-null value")); }
    public static void assertSame(Object a, Object b) { assertSame(a, b, null); }
    public static void assertSame(Object a, Object b, String m) { if (a != b) throw new AssertionError(fmt(m,"expected same instance")); }
    public static void assertNotSame(Object a, Object b) { assertNotSame(a, b, null); }
    public static void assertNotSame(Object a, Object b, String m) { if (a == b) throw new AssertionError(fmt(m,"expected different instances")); }
    public static void assertEquals(Object e, Object a) { assertEquals(e, a, null); }
    public static void assertEquals(Object e, Object a, String m) { if (!Objects.equals(e, a)) throw new AssertionError(fmt(m,"expected: <"+e+"> but was: <"+a+">")); }
    public static void assertEquals(int e, int a) { assertEquals(e, a, null); }
    public static void assertEquals(int e, int a, String m) { if (e != a) throw new AssertionError(fmt(m,"expected: <"+e+"> but was: <"+a+">")); }
    public static void assertEquals(long e, long a) { assertEquals(e, a, null); }
    public static void assertEquals(long e, long a, String m) { if (e != a) throw new AssertionError(fmt(m,"expected: <"+e+"> but was: <"+a+">")); }
    public static void assertEquals(double e, double a, double delta) { assertEquals(e, a, delta, null); }
    public static void assertEquals(double e, double a, double delta, String m) {
        if (Math.abs(e - a) > delta || (Double.isNaN(e) != Double.isNaN(a))) throw new AssertionError(fmt(m,"expected: <"+e+"> but was: <"+a+">"));
    }
    public static void assertNotEquals(Object e, Object a) { assertNotEquals(e, a, null); }
    public static void assertNotEquals(Object e, Object a, String m) { if (Objects.equals(e, a)) throw new AssertionError(fmt(m,"unexpected equal value: <"+a+">")); }
    public static void assertNotEquals(long e, long a) { if (e == a) throw new AssertionError("unexpected equal value: <"+a+">"); }
    public static void assertArrayEquals(int[] e, int[] a) { assertArrayEquals(e, a, null); }
    public static void assertArrayEquals(int[] e, int[] a, String m) { if (!java.util.Arrays.equals(e, a)) throw new AssertionError(fmt(m,"arrays differ: "+java.util.Arrays.toString(e)+" vs "+java.util.Arrays.toString(a))); }
    public static void assertArrayEquals(Object[] e, Object[] a) { assertArrayEquals(e, a, null); }
    public static void assertArrayEquals(Object[] e, Object[] a, String m) { if (!java.util.Arrays.equals(e, a)) throw new AssertionError(fmt(m,"arrays differ")); }
    public static void assertArrayEquals(boolean[] e, boolean[] a) { if (!java.util.Arrays.equals(e, a)) throw new AssertionError("arrays differ"); }
    public interface Executable { void execute() throws Throwable; }
    public static <T extends Throwable> T assertThrows(Class<T> type, Executable exec) {
        try { exec.execute(); } catch (Throwable t) {
            if (type.isInstance(t)) return type.cast(t);
            throw new AssertionError("unexpected exception type thrown, expected <"+type.getName()+"> but was <"+t.getClass().getName()+">", t);
        }
        throw new AssertionError("expected <"+type.getName()+"> to be thrown, but nothing was thrown");
    }
}
