package io.github.tst_038.minigami.paper.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import io.github.tst_038.minigami.api.message.Messages;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

/**
 * Lists what can follow the command that was typed, only the parts the sender may use; clicking a
 * line puts it in the chat box. Generated from the command tree, so it never goes out of date.
 */
final class Help {

    private Help() {
    }

    static Command<CommandSourceStack> command(Messages messages) {
        return context -> {
            show(context, messages);
            return Command.SINGLE_SUCCESS;
        };
    }

    private static void show(CommandContext<CommandSourceStack> context, Messages messages) {
        var sender = context.getSource().getSender();
        var node = context.getNodes().getLast().getNode();
        var typed = "/" + context.getInput().strip().replaceFirst("^/", "");
        var usages = new CommandDispatcher<CommandSourceStack>().getSmartUsage(node, context.getSource());
        messages.send(sender, "help.header", Placeholder.unparsed("command", typed));
        usages.forEach((child, usage) -> messages.send(sender, "help.line",
                Placeholder.unparsed("command", typed + " " + usage),
                Placeholder.styling("suggest", ClickEvent.suggestCommand(typed + " " + child.getUsageText() + " "))));
    }
}
