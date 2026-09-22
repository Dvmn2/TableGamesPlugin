package net.dvmn2.tablegamesplugin.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.dvmn2.tablegamesplugin.Lang;
import net.dvmn2.tablegamesplugin.manager.DeckItemFactory;
import net.dvmn2.tablegamesplugin.model.Card;
import net.dvmn2.tablegamesplugin.model.DeckType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;

/**
 * Brigadier implementation of {@code /tablegames give <poker|durak>}, the only command
 * exposed by this plugin. Registered via {@code LifecycleEvents.COMMANDS} in
 * {@link net.dvmn2.tablegamesplugin.TableGamesPlugin}; no {@code plugin.yml} command
 * entry is required.
 * <p>
 * Permission is enforced via {@link #build()}'s {@code requires} predicate rather than
 * {@code plugin.yml}'s command permission block, so it applies uniformly regardless of
 * how the node is reached (including via {@code /minecraft:tablegames} aliasing).
 * <p>
 * One literal is generated per {@link DeckType}, so adding a new deck type automatically
 * adds its subcommand and tab-completion. A bare {@code /tablegames give} prints usage.
 */
public final class TableGamesCommand {

    private static final String PERMISSION = "tablegames.give";

    private TableGamesCommand() {
    }

    public static LiteralCommandNode<CommandSourceStack> build() {
        LiteralArgumentBuilder<CommandSourceStack> give = Commands.literal("give")
                .executes(TableGamesCommand::executeUsage);

        for (DeckType type : DeckType.values()) {
            give.then(Commands.literal(type.getCommandName())
                    .executes(ctx -> executeGive(ctx, type)));
        }

        return Commands.literal("tablegames")
                .requires(source -> source.getSender().hasPermission(PERMISSION))
                .then(give)
                .build();
    }

    private static int executeUsage(CommandContext<CommandSourceStack> ctx) {
        String names = String.join("|", Arrays.stream(DeckType.values())
                .map(DeckType::getCommandName)
                .toList());
        Lang.send(ctx.getSource().getSender(), Lang.Key.GIVE_USAGE, names);
        return Command.SINGLE_SUCCESS;
    }

    private static int executeGive(CommandContext<CommandSourceStack> ctx, DeckType type) {
        CommandSourceStack source = ctx.getSource();

        if (!(source.getExecutor() instanceof Player player)) {
            Lang.send(source.getSender(), Lang.Key.NOT_A_PLAYER);
            return Command.SINGLE_SUCCESS;
        }

        ItemStack deckItem = createFreshDeckItem(type, player);
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(deckItem);
        for (ItemStack extra : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), extra);
        }

        Lang.send(player, Lang.Key.DECK_GIVEN, Lang.get(type.getNameKey(), player));
        return Command.SINGLE_SUCCESS;
    }

    private static ItemStack createFreshDeckItem(DeckType type, Player viewer) {
        UUID deckId = UUID.randomUUID();
        String[] symbols = type.initialOrder();
        List<Card> cards = new ArrayList<>(symbols.length);
        for (String symbol : symbols) {
            cards.add(new Card(UUID.randomUUID(), deckId, symbol));
        }
        return DeckItemFactory.createFullDeckItem(deckId, type, cards, viewer);
    }
}