package net.minecraft.client.entity;

import cn.hanabi.Wrapper;
import cn.hanabi.events.*;
import cn.hanabi.modules.ModManager;
import cn.hanabi.modules.modules.combat.KeepSprint;
import cn.hanabi.modules.modules.ghost.Hitbox;
import cn.hanabi.modules.modules.movement.NoSlow;
import com.darkmagician6.eventapi.EventManager;
import com.darkmagician6.eventapi.types.EventType;
import net.minecraft.block.Block;
import net.minecraft.block.BlockFence;
import net.minecraft.block.BlockFenceGate;
import net.minecraft.block.BlockWall;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.MovingSoundMinecartRiding;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.GuiCommandBlock;
import net.minecraft.client.gui.GuiEnchantment;
import net.minecraft.client.gui.GuiHopper;
import net.minecraft.client.gui.GuiMerchant;
import net.minecraft.client.gui.GuiRepair;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiScreenBook;
import net.minecraft.client.gui.inventory.GuiBeacon;
import net.minecraft.client.gui.inventory.GuiBrewingStand;
import net.minecraft.client.gui.inventory.GuiChest;
import net.minecraft.client.gui.inventory.GuiCrafting;
import net.minecraft.client.gui.inventory.GuiDispenser;
import net.minecraft.client.gui.inventory.GuiEditSign;
import net.minecraft.client.gui.inventory.GuiFurnace;
import net.minecraft.client.gui.inventory.GuiScreenHorseInventory;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.command.server.CommandBlockLogic;
import net.minecraft.crash.CrashReport;
import net.minecraft.crash.CrashReportCategory;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.*;
import net.minecraft.entity.boss.EntityDragonPart;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.item.EntityMinecart;
import net.minecraft.entity.passive.EntityHorse;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C01PacketChatMessage;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.client.C0APacketAnimation;
import net.minecraft.network.play.client.C0BPacketEntityAction;
import net.minecraft.network.play.client.C0CPacketInput;
import net.minecraft.network.play.client.C0DPacketCloseWindow;
import net.minecraft.network.play.client.C13PacketPlayerAbilities;
import net.minecraft.network.play.client.C16PacketClientStatus;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.potion.Potion;
import net.minecraft.stats.AchievementList;
import net.minecraft.stats.StatBase;
import net.minecraft.stats.StatFileWriter;
import net.minecraft.stats.StatList;
import net.minecraft.tileentity.TileEntitySign;
import net.minecraft.util.*;
import net.minecraft.world.IInteractionObject;
import net.minecraft.world.World;

import java.util.Iterator;
import java.util.List;

import static net.optifine.reflect.Reflector.ForgeHooks;

public class EntityPlayerSP extends AbstractClientPlayer {
    public final NetHandlerPlayClient sendQueue;
    private final StatFileWriter statWriter;

    /**
     * The last X position which was transmitted to the server, used to determine when the X position changes and needs
     * to be re-trasmitted
     */
    private double lastReportedPosX;

    /**
     * The last Y position which was transmitted to the server, used to determine when the Y position changes and needs
     * to be re-transmitted
     */
    private double lastReportedPosY;

    /**
     * The last Z position which was transmitted to the server, used to determine when the Z position changes and needs
     * to be re-transmitted
     */
    private double lastReportedPosZ;

    /**
     * The last yaw value which was transmitted to the server, used to determine when the yaw changes and needs to be
     * re-transmitted
     */
    private float lastReportedYaw;

    /**
     * The last pitch value which was transmitted to the server, used to determine when the pitch changes and needs to
     * be re-transmitted
     */
    private float lastReportedPitch;

    /**
     * the last sneaking state sent to the server
     */
    private boolean serverSneakState;

    /**
     * the last sprinting state sent to the server
     */
    private boolean serverSprintState;

    /**
     * Reset to 0 every time position is sent to the server, used to send periodic updates every 20 ticks even when the
     * player is not moving.
     */
    private int positionUpdateTicks;
    private boolean hasValidHealth;
    private String clientBrand;
    public MovementInput movementInput;
    protected Minecraft mc;

    /**
     * Used to tell if the player pressed forward twice. If this is at 0 and it's pressed (And they are allowed to
     * sprint, aka enough food on the ground etc) it sets this to 7. If it's pressed and it's greater than 0 enable
     * sprinting.
     */
    protected int sprintToggleTimer;

    /**
     * Ticks left before sprinting is disabled.
     */
    public int sprintingTicksLeft;
    public float renderArmYaw;
    public float renderArmPitch;
    public float prevRenderArmYaw;
    public float prevRenderArmPitch;
    private int horseJumpPowerCounter;
    private float horseJumpPower;

    /**
     * The amount of time an entity has been in a Portal
     */
    public float timeInPortal;

    /**
     * The amount of time an entity has been in a Portal the previous tick
     */
    public float prevTimeInPortal;

    public EntityPlayerSP(Minecraft mcIn, World worldIn, NetHandlerPlayClient netHandler, StatFileWriter statFile) {
        super(worldIn, netHandler.getGameProfile());
        this.sendQueue = netHandler;
        this.statWriter = statFile;
        this.mc = mcIn;
        this.dimension = 0;
    }

    /**
     * Called when the entity is attacked.
     */
    public boolean attackEntityFrom(DamageSource source, float amount) {
        return false;
    }

    /**
     * Heal living entity (param: amount of half-hearts)
     */
    public void heal(float healAmount) {
    }

    /**
     * Called when a player mounts an entity. e.g. mounts a pig, mounts a boat.
     */
    public void mountEntity(Entity entityIn) {
        super.mountEntity(entityIn);

        if (entityIn instanceof EntityMinecart) {
            this.mc.getSoundHandler().playSound(new MovingSoundMinecartRiding(this, (EntityMinecart) entityIn));
        }
    }

    /**
     * Called to update the entity's position/logic.
     */
    public void onUpdate() {
        if (Minecraft.getMinecraft().thePlayer != null && Minecraft.getMinecraft().theWorld != null) {
            EventUpdate event = new EventUpdate();
            EventManager.call(event);
        }
        if (this.worldObj.isBlockLoaded(new BlockPos(this.posX, 0.0D, this.posZ))) {
            super.onUpdate();

            if (this.isRiding()) {
                this.sendQueue.addToSendQueue(new C03PacketPlayer.C05PacketPlayerLook(this.rotationYaw, this.rotationPitch, this.onGround));
                this.sendQueue.addToSendQueue(new C0CPacketInput(this.moveStrafing, this.moveForward, this.movementInput.jump, this.movementInput.sneak));
            } else {
                this.onUpdateWalkingPlayer();
            }
        }
    }

    /**
     * called every tick when the player is on foot. Performs all the things that normally happen during movement.
     */
    public void onUpdateWalkingPlayer() {
        boolean flag = this.isSprinting();
        EventPreMotion pre = new EventPreMotion(this.posX, this.posY, this.posZ, this.rotationYaw, this.rotationPitch, this.onGround);
        EventManager.call(pre);
        if (pre.cancel) {
            EventPostMotion post = new EventPostMotion(pre.pitch);
            EventManager.call(post);
        } else {
            if (flag != this.serverSprintState) {
                if (Wrapper.canSendMotionPacket) {
                    if (flag) {
                        this.sendQueue.addToSendQueue(new C0BPacketEntityAction(this, C0BPacketEntityAction.Action.START_SPRINTING));
                    } else {
                        this.sendQueue.addToSendQueue(new C0BPacketEntityAction(this, C0BPacketEntityAction.Action.STOP_SPRINTING));
                    }
                }

                this.serverSprintState = flag;
            }

            boolean flag1 = this.isSneaking();
            if (flag1 != this.serverSneakState) {
                if (Wrapper.canSendMotionPacket) {
                    if (flag1) {
                        this.sendQueue.addToSendQueue(new C0BPacketEntityAction(this, C0BPacketEntityAction.Action.START_SNEAKING));
                    } else {
                        this.sendQueue.addToSendQueue(new C0BPacketEntityAction(this, C0BPacketEntityAction.Action.STOP_SNEAKING));
                    }
                }

                this.serverSneakState = flag1;
            }

            if (this.isCurrentViewEntity()) {
                double x = pre.getX();
                double y = pre.getY();
                double z = pre.getZ();
                float yaw = pre.getYaw();
                float pitch = pre.getPitch();
                boolean ground = pre.isOnGround();
                double d0 = x - this.lastReportedPosX;
                double d1 = y - this.lastReportedPosY;
                double d2 = z - this.lastReportedPosZ;
                double d3 = (double) (yaw - this.lastReportedYaw);
                double d4 = (double) (pitch - this.lastReportedPitch);
                boolean flag2 = d0 * d0 + d1 * d1 + d2 * d2 > 9.0E-4 || this.positionUpdateTicks >= 20;
                boolean flag3 = d3 != 0.0 || d4 != 0.0;
                if (!pre.isCancelled()) {
                    if (flag2 && flag3) {
                        this.sendQueue.addToSendQueue(new C03PacketPlayer.C06PacketPlayerPosLook(x, y, z, yaw, pitch, ground));
                    } else if (flag2) {
                        this.sendQueue.addToSendQueue(new C03PacketPlayer.C04PacketPlayerPosition(x, y, z, ground));
                    } else if (flag3) {
                        this.sendQueue.addToSendQueue(new C03PacketPlayer.C05PacketPlayerLook(yaw, pitch, ground));
                    } else if (!ModManager.getModule("LessPacket").isEnabled()) {
                        this.sendQueue.addToSendQueue(new C03PacketPlayer(ground));
                    }
                }

                ++this.positionUpdateTicks;
                if (flag2) {
                    this.lastReportedPosX = x;
                    this.lastReportedPosY = y;
                    this.lastReportedPosZ = z;
                    this.positionUpdateTicks = 0;
                }

                if (flag3) {
                    this.lastReportedYaw = yaw;
                    this.lastReportedPitch = pitch;
                }

                EventPostMotion post2 = new EventPostMotion(pre.pitch);
                EventManager.call(post2);
            }

        }
    }

    /**
     * Called when player presses the drop item key
     */
    public EntityItem dropOneItem(boolean dropAll) {
        C07PacketPlayerDigging.Action c07packetplayerdigging$action = dropAll ? C07PacketPlayerDigging.Action.DROP_ALL_ITEMS : C07PacketPlayerDigging.Action.DROP_ITEM;
        this.sendQueue.addToSendQueue(new C07PacketPlayerDigging(c07packetplayerdigging$action, BlockPos.ORIGIN, EnumFacing.DOWN));
        return null;
    }

    /**
     * Joins the passed in entity item with the world. Args: entityItem
     */
    protected void joinEntityItemWithWorld(EntityItem itemIn) {
    }

    /**
     * Sends a chat message from the player. Args: chatMessage
     */
    public void sendChatMessage(String message) {
        this.sendQueue.addToSendQueue(new C01PacketChatMessage(message));
    }

    /**
     * Swings the item the player is holding.
     */
    public void swingItem() {
        super.swingItem();
        this.sendQueue.addToSendQueue(new C0APacketAnimation());
    }

    public void respawnPlayer() {
        this.sendQueue.addToSendQueue(new C16PacketClientStatus(C16PacketClientStatus.EnumState.PERFORM_RESPAWN));
    }

    /**
     * Deals damage to the entity. If its a EntityPlayer then will take damage from the armor first and then health
     * second with the reduced value. Args: damageAmount
     */
    protected void damageEntity(DamageSource damageSrc, float damageAmount) {
        if (!this.isEntityInvulnerable(damageSrc)) {
            this.setHealth(this.getHealth() - damageAmount);
        }
    }

    /**
     * set current crafting inventory back to the 2x2 square
     */
    public void closeScreen() {
        this.sendQueue.addToSendQueue(new C0DPacketCloseWindow(this.openContainer.windowId));
        this.closeScreenAndDropStack();
    }

    public void closeScreenAndDropStack() {
        this.inventory.setItemStack((ItemStack) null);
        super.closeScreen();
        this.mc.displayGuiScreen((GuiScreen) null);
    }

    /**
     * Updates health locally.
     */
    public void setPlayerSPHealth(float health) {
        if (this.hasValidHealth) {
            float f = this.getHealth() - health;

            if (f <= 0.0F) {
                this.setHealth(health);

                if (f < 0.0F) {
                    this.hurtResistantTime = this.maxHurtResistantTime / 2;
                }
            } else {
                this.lastDamage = f;
                this.setHealth(this.getHealth());
                this.hurtResistantTime = this.maxHurtResistantTime;
                this.damageEntity(DamageSource.generic, f);
                this.hurtTime = this.maxHurtTime = 10;
            }
        } else {
            this.setHealth(health);
            this.hasValidHealth = true;
        }
    }

    /**
     * Adds a value to a statistic field.
     */
    public void addStat(StatBase stat, int amount) {
        if (stat != null) {
            if (stat.isIndependent) {
                super.addStat(stat, amount);
            }
        }
    }

    /**
     * Sends the player's abilities to the server (if there is one).
     */
    public void sendPlayerAbilities() {
        this.sendQueue.addToSendQueue(new C13PacketPlayerAbilities(this.capabilities));
    }

    /**
     * returns true if this is an EntityPlayerSP, or the logged in player.
     */
    public boolean isUser() {
        return true;
    }

    protected void sendHorseJump() {
        this.sendQueue.addToSendQueue(new C0BPacketEntityAction(this, C0BPacketEntityAction.Action.RIDING_JUMP, (int) (this.getHorseJumpPower() * 100.0F)));
    }

    public void sendHorseInventory() {
        this.sendQueue.addToSendQueue(new C0BPacketEntityAction(this, C0BPacketEntityAction.Action.OPEN_INVENTORY));
    }

    public void setClientBrand(String brand) {
        this.clientBrand = brand;
    }

    public String getClientBrand() {
        return this.clientBrand;
    }

    public StatFileWriter getStatFileWriter() {
        return this.statWriter;
    }

    public void addChatComponentMessage(IChatComponent chatComponent) {
        this.mc.ingameGUI.getChatGUI().printChatMessage(chatComponent);
    }

    private boolean isHeadspaceFree(BlockPos pos, int height) {
        return false;
    }

    public void moveFlying(float strafe, float forward, float friction) {
        float yaw = this.rotationYaw;
        EventStrafe event = new EventStrafe(forward, strafe, friction, yaw);
        EventManager.call(event);
        if (!event.cancelled) {
            strafe = event.strafe;
            forward = event.forward;
            friction = event.friction;
            yaw = event.yaw;
            float f = strafe * strafe + forward * forward;
            if (f >= 1.0E-4F) {
                f = MathHelper.sqrt_float(f);
                if (f < 1.0F) {
                    f = 1.0F;
                }

                f = friction / f;
                strafe *= f;
                forward *= f;
                float f1 = MathHelper.sin(yaw * 3.1415927F / 180.0F);
                float f2 = MathHelper.cos(yaw * 3.1415927F / 180.0F);
                this.motionX += (double) (strafe * f2 - forward * f1);
                this.motionZ += (double) (forward * f2 + strafe * f1);
            }

        }
    }

    public void moveEntity(double x, double y, double z) {
        EventMove event = new EventMove(x, y, z);
        EventManager.call(event);
        this.onMoveEntity(event.getX(), event.getY(), event.getZ());
    }

    public void onMoveEntity(double x, double y, double z) {
        if (this.noClip) {
            this.setEntityBoundingBox(this.getEntityBoundingBox().offset(x, y, z));
            this.posX = (this.getEntityBoundingBox().minX + this.getEntityBoundingBox().maxX) / 2.0;
            this.posY = this.getEntityBoundingBox().minY;
            this.posZ = (this.getEntityBoundingBox().minZ + this.getEntityBoundingBox().maxZ) / 2.0;
        } else {
            this.worldObj.theProfiler.startSection("move");
            double d0 = this.posX;
            double d1 = this.posY;
            double d2 = this.posZ;
            if (this.isInWeb) {
                this.isInWeb = false;
                x *= 0.25;
                y *= 0.05000000074505806;
                z *= 0.25;
                this.motionX = 0.0;
                this.motionY = 0.0;
                this.motionZ = 0.0;
            }

            EventSafeWalk event = new EventSafeWalk(false);
            EventManager.call(event);
            double d3 = x;
            double d4 = y;
            double d5 = z;
            boolean flag = this.onGround && this.isSneaking() && !ModManager.getModule("Scaffold").isEnabled() && this instanceof EntityPlayer;
            boolean safeMode = this.onGround && event.getSafe() && this instanceof EntityPlayer;
            if (flag || safeMode) {
                double d6;
                for (d6 = 0.05; x != 0.0 && this.worldObj.getCollidingBoundingBoxes(this, this.getEntityBoundingBox().offset(x, -1.0, 0.0)).isEmpty(); d3 = x) {
                    if (x < d6 && x >= -d6) {
                        x = 0.0;
                    } else if (x > 0.0) {
                        x -= d6;
                    } else {
                        x += d6;
                    }
                }

                for (; z != 0.0 && this.worldObj.getCollidingBoundingBoxes(this, this.getEntityBoundingBox().offset(0.0, -1.0, z)).isEmpty(); d5 = z) {
                    if (z < d6 && z >= -d6) {
                        z = 0.0;
                    } else if (z > 0.0) {
                        z -= d6;
                    } else {
                        z += d6;
                    }
                }

                for (; x != 0.0 && z != 0.0 && this.worldObj.getCollidingBoundingBoxes(this, this.getEntityBoundingBox().offset(x, -1.0, z)).isEmpty(); d5 = z) {
                    if (x < d6 && x >= -d6) {
                        x = 0.0;
                    } else if (x > 0.0) {
                        x -= d6;
                    } else {
                        x += d6;
                    }

                    d3 = x;
                    if (z < d6 && z >= -d6) {
                        z = 0.0;
                    } else if (z > 0.0) {
                        z -= d6;
                    } else {
                        z += d6;
                    }
                }
            }

            List list1 = this.worldObj.getCollidingBoundingBoxes(this, this.getEntityBoundingBox().addCoord(x, y, z));
            AxisAlignedBB axisalignedbb = this.getEntityBoundingBox();

            AxisAlignedBB axisalignedbb1;
            for (Iterator var24 = list1.iterator(); var24.hasNext(); y = axisalignedbb1.calculateYOffset(this.getEntityBoundingBox(), y)) {
                axisalignedbb1 = (AxisAlignedBB) var24.next();
            }

            this.setEntityBoundingBox(this.getEntityBoundingBox().offset(0.0, y, 0.0));
            boolean flag1 = this.onGround || d4 != y && d4 < 0.0;

            AxisAlignedBB axisalignedbb13;
            Iterator var58;
            for (var58 = list1.iterator(); var58.hasNext(); x = axisalignedbb13.calculateXOffset(this.getEntityBoundingBox(), x)) {
                axisalignedbb13 = (AxisAlignedBB) var58.next();
            }

            this.setEntityBoundingBox(this.getEntityBoundingBox().offset(x, 0.0, 0.0));

            for (var58 = list1.iterator(); var58.hasNext(); z = axisalignedbb13.calculateZOffset(this.getEntityBoundingBox(), z)) {
                axisalignedbb13 = (AxisAlignedBB) var58.next();
            }

            this.setEntityBoundingBox(this.getEntityBoundingBox().offset(0.0, 0.0, z));
            double d8;
            if (this.stepHeight > 0.0F && flag1 && (d3 != x || d5 != z)) {
                EventStep es = new EventStep(EventType.PRE, this.stepHeight);
                EventManager.call(es);
                double d11 = x;
                double d7 = y;
                d8 = z;
                AxisAlignedBB axisalignedbb3 = this.getEntityBoundingBox();
                this.setEntityBoundingBox(axisalignedbb);
                y = (double) this.stepHeight;
                List list = this.worldObj.getCollidingBoundingBoxes(this, this.getEntityBoundingBox().addCoord(d3, y, d5));
                AxisAlignedBB axisalignedbb4 = this.getEntityBoundingBox();
                AxisAlignedBB axisalignedbb5 = axisalignedbb4.addCoord(d3, 0.0, d5);
                double d9 = y;

                AxisAlignedBB axisalignedbb6;
                for (Iterator var38 = list.iterator(); var38.hasNext(); d9 = axisalignedbb6.calculateYOffset(axisalignedbb5, d9)) {
                    axisalignedbb6 = (AxisAlignedBB) var38.next();
                }

                axisalignedbb4 = axisalignedbb4.offset(0.0, d9, 0.0);
                double d15 = d3;

                AxisAlignedBB axisalignedbb7;
                for (Iterator var40 = list.iterator(); var40.hasNext(); d15 = axisalignedbb7.calculateXOffset(axisalignedbb4, d15)) {
                    axisalignedbb7 = (AxisAlignedBB) var40.next();
                }

                axisalignedbb4 = axisalignedbb4.offset(d15, 0.0, 0.0);
                double d16 = d5;

                AxisAlignedBB axisalignedbb8;
                for (Iterator var42 = list.iterator(); var42.hasNext(); d16 = axisalignedbb8.calculateZOffset(axisalignedbb4, d16)) {
                    axisalignedbb8 = (AxisAlignedBB) var42.next();
                }

                axisalignedbb4 = axisalignedbb4.offset(0.0, 0.0, d16);
                AxisAlignedBB axisalignedbb14 = this.getEntityBoundingBox();
                double d17 = y;

                AxisAlignedBB axisalignedbb9;
                for (Iterator var45 = list.iterator(); var45.hasNext(); d17 = axisalignedbb9.calculateYOffset(axisalignedbb14, d17)) {
                    axisalignedbb9 = (AxisAlignedBB) var45.next();
                }

                axisalignedbb14 = axisalignedbb14.offset(0.0, d17, 0.0);
                double d18 = d3;

                AxisAlignedBB axisalignedbb10;
                for (Iterator var47 = list.iterator(); var47.hasNext(); d18 = axisalignedbb10.calculateXOffset(axisalignedbb14, d18)) {
                    axisalignedbb10 = (AxisAlignedBB) var47.next();
                }

                axisalignedbb14 = axisalignedbb14.offset(d18, 0.0, 0.0);
                double d19 = d5;

                AxisAlignedBB axisalignedbb11;
                for (Iterator var49 = list.iterator(); var49.hasNext(); d19 = axisalignedbb11.calculateZOffset(axisalignedbb14, d19)) {
                    axisalignedbb11 = (AxisAlignedBB) var49.next();
                }

                axisalignedbb14 = axisalignedbb14.offset(0.0, 0.0, d19);
                double d20 = d15 * d15 + d16 * d16;
                double d10 = d18 * d18 + d19 * d19;
                if (d20 > d10) {
                    x = d15;
                    z = d16;
                    y = -d9;
                    this.setEntityBoundingBox(axisalignedbb4);
                } else {
                    x = d18;
                    z = d19;
                    y = -d17;
                    this.setEntityBoundingBox(axisalignedbb14);
                }

                AxisAlignedBB axisalignedbb12;
                for (Iterator var53 = list.iterator(); var53.hasNext(); y = axisalignedbb12.calculateYOffset(this.getEntityBoundingBox(), y)) {
                    axisalignedbb12 = (AxisAlignedBB) var53.next();
                }

                this.setEntityBoundingBox(this.getEntityBoundingBox().offset(0.0, y, 0.0));
                if (d11 * d11 + d8 * d8 >= x * x + z * z) {
                    x = d11;
                    y = d7;
                    z = d8;
                    this.setEntityBoundingBox(axisalignedbb3);
                } else {
                    EventStep post = new EventStep(EventType.POST, this.stepHeight);
                    EventManager.call(post);
                }
            }

            this.worldObj.theProfiler.endSection();
            this.worldObj.theProfiler.startSection("rest");
            this.posX = (this.getEntityBoundingBox().minX + this.getEntityBoundingBox().maxX) / 2.0;
            this.posY = this.getEntityBoundingBox().minY;
            this.posZ = (this.getEntityBoundingBox().minZ + this.getEntityBoundingBox().maxZ) / 2.0;
            this.isCollidedHorizontally = d3 != x || d5 != z;
            this.isCollidedVertically = d4 != y;
            this.onGround = this.isCollidedVertically && d4 < 0.0;
            this.isCollided = this.isCollidedHorizontally || this.isCollidedVertically;
            int i = MathHelper.floor_double(this.posX);
            int j = MathHelper.floor_double(this.posY - 0.20000000298023224);
            int k = MathHelper.floor_double(this.posZ);
            BlockPos blockpos = new BlockPos(i, j, k);
            Block block1 = this.worldObj.getBlockState(blockpos).getBlock();
            if (block1.getMaterial() == Material.air) {
                Block block = this.worldObj.getBlockState(blockpos.down()).getBlock();
                if (block instanceof BlockFence || block instanceof BlockWall || block instanceof BlockFenceGate) {
                    block1 = block;
                    blockpos = blockpos.down();
                }
            }

            this.updateFallState(y, this.onGround, block1, blockpos);
            if (d3 != x) {
                this.motionX = 0.0;
            }

            if (d5 != z) {
                this.motionZ = 0.0;
            }

            if (d4 != y) {
                block1.onLanded(this.worldObj, this);
            }

            if (this.canTriggerWalking() && !flag && this.ridingEntity == null) {
                d8 = this.posX - d0;
                double d13 = this.posY - d1;
                double d14 = this.posZ - d2;
                if (block1 != Blocks.ladder) {
                    d13 = 0.0;
                }

                if (block1 != null && this.onGround) {
                    block1.onEntityCollidedWithBlock(this.worldObj, blockpos, this);
                }

                this.distanceWalkedModified = (float) ((double) this.distanceWalkedModified + (double) MathHelper.sqrt_double(d8 * d8 + d14 * d14) * 0.6);
                this.distanceWalkedOnStepModified = (float) ((double) this.distanceWalkedOnStepModified + (double) MathHelper.sqrt_double(d8 * d8 + d13 * d13 + d14 * d14) * 0.6);
                if (this.distanceWalkedOnStepModified > (float) ((Entity) this).getNextStepDistance() && block1.getMaterial() != Material.air) {
                    ((Entity) this).setNextStepDistance((int) this.distanceWalkedOnStepModified + 1);
                    if (this.isInWater()) {
                        float f = MathHelper.sqrt_double(this.motionX * this.motionX * 0.20000000298023224 + this.motionY * this.motionY + this.motionZ * this.motionZ * 0.20000000298023224) * 0.35F;
                        if (f > 1.0F) {
                            f = 1.0F;
                        }

                        this.playSound(this.getSwimSound(), f, 1.0F + (this.rand.nextFloat() - this.rand.nextFloat()) * 0.4F);
                    }

                    this.playStepSound(blockpos, block1);
                }
            }

            try {
                this.doBlockCollisions();
            } catch (Throwable var55) {
                CrashReport crashreport = CrashReport.makeCrashReport(var55, "Checking entity block collision");
                CrashReportCategory crashreportcategory = crashreport.makeCategory("Entity being checked for collision");
                this.addEntityCrashInfo(crashreportcategory);
                throw new ReportedException(crashreport);
            }

            boolean flag2 = this.isWet();
            Entity ent = this;
            if (this.worldObj.isFlammableWithin(this.getEntityBoundingBox().contract(0.001, 0.001, 0.001))) {
                this.dealFireDamage(1);
                if (!flag2) {
                    ent.setFire(ent.getFire() + 1);
                    if (ent.getFire() == 0) {
                        this.setFire(8);
                    }
                }
            } else if (ent.getFire() <= 0) {
                ent.setFire(-this.fireResistance);
            }

            if (flag2 && ent.getFire() > 0) {
                this.playSound("random.fizz", 0.7F, 1.6F + (this.rand.nextFloat() - this.rand.nextFloat()) * 0.4F);
                ent.setFire(-this.fireResistance);
            }

            this.worldObj.theProfiler.endSection();
        }

    }

    public void jump() {
        EventJump jumpEvent = new EventJump(this.rotationYaw);
        EventManager.call(jumpEvent);
        if (!jumpEvent.isCancelled()) {
            this.motionY = (double) this.getJumpUpwardsMotion();
            if (this.isPotionActive(Potion.jump)) {
                this.motionY += (double) ((float) (this.getActivePotionEffect(Potion.jump).getAmplifier() + 1) * 0.1F);
            }

            if (this.isSprinting()) {
                float f = jumpEvent.yaw * 0.017453292F;
                this.motionX -= (double) (MathHelper.sin(f) * 0.2F);
                this.motionZ += (double) (MathHelper.cos(f) * 0.2F);
            }

            this.isAirBorne = true;
            this.triggerAchievement(StatList.jumpStat);
        }
    }

    public boolean moving() {
        return (double) this.moveForward > 0.0 | (double) this.moveStrafing > 0.0;
    }

    public float getSpeed() {
        return (float) Math.sqrt(this.motionX * this.motionX + this.motionZ * this.motionZ);
    }

    public void setSpeed(double speed) {
        this.motionX = (double) (-MathHelper.sin(this.getDirection())) * speed;
        this.motionZ = (double) MathHelper.cos(this.getDirection()) * speed;
    }

    public float getDirection() {
        float yaw = this.rotationYaw;
        float forward = this.moveForward;
        float strafe = this.moveStrafing;
        yaw += (float) (forward < 0.0F ? 180 : 0);
        if (strafe < 0.0F) {
            yaw += forward < 0.0F ? -45.0F : (forward == 0.0F ? 90.0F : 45.0F);
        }

        if (strafe > 0.0F) {
            yaw -= forward < 0.0F ? -45.0F : (forward == 0.0F ? 90.0F : 45.0F);
        }

        return yaw * 0.017453292F;
    }

    public void setYaw(double yaw) {
        this.rotationYaw = (float) yaw;
    }

    public void setPitch(double pitch) {
        this.rotationPitch = (float) pitch;
    }

    public void setMoveSpeed(EventMove event, double speed) {
        double forward = (double) this.movementInput.moveForward;
        double strafe = (double) this.movementInput.moveStrafe;
        float yaw = this.rotationYaw;
        if (forward == 0.0 && strafe == 0.0) {
            event.setX(0.0);
            event.setZ(0.0);
        } else {
            if (forward != 0.0) {
                if (strafe > 0.0) {
                    yaw += (float) (forward > 0.0 ? -45 : 45);
                } else if (strafe < 0.0) {
                    yaw += (float) (forward > 0.0 ? 45 : -45);
                }

                strafe = 0.0;
                if (forward > 0.0) {
                    forward = 1.0;
                } else if (forward < 0.0) {
                    forward = -1.0;
                }
            }

            event.setX(forward * speed * Math.cos(Math.toRadians((double) (yaw + 90.0F))) + strafe * speed * Math.sin(Math.toRadians((double) (yaw + 90.0F))));
            event.setZ(forward * speed * Math.sin(Math.toRadians((double) (yaw + 90.0F))) - strafe * speed * Math.cos(Math.toRadians((double) (yaw + 90.0F))));
        }

    }

    public void setLastReportedPosY(double f) {
        this.lastReportedPosY = f;
    }

    public float getCollisionBorderSize() {
        Hitbox hitBox = (Hitbox) ModManager.getModule(Hitbox.class);
        return hitBox.isEnabled() ? Hitbox.getSize() : super.getCollisionBorderSize();
    }

    public void attackTargetEntityWithCurrentItem(Entity targetEntity) {
        ForgeHooks.getClass();
        //if (ForgeHooks.onPlayerAttackTarget(this, targetEntity)) {
        if (targetEntity.canAttackWithItem() && !targetEntity.hitByEntity(this)) {
            float f = (float) this.getEntityAttribute(SharedMonsterAttributes.attackDamage).getAttributeValue();
            int i = 0;
            float f1 = 0.0F;
            if (targetEntity instanceof EntityLivingBase) {
                f1 = EnchantmentHelper.getModifierForCreature(this.getHeldItem(), ((EntityLivingBase) targetEntity).getCreatureAttribute());
            } else {
                f1 = EnchantmentHelper.getModifierForCreature(this.getHeldItem(), EnumCreatureAttribute.UNDEFINED);
            }

            i += EnchantmentHelper.getKnockbackModifier(this);
            if (this.isSprinting()) {
                ++i;
            }

            if (f > 0.0F || f1 > 0.0F) {
                boolean flag = this.fallDistance > 0.0F && !this.onGround && !this.isOnLadder() && !this.isInWater() && !this.isPotionActive(Potion.blindness) && this.ridingEntity == null && targetEntity instanceof EntityLivingBase;
                if (flag && f > 0.0F) {
                    f *= 1.5F;
                }

                f += f1;
                boolean flag1 = false;
                int j = EnchantmentHelper.getFireAspectModifier(this);
                if (targetEntity instanceof EntityLivingBase && j > 0 && !targetEntity.isBurning()) {
                    flag1 = true;
                    targetEntity.setFire(1);
                }

                double d0 = targetEntity.motionX;
                double d1 = targetEntity.motionY;
                double d2 = targetEntity.motionZ;
                boolean flag2 = targetEntity.attackEntityFrom(DamageSource.causePlayerDamage(this), f);
                if (flag2) {
                    if (i > 0) {
                        targetEntity.addVelocity((double) (-MathHelper.sin(this.rotationYaw * 3.1415927F / 180.0F) * (float) i * 0.5F), 0.1, (double) (MathHelper.cos(this.rotationYaw * 3.1415927F / 180.0F) * (float) i * 0.5F));
                        if (!((KeepSprint) ModManager.getModule(KeepSprint.class)).getState()) {
                            this.motionX *= 0.6;
                            this.motionZ *= 0.6;
                            this.setSprinting(false);
                        }
                    }

                    if (targetEntity instanceof EntityPlayerMP && targetEntity.velocityChanged) {
                        ((EntityPlayerMP) targetEntity).playerNetServerHandler.sendPacket(new S12PacketEntityVelocity(targetEntity));
                        targetEntity.velocityChanged = false;
                        targetEntity.motionX = d0;
                        targetEntity.motionY = d1;
                        targetEntity.motionZ = d2;
                    }

                    if (flag) {
                        this.onCriticalHit(targetEntity);
                    }

                    if (f1 > 0.0F) {
                        this.onEnchantmentCritical(targetEntity);
                    }

                    if (f >= 18.0F) {
                        this.triggerAchievement(AchievementList.overkill);
                    }

                    this.setLastAttacker(targetEntity);
                    if (targetEntity instanceof EntityLivingBase) {
                        EnchantmentHelper.applyThornEnchantments((EntityLivingBase) targetEntity, this);
                    }

                    EnchantmentHelper.applyArthropodEnchantments(this, targetEntity);
                    ItemStack itemstack = this.getCurrentEquippedItem();
                    Entity entity = targetEntity;
                    if (targetEntity instanceof EntityDragonPart) {
                        IEntityMultiPart ientitymultipart = ((EntityDragonPart) targetEntity).entityDragonObj;
                        if (ientitymultipart instanceof EntityLivingBase) {
                            entity = (EntityLivingBase) ientitymultipart;
                        }
                    }

                    if (itemstack != null && entity instanceof EntityLivingBase) {
                        itemstack.hitEntity((EntityLivingBase) entity, this);
                        if (itemstack.stackSize <= 0) {
                            this.destroyCurrentEquippedItem();
                        }
                    }

                    if (targetEntity instanceof EntityLivingBase) {
                        this.addStat(StatList.damageDealtStat, Math.round(f * 10.0F));
                        if (j > 0) {
                            targetEntity.setFire(j * 4);
                        }
                    }

                    this.addExhaustion(0.3F);
                } else if (flag1) {
                    targetEntity.extinguish();
                }
            }
        }
        //}
    }

    protected boolean pushOutOfBlocks(double x, double y, double z) {
        EventPushBlock event = new EventPushBlock();
        EventManager.call(event);
        if (!this.noClip && !event.isCancelled()) {
            BlockPos blockpos = new BlockPos(x, y, z);
            double d0 = x - (double) blockpos.getX();
            double d1 = z - (double) blockpos.getZ();
            int entHeight = Math.max((int) Math.ceil((double) this.height), 1);
            boolean inTranslucentBlock = !this.isHeadspaceFree(blockpos, entHeight);
            if (inTranslucentBlock) {
                int i = -1;
                double d2 = 9999.0;
                if (this.isHeadspaceFree(blockpos.west(), entHeight) && d0 < d2) {
                    d2 = d0;
                    i = 0;
                }

                if (this.isHeadspaceFree(blockpos.east(), entHeight) && 1.0 - d0 < d2) {
                    d2 = 1.0 - d0;
                    i = 1;
                }

                if (this.isHeadspaceFree(blockpos.north(), entHeight) && d1 < d2) {
                    d2 = d1;
                    i = 4;
                }

                if (this.isHeadspaceFree(blockpos.south(), entHeight) && 1.0 - d1 < d2) {
                    d2 = 1.0 - d1;
                    i = 5;
                }

                float f = 0.1F;
                if (i == 0) {
                    this.motionX = (double) (-f);
                }

                if (i == 1) {
                    this.motionX = (double) f;
                }

                if (i == 4) {
                    this.motionZ = (double) (-f);
                }

                if (i == 5) {
                    this.motionZ = (double) f;
                }
            }

            return false;
        } else {
            return false;
        }
    }

    /**
     * Returns true if the block at the given BlockPos and the block above it are NOT full cubes.
     */
    private boolean isOpenBlockSpace(BlockPos pos) {
        return !this.worldObj.getBlockState(pos).getBlock().isNormalCube() && !this.worldObj.getBlockState(pos.up()).getBlock().isNormalCube();
    }

    /**
     * Set sprinting switch for Entity.
     */
    public void setSprinting(boolean sprinting) {
        super.setSprinting(sprinting);
        this.sprintingTicksLeft = sprinting ? 600 : 0;
    }

    /**
     * Sets the current XP, total XP, and level number.
     */
    public void setXPStats(float currentXP, int maxXP, int level) {
        this.experience = currentXP;
        this.experienceTotal = maxXP;
        this.experienceLevel = level;
    }

    /**
     * Send a chat message to the CommandSender
     */
    public void addChatMessage(IChatComponent component) {
        this.mc.ingameGUI.getChatGUI().printChatMessage(component);
    }

    /**
     * Returns {@code true} if the CommandSender is allowed to execute the command, {@code false} if not
     */
    public boolean canCommandSenderUseCommand(int permLevel, String commandName) {
        return permLevel <= 0;
    }

    /**
     * Get the position in the world. <b>{@code null} is not allowed!</b> If you are not an entity in the world, return
     * the coordinates 0, 0, 0
     */
    public BlockPos getPosition() {
        return new BlockPos(this.posX + 0.5D, this.posY + 0.5D, this.posZ + 0.5D);
    }

    public void playSound(String name, float volume, float pitch) {
        this.worldObj.playSound(this.posX, this.posY, this.posZ, name, volume, pitch, false);
    }

    /**
     * Returns whether the entity is in a server world
     */
    public boolean isServerWorld() {
        return true;
    }

    public boolean isRidingHorse() {
        return this.ridingEntity != null && this.ridingEntity instanceof EntityHorse && ((EntityHorse) this.ridingEntity).isHorseSaddled();
    }

    public float getHorseJumpPower() {
        return this.horseJumpPower;
    }

    public void openEditSign(TileEntitySign signTile) {
        this.mc.displayGuiScreen(new GuiEditSign(signTile));
    }

    public void openEditCommandBlock(CommandBlockLogic cmdBlockLogic) {
        this.mc.displayGuiScreen(new GuiCommandBlock(cmdBlockLogic));
    }

    /**
     * Displays the GUI for interacting with a book.
     */
    public void displayGUIBook(ItemStack bookStack) {
        Item item = bookStack.getItem();

        if (item == Items.writable_book) {
            this.mc.displayGuiScreen(new GuiScreenBook(this, bookStack, true));
        }
    }

    /**
     * Displays the GUI for interacting with a chest inventory. Args: chestInventory
     */
    public void displayGUIChest(IInventory chestInventory) {
        String s = chestInventory instanceof IInteractionObject ? ((IInteractionObject) chestInventory).getGuiID() : "minecraft:container";

        if ("minecraft:chest".equals(s)) {
            this.mc.displayGuiScreen(new GuiChest(this.inventory, chestInventory));
        } else if ("minecraft:hopper".equals(s)) {
            this.mc.displayGuiScreen(new GuiHopper(this.inventory, chestInventory));
        } else if ("minecraft:furnace".equals(s)) {
            this.mc.displayGuiScreen(new GuiFurnace(this.inventory, chestInventory));
        } else if ("minecraft:brewing_stand".equals(s)) {
            this.mc.displayGuiScreen(new GuiBrewingStand(this.inventory, chestInventory));
        } else if ("minecraft:beacon".equals(s)) {
            this.mc.displayGuiScreen(new GuiBeacon(this.inventory, chestInventory));
        } else if (!"minecraft:dispenser".equals(s) && !"minecraft:dropper".equals(s)) {
            this.mc.displayGuiScreen(new GuiChest(this.inventory, chestInventory));
        } else {
            this.mc.displayGuiScreen(new GuiDispenser(this.inventory, chestInventory));
        }
    }

    public void displayGUIHorse(EntityHorse horse, IInventory horseInventory) {
        this.mc.displayGuiScreen(new GuiScreenHorseInventory(this.inventory, horseInventory, horse));
    }

    public void displayGui(IInteractionObject guiOwner) {
        String s = guiOwner.getGuiID();

        if ("minecraft:crafting_table".equals(s)) {
            this.mc.displayGuiScreen(new GuiCrafting(this.inventory, this.worldObj));
        } else if ("minecraft:enchanting_table".equals(s)) {
            this.mc.displayGuiScreen(new GuiEnchantment(this.inventory, this.worldObj, guiOwner));
        } else if ("minecraft:anvil".equals(s)) {
            this.mc.displayGuiScreen(new GuiRepair(this.inventory, this.worldObj));
        }
    }

    public void displayVillagerTradeGui(IMerchant villager) {
        this.mc.displayGuiScreen(new GuiMerchant(this.inventory, villager, this.worldObj));
    }

    /**
     * Called when the player performs a critical hit on the Entity. Args: entity that was hit critically
     */
    public void onCriticalHit(Entity entityHit) {
        this.mc.effectRenderer.emitParticleAtEntity(entityHit, EnumParticleTypes.CRIT);
    }

    public void onEnchantmentCritical(Entity entityHit) {
        this.mc.effectRenderer.emitParticleAtEntity(entityHit, EnumParticleTypes.CRIT_MAGIC);
    }

    /**
     * Returns if this entity is sneaking.
     */
    public boolean isSneaking() {
        boolean flag = this.movementInput != null ? this.movementInput.sneak : false;
        return flag && !this.sleeping;
    }

    public void updateEntityActionState() {
        super.updateEntityActionState();

        if (this.isCurrentViewEntity()) {
            this.moveStrafing = this.movementInput.moveStrafe;
            this.moveForward = this.movementInput.moveForward;
            this.isJumping = this.movementInput.jump;
            this.prevRenderArmYaw = this.renderArmYaw;
            this.prevRenderArmPitch = this.renderArmPitch;
            this.renderArmPitch = (float) ((double) this.renderArmPitch + (double) (this.rotationPitch - this.renderArmPitch) * 0.5D);
            this.renderArmYaw = (float) ((double) this.renderArmYaw + (double) (this.rotationYaw - this.renderArmYaw) * 0.5D);
        }
    }

    protected boolean isCurrentViewEntity() {
        return this.mc.getRenderViewEntity() == this;
    }

    private float cacheStrafe = 0.0F;
    private float cacheForward = 0.0F;

    public final boolean isSlow() {
        return !Minecraft.getMinecraft().thePlayer.isUsingItem() || Minecraft.getMinecraft().thePlayer.isRiding();
    }

    /**
     * Called frequently so the entity can update its state every tick as required. For example, zombies and skeletons
     * use this to react to sunlight and start to burn.
     */
    public void onLivingUpdate() {
        if (this.sprintingTicksLeft > 0) {
            --this.sprintingTicksLeft;

            if (this.sprintingTicksLeft == 0) {
                this.setSprinting(false);
            }
        }

        if (this.sprintToggleTimer > 0) {
            --this.sprintToggleTimer;
        }

        this.prevTimeInPortal = this.timeInPortal;

        if (this.inPortal) {
            if (this.mc.currentScreen != null && !this.mc.currentScreen.doesGuiPauseGame()) {
                this.mc.displayGuiScreen((GuiScreen) null);
            }

            if (this.timeInPortal == 0.0F) {
                this.mc.getSoundHandler().playSound(PositionedSoundRecord.create(new ResourceLocation("portal.trigger"), this.rand.nextFloat() * 0.4F + 0.8F));
            }

            this.timeInPortal += 0.0125F;

            if (this.timeInPortal >= 1.0F) {
                this.timeInPortal = 1.0F;
            }

            this.inPortal = false;
        } else if (this.isPotionActive(Potion.confusion) && this.getActivePotionEffect(Potion.confusion).getDuration() > 60) {
            this.timeInPortal += 0.006666667F;

            if (this.timeInPortal > 1.0F) {
                this.timeInPortal = 1.0F;
            }
        } else {
            if (this.timeInPortal > 0.0F) {
                this.timeInPortal -= 0.05F;
            }

            if (this.timeInPortal < 0.0F) {
                this.timeInPortal = 0.0F;
            }
        }

        if (this.timeUntilPortal > 0) {
            --this.timeUntilPortal;
        }

        boolean flag = this.movementInput.jump;
        boolean flag1 = this.movementInput.sneak;
        float f = 0.8F;
        boolean flag2 = this.movementInput.moveForward >= f;
        this.movementInput.updatePlayerMoveState();
        if (!this.isSlow()) {
            if (ModManager.getModule(NoSlow.class).isEnabled()) {
                this.cacheStrafe = Minecraft.getMinecraft().thePlayer.movementInput.moveStrafe;
                this.cacheForward = Minecraft.getMinecraft().thePlayer.movementInput.moveForward;
            }
        }

        if (this.isUsingItem() && !this.isRiding()) {
            this.movementInput.moveStrafe *= 0.2F;
            this.movementInput.moveForward *= 0.2F;
            this.sprintToggleTimer = 0;
        }

        if (!this.isSlow()) {
            if (ModManager.getModule(NoSlow.class).isEnabled()) {
                Minecraft.getMinecraft().thePlayer.movementInput.moveStrafe = this.cacheStrafe;
                Minecraft.getMinecraft().thePlayer.movementInput.moveForward = this.cacheForward;
            }
        }
        this.pushOutOfBlocks(this.posX - (double) this.width * 0.35D, this.getEntityBoundingBox().minY + 0.5D, this.posZ + (double) this.width * 0.35D);
        this.pushOutOfBlocks(this.posX - (double) this.width * 0.35D, this.getEntityBoundingBox().minY + 0.5D, this.posZ - (double) this.width * 0.35D);
        this.pushOutOfBlocks(this.posX + (double) this.width * 0.35D, this.getEntityBoundingBox().minY + 0.5D, this.posZ - (double) this.width * 0.35D);
        this.pushOutOfBlocks(this.posX + (double) this.width * 0.35D, this.getEntityBoundingBox().minY + 0.5D, this.posZ + (double) this.width * 0.35D);
        boolean flag3 = (float) this.getFoodStats().getFoodLevel() > 6.0F || this.capabilities.allowFlying;

        if (this.onGround && !flag1 && !flag2 && this.movementInput.moveForward >= f && !this.isSprinting() && flag3 && !this.isUsingItem() && !this.isPotionActive(Potion.blindness)) {
            if (this.sprintToggleTimer <= 0 && !this.mc.gameSettings.keyBindSprint.isKeyDown()) {
                this.sprintToggleTimer = 7;
            } else {
                this.setSprinting(true);
            }
        }

        if (!this.isSprinting() && this.movementInput.moveForward >= f && flag3 && !this.isUsingItem() && !this.isPotionActive(Potion.blindness) && this.mc.gameSettings.keyBindSprint.isKeyDown()) {
            this.setSprinting(true);
        }

        if (this.isSprinting() && (this.movementInput.moveForward < f || this.isCollidedHorizontally || !flag3)) {
            this.setSprinting(false);
        }

        if (this.capabilities.allowFlying) {
            if (this.mc.playerController.isSpectatorMode()) {
                if (!this.capabilities.isFlying) {
                    this.capabilities.isFlying = true;
                    this.sendPlayerAbilities();
                }
            } else if (!flag && this.movementInput.jump) {
                if (this.flyToggleTimer == 0) {
                    this.flyToggleTimer = 7;
                } else {
                    this.capabilities.isFlying = !this.capabilities.isFlying;
                    this.sendPlayerAbilities();
                    this.flyToggleTimer = 0;
                }
            }
        }

        if (this.capabilities.isFlying && this.isCurrentViewEntity()) {
            if (this.movementInput.sneak) {
                this.motionY -= (double) (this.capabilities.getFlySpeed() * 3.0F);
            }

            if (this.movementInput.jump) {
                this.motionY += (double) (this.capabilities.getFlySpeed() * 3.0F);
            }
        }

        if (this.isRidingHorse()) {
            if (this.horseJumpPowerCounter < 0) {
                ++this.horseJumpPowerCounter;

                if (this.horseJumpPowerCounter == 0) {
                    this.horseJumpPower = 0.0F;
                }
            }

            if (flag && !this.movementInput.jump) {
                this.horseJumpPowerCounter = -10;
                this.sendHorseJump();
            } else if (!flag && this.movementInput.jump) {
                this.horseJumpPowerCounter = 0;
                this.horseJumpPower = 0.0F;
            } else if (flag) {
                ++this.horseJumpPowerCounter;

                if (this.horseJumpPowerCounter < 10) {
                    this.horseJumpPower = (float) this.horseJumpPowerCounter * 0.1F;
                } else {
                    this.horseJumpPower = 0.8F + 2.0F / (float) (this.horseJumpPowerCounter - 9) * 0.1F;
                }
            }
        } else {
            this.horseJumpPower = 0.0F;
        }

        super.onLivingUpdate();

        if (this.onGround && this.capabilities.isFlying && !this.mc.playerController.isSpectatorMode()) {
            this.capabilities.isFlying = false;
            this.sendPlayerAbilities();
        }
    }
}
