package git.buchard36.civilizations.npc;

import git.buchard36.civilizations.Civilizations;
import git.buchard36.civilizations.npc.interfaces.CallbackFunction;
import git.buchard36.civilizations.npc.interfaces.CallbackFunctionResult;
import git.buchard36.civilizations.npc.interfaces.SurvivalTrackingStrategy;
import net.citizensnpcs.npc.CitizensNPC;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.craftbukkit.v1_19_R1.block.CraftBlock;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import xyz.oli.PatheticMapper;
import xyz.oli.bukkit.BukkitMapper;
import xyz.oli.wrapper.PathLocation;

import java.util.*;


public class NpcFactory {

    public static Random random = new Random();

    protected final Civilizations civs;
    protected List<CitizensNPC> npcs;

    public NpcFactory(Civilizations civs) {
        this.civs = civs;
        this.npcs = new ArrayList<>();
    }

    protected void generateAsync(Location start, Location forLocation, CallbackFunctionResult results) {
        PatheticMapper.newPathfinder().findPathAsync(
                BukkitMapper.toPathLocation(start),
                BukkitMapper.toPathLocation(forLocation),
                SurvivalTrackingStrategy.class).thenAccept((pathfinderResult -> {
                    results.onComplete(pathfinderResult.getPath().getLocations().iterator());
        }));
    }

    public void createNpc(Player player) {

        /*HNPC npc = HCore.npcBuilder(player.getName() + System.currentTimeMillis())
                .showEveryone(true)
                .location(player.getLocation())
                .skin(player.getName())
                .lines(List.of("Holograms!"))
                .addViewers(player.getUniqueId())
                .build();
        npc.expire(1, TimeUnit.DAYS);
        npc.setEquipment(HNPC.EquipmentType.CHEST, new ItemStack(Material.DIAMOND_CHESTPLATE));
        npc.setEquipment(HNPC.EquipmentType.LEGS, new ItemStack(Material.LEATHER_LEGGINGS));
        Bukkit.getScheduler().runTaskTimer(Civilizations.INSTANCE, () -> {
            Bukkit.broadcastMessage("Can everyone see me!? " + npc.canEveryoneSee());
            Bukkit.broadcastMessage("Im at"
                    + " X: "
                    + npc.getLocation().getBlockX()
                    + " Y: "
                    + npc.getLocation().getBlockY()
                    + " Z: "
                    + npc.getLocation().getBlockZ()
            );
        }, 0L, 100L);*/
        //this.pathFindTo(player, npc); // endless recursive async function

        CivNpc npc = new CivNpc();
        npc.spawnIn(player.getLocation(), () -> {
            Bukkit.broadcastMessage("Starting pathfinding!");
            pathFindTo(player, npc);
        });
        /*this.destroy();
        for (int x = 0; x <= 1; x++) {
            CitizensNPC npc = (CitizensNPC) CitizensAPI.getNPCRegistry().createNPC(EntityType.PLAYER, "LEEROY JENKINS" + x);
            Bukkit.getScheduler().runTaskLater(Civilizations.INSTANCE, () -> {
                npc.spawn(player.getLocation().clone().add(
                        ThreadLocalRandom.current().nextInt(-20, 20),
                        0, ThreadLocalRandom.current().nextInt(-20, 20))
                );
            }, x);
            Bukkit.getScheduler().runTaskLater(Civilizations.INSTANCE, () -> {
                final NpcController controller = new NpcController(npc, player);
                controller.lockToOwner();
                npcs.add(npc);

                controller.registerRepeatingAction(new TntTrollAction());
            }, x * 2);

        }*/
    }

    public void pathFindTo(Player player, CivNpc npc) {
        Bukkit.broadcastMessage("Calculating path. . .");
        this.generateAsync(
                npc.getBukkitEntity().getLocation(),
                player.getLocation().clone()/*.add(
                        ThreadLocalRandom.current().nextInt(-40, 40),
                        0,
                        ThreadLocalRandom.current().nextInt(-40, 40)*/
                , (locations) -> {
                Bukkit.broadcastMessage("Calculated!");
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        Bukkit.broadcastMessage("Moving!!!!");
                        processMove(npc, locations, () -> {
                            Bukkit.broadcastMessage("Finished! Recalculating. . .");
                            Bukkit.getScheduler().runTaskLater(Civilizations.INSTANCE,
                                    () -> pathFindTo(player, npc), 100L);
                        });
                    }
                }.runTaskAsynchronously(Civilizations.INSTANCE);

        });
    }

    protected void processMove(CivNpc npc, Iterator<PathLocation> locations, CallbackFunction onCompletion) {
        if (locations.hasNext()) {
            PathLocation location = locations.next();
            Location loc = BukkitMapper.toLocation(location);

            /*AABB entity = npc.getBoundingBox();
            AABB box = AABB.of(new BoundingBox(((CraftBlock) loc.getBlock()).getPosition()));
            if (entity.intersects(box)) {
                AABB inter = entity.intersect(box);
                Bukkit.broadcastMessage("Interecting at!");
            }*/

            /*if (
                    (entity.minX <= box.maxX && entity.maxX >= box.minX) &&
                    (entity.minY <= box.maxY && entity.maxY >= box.minY) &&
                    (entity.minZ <= box.maxZ && entity.minZ >= box.maxZ)
            ) {
                Bukkit.broadcastMessage("Colliding!");
                entity.intersect()
            }*/

            double x = (double) (location.getBlockX()) + 0.5D;
            double y = location.getY();
            double z = (double) (location.getBlockZ()) + 0.5D;
            npc.moveEntityTo(x, y, z, () -> {
                processMove(npc, locations, onCompletion);
            });
        } else onCompletion.onComplete();
    }

    public void destroy() {
        this.npcs.forEach(CitizensNPC::despawn);
        this.npcs.forEach(CitizensNPC::destroy);
        this.npcs.clear();
    }

}
