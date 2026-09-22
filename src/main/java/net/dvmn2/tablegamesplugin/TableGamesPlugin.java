package net.dvmn2.tablegamesplugin;

import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.dvmn2.tablegamesplugin.command.TableGamesCommand;
import net.dvmn2.tablegamesplugin.listener.BlockInteractListener;
import net.dvmn2.tablegamesplugin.listener.EntityInteractListener;
import net.dvmn2.tablegamesplugin.manager.DeckManager;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public final class TableGamesPlugin extends JavaPlugin {

    /**
     * How often {@link DeckManager#flushIfDirty()} runs. Chosen as a compromise between
     * "state is basically always safely on disk" and "not doing pointless work when
     * nothing changed" — flushIfDirty() is a no-op unless something actually changed
     * since the last flush.
     */
    private static final long SAVE_FLUSH_INTERVAL_TICKS = 20L * 5; // every 5 seconds

    private static TableGamesPlugin instance;

    private DeckManager deckManager;
    private BukkitTask saveFlushTask;

    public TableGamesPlugin() {
        // Per Paper's guidance, LifecycleEvents.COMMANDS is registered in the
        // constructor rather than onEnable(): the event can fire before onEnable()
        // runs (e.g. during certain reload flows), so registering it here guarantees
        // the handler is attached in time.
        this.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            Commands commands = event.registrar();
            commands.register(
                    TableGamesCommand.build(),
                    "Give a fresh deck of table game.",
                    java.util.List.of() // no aliases
            );
        });
    }

    public static TableGamesPlugin getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;

        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }

        // Writes the bundled config.yml on first run; Lang reads settings.language from it.
        saveDefaultConfig();
        Lang.setLanguage(getConfig().getString("settings.language", "auto"));

        this.deckManager = new DeckManager(this);
        this.deckManager.loadState();

        getServer().getPluginManager().registerEvents(new BlockInteractListener(this, deckManager), this);
        getServer().getPluginManager().registerEvents(new EntityInteractListener(this, deckManager), this);

        // Runs on the main thread (cheap unless dirty), and internally hands the actual
        // disk write off to an async task — see DeckManager#flushIfDirty() for why.
        this.saveFlushTask = Bukkit.getScheduler().runTaskTimer(
                this, deckManager::flushIfDirty, SAVE_FLUSH_INTERVAL_TICKS, SAVE_FLUSH_INTERVAL_TICKS);

        getLogger().info("TableGamesPlugin enabled with " + deckManager.getAllDecks().size() + " active deck(s).");
    }

    @Override
    public void onDisable() {
        if (saveFlushTask != null) {
            saveFlushTask.cancel();
        }
        if (deckManager != null) {
            // Synchronous, blocking save: we need this to fully complete before shutdown
            // proceeds, so the periodic async flush path is not used here.
            deckManager.saveState();
        }
        getLogger().info("TableGamesPlugin disabled.");
    }

    public DeckManager getDeckManager() {
        return deckManager;
    }
}