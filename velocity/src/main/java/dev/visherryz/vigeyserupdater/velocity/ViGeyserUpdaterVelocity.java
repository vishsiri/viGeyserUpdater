package dev.visherryz.vigeyserupdater.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import dev.visherryz.vigeyserupdater.LogSink;
import dev.visherryz.vigeyserupdater.PlatformKind;
import dev.visherryz.vigeyserupdater.UpdaterEngine;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

@Plugin(id = "vigeyserupdater", name = "viGeyserUpdater", version = "1.0.0",
        authors = {"VisherRyz"}, description = "Crash-safe Geyser companion updater")
public final class ViGeyserUpdaterVelocity {
    private final ProxyServer proxy;
    private final Logger logger;
    private final Path dataDirectory;
    private UpdaterEngine engine;

    @Inject
    public ViGeyserUpdaterVelocity(ProxyServer proxy, Logger logger, @DataDirectory Path dataDirectory) {
        this.proxy = proxy; this.logger = logger; this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onInitialize(ProxyInitializeEvent event) {
        try {
            engine = new UpdaterEngine(Path.of("").toAbsolutePath().normalize(), dataDirectory,
                    PlatformKind.VELOCITY, new VelocityLog(logger));
            var meta = proxy.getCommandManager().metaBuilder("geyserupdates").aliases("gupdates")
                    .plugin(this).build();
            proxy.getCommandManager().register(meta, new UpdaterCommand());
        } catch (Exception error) {
            logger.error("Could not start viGeyserUpdater", error);
        }
    }

    @Subscribe public void onShutdown(ProxyShutdownEvent event) { if (engine != null) engine.close(); }

    private final class UpdaterCommand implements SimpleCommand {
        @Override public void execute(Invocation invocation) {
            CommandSource sender = invocation.source();
            if (!sender.hasPermission("vigeyserupdater.admin")) { sender.sendMessage(Component.text("You do not have permission.")); return; }
            if (engine == null) { sender.sendMessage(Component.text("viGeyserUpdater failed to initialize.")); return; }
            String[] args = invocation.arguments();
            String action = args.length == 0 ? "status" : args[0].toLowerCase(Locale.ROOT);
            Consumer<String> output = message -> proxy.getScheduler().buildTask(ViGeyserUpdaterVelocity.this,
                    () -> sender.sendMessage(Component.text("[viGeyserUpdater] " + message))).schedule();
            switch (action) {
                case "status" -> engine.snapshot().forEach(status -> output.accept(UpdaterEngine.format(status)));
                case "check" -> { output.accept("Checking for updates..."); engine.check(false, args.length > 1 ? args[1] : null, output); }
                case "update" -> { output.accept("Checking and installing updates..."); engine.check(true, args.length > 1 ? args[1] : null, output); }
                case "reload" -> {
                    try { engine.reload(); output.accept("Configuration reloaded."); }
                    catch (Exception error) { output.accept("Reload failed: " + error.getMessage()); }
                }
                default -> output.accept("Usage: /geyserupdates <status|check|update|reload> [artifact|all]");
            }
        }

        @Override public List<String> suggest(Invocation invocation) {
            if (!invocation.source().hasPermission("vigeyserupdater.admin") || engine == null) return List.of();
            String[] args = invocation.arguments();
            if (args.length <= 1) return complete(List.of("status", "check", "update", "reload"), args.length == 0 ? "" : args[0]);
            if (args.length == 2 && (args[0].equalsIgnoreCase("check") || args[0].equalsIgnoreCase("update"))) {
                List<String> ids = new ArrayList<>(); ids.add("all"); engine.snapshot().forEach(status -> ids.add(status.id()));
                return complete(ids, args[1]);
            }
            return List.of();
        }
        @Override public boolean hasPermission(Invocation invocation) { return invocation.source().hasPermission("vigeyserupdater.admin"); }
    }

    private static List<String> complete(List<String> values, String input) {
        String prefix = input.toLowerCase(Locale.ROOT);
        return values.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
    }

    private record VelocityLog(Logger delegate) implements LogSink {
        @Override public void info(String message) { delegate.info(message); }
        @Override public void warn(String message) { delegate.warn(message); }
        @Override public void error(String message, Throwable error) { delegate.error(message, error); }
    }
}
