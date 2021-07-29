package com.cavetale.itemmerchant;

import com.winthier.playercache.PlayerCache;
import java.io.File;
import java.util.Arrays;
import java.util.Date;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.val;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;

@RequiredArgsConstructor
final class ItemMerchantCommand implements TabExecutor {
    private final ItemMerchantPlugin plugin;
    private static final List<String> COMMANDS = Arrays.asList("get", "set", "list", "rank", "import", "export");

    static class CommandException extends Exception {
        CommandException(final String msg) {
            super(msg);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 0) return false;
        try {
            return onCommand(sender, args[0], Arrays.copyOfRange(args, 1, args.length));
        } catch (CommandException e) {
            sender.sendMessage(ChatColor.RED + e.getMessage());
            return true;
        }
    }

    final class SearchCache {
        UUID uuid = null;
        Material mat = null;
        int amount = 0;
        double money = 0;

        SearchCache(final UUID uuid) {
            this.uuid = uuid;
        }

        SearchCache(final Material mat) {
            this.mat = mat;
        }
    }

    private boolean onCommand(CommandSender sender, String cmd, String[] args) throws CommandException {
        switch (cmd) {
        case "set": {
            if (args.length != 2) return false;
            Material mat = expectMaterial(args[0]);
            double price = expectPrice(args[1]);
            this.plugin.setMaterialPrice(mat, price);
            sender.sendMessage("Price of " + this.plugin.niceEnum(mat.name())
                               + " is now " + this.plugin.fmt(price) + ".");
            return true;
        }
        case "get": {
            if (args.length != 1) return false;
            Material mat = expectMaterial(args[0]);
            sender.sendMessage("Price of " + this.plugin.niceEnum(mat.name())
                               + " is " + this.plugin.fmt(this.plugin.getMaterialPrice(mat)) + ".");
            return true;
        }
        case "list": {
            if (args.length != 1) return false;
            String pat = args[0].toUpperCase();
            long count = Arrays
                .stream(Material.values())
                .filter(s -> s.name().contains(pat))
                .peek(mat -> {
                        sender.sendMessage("" + mat + ": "
                                           + this.plugin.fmt(this.plugin.getMaterialPrice(mat)));
                    }).count();
            sender.sendMessage("Total " + count + " items.");
            return true;
        }
        case "rank": {
            if (args.length < 1) return false;
            if (args.length % 2 != 1) return false;
            val query = this.plugin.getSqlDatabase().find(SQLLog.class);
            Iterator<String> iter = Arrays.asList(args).iterator();
            String what = iter.next();
            while (iter.hasNext()) {
                String arg = iter.next();
                switch (arg) {
                case "days": {
                    String n = iter.next();
                    final int days;
                    try {
                        days = Integer.parseInt(n);
                    } catch (NumberFormatException nfe) {
                        throw new CommandException("Invalid days: " + n);
                    }
                    query.gt("time", new Date(System.currentTimeMillis() - (long) days * 24 * 60 * 60 * 1000));
                    break;
                }
                case "item": {
                    Material mat = expectMaterial(iter.next());
                    query.eq("material", mat.name().toLowerCase());
                    break;
                }
                case "player": {
                    String n = iter.next();
                    UUID uuid = PlayerCache.uuidForName(n);
                    if (uuid == null) throw new CommandException("Player not found: " + n);
                    query.eq("player", uuid);
                    break;
                }
                default: throw new CommandException("Unknown filter: " + arg);
                }
            }
            List<SQLLog> searchResult = query.findList();
            switch (what) {
            case "items": {
                Map<Material, SearchCache> cache = new EnumMap<>(Material.class);
                Arrays.stream(Material.values()).forEach(m -> cache.put(m, new SearchCache(m)));
                for (SQLLog log: searchResult) {
                    Material mat;
                    try {
                        mat = Material.valueOf(log.getMaterial().toUpperCase());
                    } catch (IllegalArgumentException iae) {
                        iae.printStackTrace();
                        continue;
                    }
                    SearchCache sc = cache.get(mat);
                    sc.amount += log.getAmount();
                    sc.money += log.getPrice();
                }
                List<SearchCache> rankings = cache.values()
                    .stream().filter(r -> r.money > 0 && r.amount > 0)
                    .sorted((a, b) -> Double.compare(b.money, a.money))
                    .collect(Collectors.toList());
                sender.sendMessage("Total " + rankings.size() + " search results");
                for (int i = 0; i < 20; i += 1) {
                    if (i >= rankings.size()) break;
                    SearchCache rank = rankings.get(i);
                    if (rank.money == 0 && rank.amount == 0) break;
                    sender.sendMessage("" + (i + 1) + ")"
                                       + " x" + rank.amount
                                       + " $" + this.plugin.fmt(rank.money)
                                       + " " + this.plugin.niceEnum(rank.mat.name()));
                }
                break;
            }
            case "players": {
                Map<UUID, SearchCache> cache = new HashMap<>();
                for (SQLLog log: searchResult) {
                    UUID uuid = log.getPlayer();
                    SearchCache sc = cache.get(uuid);
                    if (sc == null) {
                        sc = new SearchCache(uuid);
                        cache.put(uuid, sc);
                    }
                    sc.amount += log.getAmount();
                    sc.money += log.getPrice();
                }
                List<SearchCache> rankings = cache.values()
                    .stream().filter(r -> r.money > 0 && r.amount > 0)
                    .sorted((a, b) -> Double.compare(b.money, a.money))
                    .collect(Collectors.toList());
                sender.sendMessage("Total " + rankings.size() + " search results");
                for (int i = 0; i < 20; i += 1) {
                    if (i >= rankings.size()) break;
                    SearchCache rank = rankings.get(i);
                    sender.sendMessage("" + (i + 1) + ")"
                                       + " x" + rank.amount
                                       + " $" + this.plugin.fmt(rank.money)
                                       + " " + PlayerCache.nameForUuid(rank.uuid));
                }
                break;
            }
            default: throw new CommandException("Invalid what: " + what);
            }
            return true;
        }
        case "import": {
            File file = new File(this.plugin.getDataFolder(), "prices.yml");
            if (!file.exists()) throw new CommandException("File not found: " + file);
            long count = this.plugin.importMaterialPrices(file).entrySet().stream().peek(e -> {
                    this.plugin.setMaterialPrice(e.getKey(), e.getValue());
                }).count();
            sender.sendMessage("" + count + " prices imported from " + file + ".");
            return true;
        }
        case "export": {
            File file = new File(this.plugin.getDataFolder(), "prices.yml");
            this.plugin.exportMaterialPrices(file);
            sender.sendMessage("Prices written to " + file + ".");
            return true;
        }
        default:
            return false;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 0) return null;
        if (args.length == 1) {
            return complete(args[0], COMMANDS.stream());
        }
        if (args.length == 2 && (args[0].equals("get") || args[0].equals("set"))) {
            return complete(args[1], Arrays.stream(Material.values()).map(Object::toString).map(String::toLowerCase));
        }
        if (args.length == 3 && args[0].equals("set")) {
            try {
                return Arrays.asList(String.format("%.02f", Double.parseDouble(args[2])));
            } catch (NumberFormatException nfe) {
                return null;
            }
        }
        return null;
    }

    private List<String> complete(String arg, Stream<String> opt) {
        return opt.filter(o -> o.startsWith(arg)).collect(Collectors.toList());
    }

    Material expectMaterial(String arg) throws CommandException {
        try {
            return Material.valueOf(arg.toUpperCase());
        } catch (IllegalArgumentException iae) {
            throw new CommandException("Unknown item: " + arg);
        }
    }

    Double expectPrice(String arg) throws CommandException {
        final double val;
        try {
            val = Double.parseDouble(arg);
        } catch (NumberFormatException nfe) {
            throw new CommandException("Invalid price: " + arg);
        }
        if (val < 0) throw new CommandException("Prices cannot be negative!");
        return val;
    }
}
