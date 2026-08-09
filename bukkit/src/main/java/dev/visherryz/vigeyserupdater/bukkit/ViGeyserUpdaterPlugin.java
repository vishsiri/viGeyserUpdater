package dev.visherryz.vigeyserupdater.bukkit;

import dev.visherryz.vigeyserupdater.LogSink;
import dev.visherryz.vigeyserupdater.PlatformKind;
import dev.visherryz.vigeyserupdater.UpdaterEngine;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;

public final class ViGeyserUpdaterPlugin extends JavaPlugin implements CommandExecutor, TabCompleter {
    private UpdaterEngine engine;

    @Override
    public void onEnable() {
        try {
            Path serverRoot = getServer().getWorldContainer().toPath().toAbsolutePath().normalize();
            engine = new UpdaterEngine(serverRoot, getDataFolder().toPath(), PlatformKind.BUKKIT, new BukkitLog(getLogger()));
            Objects.requireNonNull(getCommand("geyserupdates")).setExecutor(this);
            Objects.requireNonNull(getCommand("geyserupdates")).setTabCompleter(this);
        } catch (Exception error) {
            getLogger().log(java.util.logging.Level.SEVERE, "Could not start viGeyserUpdater", error);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override public void onDisable() { if (engine != null) engine.close(); }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("vigeyserupdater.admin")) { sender.sendMessage("You do not have permission."); return true; }
        String action = args.length == 0 ? "status" : args[0].toLowerCase(Locale.ROOT);
        Consumer<String> output = message -> dispatch(() -> sender.sendMessage("[viGeyserUpdater] " + message));
        switch (action) {
            case "status" -> engine.snapshot().forEach(status -> output.accept(UpdaterEngine.format(status)));
            case "check" -> { output.accept("Checking for updates..."); engine.check(false, args.length > 1 ? args[1] : null, output); }
            case "update" -> { output.accept("Checking and installing updates..."); engine.check(true, args.length > 1 ? args[1] : null, output); }
            case "reload" -> {
                try { engine.reload(); output.accept("Configuration reloaded."); }
                catch (Exception error) { output.accept("Reload failed: " + error.getMessage()); }
            }
            default -> output.accept("Usage: /" + label + " <status|check|update|reload> [artifact|all]");
        }
        return true;
    }

    private void dispatch(Runnable task) {
        try {
            Method getter = Bukkit.class.getMethod("getGlobalRegionScheduler");
            Object scheduler = getter.invoke(null);
            Method execute = scheduler.getClass().getMethod("execute", org.bukkit.plugin.Plugin.class, Runnable.class);
            execute.invoke(scheduler, this, task);
        } catch (NoSuchMethodException unavailable) {
            Bukkit.getScheduler().runTask(this, task);
        } catch (ReflectiveOperationException error) {
            getLogger().log(java.util.logging.Level.WARNING, "Could not dispatch command response", error);
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("vigeyserupdater.admin")) return List.of();
        if (args.length == 1) return complete(List.of("status", "check", "update", "reload"), args[0]);
        if (args.length == 2 && (args[0].equalsIgnoreCase("check") || args[0].equalsIgnoreCase("update"))) {
            List<String> ids = new ArrayList<>(); ids.add("all"); engine.snapshot().forEach(status -> ids.add(status.id()));
            return complete(ids, args[1]);
        }
        return List.of();
    }
    private static List<String> complete(List<String> values, String input) {
        String prefix = input.toLowerCase(Locale.ROOT);
        return values.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
    }

    private record BukkitLog(java.util.logging.Logger delegate) implements LogSink {
        @Override public void info(String message) { delegate.info(message); }
        @Override public void warn(String message) { delegate.warning(message); }
        @Override public void error(String message, Throwable error) { delegate.log(java.util.logging.Level.SEVERE, message, error); }
    }
}
