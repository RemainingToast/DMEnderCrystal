package com.au2b2t.dmendercrystal;

import com.au2b2t.deathmessages.*;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.type.Bed;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.*;

public final class DMEnderCrystal extends JavaPlugin implements Listener {

    private static final int CONFIG_VERSION = 2;

    private static final String CRYSTAL_KILL_TAG = "natural.EnderCrystalKill";
    private static final String CRYSTAL_SUICIDE_TAG = "natural.EnderCrystalSuicide";
    private static final String BED_KILL_TAG = "natural.BedKill";
    private static final String BED_SUICIDE_TAG = "natural.BedSuicide";

    private boolean BED_ENABLED = false;
    private boolean TRACK_PLACER_NOT_HITTER = true;
    private boolean TRACK_BED_PLACER_NOT_HITTER = true;

    private final Map<UUID, UUID> END_CRYSTAL_KILL;
    private final Map<UUID, UUID> BED_KILL;
    private static Set<String> BED_TYPES;

    public DMEnderCrystal() {
        END_CRYSTAL_KILL = new HashMap<>();
        BED_KILL = new HashMap<>();
        BED_TYPES = new HashSet<>();
        BED_TYPES.addAll(Arrays.asList("BED", "BED_BLOCK",
                "BLACK_BED", "BLUE_BED", "BROWN_BED", "CYAN_BED",
                "GRAY_BED", "GREEN_BED", "LIGHT_BLUE_BED", "LIGHT_GRAY_BED",
                "LIME_BED", "MAGENTA_BED", "ORANGE_BED", "PINK_BED",
                "PURPLE_BED", "RED_BED", "WHITE_BED", "YELLOW_BED"));
    }

    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        this.loadConfig();
        try {
            String ver = Bukkit.getServer().getVersion().split("\\(MC:")[1].split("\\)")[0].trim().split(" ")[0].trim();
            String[] tokens = ver.split("\\.");
            int mcMajor = Integer.parseInt(tokens[0]);
            int mcMinor = 0;
            int mcRevision = 0;
            if (tokens.length > 1) {
                mcMinor = Integer.parseInt(tokens[1]);
            }
            if (tokens.length > 2) {
                mcRevision = Integer.parseInt(tokens[2]);
            }
            VER = mcMajor * 1000 + mcMinor;
            REV = mcRevision;
            // 1.8 = 1_008
            // 1.9 = 1_009
            // 1.10 = 1_010
            // ...
            // 1.14 = 1_014
            // 1.15 = 1_015
        } catch (Exception ex) {
            this.getLogger().warning("Cannot detect Minecraft version from string - " +
                    "some features will not work properly. " +
                    "Please contact the plugin author if you are on " +
                    "standard CraftBukkit or Spigot. This plugin " +
                    "expects getVersion() to return a string " +
                    "containing '(MC: 1.15)' or similar. The version " +
                    "DMP tried to parse was '" + Bukkit.getServer().getVersion() + "'");
        }
    }

    @EventHandler
    public void reloadConfig(DMPReloadEvent e) {
        this.loadConfig();
    }

    private void loadConfig() {
        FileConfiguration config = this.getConfig();
        try {
            config.load(new File(this.getDataFolder(), "config.yml"));
            if (!config.contains("config-version")) {
                throw new Exception();
            }
            if (config.getInt("config-version") < CONFIG_VERSION) {
                throw new ConfigTooOldException();
            }
        }
        catch (FileNotFoundException e6) {
            this.getLogger().info("Extracting default config.");
            this.saveResource("config.yml", true);
            try {
                config.load(new File(this.getDataFolder(), "config.yml"));
            }
            catch (IOException | InvalidConfigurationException ex3) {
                ex3.printStackTrace();
                this.getLogger().severe("The JAR config is broken, disabling");
                this.getServer().getPluginManager().disablePlugin(this);
                this.setEnabled(false);
            }
        }
        catch (ConfigTooOldException e3) {
            this.getLogger().warning("!!! WARNING !!! Your configuration is old. There may be new features or some config behavior might have changed, so it is advised to regenerate your config when possible!");
        }
        catch (Exception e4) {
            e4.printStackTrace();
            this.getLogger().severe("Configuration is invalid. Re-extracting it.");
            final boolean success = !new File(this.getDataFolder(), "config.yml").isFile() || new File(this.getDataFolder(), "config.yml").renameTo(new File(this.getDataFolder(), "config.yml.broken" + new Date().getTime()));
            if (!success) {
                this.getLogger().severe("Cannot rename the broken config, disabling");
                this.getServer().getPluginManager().disablePlugin(this);
                this.setEnabled(false);
            }
            this.saveResource("config.yml", true);
            try {
                config.load(new File(this.getDataFolder(), "config.yml"));
            }
            catch (IOException | InvalidConfigurationException ex4) {
                ex4.printStackTrace();
                this.getLogger().severe("The JAR config is broken, disabling");
                this.getServer().getPluginManager().disablePlugin(this);
                this.setEnabled(false);
            }
        }
        BED_ENABLED = config.getBoolean("use-bed-messages", false);
        TRACK_PLACER_NOT_HITTER = config.getBoolean("track-placer-not-hitter", true);
        TRACK_BED_PLACER_NOT_HITTER = config.getBoolean("track-bed-placer-not-hitter", true);
    }

    private static int VER = 0;
    private static int REV = 0;

    public static boolean mcVer(int comp) {
        return VER >= comp;
    }

    public static boolean mcVerRev(int comp, int rev) {
        return VER > comp || (VER == comp && REV >= rev);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(final BlockPlaceEvent event)
    {
        if (isBed(event.getBlockPlaced().getType())) {
            String us = event.getPlayer().getUniqueId().toString();
            Block b = event.getBlockPlaced();
            b.setMetadata("dmp.bedPlacer", new FixedMetadataValue(this, us));
            b = getOtherBedBlock(event, b);
            if (isBed(b.getType()))
                b.setMetadata("dmp.bedPlacer", new FixedMetadataValue(this, us));
        }
    }

    private Block getOtherBedBlock(final BlockPlaceEvent event, final Block block) {
        BlockFace orientation = getBedOrientation(block, event.getBlockPlaced().getState());
        return block.getRelative(orientation);
    }

    private BlockFace getBedOrientation(Block block, BlockState newState) {
        if (mcVer(1_015))
            return getBedOrientation15(block, newState);
        else if (mcVer(1_008))
            return getBedOrientation8(block, newState);
        else
            return getBedOrientationRawData(block, newState);
    }

    private BlockFace getBedOrientation15(Block block, BlockState newState) {
        Bed bed = (Bed) newState.getBlockData();
        BlockFace orientation = bed.getFacing();
        if (bed.getPart() == Bed.Part.HEAD) orientation = orientation.getOppositeFace();
        return orientation;
    }

    @SuppressWarnings("deprecation")
    private BlockFace getBedOrientation8(Block block, BlockState newState) {
        BlockFace orientation = BlockFace.SELF;
        org.bukkit.material.MaterialData md = newState.getData();
        if (md instanceof org.bukkit.material.Directional)
            orientation = ((org.bukkit.material.Directional)md).getFacing();
        if (md instanceof org.bukkit.material.Bed)
        {
            org.bukkit.material.Bed bed = (org.bukkit.material.Bed)md;
            if (bed.isHeadOfBed())
                orientation = orientation.getOppositeFace();
        }
        return orientation;
    }

    @SuppressWarnings("deprecation")
    private BlockFace getBedOrientationRawData(Block block, BlockState newState) {
        byte b =  newState.getRawData();
        BlockFace orientation = (new BlockFace[] {BlockFace.SOUTH, BlockFace.WEST, BlockFace.NORTH, BlockFace.EAST})[b & 3];
        if ((b & 8) != 0)
            orientation = orientation.getOppositeFace();
        return orientation;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerInteract(final PlayerInteractEvent event) {
        // track players who place ender crystals
        if (Action.RIGHT_CLICK_BLOCK == event.getAction()) {
            if (Material.OBSIDIAN == event.getClickedBlock().getType() || Material.BEDROCK == event.getClickedBlock().getType()) {
                if (Material.END_CRYSTAL == event.getMaterial()) {
                    Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
                        List<Entity> entities = event.getPlayer().getNearbyEntities(5, 5, 5);

                        for (Entity entity : entities) {
                            if (EntityType.ENDER_CRYSTAL == entity.getType()) {
                                EnderCrystal crystal = (EnderCrystal) entity;
                                Block belowCrystal = crystal.getLocation().getBlock().getRelative(BlockFace.DOWN);

                                if (event.getClickedBlock().equals(belowCrystal) && crystal.getTicksLived() < 3) {
                                    crystal.setMetadata("dmp.enderCrystalPlacer", new FixedMetadataValue(this, event.getPlayer().getUniqueId().toString()));
                                }
                            }
                        }
                    });
                }
            }
            else if (isBed(event.getClickedBlock().getType())) {
                UUID u = event.getPlayer().getUniqueId();
                if (TRACK_BED_PLACER_NOT_HITTER) {
                    u = null;
                    Block b = event.getClickedBlock();
                    if (b != null && b.hasMetadata("dmp.bedPlacer") && b.getMetadata("dmp.bedPlacer").size() > 0)
                        u = UUID.fromString(b.getMetadata("dmp.bedPlacer").get(0).asString());
                }
                if (u != null)
                    for (Entity e: event.getClickedBlock().getWorld().getNearbyEntities(event.getClickedBlock().getLocation(), 8, 8, 8)) {
                        if (e instanceof Player) {
                            Player p = (Player)e;
                            BED_KILL.put(p.getUniqueId(), u);
                        }
                    }
            }
        }
    }

    private boolean isBed(Material type) {
        return BED_TYPES.contains(type.name());
    }

    @EventHandler
    public void prePrepare(DeathPreDMPEvent e) {
        Entity damager = e.getDamager();
        if (damager instanceof EnderCrystal) {
            EnderCrystal ec = (EnderCrystal) damager;
            UUID ped = null;
            if (!TRACK_PLACER_NOT_HITTER) { // ped = hitter
                if (damager.getLastDamageCause() != null && ((
                    damager.getLastDamageCause().getCause() == EntityDamageEvent.DamageCause.ENTITY_ATTACK ||
                    damager.getLastDamageCause().getCause() == EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK)) &&
                    damager.getLastDamageCause() instanceof EntityDamageByEntityEvent) {
                    EntityDamageByEntityEvent ed = (EntityDamageByEntityEvent) damager.getLastDamageCause();
                    if (ed.getDamager() instanceof Player) {
                        ped = ed.getDamager().getUniqueId();
                    }
                }
            } else { // ped = placer
                if (ec.hasMetadata("dmp.enderCrystalPlacer") && ec.getMetadata("dmp.enderCrystalPlacer").size() > 0) {
                    ped = UUID.fromString(ec.getMetadata("dmp.enderCrystalPlacer").get(0).asString());
                }
            }

            if (ped != null) { //&& !ped.equals(e.getPlayer().getUniqueId())) {
                // store killer player
                END_CRYSTAL_KILL.put(e.getPlayer().getUniqueId(), ped);
            }
        } else if (e.getCause() != EntityDamageEvent.DamageCause.BLOCK_EXPLOSION) {
            BED_KILL.remove(e.getPlayer().getUniqueId());
        }
    }

    @EventHandler
    public void preBroadcast(DeathMessageCustomEvent e) {
        UUID u = e.getPlayer().getUniqueId();
        // apply custom ender crystal kill message
        if (BED_ENABLED && BED_KILL.containsKey(u)) {
            UUID ku = BED_KILL.get(u);
            if (ku != null) {
                Player p = getServer().getPlayer(ku);
                if (p != null) {
                    if (p.getUniqueId().equals(u)) {
                        e.setTag(BED_SUICIDE_TAG);
                    } else {
                        e.setTag(BED_KILL_TAG);
                        e.setKiller(p.getName());
                        e.setKiller2(p.getDisplayName());
                    }
                }
            }
            BED_KILL.remove(u);
        }
        if (END_CRYSTAL_KILL.containsKey(u)) {
            UUID ku = END_CRYSTAL_KILL.get(u);
            if (ku != null) {
                Player p = getServer().getPlayer(ku);
                if (p != null) {
                    if (p.getUniqueId().equals(u)) {
                        e.setTag(CRYSTAL_SUICIDE_TAG);
                    } else {
                        e.setTag(CRYSTAL_KILL_TAG);
                        e.setKiller(p.getName());
                        e.setKiller2(p.getDisplayName());
                    }
                }
            }
            END_CRYSTAL_KILL.remove(u);
        }
    }
}
