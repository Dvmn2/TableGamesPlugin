package net.dvmn2.tablegamesplugin.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import net.dvmn2.tablegamesplugin.DeckItemFactory;
import net.dvmn2.tablegamesplugin.Lang;
import net.dvmn2.tablegamesplugin.Permissions;
import net.dvmn2.tablegamesplugin.TableGamesPlugin;
import net.dvmn2.tablegamesplugin.model.DeckType;
import net.dvmn2.tablegamesplugin.util.PlayerItems;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;

/**
 * The {@code /tablegames} command tree.
 * <pre>
 * /tablegames
 *   give &lt;deckType&gt; [targets]   (tablegames.give)
 *   reload                      (tablegames.reload)
 * </pre>
 * One literal is generated per {@link DeckType}, so a new deck type automatically gets its subcommand
 * and tab completion. Without {@code targets} the deck goes to the executor.
 */
public final class TableGamesCommand {

    private static final String ARG_TARGETS = "targets";

    private final TableGamesPlugin plugin;
    private final DeckItemFactory deckItems;

    public TableGamesCommand(TableGamesPlugin plugin, DeckItemFactory deckItems) {
        this.plugin = plugin;
        this.deckItems = deckItems;
    }

    public LiteralCommandNode<CommandSourceStack> build() {
        LiteralArgumentBuilder<CommandSourceStack> give = Commands.literal("give")
                .requires(src -> src.getSender().hasPermission(Permissions.GIVE))
                .executes(this::usage);
        for (DeckType type : DeckType.values()) {
            give.then(Commands.literal(type.getCommandName())
                    .executes(ctx -> give(ctx, type, false))
                    .then(Commands.argument(ARG_TARGETS, ArgumentTypes.players())
                            .executes(ctx -> give(ctx, type, true))));
        }

        return Commands.literal("tablegames")
                .requires(src -> src.getSender().hasPermission(Permissions.GIVE)
                        || src.getSender().hasPermission(Permissions.RELOAD))
                .then(give)
                .then(Commands.literal("reload")
                        .requires(src -> src.getSender().hasPermission(Permissions.RELOAD))
                        .executes(this::reload))
                .build();
    }

    private int usage(CommandContext<CommandSourceStack> ctx) {
        String types = String.join("|", Arrays.stream(DeckType.values()).map(DeckType::getCommandName).toList());
        Lang.sendChat(ctx.getSource().getSender(), Lang.Key.GIVE_USAGE,
                Placeholder.unparsed("usage", "give <" + types + "> [targets]"));
        return 0;
    }

    private int reload(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        plugin.reloadAll();
        Lang.sendChat(sender, Lang.Key.RELOADED);
        return Command.SINGLE_SUCCESS;
    }

    private int give(CommandContext<CommandSourceStack> ctx, DeckType type, boolean hasTargets) {
        CommandSourceStack source = ctx.getSource();
        CommandSender sender = source.getSender();

        List<Player> targets;
        if (hasTargets) {
            targets = resolveTargets(ctx);
            if (targets.isEmpty()) {
                Lang.sendChat(sender, Lang.Key.NO_PLAYERS_FOUND);
                return 0;
            }
        } else if (source.getExecutor() instanceof Player self) {
            targets = List.of(self);
        } else {
            Lang.sendChat(sender, Lang.Key.ONLY_PLAYERS);
            return 0;
        }

        for (Player target : targets) {
            PlayerItems.giveOrDrop(target, deckItems.createNewDeckItem(type, target));
            if (target != sender) {
                Lang.sendChat(sender, Lang.Key.DECK_GIVEN,
                        Placeholder.unparsed("player", target.getName()),
                        Placeholder.component("deck", Lang.get(type.getNameKey(), sender)));
            }
        }
        return targets.size();
    }

    private static List<Player> resolveTargets(CommandContext<CommandSourceStack> ctx) {
        try {
            return ctx.getArgument(ARG_TARGETS, PlayerSelectorArgumentResolver.class).resolve(ctx.getSource());
        } catch (CommandSyntaxException ex) {
            return List.of();
        }
    }
}
