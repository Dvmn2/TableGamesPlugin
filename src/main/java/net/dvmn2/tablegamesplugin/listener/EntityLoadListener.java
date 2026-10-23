package net.dvmn2.tablegamesplugin.listener;

import net.dvmn2.tablegamesplugin.DeckManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.EntitiesLoadEvent;

import java.util.List;

/**
 * Removes orphaned plugin entities as chunks load. Entities in chunks that were not loaded at
 * startup cannot be checked then, so every chunk load is checked as well. Thread: main server thread.
 */
public final class EntityLoadListener implements Listener {

    private final DeckManager manager;

    public EntityLoadListener(DeckManager manager) {
        this.manager = manager;
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        manager.removeOrphansLater(List.copyOf(event.getEntities()));
    }
}
