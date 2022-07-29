package git.buchard36.civilizations.npc.interfaces;

import git.buchard36.civilizations.Civilizations;
import net.citizensnpcs.api.ai.AbstractPathStrategy;
import net.citizensnpcs.api.ai.NavigatorParameters;
import net.citizensnpcs.api.ai.TargetType;
import net.citizensnpcs.npc.CitizensNPC;
import net.citizensnpcs.util.NMS;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;
import xyz.oli.PatheticMapper;
import xyz.oli.bukkit.BukkitMapper;
import xyz.oli.pathing.Pathfinder;
import xyz.oli.wrapper.PathLocation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicReference;

public class ChasingEntityAStarStrategy extends AbstractPathStrategy {

    protected final CitizensNPC npc;
    protected NavigatorParameters parameters;
    protected final AtomicReference<Location> currentPathfindingLocation;
    protected final ArrayList<Vector> currentPath;
    public boolean pathfinderRunning;
    protected final LivingEntity target;
    protected final Pathfinder pathfinder;
    public Vector currentTravelingLocation;
    public HashMap<Location, Material> cancelBlockChanges;
    protected double speed;

    public ChasingEntityAStarStrategy(NavigatorParameters parameters,
                                      LivingEntity target,
                                      CitizensNPC npc,
                                      double speed) {
        super(TargetType.LOCATION);
        this.parameters = parameters;
        this.target = target;
        this.currentPathfindingLocation = new AtomicReference<>(null);
        this.currentPath = new ArrayList<>();
        this.pathfinderRunning = false;
        this.npc = npc;
        this.pathfinder = PatheticMapper.newPathfinder();
        this.currentTravelingLocation = null;
        this.cancelBlockChanges = new HashMap<>();
        this.speed = speed;
    }

    @Override
    public Location getCurrentDestination() {
        return this.currentPathfindingLocation.get();
    }

    @Override
    public Iterable<Vector> getPath() {
        return this.currentPath;
    }

    @Override
    public Location getTargetAsLocation() {
        return this.target.getLocation();
    }

    @Override
    public void stop() {
        // I dont have anything to stop :c
        NMS.cancelMoveDestination(npc.getEntity());
        this.currentPath.clear();
    }

    @Override
    public boolean update() {
        if (this.pathfinderRunning) { // bad variable name, actually is "isPathfinderGoalsSet", basically it gets set to true once we receive our iterable vectors
            if (this.currentPath.size() <= 0) {

                /*Bukkit.getScheduler().runTaskLater(Civilizations.INSTANCE, () -> {
                    cancelBlockChanges.forEach((loc, before) -> {
                        Bukkit.getPlayer("Burchard36").sendBlockChange(loc, before.createBlockData());
                    });
                    cancelBlockChanges.clear();
                }, 0L);*/

                return true;
            }

            Vector nextLocation = this.getPath().iterator().next();
            if (NMS.getDestination(npc.getEntity()) != null) {
                return false;
            }
            if (NMS.getDestination(npc.getEntity()) == null) {
                this.currentPath.remove(0);
                NMS.updatePathfindingRange(npc, 500f);
                NMS.setDestination(npc.getEntity(), nextLocation.getX(), nextLocation.getY(), nextLocation.getZ(), (float) this.speed);
            }
            return false;
        }

        PathLocation currentTargetLocation = BukkitMapper.toPathLocation(this.target.getLocation());
        PathLocation npcCurrentLocation = BukkitMapper.toPathLocation(this.npc.getEntity().getLocation());
        this.pathfinder.findPathAsync(npcCurrentLocation, currentTargetLocation, SurvivalTrackingStrategy.class).thenAccept((result) -> {
            ChasingEntityAStarStrategy.this.currentPath.clear();

            for (PathLocation loc : result.getPath().getLocations()) {
                Vector vector = BukkitMapper.toVector(loc.toVector());
                ChasingEntityAStarStrategy.this.currentPath.add(vector);
                Bukkit.getScheduler().runTask(Civilizations.INSTANCE, () -> {
                    Location locc = BukkitMapper.toLocation(loc).clone().subtract(0, 1, 0);
                    Material before = locc.getBlock().getType();
                    //Bukkit.getPlayer("Burchard36").sendBlockChange(locc, Material.GOLD_BLOCK.createBlockData());
                    cancelBlockChanges.put(locc, before);
                });
            }
            this.pathfinderRunning = true;
        });
        return false;
    }

    protected boolean makeExtraSureOfLocation(Location location) {
        int maxSearch = 4;
        int currentFound = 0;
        for (int x = 0; x <= 5; x++) {
            Block currentBlock = location.getBlock();
            switch (currentBlock.getType()) {
                case AIR, CAVE_AIR, VOID_AIR -> {
                    currentFound++;
                }

                case WATER, LAVA -> {
                    return false; // avoid water and lava entirely
                }

                default -> {
                    return true; // sure allow all
                }
            }

            if (currentFound >= maxSearch) return false;
            location = location.clone().subtract(0, 1, 0);
        }

        return true;
    }
}
