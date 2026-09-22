package net.dvmn2.tablegamesplugin.display;

import net.dvmn2.tablegamesplugin.TableGamesPlugin;
import net.dvmn2.tablegamesplugin.model.Card;
import net.dvmn2.tablegamesplugin.util.CardSymbols;
import net.dvmn2.tablegamesplugin.util.PluginKeys;
import net.dvmn2.tablegamesplugin.util.TransformUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Display;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.joml.Vector3f;

import java.util.List;
import java.util.UUID;

/**
 * Spawns the {@link ItemDisplay} and {@link TextDisplay} entities used to represent a
 * physical deck, its discard pile, its trump card, and revealed cards on the table, as
 * well as the invisible {@link Interaction} entities that make the deck, discard pile,
 * and revealed cards actually clickable.
 * <p>
 * {@code Display} entities (item_display/text_display/block_display) have no interaction
 * hitbox in vanilla Minecraft &mdash; a player's right-click raytrace can never select
 * them, so {@link org.bukkit.event.player.PlayerInteractEntityEvent} would never fire for
 * them on its own. This is exactly why Mojang introduced the {@code Interaction} entity
 * type in 1.19.4: a companion, invisible hitbox with configurable width/height that is
 * itself pickable and interactable, meant to be layered on top of a purely visual
 * {@code Display} entity. Every clickable surface here therefore has both an entity for
 * looks and a separate {@code Interaction} entity, at the same position, carrying the PDC
 * tags that {@code EntityInteractListener} reads. The trump display has no companion
 * {@code Interaction} entity since it is not meant to be interactive.
 * <p>
 * All visual parameters follow specification sections 8-10, 18, and 32-33.
 */
public final class DisplayFactory {

    private static final Vector3f ITEM_DISPLAY_SCALE = new Vector3f(0.5f, 0.5f, 1.0f);
    private static final Vector3f TEXT_DISPLAY_SCALE = new Vector3f(0.1875f, 0.1875f, 1.0f);
    private static final float PITCH_DEGREES = -90f;

    /**
     * Footprint of the clickable box for the deck/discard pile. Wide enough to be easy to
     * click, but narrower than the 1-block gap between the deck and discard piles (see
     * {@code DeckManager#createPhysicalDeck}) so the two boxes never overlap.
     */
    private static final float PILE_INTERACTION_WIDTH = 0.7f;
    private static final float PILE_INTERACTION_HEIGHT = 0.4f;

    private static final float REVEALED_CARD_INTERACTION_WIDTH = 0.5f;
    private static final float REVEALED_CARD_INTERACTION_HEIGHT = 0.3f;

    @SuppressWarnings("unused")
    private final TableGamesPlugin plugin;

    public DisplayFactory(TableGamesPlugin plugin) {
        this.plugin = plugin;
    }

    public ItemDisplay spawnCardItemDisplay(Location location, float yaw, UUID deckId, String pdcType) {
        return location.getWorld().spawn(location, ItemDisplay.class, entity -> {
            entity.setBillboard(Display.Billboard.FIXED);
            entity.setItemStack(buildDisplayMapItem());
            entity.setTransformation(TransformUtil.flatTransformation(yaw, PITCH_DEGREES, ITEM_DISPLAY_SCALE));

            PersistentDataContainer pdc = entity.getPersistentDataContainer();
            pdc.set(PluginKeys.type(), PersistentDataType.STRING, pdcType);
            pdc.set(PluginKeys.deckId(), PersistentDataType.STRING, deckId.toString());
        });
    }

    public TextDisplay spawnTrumpDisplay(Location location, float yaw, UUID deckId, String symbol) {
        return location.getWorld().spawn(location, TextDisplay.class, entity -> {
            configureTextDisplay(entity, yaw);
            entity.text(Component.text(symbol == null ? "" : symbol));

            PersistentDataContainer pdc = entity.getPersistentDataContainer();
            pdc.set(PluginKeys.type(), PersistentDataType.STRING, PluginKeys.TYPE_TRUMP_DISPLAY);
            pdc.set(PluginKeys.deckId(), PersistentDataType.STRING, deckId.toString());
        });
    }

    /**
     * Spawns the visual for a card on the table. A face-down card shows only the card
     * back ({@link CardSymbols#BACK}), so the real symbol is never sent to clients.
     */
    public TextDisplay spawnRevealedCardDisplay(Location location, float yaw, UUID deckId, Card card, boolean faceDown) {
        return location.getWorld().spawn(location, TextDisplay.class, entity -> {
            configureTextDisplay(entity, yaw);
            entity.text(cardFace(card, faceDown));

            PersistentDataContainer pdc = entity.getPersistentDataContainer();
            pdc.set(PluginKeys.type(), PersistentDataType.STRING, PluginKeys.TYPE_REVEALED_CARD);
            pdc.set(PluginKeys.deckId(), PersistentDataType.STRING, deckId.toString());
            pdc.set(PluginKeys.cardId(), PersistentDataType.STRING, card.getCardId().toString());
        });
    }

    /**
     * Switches an existing table-card display between its face and its back, in place.
     */
    public void setRevealedCardFace(TextDisplay display, Card card, boolean faceDown) {
        display.text(cardFace(card, faceDown));
    }

    private static Component cardFace(Card card, boolean faceDown) {
        return Component.text(faceDown ? CardSymbols.BACK : card.getSymbol());
    }

    /**
     * Spawns the invisible, clickable hitbox for the deck's main pile or its discard pile.
     * {@code visualCenter} should be the same location passed to
     * {@link #spawnCardItemDisplay}.
     */
    public Interaction spawnPileInteraction(Location visualCenter, UUID deckId, String pdcType) {
        Location loc = centeredInteractionLocation(visualCenter, PILE_INTERACTION_HEIGHT);
        return loc.getWorld().spawn(loc, Interaction.class, entity -> {
            entity.setInteractionWidth(PILE_INTERACTION_WIDTH);
            entity.setInteractionHeight(PILE_INTERACTION_HEIGHT);
            entity.setResponsive(false);

            PersistentDataContainer pdc = entity.getPersistentDataContainer();
            pdc.set(PluginKeys.type(), PersistentDataType.STRING, pdcType);
            pdc.set(PluginKeys.deckId(), PersistentDataType.STRING, deckId.toString());
        });
    }

    /**
     * Spawns the invisible, clickable hitbox for a single revealed card on the table.
     * {@code visualCenter} should be the same location passed to
     * {@link #spawnRevealedCardDisplay}.
     */
    public Interaction spawnRevealedCardInteraction(Location visualCenter, UUID deckId, Card card) {
        Location loc = centeredInteractionLocation(visualCenter, REVEALED_CARD_INTERACTION_HEIGHT);
        return loc.getWorld().spawn(loc, Interaction.class, entity -> {
            entity.setInteractionWidth(REVEALED_CARD_INTERACTION_WIDTH);
            entity.setInteractionHeight(REVEALED_CARD_INTERACTION_HEIGHT);
            entity.setResponsive(false);

            PersistentDataContainer pdc = entity.getPersistentDataContainer();
            pdc.set(PluginKeys.type(), PersistentDataType.STRING, PluginKeys.TYPE_REVEALED_CARD);
            pdc.set(PluginKeys.deckId(), PersistentDataType.STRING, deckId.toString());
            pdc.set(PluginKeys.cardId(), PersistentDataType.STRING, card.getCardId().toString());
        });
    }

    /**
     * Repositions and re-tags an existing revealed-card {@link Interaction} entity to
     * represent {@code card} at {@code visualCenter}, instead of spawning a fresh entity.
     * Used when a card is stacked on top of another, or when the top of a stack is taken
     * and the card underneath becomes the new top: in both cases exactly one card at that
     * spot is ever clickable, so the same hitbox entity can simply move and be re-tagged
     * rather than being destroyed and recreated.
     */
    public void relocateRevealedCardInteraction(Interaction interaction, Location visualCenter, UUID deckId, Card card) {
        Location loc = centeredInteractionLocation(visualCenter, REVEALED_CARD_INTERACTION_HEIGHT);
        interaction.teleport(loc);

        PersistentDataContainer pdc = interaction.getPersistentDataContainer();
        pdc.set(PluginKeys.type(), PersistentDataType.STRING, PluginKeys.TYPE_REVEALED_CARD);
        pdc.set(PluginKeys.deckId(), PersistentDataType.STRING, deckId.toString());
        pdc.set(PluginKeys.cardId(), PersistentDataType.STRING, card.getCardId().toString());
    }

    /**
     * An {@link Interaction} entity's box has its bottom anchored at the entity's own
     * location and extends {@code height} upward, while our visual centers mark the
     * vertical middle of where the card appears (table surface level). Shift down by half
     * the height so the clickable box straddles the visible surface instead of floating
     * entirely above or sitting entirely below it.
     */
    private Location centeredInteractionLocation(Location visualCenter, float height) {
        return visualCenter.clone().subtract(0, height / 2.0, 0);
    }

    private void configureTextDisplay(TextDisplay entity, float yaw) {
        entity.setBillboard(Display.Billboard.FIXED);
        entity.setAlignment(TextDisplay.TextAlignment.CENTER);
        entity.setLineWidth(200);
        entity.setDefaultBackground(false);
        entity.setShadowed(false);
        entity.setSeeThrough(false);
        entity.setTransformation(TransformUtil.flatTransformation(yaw, PITCH_DEGREES, TEXT_DISPLAY_SCALE));
    }

    private ItemStack buildDisplayMapItem() {
        ItemStack item = new ItemStack(Material.MAP);
        ItemMeta meta = item.getItemMeta();
        CustomModelDataComponent component = meta.getCustomModelDataComponent();
        component.setStrings(List.of("Playing Card"));
        meta.setCustomModelDataComponent(component);
        item.setItemMeta(meta);
        return item;
    }
}