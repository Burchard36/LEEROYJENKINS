package git.buchard36.civilizations.npc;

import git.buchard36.civilizations.Civilizations;
import git.buchard36.civilizations.npc.actions.StaticRepeatingAction;
import git.buchard36.civilizations.npc.interfaces.CallbackFunction;
import git.buchard36.civilizations.utils.BlockScanner;
import net.citizensnpcs.api.npc.NPC;
import net.minecraft.world.InteractionHand;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.craftbukkit.v1_19_R1.entity.CraftLivingEntity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Controller method to handle the NPC's behavior.
 */
public class NpcController extends NpcInventoryDecider {

    public final Player linkedPlayer;
    protected final BlockScanner blockScanner;
    protected BukkitTask lockToTask;
    protected final List<StaticRepeatingAction> repeatingActions;

    public NpcController(NPC npc, Player linkedPlayer) {
        super(npc);
        this.linkedPlayer = linkedPlayer;
        this.blockScanner = new BlockScanner();
        this.repeatingActions = new ArrayList<>();
    }

    @Override
    public void think() {
        if (this.needsWood()) {

        }
    }

    public void registerRepeatingAction(StaticRepeatingAction action) {
        this.repeatingActions.add(action);
    }

    protected void makePlayerAttack(LivingEntity entity,
                                    int times,
                                    long delayBetweenHits,
                                    @Nullable CallbackFunction callback) {
        AtomicInteger timesRan = new AtomicInteger(0);
        net.minecraft.world.entity.LivingEntity nmsEntity = ((CraftLivingEntity) entity).getHandle();
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(Civilizations.INSTANCE, () -> {
            if (timesRan.get() >= times) return; // Make sure the task doesn't run more than the times specified.
            if (!this.combatTargetConditions.test(nmsEntity, this.nmsNpc)) {
                this.sendChatMessage("Your lucky i cant fucking hit you fuckface");
                return;
            }
            this.sendChatMessage("Take this, asshole!");
            this.nmsNpc.attack(nmsEntity);
            this.nmsNpc.swing(InteractionHand.MAIN_HAND);
            timesRan.incrementAndGet();
        }, 0, delayBetweenHits);

        Bukkit.getScheduler().runTaskLater(Civilizations.INSTANCE, () -> {
            task.cancel();
            if (callback != null) callback.onComplete();
        }, (delayBetweenHits * times) + 5); // Add 5 ticks to account for the delay between each hit & processing time
    }

    /**
     * Sets the main hand to the typeToPlace, waits, faces torwards placing locations, waits again, and then places item with sound
     * @param placingLocation Location to place block at
     * @param typeToPlace Material type to place
     * @param onCompletion Callback function to run when the block is placed
     */
    public void placeBlockAsNpc(Location placingLocation,
                                   Material typeToPlace,
                                   @Nullable CallbackFunction onCompletion) {
        final ItemStack stack = new ItemStack(typeToPlace);
        Bukkit.getScheduler().runTaskLater(Civilizations.INSTANCE, () -> {
            this.citizensNpc.faceLocation(placingLocation); // 4 ticks later, face towards the block to place

            Bukkit.getScheduler().runTaskLater(Civilizations.INSTANCE, () -> {
                this.bukkitPlayer.getEquipment().setItemInMainHand(stack); // 3 ticks later set the item in the NPC's hand

                Bukkit.getScheduler().runTaskLater(Civilizations.INSTANCE, () -> {
                    this.nmsNpc.swing(InteractionHand.MAIN_HAND);
                    Objects.requireNonNull(placingLocation.getWorld())
                            .playSound(placingLocation, Sound.BLOCK_GRASS_PLACE, 1, 1); // Make better sound processing later
                    placingLocation.getBlock().setType(typeToPlace);
                    //this.bukkitPlayer.getEquipment().setItemInMainHand(new ItemStack(Material.AIR));
                    if (onCompletion != null) onCompletion.onComplete();
                }, 5L);
            }, 3L);
        }, 4L);
    }

    public TNTPrimed fakeIgniteTnt(Location tntLocation) {
        this.citizensNpc.faceLocation(tntLocation);
        this.makeNpcEquipItem(Material.FLINT_AND_STEEL);
        this.nmsNpc.swing(InteractionHand.MAIN_HAND);
        tntLocation.getWorld().playSound(tntLocation, Sound.ITEM_FLINTANDSTEEL_USE, 1F, 1F);
        tntLocation.getBlock().setType(Material.AIR);
        return (TNTPrimed) tntLocation.getWorld().spawnEntity(tntLocation, EntityType.PRIMED_TNT);
    }

    public void makeNpcEquipItem(Material material) {
        this.bukkitPlayer.getEquipment().setItemInMainHand(new ItemStack(material));
    }

    public void navigateNpcTo(Location location,
                                 float atBaseSpeed,
                                 boolean useSprintingAnimation,
                                 @Nullable CallbackFunction onCompletion) {
        double distance = location.distance(this.bukkitPlayer.getLocation());
        this.npcNavigator.getDefaultParameters().range((float) (distance * 2F));
        float initialBaseSpeed = this.npcNavigator.getDefaultParameters().baseSpeed();
        this.npcNavigator.getDefaultParameters().baseSpeed(atBaseSpeed);
        this.nmsNpc.setSprinting(useSprintingAnimation);
        this.npcNavigator.setTarget(location.add(1, 0, 1));
        CompletableFuture.runAsync(() -> { // begin NPC waiting on a separate thread, so we don't halt the main thread
            while (this.npcNavigator.isNavigating()) {
                try {
                    Thread.sleep(50); // block while NPC is navigating
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            Bukkit.getScheduler().runTask(Civilizations.INSTANCE, () -> {
                this.nmsNpc.setSpeed(initialBaseSpeed);
                if (onCompletion != null) onCompletion.onComplete();
            }); // Run completions operations back on main thread
        });
    }

    public void sendChatMessage(String msg) {
        this.nmsNpc.connection.chat(msg, false);
    }

    public void sendMessageTo(LivingEntity entity, String msg) {
        entity.sendMessage(msg);
    }

    public void lockTo(LivingEntity entity) {
        if (this.lockToTask != null) this.lockToTask.cancel();
        this.lockToTask = Bukkit.getScheduler().runTaskTimer(Civilizations.INSTANCE, () -> {
            this.npcNavigator.setTarget(entity.getLocation());
            this.citizensNpc.faceLocation(entity.getEyeLocation());
        }, 0, 60L);
    }

    public void stopLockingTask() {
        this.lockToTask.cancel();
    }

    public void runLater(Runnable runnable, long delay, @Nullable CallbackFunction callback) {
        Bukkit.getScheduler().runTaskLater(Civilizations.INSTANCE, () -> {
            runnable.run();
            if (callback !=null) callback.onComplete();
        }, delay);
    }
}
