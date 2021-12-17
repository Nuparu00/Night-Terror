package nuparu.nightterror;

import com.mojang.math.Vector3d;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerSleepInBedEvent;
import net.minecraftforge.event.world.SleepFinishedTimeEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.loading.FMLPaths;
import nuparu.config.CommonConfig;
import nuparu.config.ConfigHelper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Mod(NightTerror.MODID)
public class NightTerror
{
    public static final String MODID = "nightterror";
    // Directly reference a log4j logger.
    private static final Logger LOGGER = LogManager.getLogger();

    public NightTerror() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, ConfigHelper.commonConfig);
        ConfigHelper.loadConfig(ConfigHelper.commonConfig,
                FMLPaths.CONFIGDIR.get().resolve("nightterror-common.toml").toString());
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onPlayerSleepInBed(PlayerSleepInBedEvent event) {
        if (event.getResultStatus() != Player.BedSleepingProblem.NOT_SAFE && event.getResultStatus() != null)
            return;
        Player player = event.getPlayer();
        if (net.minecraftforge.event.ForgeEventFactory.fireSleepingTimeCheck(player, event.getOptionalPos())) {
            event.setResult((Player.BedSleepingProblem) null);
            player.startSleeping(event.getOptionalPos().get());
            player.sleepCounter = 0;
            if (player.level instanceof ServerLevel) {
                ((ServerLevel) player.level).updateSleepingPlayerList();
            }

        }
    }

    @SubscribeEvent
    public void onSleepFinished(SleepFinishedTimeEvent event) {
        LevelAccessor iw = event.getWorld();
        if (iw instanceof Level) {
            Level world = (Level) iw;
            MinecraftServer server = world.getServer();
            if (server == null)
                return;
            List<ServerPlayer> players = server.getPlayerList().getPlayers();
            boolean finished = true;
            for (ServerPlayer player : players) {
                if (player.isCreative() || player.isSpectator())
                    continue;
                BlockPos bed = player.getSleepingPos().orElse(null);
                if(bed == null) continue;

                BlockState state = world.getBlockState(bed);
                boolean woke = false;
                if (world.random.nextDouble() > CommonConfig.terrorChance.get() * (getModifier(world))) {
                    continue;
                }
                for (int x = -CommonConfig.checkRange.get(); x < CommonConfig.checkRange.get(); x++) {
                    for (int y = -CommonConfig.checkRange.get()/2; y < CommonConfig.checkRange.get()/2; y++) {
                        for (int z = -CommonConfig.checkRange.get(); z < CommonConfig.checkRange.get(); z++) {
                            List<String> list = new ArrayList<>();
                            list.addAll(CommonConfig.wakingEntites.get());
                            if (list.size() < 1)
                                return;

                            BlockPos pos = bed.offset(x, y, z);
                            Mob mob = null;
                            while (mob == null && list.size() > 0) {
                                int index = world.random.nextInt(list.size());
                                Optional<EntityType<?>> o = EntityType.byString(list.get(index));
                                list.remove(index);
                                if (!o.isPresent())
                                    continue;
                                EntityType<?> type = o.get();
                                Entity entity = type.create(world);
                                if (!(entity instanceof Mob)) {
                                    LOGGER.warn(type.getRegistryName() + " is not an instance of MobEntity! Skipping.");
                                    continue;
                                }
                                mob = (Mob) entity;
                            }
                            if (mob == null)
                                return;
                            if (!Zombie.isDarkEnoughToSpawn(player.getLevel(), pos, world.random))
                                continue;
                            /*try {
                                boolean path = (boolean) (m_isDirectPathBetweenPoints.invoke(mob.getNavigation(),
                                        new Vector3d(pos.getX(), pos.getY(), pos.getZ()),
                                        new Vector3d(bed.getX(), bed.getY(), bed.getZ()), 1 , 1 , 1));
                                if (!path)
                                    continue;
                            } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
                                e.printStackTrace();
                                continue;
                            }*/

                            world.addFreshEntity(mob);
                            Optional<Vec3> vec = state.getRespawnPosition(EntityType.PLAYER, world, bed, player.getRespawnAngle(),null);
                            mob.setPos(vec.get().x, vec.get().y, vec.get().z);
                            mob.getLookControl().setLookAt(player);
                            mob.doHurtTarget(player);
                            player.hurt(DamageSource.mobAttack(mob), CommonConfig.wakeDamage.get());
                            woke = true;
                            finished = false;
                            break;
                        }
                        if (woke) {
                            break;
                        }
                    }
                    if (woke) {
                        break;
                    }
                }

            }
            if (!finished && CommonConfig.interruptSleep.get()) {
                event.setTimeAddition(world.getDayTime());
            }

        }
    }

    public static double getModifier(Level world) {
        switch (world.getDifficulty()) {
            default:
            case PEACEFUL:
                return 0.5d;
            case EASY:
                return 0.75d;
            case NORMAL:
                return 1d;
            case HARD:
                return 1.5d;
        }
    }
}
