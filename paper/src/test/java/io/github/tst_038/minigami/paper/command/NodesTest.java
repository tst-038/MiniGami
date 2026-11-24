package io.github.tst_038.minigami.paper.command;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class NodesTest {

    @Test
    void tellsASelectorFromAName() throws Exception {
        var seen = new ArrayList<Boolean>();
        var dispatcher = new CommandDispatcher<Object>();
        dispatcher.register(LiteralArgumentBuilder.literal("add")
                .then(RequiredArgumentBuilder.argument("players", StringArgumentType.greedyString()).executes(context -> {
                    seen.add(Nodes.isSelector(context, "players"));
                    return 1;
                })));

        for (var input : List.of("add @a", "add @a[distance=..20]", "add Steve")) {
            dispatcher.execute(input, new Object());
        }

        assertEquals(List.of(true, true, false), seen);
    }
}
