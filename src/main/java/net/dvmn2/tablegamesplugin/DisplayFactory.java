package net.dvmn2.tablegamesplugin;

import net.dvmn2.tablegamesplugin.model.Card;
import net.dvmn2.tablegamesplugin.model.EntityRole;
import net.dvmn2.tablegamesplugin.util.CardSymbols;
import net.dvmn2.tablegamesplugin.util.TransformUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.List;
import java.util.UUID;

/**
 * Spawns the entities that make up a physical deck: {@link ItemDisplay}s for the piles,
 * {@link TextDisplay}s for the trump and for cards on the table, and the invisible
 * {@link Interaction} entities that make them clickable.
 * <p>
 * Display entities have no interaction hitbox in vanilla Minecraft, so a right click can never
 * select them. Every clickable surface therefore pairs a visual display with a separate
 * {@code Interaction} entity at the same position; the click listener reads the role and deck id
 * tagged on the hitbox. The trump display is not interactive and has no hitbox. Every entity is
 * tagged with an {@link EntityRole} so orphans can be recognised. Thread: main server thread.
 */
public final class DisplayFactory {

    private static final Vector3f ITEM_DISPLAY_SCALE = new Vector3f(0.5f, 0.5f, 1.0f);
    private static final Vector3f TEXT_DISPLAY_SCALE = new Vector3f(0.1875f, 0.1875f, 1.0f);

    /** Pitch that lays a display flat on a surface, degrees. */
    private static final float FLAT_PITCH_DEGREES = -90f;

    /** Text display line width, pixels; wide enough that a single glyph never wraps. */
    private static final int TEXT_LINE_WIDTH = 200;

    /**
     * Footprint of the pile click box, blocks. Wide enough to be easy to click but narrower than the
     * one-block gap between the main and discard piles, so the two boxes never overlap.
     */
    private static final float PILE_INTERACTION_WIDTH = 0.7f;
    private static final float PILE_INTERACTION_HEIGHT = 0.4f;

    private static final float CARD_INTERACTION_WIDTH = 0.5f;
    private static final float CARD_INTERACTION_HEIGHT = 0.3f;

    private final PluginKeys keys;

    public DisplayFactory(PluginKeys keys) {
        this.keys = keys;
    }

    /** Spawns the visual of a pile; {@code role} is {@link EntityRole#DECK} or {@link EntityRole#DISCARD}. */
    public ItemDisplay spawnPileDisplay(Location location, float yaw, UUID deckId, EntityRole role) {
        return location.getWorld().spawn(location, ItemDisplay.class, entity -> {
            entity.setBillboard(Display.Billboard.FIXED);
            entity.setItemStack(buildDisplayItem());
            entity.setTransformation(TransformUtil.flatTransformation(yaw, FLAT_PITCH_DEGREES, ITEM_DISPLAY_SCALE));
            tag(entity, role, deckId);
        });
    }

    /** Spawns the face-up trump card shown on top of the main pile. */
    public TextDisplay spawnTrumpDisplay(Location location, float yaw, UUID deckId, Card trump) {
        return location.getWorld().spawn(location, TextDisplay.class, entity -> {
            configureTextDisplay(entity, yaw);
            entity.text(Component.text(trump.symbol()));
            tag(entity, EntityRole.TRUMP, deckId);
        });
    }

    /** Updates the trump display, blanking it when no trump card remains. */
    public void setTrumpSymbol(TextDisplay display, @Nullable Card trump) {
        display.text(Component.text(trump == null ? "" : trump.symbol()));
    }

    /**
     * Spawns the visual of a card on the table. A face-down card shows only {@link CardSymbols#BACK},
     * so its real symbol is never sent to clients.
     */
    public TextDisplay spawnRevealedCardDisplay(Location location, float yaw, UUID deckId, Card card,
                                                boolean faceDown) {
        return location.getWorld().spawn(location, TextDisplay.class, entity -> {
            configureTextDisplay(entity, yaw);
            entity.text(cardFace(card, faceDown));
            tag(entity, EntityRole.REVEALED_CARD, deckId);
            entity.getPersistentDataContainer()
                    .set(keys.cardId(), PersistentDataType.STRING, card.cardId().toString());
        });
    }

    /** Switches a table card between its face and its back in place. */
    public void setRevealedCardFace(TextDisplay display, Card card, boolean faceDown) {
        display.text(cardFace(card, faceDown));
    }

    /**
     * Spawns the click hitbox of a pile.
     *
     * @param visualCenter the location passed to {@link #spawnPileDisplay}
     */
    public Interaction spawnPileInteraction(Location visualCenter, UUID deckId, EntityRole role) {
        Location location = centered(visualCenter, PILE_INTERACTION_HEIGHT);
        return location.getWorld().spawn(location, Interaction.class, entity -> {
            entity.setInteractionWidth(PILE_INTERACTION_WIDTH);
            entity.setInteractionHeight(PILE_INTERACTION_HEIGHT);
            entity.setResponsive(false);
            tag(entity, role, deckId);
        });
    }

    /**
     * Spawns the click hitbox of one card on the table.
     *
     * @param visualCenter the location passed to {@link #spawnRevealedCardDisplay}
     */
    public Interaction spawnRevealedCardInteraction(Location visualCenter, UUID deckId, Card card) {
        Location location = centered(visualCenter, CARD_INTERACTION_HEIGHT);
        return location.getWorld().spawn(location, Interaction.class, entity -> {
            entity.setInteractionWidth(CARD_INTERACTION_WIDTH);
            entity.setInteractionHeight(CARD_INTERACTION_HEIGHT);
            entity.setResponsive(false);
            tag(entity, EntityRole.REVEALED_CARD, deckId);
            entity.getPersistentDataContainer()
                    .set(keys.cardId(), PersistentDataType.STRING, card.cardId().toString());
        });
    }

    /**
     * Moves and re-tags an existing card hitbox to represent {@code card} at {@code visualCenter}.
     * Exactly one card per stack is ever clickable, so when a card is stacked on or the top card is
     * taken, the same hitbox is reused instead of being destroyed and respawned.
     */
    public void relocateRevealedCardInteraction(Interaction interaction, Location visualCenter, UUID deckId,
                                                Card card) {
        interaction.teleport(centered(visualCenter, CARD_INTERACTION_HEIGHT));
        tag(interaction, EntityRole.REVEALED_CARD, deckId);
        interaction.getPersistentDataContainer()
                .set(keys.cardId(), PersistentDataType.STRING, card.cardId().toString());
    }

    private void tag(Entity entity, EntityRole role, UUID deckId) {
        PersistentDataContainer pdc = entity.getPersistentDataContainer();
        pdc.set(keys.type(), PersistentDataType.STRING, role.getId());
        pdc.set(keys.deckId(), PersistentDataType.STRING, deckId.toString());
    }

    private static Component cardFace(Card card, boolean faceDown) {
        return Component.text(faceDown ? CardSymbols.BACK : card.symbol());
    }

    /**
     * An {@link Interaction} box has its bottom at the entity's location and extends upward, while
     * visual centres mark the vertical middle of the card. Shifting down by half the height makes
     * the box straddle the visible surface instead of floating above it.
     */
    private static Location centered(Location visualCenter, float height) {
        return visualCenter.clone().subtract(0, height / 2.0, 0);
    }

    private static void configureTextDisplay(TextDisplay entity, float yaw) {
        entity.setBillboard(Display.Billboard.FIXED);
        entity.setAlignment(TextDisplay.TextAlignment.CENTER);
        entity.setLineWidth(TEXT_LINE_WIDTH);
        entity.setDefaultBackground(false);
        entity.setShadowed(false);
        entity.setSeeThrough(false);
        entity.setTransformation(TransformUtil.flatTransformation(yaw, FLAT_PITCH_DEGREES, TEXT_DISPLAY_SCALE));
    }

    private static ItemStack buildDisplayItem() {
        ItemStack item = new ItemStack(Material.MAP);
        ItemMeta meta = item.getItemMeta();
        CustomModelDataComponent component = meta.getCustomModelDataComponent();
        component.setStrings(List.of(CardItemFactory.MODEL_STRING));
        meta.setCustomModelDataComponent(component);
        item.setItemMeta(meta);
        return item;
    }
}
