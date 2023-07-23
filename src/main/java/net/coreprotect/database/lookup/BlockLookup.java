package net.coreprotect.database.lookup;

import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Locale;

import net.coreprotect.utility.Chat;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.BlockState;
import org.bukkit.command.CommandSender;

import net.coreprotect.config.ConfigHandler;
import net.coreprotect.database.statement.UserStatement;
import net.coreprotect.language.Phrase;
import net.coreprotect.language.Selector;
import net.coreprotect.listener.channel.PluginChannelListener;
import net.coreprotect.utility.Color;
import net.coreprotect.utility.Util;

public class BlockLookup {

    public static Component performLookup(String command, Statement statement, BlockState block, CommandSender commandSender, int offset, int page, int limit) {
        String resultText = "";

        //Replace to component
        Component resultComponent = Component.empty();

        try {
            if (block == null) {
                return resultComponent;
            }

            if (command == null) {
                if (commandSender.hasPermission("coreprotect.co")) {
                    command = "co";
                }
                else if (commandSender.hasPermission("coreprotect.core")) {
                    command = "core";
                }
                else if (commandSender.hasPermission("coreprotect.coreprotect")) {
                    command = "coreprotect";
                }
                else {
                    command = "co";
                }
            }

            boolean found = false;
            int x = block.getX();
            int y = block.getY();
            int z = block.getZ();
            long time = (System.currentTimeMillis() / 1000L);
            int worldId = Util.getWorldId(block.getWorld().getName());
            long checkTime = 0;
            int count = 0;
            int rowMax = page * limit;
            int page_start = rowMax - limit;
            if (offset > 0) {
                checkTime = time - offset;
            }

            String blockName = block.getType().name().toLowerCase(Locale.ROOT);

            String query = "SELECT COUNT(*) as count from " + ConfigHandler.prefix + "block " + Util.getWidIndex("block") + "WHERE wid = '" + worldId + "' AND x = '" + x + "' AND z = '" + z + "' AND y = '" + y + "' AND action IN(0,1) AND time >= '" + checkTime + "' LIMIT 0, 1";
            ResultSet results = statement.executeQuery(query);
            while (results.next()) {
                count = results.getInt("count");
            }
            results.close();
            int totalPages = (int) Math.ceil(count / (limit + 0.0));

            query = "SELECT time,user,action,type,data,rolled_back FROM " + ConfigHandler.prefix + "block " + Util.getWidIndex("block") + "WHERE wid = '" + worldId + "' AND x = '" + x + "' AND z = '" + z + "' AND y = '" + y + "' AND action IN(0,1) AND time >= '" + checkTime + "' ORDER BY rowid DESC LIMIT " + page_start + ", " + limit + "";
            results = statement.executeQuery(query);


            while (results.next()) {
                int resultUserId = results.getInt("user");
                int resultAction = results.getInt("action");
                int resultType = results.getInt("type");
                long resultTime = results.getLong("time");
                int resultRolledBack = results.getInt("rolled_back");

                if (ConfigHandler.playerIdCacheReversed.get(resultUserId) == null) {
                    UserStatement.loadName(statement.getConnection(), resultUserId);
                }

                String resultUser = ConfigHandler.playerIdCacheReversed.get(resultUserId);
                Component timeAgo = Util.getTimeSinceComponent(resultTime, time, true);

                if (!found) {

                    resultComponent = Chat.deserializeString(Color.WHITE + "----- " + Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "----- ")
                            .append(Util.getCoordinatesComponent(command, worldId, x, y, z, false, false))
                            .append(Component.newline());

                }
                found = true;

                Phrase phrase = Phrase.LOOKUP_BLOCK;
                String selector = Selector.FIRST;
                String tag = Color.WHITE + "-";
                if (resultAction == 2 || resultAction == 3) {
                    phrase = Phrase.LOOKUP_INTERACTION; // {clicked|killed}
                    selector = (resultAction != 3 ? Selector.FIRST : Selector.SECOND);
                    tag = (resultAction != 3 ? Color.WHITE + "-" : Color.RED + "-");
                }
                else {
                    phrase = Phrase.LOOKUP_BLOCK; // {placed|broke}
                    selector = (resultAction != 0 ? Selector.FIRST : Selector.SECOND);
                    tag = (resultAction != 0 ? Color.GREEN + "+" : Color.RED + "-");
                }

                String rbFormat = "";
                if (resultRolledBack == 1 || resultRolledBack == 3) {
                    rbFormat = Color.STRIKETHROUGH;
                }

                String target;
                if (resultAction == 3) {
                    target = Util.getEntityType(resultType).translationKey();
                }
                else {
                    Material resultMaterial = Util.getType(resultType);
                    if (resultMaterial == null) {
                        resultMaterial = Material.AIR;
                    }
                    target = resultMaterial.translationKey();
                }


                resultComponent = resultComponent
                        .append(timeAgo)
                        .append(Chat.deserializeString(" " + tag + " ")) ;

                resultComponent = resultComponent.append(Phrase.buildComponent(phrase,
                        Chat.deserializeString(Color.DARK_AQUA + rbFormat + resultUser ),
                        Chat.deserializeString(Color.WHITE + rbFormat)
                        .append(Component.translatable(target)
                                .color(NamedTextColor.DARK_AQUA)
                                .decoration(TextDecoration.ITALIC,false)),
                        Chat.deserializeString(selector))).append(Component.newline());
                PluginChannelListener.getInstance().sendData(commandSender, resultTime, phrase, selector, resultUser, target, -1, x, y, z, worldId, rbFormat, false, tag.contains("+"));
            }

            results.close();

            if (found) {
                if (count > limit) {
                    resultComponent = resultComponent.append(Chat.deserializeString(Color.WHITE + "-----"))
                            .append(Component.newline())
                            .append(Util.getPageNavigationComponent(command, page, totalPages))
                            .append(Component.newline());
                }
            }
            else {
                if (rowMax > count && count > 0) {
                    resultComponent = Chat.deserializeString(Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.NO_RESULTS_PAGE, Selector.SECOND));
                }
                else {
                    // resultText = Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Color.WHITE + "No block data found at " + Color.ITALIC + "x" + x + "/y" + y + "/z" + z + ".";
                    resultComponent = Chat.deserializeString(Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.NO_DATA_LOCATION, Selector.FIRST));
                    if (!blockName.equals("air") && !blockName.equals("cave_air")) {
                        resultComponent = Chat.deserializeString(Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- ")
                                .append(Phrase.buildComponent(Phrase.NO_DATA, Component.translatable( block.getType().translationKey(), TextColor.color(255,140,140)).decoration(TextDecoration.ITALIC,true)))
                                .append(Component.newline());
                    }
                }
            }

            ConfigHandler.lookupPage.put(commandSender.getName(), page);
            ConfigHandler.lookupType.put(commandSender.getName(), 2);
            ConfigHandler.lookupCommand.put(commandSender.getName(), x + "." + y + "." + z + "." + worldId + ".0." + limit);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        return resultComponent;
    }

}
