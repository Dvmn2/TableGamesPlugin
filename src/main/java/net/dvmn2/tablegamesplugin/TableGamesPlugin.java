package net.dvmn2.tablegamesplugin;

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.dvmn2.tablegamesplugin.command.TableGamesCommand;
import net.dvmn2.tablegamesplugin.listener.BlockInteractListener;
import net.dvmn2.tablegamesplugin.listener.EntityInteractListener;
import net.dvmn2.tablegamesplugin.listener.EntityLoadListener;
import net.dvmn2.tablegamesplugin.storage.DeckStore;
import net.dvmn2.tablegamesplugin.util.InteractionGuard;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.function.Supplier;

/** Composition root of the plugin: builds the objects, registers them and stops them. */
public final class TableGamesPlugin extends JavaPlugin {

    /** How often stale click-debounce entries are swept, ticks (once a minute). */
    private static final long GUARD_SWEEP_INTERVAL_TICKS = 20L * 60;

    private volatile TableGamesSettings settings;
    private DeckManager manager;
    private BukkitTask guardSweepTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadSettings();
        Supplier<TableGamesSettings> settingsSupplier = () -> settings;

        PluginKeys keys = new PluginKeys(this);
        CardItemFactory cardItems = new CardItemFactory(keys);
        DeckItemFactory deckItems = new DeckItemFactory(keys);
        DeckStore store = new DeckStore(getDataFolder().toPath().resolve("decks.json"), getLogger());

        manager = new DeckManager(this, settingsSupplier, keys, new DisplayFactory(keys), deckItems, cardItems,
                new SoundPlayer(settingsSupplier), store);
        manager.start();

        InteractionGuard guard = new InteractionGuard();
        getServer().getPluginManager().registerEvents(
                new BlockInteractListener(manager, deckItems, cardItems, guard), this);
        getServer().getPluginManager().registerEvents(
                new EntityInteractListener(manager, deckItems, cardItems, keys, guard), this);
        getServer().getPluginManager().registerEvents(new EntityLoadListener(manager), this);

        TableGamesCommand command = new TableGamesCommand(this, deckItems);
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
                event.registrar().register(command.build(), "Give table game decks and manage the plugin"));

        guardSweepTask = getServer().getScheduler().runTaskTimer(
                this, guard::sweepStaleEntries, GUARD_SWEEP_INTERVAL_TICKS, GUARD_SWEEP_INTERVAL_TICKS);

        getLogger().info("Enabled with " + manager.getDeckCount() + " active deck(s).");
    }

    @Override
    public void onDisable() {
        if (guardSweepTask != null) {
            guardSweepTask.cancel();
        }
        if (manager != null) {
            manager.shutdown();
        }
    }

    /** Re-reads config.yml and applies it without restarting the plugin or losing state. */
    public void reloadAll() {
        reloadConfig();
        reloadSettings();
        manager.onSettingsReloaded();
    }

    private void reloadSettings() {
        settings = TableGamesSettings.parse(getConfig(), getLogger());
        Lang.setLanguage(settings.language());
    }
}
