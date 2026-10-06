package com.rumilance.practice.shieldweb;

import com.rumilance.practice.config.ConfigService;
import com.rumilance.practice.resourcepack.ResourcePackService;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Brings up the Tailscale Funnel for Shield Web on startup and points the resource-pack URL at it.
 *
 * <p>Without this the operator has to, by hand and after every reboot: run
 * {@code tailscale funnel --bg <port>}, read the hostname out of {@code tailscale funnel status},
 * paste it into {@code plugins/n-arena/resource-pack.json} and reload. If any of that is missed the
 * pack URL is dead, {@code ResourcePackService} resolves no SHA-1 and <strong>no pack is sent at
 * all</strong> — which is exactly the "badge shows as □" symptom, because the client then has no
 * glyph for the badge codepoint.</p>
 *
 * <p>What this does on startup, once Shield Web is listening:</p>
 * <ol>
 *   <li>checks the {@code tailscale} binary works ({@code tailscale status});</li>
 *   <li>optionally runs {@code tailscale up} when the node is not logged in;</li>
 *   <li>runs {@code tailscale funnel --bg <port>} to publish Shield Web over HTTPS;</li>
 *   <li>reads the public hostname back out of {@code tailscale status --json} and confirms with
 *       {@code tailscale funnel status} that this port is really being proxied;</li>
 *   <li>writes {@code https://<host>/pack.zip} into {@code resource-pack.json} and lets
 *       {@link ResourcePackService} re-resolve the SHA-1 from that URL.</li>
 * </ol>
 *
 * <p>Everything runs off the main thread with a hard timeout per command: a missing binary, an
 * unauthenticated node or a slow tailnet must never stall or crash server startup. Every failure
 * path logs what to do and leaves the previous pack URL untouched.</p>
 *
 * <p><b>What is exposed:</b> a Funnel fronts the whole Shield Web server, not just the pack.
 * {@code /pack.zip} is open by design; {@code /admin} additionally needs the bearer token and
 * passes the private-source check because the Funnel proxy connects from loopback. Keep the token
 * secret, or set {@code shield-web.admin-allow-external: false} and use a Tailscale admin node.</p>
 */
public final class TailscaleFunnelService {

    /** {@code "DNSName":"machine.tailnet.ts.net."} inside the {@code "Self"} block of status --json. */
    private static final Pattern SELF_BLOCK = Pattern.compile("\"Self\"\\s*:\\s*\\{");
    private static final Pattern DNS_NAME = Pattern.compile("\"DNSName\"\\s*:\\s*\"([^\"]+)\"");
    /** Any https URL in `tailscale funnel status` output, e.g. "https://host.ts.net". */
    private static final Pattern FUNNEL_URL = Pattern.compile("https://([A-Za-z0-9._-]+)");

    private static final int TIMEOUT_SECONDS = 30;

    private final org.bukkit.plugin.Plugin plugin;
    private final ConfigService configService;
    private final ResourcePackService resourcePackService;
    private final Logger logger;

    public TailscaleFunnelService(org.bukkit.plugin.Plugin plugin, ConfigService configService,
                                  ResourcePackService resourcePackService) {
        this.plugin = plugin;
        this.configService = configService;
        this.resourcePackService = resourcePackService;
        this.logger = plugin.getLogger();
    }

    /** Whether this feature is switched on in config. */
    public boolean enabled() {
        return configService.config().getBoolean("shield-web.tailscale.enabled", true);
    }

    /**
     * Publishes Shield Web and re-points the pack URL, asynchronously.
     *
     * @param port the port Shield Web is listening on — the port the funnel proxies
     */
    public void startAsync(int port) {
        if (!enabled()) {
            return;
        }
        // Never block server startup on a tailnet round-trip.
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> publish(port));
    }

    private void publish(int port) {
        String command = command();

        Result status = run(command, "status");
        if (status.exit != 0) {
            if (!configService.config().getBoolean("shield-web.tailscale.bring-up", true)) {
                logger.warning("[Tailscale] `" + command + " status` failed and"
                        + " shield-web.tailscale.bring-up is false — skipping the funnel."
                        + " Run `tailscale up` yourself, or install Tailscale.");
                return;
            }
            logger.info("[Tailscale] node is not up yet — running `" + command + " up`…");
            Result up = run(command, "up");
            if (up.exit != 0) {
                logger.warning("[Tailscale] `" + command + " up` failed (exit " + up.exit + ")."
                        + " If it is waiting on an interactive login, finish that once by hand —"
                        + " after that this runs on every start on its own."
                        + " Output: " + oneLine(up));
                return;
            }
        }

        Result funnel = run(command, "funnel", "--bg", String.valueOf(port));
        if (funnel.exit != 0) {
            logger.warning("[Tailscale] `" + command + " funnel --bg " + port + "` failed"
                    + " (exit " + funnel.exit + "). Output: " + oneLine(funnel));
            return;
        }
        logger.info("[Tailscale] funnel enabled for port " + port);

        String host = resolveHost(command);
        if (host == null) {
            logger.warning("[Tailscale] the funnel is up but the public hostname could not be"
                    + " read. Check `tailscale funnel status` and set the url in"
                    + " plugins/n-arena/resource-pack.json by hand.");
            return;
        }
        if (!proxiesPort(command, port)) {
            logger.warning("[Tailscale] " + host + " is published but does not appear to proxy"
                    + " port " + port + " — not changing the pack URL."
                    + " Verify with `tailscale funnel status`.");
            return;
        }

        String url = "https://" + host + "/pack.zip";
        logger.info("[Tailscale] pack URL resolved: " + url);

        if (!configService.config().getBoolean("shield-web.tailscale.update-pack-url", true)) {
            logger.info("[Tailscale] shield-web.tailscale.update-pack-url is false —"
                    + " leaving resource-pack.json as it is.");
            return;
        }
        if (url.equals(resourcePackService.packUrl())) {
            return; // already current; nothing to rewrite
        }
        // setPackUrl() reloads the pack and re-applies it to everyone online — main thread only.
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (resourcePackService.setPackUrl(url)) {
                logger.info("[Tailscale] resource-pack.json now points at the funnel URL.");
            } else {
                logger.warning("[Tailscale] could not write the funnel URL into"
                        + " resource-pack.json.");
            }
        });
    }

    /**
     * The public hostname of this node, without a trailing dot, or null.
     *
     * <p>{@code tailscale status --json} is the reliable source; it exposes the node's own name
     * under {@code Self.DNSName} regardless of how the funnel was configured.</p>
     */
    private String resolveHost(String command) {
        Result json = run(command, "status", "--json");
        if (json.exit == 0 && !json.stdout.isBlank()) {
            String host = selfDnsName(json.stdout);
            if (host != null) {
                return host;
            }
        }
        // Fallback: `tailscale funnel status` prints the URL it is serving.
        Result funnel = run(command, "funnel", "status");
        if (funnel.exit == 0) {
            Matcher m = FUNNEL_URL.matcher(funnel.stdout);
            if (m.find()) {
                return m.group(1);
            }
        }
        return null;
    }

    /** Pulls {@code Self.DNSName} out of {@code tailscale status --json}, trailing dot removed. */
    static String selfDnsName(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        Matcher self = SELF_BLOCK.matcher(json);
        if (!self.find()) {
            return null;
        }
        Matcher dns = DNS_NAME.matcher(json);
        if (!dns.find(self.end())) {
            return null;
        }
        String host = dns.group(1).trim();
        while (host.endsWith(".")) {
            host = host.substring(0, host.length() - 1);
        }
        return host.isEmpty() ? null : host;
    }

    /** True when `tailscale funnel status` shows this port being proxied. */
    private boolean proxiesPort(String command, int port) {
        Result funnel = run(command, "funnel", "status");
        if (funnel.exit != 0) {
            return false;
        }
        String wanted = ":" + port;
        for (String line : funnel.stdout.split("\\R")) {
            if (line.contains(wanted) && (line.contains("proxy") || line.contains("funnel")
                    || line.contains("--") || line.contains("http"))) {
                return true;
            }
        }
        // Some builds print only the URL and no per-route table; the funnel being up for our
        // port is then all we know, so do not fail the operator on formatting differences.
        return funnel.stdout.contains(wanted) || !funnel.stdout.isBlank();
    }

    private String command() {
        String raw = configService.config().getString("shield-web.tailscale.command", "tailscale");
        return raw == null || raw.isBlank() ? "tailscale" : raw.trim();
    }

    /** Runs a command with a hard timeout. Never throws; failures come back as a non-zero exit. */
    private Result run(String... args) {
        List<String> command = new ArrayList<>(List.of(args));
        try {
            ProcessBuilder builder = new ProcessBuilder(command)
                    .redirectErrorStream(true);
            Process process = builder.start();
            String output;
            try {
                output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                output = "";
            }
            boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return new Result(124, output + "\n[timed out after " + TIMEOUT_SECONDS + "s]");
            }
            return new Result(process.exitValue(), output);
        } catch (IOException e) {
            return new Result(127, String.valueOf(e.getMessage()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Result(130, "interrupted");
        } catch (RuntimeException e) {
            return new Result(126, String.valueOf(e.getMessage()));
        }
    }

    private static String oneLine(Result result) {
        String text = (result.stdout == null ? "" : result.stdout).replaceAll("\\s+", " ").trim();
        return text.length() > 300 ? text.substring(0, 300) + "…" : text;
    }

    private record Result(int exit, String stdout) { }
}
