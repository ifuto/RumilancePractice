package com.rumilance.practice.security.sign;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.KeybindComponent;
import net.kyori.adventure.text.ScoreComponent;
import net.kyori.adventure.text.SelectorComponent;
import net.kyori.adventure.text.TranslatableComponent;

import java.util.List;
import java.util.Locale;

/**
 * Walks a sign's Adventure {@link Component} tree looking for structures a normal client would
 * never place on a sign but that cheat clients use to probe/exploit servers via translation keys:
 * keybind components, click/hover events, score/selector components, deeply nested trees, and
 * translatable keys outside the vanilla block/item namespaces.
 *
 * <p>To avoid false bans, only the highest-confidence structures (keybind, click events, and
 * configured probe keys) are classified as {@link SignScanResult.Severity#MALICIOUS}; everything
 * else that is unusual is merely {@link SignScanResult.Severity#SUSPICIOUS} (blocked, not banned).</p>
 */
public final class SignComponentScanner {

    private final int maxNestDepth;
    private final List<String> allowedTranslatablePrefixes;
    private final List<String> criticalKeys;
    private final List<String> criticalKeyPrefixes;

    public SignComponentScanner(int maxNestDepth, List<String> allowedTranslatablePrefixes,
                                List<String> criticalKeys, List<String> criticalKeyPrefixes) {
        this.maxNestDepth = maxNestDepth;
        this.allowedTranslatablePrefixes = allowedTranslatablePrefixes;
        this.criticalKeys = criticalKeys;
        this.criticalKeyPrefixes = criticalKeyPrefixes == null ? List.of() : criticalKeyPrefixes;
    }

    public SignScanResult scan(Component root) {
        if (root == null) {
            return SignScanResult.CLEAN;
        }
        return walk(root, 0);
    }

    private SignScanResult walk(Component component, int depth) {
        SignScanResult result = classify(component, depth);
        for (Component child : component.children()) {
            result = result.max(walk(child, depth + 1));
            if (result.isMalicious()) {
                return result;
            }
        }
        return result;
    }

    private SignScanResult classify(Component component, int depth) {
        // Click events on a sign are always malicious (phishing / command execution probes).
        if (component.style().clickEvent() != null) {
            return new SignScanResult(SignScanResult.Severity.MALICIOUS, "click event in sign component");
        }
        if (component instanceof KeybindComponent keybind) {
            return new SignScanResult(SignScanResult.Severity.MALICIOUS,
                    "keybind component: " + keybind.keybind());
        }
        if (component instanceof TranslatableComponent translatable) {
            return classifyTranslatable(translatable);
        }
        if (component.style().hoverEvent() != null) {
            return new SignScanResult(SignScanResult.Severity.SUSPICIOUS, "hover event in sign component");
        }
        if (component instanceof ScoreComponent) {
            return new SignScanResult(SignScanResult.Severity.SUSPICIOUS, "score component in sign");
        }
        if (component instanceof SelectorComponent) {
            return new SignScanResult(SignScanResult.Severity.SUSPICIOUS, "selector component in sign");
        }
        if (depth > maxNestDepth) {
            return new SignScanResult(SignScanResult.Severity.SUSPICIOUS,
                    "component nesting deeper than " + maxNestDepth);
        }
        return SignScanResult.CLEAN;
    }

    private SignScanResult classifyTranslatable(TranslatableComponent translatable) {
        String key = translatable.key();
        String lower = key == null ? "" : key.toLowerCase(Locale.ROOT);
        for (String critical : criticalKeys) {
            if (lower.equals(critical.toLowerCase(Locale.ROOT))) {
                return new SignScanResult(SignScanResult.Severity.MALICIOUS, "known probe key: " + key);
            }
        }
        // Mod/cheat namespace signatures (e.g. "key.meteor-client.", "key.autototem.").
        for (String prefix : criticalKeyPrefixes) {
            if (!prefix.isBlank() && lower.startsWith(prefix.toLowerCase(Locale.ROOT))) {
                return new SignScanResult(SignScanResult.Severity.MALICIOUS, "known mod signature: " + key);
            }
        }
        // Keybind probes routinely use translatable keys under the "key." namespace.
        if (lower.startsWith("key.")) {
            return new SignScanResult(SignScanResult.Severity.MALICIOUS, "key.* translatable probe: " + key);
        }
        for (String allowed : allowedTranslatablePrefixes) {
            if (lower.startsWith(allowed.toLowerCase(Locale.ROOT))) {
                return SignScanResult.CLEAN;
            }
        }
        // A translatable outside the vanilla allow-list is unusual on a sign but not, by itself,
        // proof of an exploit  Eblock and log without banning.
        return new SignScanResult(SignScanResult.Severity.SUSPICIOUS, "non-vanilla translatable key: " + key);
    }
}
