package com.example.layers.commands;

import com.example.layers.layers.Layer;
import com.example.layers.layers.Layers;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.command.CommandSource;

public class LayerCommand extends Command {
    public LayerCommand() {
        super("layer", "管理模块界面的显示层。", "layers");
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        builder.executes(context -> {
            Layers layers = Layers.get();
            Layer active = layers.getActive();
            info("当前层 %d/%d: %s（%s，白名单 %d 个模块）",
                layers.activeIndex + 1, layers.layers.size(), active.name,
                active.showAll ? "显示全部" : "白名单", active.modules.size());
            return SINGLE_SUCCESS;
        });

        builder.then(literal("next").executes(context -> {
            Layers.get().next();
            info("已切换到层: %s", Layers.get().getActive().name);
            return SINGLE_SUCCESS;
        }));

        builder.then(literal("prev").executes(context -> {
            Layers.get().previous();
            info("已切换到层: %s", Layers.get().getActive().name);
            return SINGLE_SUCCESS;
        }));

        builder.then(literal("add").then(argument("name", StringArgumentType.greedyString()).executes(context -> {
            String name = StringArgumentType.getString(context, "name");
            Layer layer = Layers.get().add(name);
            info("已创建并切换到层: %s", layer.name);
            return SINGLE_SUCCESS;
        })));

        builder.then(literal("remove").then(argument("name", StringArgumentType.greedyString()).executes(context -> {
            String name = StringArgumentType.getString(context, "name");
            if (Layers.get().remove(name)) info("已删除层: %s", name);
            else error("删除失败: 没有找到层 \"%s\"，或者它已经是最后一层", name);
            return SINGLE_SUCCESS;
        })));

        builder.then(literal("set").then(argument("name", StringArgumentType.greedyString()).suggests((context, suggestions) -> {
            for (Layer layer : Layers.get().layers) suggestions.suggest(layer.name);
            return suggestions.buildFuture();
        }).executes(context -> {
            String name = StringArgumentType.getString(context, "name");
            if (Layers.get().setActive(name)) info("已切换到层: %s", name);
            else error("没有找到层: %s", name);
            return SINGLE_SUCCESS;
        })));

        builder.then(literal("rename").then(argument("name", StringArgumentType.greedyString()).executes(context -> {
            String name = StringArgumentType.getString(context, "name");
            if (Layers.get().rename(Layers.get().getActive(), name)) info("已重命名为: %s", name);
            else error("重命名失败: 名称 \"%s\" 已被使用", name);
            return SINGLE_SUCCESS;
        })));

        builder.then(literal("showall").executes(context -> {
            Layer active = Layers.get().getActive();
            active.showAll = !active.showAll;

            if (active.showAll) active.modules.clear();
            else if (active.modules.isEmpty()) {
                for (Module module : Modules.get().getAll()) {
                    active.modules.add(module.name);
                }
            }

            Layers.get().changed();
            info("层 %s 现在%s", active.name, active.showAll ? "显示全部模块" : "只显示白名单模块");
            return SINGLE_SUCCESS;
        }));

        builder.then(literal("list").executes(context -> {
            Layers layers = Layers.get();

            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < layers.layers.size(); i++) {
                Layer layer = layers.layers.get(i);
                if (i > 0) sb.append("§7, §r");
                sb.append(i == layers.activeIndex ? "§a+ " : "  ").append(layer.name).append("§r");
            }

            info("所有层: %s", sb);
            return SINGLE_SUCCESS;
        }));
    }
}
