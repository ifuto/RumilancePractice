package org.junit.jupiter.params.provider;
public final class Arguments {
    private final Object[] args;
    private Arguments(Object... args) { this.args = args; }
    public static Arguments of(Object... args) { return new Arguments(args); }
    public Object[] get() { return args; }
}
