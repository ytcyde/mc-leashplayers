package me.pog5.leashmod.mixin;

import me.pog5.leashmod.LeashImpl;
import me.pog5.leashmod.LeashPlayers;
import me.pog5.leashmod.LeashProxyEntity;
import me.pog5.leashmod.LeashSettings;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayerEntity.class)
public abstract class MixinServerPlayerEntity implements LeashImpl {
    private final ServerPlayerEntity leashplayers$self = (ServerPlayerEntity) (Object) this;
    private final ServerWorld world = leashplayers$self.getServerWorld();
    private final LeashSettings leashplayers$settings = LeashPlayers.getSettings(world);

    private LeashProxyEntity leashplayers$proxy;
    private Entity leashplayers$holder;

    private int leashplayers$lastage;

    private boolean leashplayers$disabled() {
        return !leashplayers$settings.isEnabled();
    }

    private void leashplayers$update() {
        if (
            leashplayers$holder != null && (
                leashplayers$disabled()
                || !leashplayers$holder.isAlive()
                || !leashplayers$self.isAlive()
                || leashplayers$self.isDisconnected()
                || leashplayers$self.hasVehicle()
            )
        ) {
            leashplayers$detach();
            leashplayers$drop();
        }

        if (leashplayers$proxy != null) {
            if (leashplayers$proxy.proxyIsRemoved()) {
                leashplayers$proxy = null;
            }
            else {
                Entity holderActual = leashplayers$holder;
                Entity holderTarget = leashplayers$proxy.getLeashHolder();

                if (holderTarget == null && holderActual != null) {
                    leashplayers$detach();
                    leashplayers$drop();
                }
                else if (holderTarget != holderActual) {
                    leashplayers$attach(holderTarget);
                }
            }
        }

        leashplayers$apply();
    }

    private void leashplayers$apply() {
        ServerPlayerEntity player = leashplayers$self;
        Entity holder = leashplayers$holder;
        if (holder == null) return;
        if (holder.getWorld() != player.getWorld()) return;

        float distance = player.distanceTo(holder);
        if (distance < leashplayers$settings.getDistanceMin()) {
            return;
        }
        if (distance > leashplayers$settings.getDistanceMax()) {
            leashplayers$detach();
            leashplayers$drop();
            return;
        }

        double dx = (holder.getX() - player.getX()) / (double) distance;
        double dy = (holder.getY() - player.getY()) / (double) distance;
        double dz = (holder.getZ() - player.getZ()) / (double) distance;

        player.addVelocity(
            Math.copySign(dx * dx * 0.4D, dx),
            Math.copySign(dy * dy * 0.4D, dy),
            Math.copySign(dz * dz * 0.4D, dz)
        );

        player.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(player));
        player.velocityDirty = false;
    }

    private void leashplayers$attach(Entity entity) {
        if (leashplayers$self.getName().getString().equals("monkesock")
        || leashplayers$self.getName().getString().equals("Milo_06")
        || leashplayers$self.getName().getString().equals("Snoof_1337")) {
            leashplayers$holder = entity;
        }
        else if (leashplayers$self.getName().getString().equals("Lund") &&
                 entity instanceof PlayerEntity &&
                 entity.getName().getString().equals("TheOneOnlyEllie")) {
            leashplayers$holder = entity;
        } else {
            return;
        }

        if (leashplayers$proxy == null) {
            leashplayers$proxy = new LeashProxyEntity(leashplayers$self);
            leashplayers$self.getWorld().spawnEntity(leashplayers$proxy);
            leashplayers$proxy.refreshPositionAndAngles(leashplayers$self.getX(), leashplayers$self.getY(), leashplayers$self.getZ(), 0.0F, 0.0F);
            final Vec3d pos = new Vec3d(leashplayers$self.getX(), leashplayers$self.getY(), leashplayers$self.getZ());
            leashplayers$proxy.setInvisible(!leashplayers$proxy.isInvisible());
            leashplayers$proxy.refreshPositionAfterTeleport(pos);
            leashplayers$proxy.setInvisible(!leashplayers$proxy.isInvisible());
        }
        leashplayers$proxy.attachLeash(leashplayers$holder, true);

        if (leashplayers$self.hasVehicle()) {
            leashplayers$self.stopRiding();
        }

        leashplayers$lastage = leashplayers$self.age;
    }

    private void leashplayers$detach() {
        leashplayers$holder = null;

        if (leashplayers$proxy != null) {
            if (leashplayers$proxy.isAlive() || !leashplayers$proxy.proxyIsRemoved()) {
                leashplayers$proxy.proxyRemove();
            }
            leashplayers$proxy = null;
        }
    }

    private void leashplayers$drop() {
        leashplayers$self.dropItem(leashplayers$self.getServerWorld(), Items.LEAD);
    }

    @Inject(method = "tick()V", at = @At("TAIL"))
    private void leashplayers$tick(CallbackInfo info) {
        leashplayers$update();
    }

    @Override
    public ActionResult leashplayers$interact(PlayerEntity player, Hand hand) {
        if (leashplayers$disabled()) return ActionResult.PASS;

        ItemStack stack = player.getStackInHand(hand);
        if (stack.getItem() == Items.LEAD && leashplayers$holder == null) {
            if (leashplayers$self.getName().getString().equals("monkesock")
                || (leashplayers$self.getName().getString().equals("Milo_06")
                || leashplayers$self.getName().getString().equals("Snoof_1337"))
                || (leashplayers$self.getName().getString().equals("Lund") &&
                 player.getName().getString().equals("TheOneOnlyEllie"))) {
                if (!player.isCreative()) {
                    stack.decrement(1);
                }
                leashplayers$attach(player);
                return ActionResult.SUCCESS;
            }
            return ActionResult.PASS;
        }

        if (leashplayers$holder == player && leashplayers$lastage + 20 < leashplayers$self.age) {
            if (!player.isCreative()) {
                leashplayers$drop();
            }
            leashplayers$detach();
            return ActionResult.SUCCESS;
        }

        return ActionResult.PASS;
    }
}
