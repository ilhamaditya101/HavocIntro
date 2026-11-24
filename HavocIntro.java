package id.havoc.intro;

import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.trait.SkinTrait;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.*;

public final class HavocIntro extends JavaPlugin implements Listener {
    private IntroSession session;
    private boolean autoEnabled;
    private final Random random = new Random();

    @Override public void onEnable() {
        saveDefaultConfig();
        autoEnabled = getConfig().getBoolean("auto-intro.enabled", true);
        Bukkit.getPluginManager().registerEvents(this, this);
        getLogger().info("HavocIntro enabled. Citizens is required for player-skin clones.");
    }

    @Override public void onDisable() { stopIntro(false); }

    @EventHandler public void onJoin(PlayerJoinEvent event) {
        if (!autoEnabled || !event.getPlayer().hasPlayedBefore()) {
            if (autoEnabled) Bukkit.getScheduler().runTaskLater(this, () -> {
                if (session == null && !Bukkit.getOnlinePlayers().isEmpty()) startIntro(Bukkit.getConsoleSender());
            }, Math.max(1, getConfig().getLong("auto-intro.delay-seconds", 5) * 20));
        }
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("havocintro.admin")) { sender.sendMessage(color("&cKamu tidak punya permission.")); return true; }
        if (args.length == 0) { sender.sendMessage(color("&d/intro start, /intro stop, /intro reload, /intro auto <on|off>")); return true; }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "start" -> startIntro(sender);
            case "stop" -> { stopIntro(true); tell(sender, "stopped"); }
            case "reload" -> {
                reloadConfig(); autoEnabled = getConfig().getBoolean("auto-intro.enabled", true);
                tell(sender, "reloaded");
            }
            case "auto" -> {
                if (args.length < 2) { sender.sendMessage(color("&cGunakan /intro auto on|off")); return true; }
                autoEnabled = args[1].equalsIgnoreCase("on");
                getConfig().set("auto-intro.enabled", autoEnabled); saveConfig();
                tell(sender, autoEnabled ? "auto-on" : "auto-off");
            }
            default -> sender.sendMessage(color("&d/intro start, /intro stop, /intro reload, /intro auto <on|off>"));
        }
        return true;
    }

    private void startIntro(CommandSender sender) {
        if (session != null) { sender.sendMessage(color("&cIntro sedang berjalan.")); return; }
        List<Player> viewers = new ArrayList<>(Bukkit.getOnlinePlayers());
        if (viewers.isEmpty()) { sender.sendMessage(color("&cTidak ada player online.")); return; }
        World world = Bukkit.getWorld(getConfig().getString("intro.world", "world"));
        if (world == null) { sender.sendMessage(color(getMessage("no-world").replace("%world%", getConfig().getString("intro.world", "world")))); return; }
        Location center = new Location(world, getConfig().getDouble("intro.center.x", .5), getConfig().getDouble("intro.center.y", 80), getConfig().getDouble("intro.center.z", .5));
        session = new IntroSession(viewers, center);
        session.begin();
        tell(sender, "started");
    }

    private void stopIntro(boolean notify) {
        if (session == null) return;
        session.finish(false);
        if (notify) Bukkit.broadcastMessage(color(getMessage("stopped")));
    }

    private final class IntroSession {
        private final List<Player> viewers;
        private final Location center;
        private final Map<UUID, PlayerState> states = new HashMap<>();
        private final List<NPC> clones = new ArrayList<>();
        private final List<ArmorStand> cameras = new ArrayList<>();
        private BukkitTask task;
        private int tick;
        private boolean done;
        private final int openingTicks = Math.max(1, getConfig().getInt("intro.opening-seconds", 5)) * 20;
        private final int perPlayerTicks = Math.max(1, getConfig().getInt("intro.seconds-per-player", 3)) * 20;
        private final int endingTicks = Math.max(1, getConfig().getInt("intro.ending-seconds", 4)) * 20;
        private final double cameraRadius = getConfig().getDouble("intro.camera-radius", 15);
        private final double cameraHeight = getConfig().getDouble("intro.camera-height", 9);

        IntroSession(List<Player> viewers, Location center) { this.viewers = viewers; this.center = center; }

        void begin() {
            int i = 0;
            for (Player p : viewers) {
                states.put(p.getUniqueId(), new PlayerState(p));
                ArmorStand cam = center.getWorld().spawn(center.clone().add(0, cameraHeight, -cameraRadius), ArmorStand.class, a -> {
                    a.setInvisible(true); a.setMarker(true); a.setGravity(false); a.setSilent(true); a.setInvulnerable(true); a.setCollidable(false);
                });
                cameras.add(cam);
                p.setGameMode(org.bukkit.GameMode.SPECTATOR);
                p.setSpectatorTarget(cam);
                i++;
            }
            createClones();
            task = Bukkit.getScheduler().runTaskTimer(HavocIntro.this, this::tick, 1L, 1L);
        }

        private void createClones() {
            int count = viewers.size();
            double radius = getConfig().getDouble("intro.showcase-radius", 5);
            for (int i = 0; i < count; i++) {
                Player source = viewers.get(i);
                double angle = (Math.PI * 2 * i / Math.max(1, count)) - Math.PI / 2;
                Location spot = center.clone().add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius);
                spot.setYaw((float) Math.toDegrees(angle + Math.PI));
                NPC npc = CitizensAPI.getNPCRegistry().createNPC(EntityType.PLAYER, String.format("[%02d] %s", i + 1, source.getName()));
                npc.getOrAddTrait(SkinTrait.class).setSkinName(source.getName());
                npc.spawn(spot);
                clones.add(npc);
            }
        }

        private void tick() {
            if (done) return;
            int showcaseEnd = openingTicks + perPlayerTicks * viewers.size();
            if (tick < openingTicks) {
                double progress = tick / (double) openingTicks;
                moveCameras(progress, 0, true);
                if (tick == 1) titleAll("&5&lHAVOC", "&7Welcome to the server");
            } else if (tick < showcaseEnd) {
                int index = Math.min(viewers.size() - 1, (tick - openingTicks) / perPlayerTicks);
                int within = (tick - openingTicks) % perPlayerTicks;
                moveCameras(within / (double) perPlayerTicks, index, false);
                if (within == 0) titleAll("&6&l" + String.format("%02d", index + 1) + " &f&l" + viewers.get(index).getName(), "&7Player Showcase");
            } else if (tick < showcaseEnd + endingTicks) {
                moveCameras((tick - showcaseEnd) / (double) endingTicks, 0, true);
                if (tick == showcaseEnd) titleAll("&6&l" + "HAVOC", "&fPlayers online: " + viewers.size());
            } else {
                finish(true); return;
            }
            tick++;
        }

        private void moveCameras(double progress, int focusIndex, boolean overview) {
            Location focus = center.clone();
            if (!overview && focusIndex < clones.size()) focus = clones.get(focusIndex).getEntity().getLocation().clone().add(0, 1.2, 0);
            for (int i = 0; i < cameras.size(); i++) {
                ArmorStand cam = cameras.get(i);
                if (cam == null || !cam.isValid()) continue;
                double angle = (tick * 0.012) + (i * 0.002);
                double radius = overview ? cameraRadius : Math.max(4, cameraRadius * (0.55 - 0.2 * Math.sin(progress * Math.PI)));
                Location next = focus.clone().add(Math.cos(angle) * radius, cameraHeight * (overview ? 1 : .65), Math.sin(angle) * radius);
                Vector direction = focus.toVector().subtract(next.toVector());
                next.setDirection(direction);
                cam.teleport(next);
            }
        }

        private void titleAll(String title, String subtitle) {
            for (Player p : viewers) if (p.isOnline()) p.sendTitle(color(title), color(subtitle), 5, 30, 10);
        }

        void finish(boolean success) {
            if (done) return; done = true;
            if (task != null) task.cancel();
            for (NPC npc : clones) { try { npc.destroy(); } catch (Exception ignored) {} }
            for (ArmorStand cam : cameras) if (cam != null && cam.isValid()) cam.remove();
            for (Player p : viewers) {
                if (!p.isOnline()) continue;
                PlayerState state = states.get(p.getUniqueId());
                if (state != null) state.restore(p);
                if (success && getConfig().getBoolean("random-teleport.enabled", true)) {
                    p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 50, 1, false, false, false));
                    p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 50, 0, false, false, false));
                }
            }
            if (success && getConfig().getBoolean("random-teleport.enabled", true)) {
                Bukkit.getScheduler().runTaskLater(HavocIntro.this, () -> {
                    for (Player p : viewers) if (p.isOnline()) {
                        Location destination = randomSafeLocation();
                        if (destination != null) p.teleport(destination);
                        else p.sendMessage(color("&cLokasi RTP aman tidak ditemukan. Hubungi admin."));
                    }
                    Bukkit.getScheduler().runTaskLater(HavocIntro.this, () -> {
                        for (Player p : viewers) if (p.isOnline()) { p.removePotionEffect(PotionEffectType.BLINDNESS); p.removePotionEffect(PotionEffectType.DARKNESS); }
                        Bukkit.broadcastMessage(color(getMessage("finished")));
                    }, 25L);
                }, 10L);
            }
            if (session == this) session = null;
        }

        private Location randomSafeLocation() {
            World w = Bukkit.getWorld(getConfig().getString("random-teleport.world", "rtp_world"));
            if (w == null) return null;
            double cx = getConfig().getDouble("random-teleport.center.x", .5), cz = getConfig().getDouble("random-teleport.center.z", .5);
            double radius = Math.max(1, getConfig().getDouble("random-teleport.radius", 5000));
            double min = Math.max(0, Math.min(radius, getConfig().getDouble("random-teleport.min-radius", 0)));
            int attempts = Math.max(1, getConfig().getInt("random-teleport.max-attempts", 80));
            for (int n = 0; n < attempts; n++) {
                double angle = random.nextDouble() * Math.PI * 2;
                double distance = Math.sqrt(random.nextDouble() * (radius * radius - min * min) + min * min);
                int x = (int) Math.floor(cx + Math.cos(angle) * distance), z = (int) Math.floor(cz + Math.sin(angle) * distance);
                Location borderCheck = new Location(w, x, w.getMinHeight(), z);
                double margin = Math.max(0, getConfig().getDouble("random-teleport.border-margin", 8));
                if (!w.getWorldBorder().isInside(borderCheck) ||
                        Math.abs(x - w.getWorldBorder().getCenter().getX()) > w.getWorldBorder().getSize() / 2 - margin ||
                        Math.abs(z - w.getWorldBorder().getCenter().getZ()) > w.getWorldBorder().getSize() / 2 - margin) continue;
                Block ground = w.getHighestBlockAt(x, z);
                Location feet = ground.getLocation().add(.5, 1, .5);
                if (!getConfig().getBoolean("random-teleport.safe-location", true)) return feet;
                Block below = feet.clone().subtract(0, 1, 0).getBlock();
                Block body = feet.getBlock(), head = feet.clone().add(0, 1, 0).getBlock();
                if (!below.getType().isSolid() || !body.isPassable() || !head.isPassable()) continue;
                if (getConfig().getBoolean("random-teleport.avoid-lava", true) &&
                        (below.getType() == org.bukkit.Material.LAVA || body.getType() == org.bukkit.Material.LAVA || head.getType() == org.bukkit.Material.LAVA)) continue;
                if (getConfig().getBoolean("random-teleport.avoid-water", false) &&
                        (below.getType() == org.bukkit.Material.WATER || body.getType() == org.bukkit.Material.WATER || head.getType() == org.bukkit.Material.WATER)) continue;
                feet.setYaw(random.nextFloat() * 360); feet.setPitch(0);
                return feet;
            }
            return null;
        }
    }

    private static final class PlayerState {
        private final Location location;
        private final org.bukkit.GameMode mode;
        private final boolean allowFlight, flying;
        PlayerState(Player p) { location = p.getLocation().clone(); mode = p.getGameMode(); allowFlight = p.getAllowFlight(); flying = p.isFlying(); }
        void restore(Player p) {
            p.setSpectatorTarget(null);
            p.setGameMode(mode);
            p.teleport(location);
            p.setAllowFlight(allowFlight);
            if (allowFlight) p.setFlying(flying);
        }
    }

    private String getMessage(String key) { return getConfig().getString("messages." + key, ""); }
    private void tell(CommandSender sender, String key) { sender.sendMessage(color(getMessage("prefix") + getMessage(key))); }
    private String color(String s) { return org.bukkit.ChatColor.translateAlternateColorCodes('&', s == null ? "" : s); }
}
