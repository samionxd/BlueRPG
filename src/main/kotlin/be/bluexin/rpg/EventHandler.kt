/*
 * Copyright (C) 2019.  Arnaud 'Bluexin' Solé
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

@file:Suppress("unused")

package be.bluexin.rpg

import be.bluexin.rpg.classes.PlayerClass
import be.bluexin.rpg.classes.PlayerClassRegistry
import be.bluexin.rpg.classes.playerClass
import be.bluexin.rpg.devutil.Textures
import be.bluexin.rpg.devutil.get
import be.bluexin.rpg.events.*
import be.bluexin.rpg.extensions.RPGCombatTracker
import be.bluexin.rpg.gear.*
import be.bluexin.rpg.inventory.RPGEnderChestContainer
import be.bluexin.rpg.inventory.RPGInventory
import be.bluexin.rpg.inventory.RPGInventoryContainer
import be.bluexin.rpg.inventory.RpgInventoryGui
import be.bluexin.rpg.pets.EggItem
import be.bluexin.rpg.pets.RenderEggItem
import be.bluexin.rpg.pets.eggData
import be.bluexin.rpg.pets.petStorage
import be.bluexin.rpg.skills.*
import be.bluexin.rpg.stats.*
import be.bluexin.rpg.utilities.DynItem
import be.bluexin.rpg.utilities.dynamicData
import be.bluexin.saomclib.onServer
import com.saomc.saoui.GLCore
import com.teamwizardry.librarianlib.features.config.ConfigDoubleRange
import com.teamwizardry.librarianlib.features.config.ConfigProperty
import com.teamwizardry.librarianlib.features.container.GuiHandler
import com.teamwizardry.librarianlib.features.container.internal.ContainerImpl
import com.teamwizardry.librarianlib.features.kotlin.Client
import com.teamwizardry.librarianlib.features.network.PacketHandler
import net.minecraft.block.Block
import net.minecraft.client.gui.Gui
import net.minecraft.client.gui.ScaledResolution
import net.minecraft.client.gui.inventory.GuiInventory
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.client.renderer.color.IItemColor
import net.minecraft.client.renderer.tileentity.TileEntityItemStackRenderer
import net.minecraft.entity.EntityLivingBase
import net.minecraft.entity.item.EntityPainting
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.inventory.ContainerPlayer
import net.minecraft.inventory.EntityEquipmentSlot
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagByte
import net.minecraft.util.ResourceLocation
import net.minecraft.util.math.BlockPos
import net.minecraft.util.text.TextComponentString
import net.minecraftforge.client.event.*
import net.minecraftforge.common.util.FakePlayer
import net.minecraftforge.event.RegistryEvent
import net.minecraftforge.event.ServerChatEvent
import net.minecraftforge.event.entity.EntityEvent
import net.minecraftforge.event.entity.EntityJoinWorldEvent
import net.minecraftforge.event.entity.living.*
import net.minecraftforge.event.entity.player.CriticalHitEvent
import net.minecraftforge.event.entity.player.EntityItemPickupEvent
import net.minecraftforge.event.entity.player.PlayerInteractEvent
import net.minecraftforge.event.entity.player.PlayerPickupXpEvent
import net.minecraftforge.event.world.BlockEvent
import net.minecraftforge.fml.client.config.GuiUtils.drawTexturedModalRect
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.common.eventhandler.Event
import net.minecraftforge.fml.common.eventhandler.EventPriority
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.gameevent.TickEvent
import net.minecraftforge.fml.relauncher.Side
import net.minecraftforge.fml.relauncher.SideOnly
import org.lwjgl.opengl.GL11
import kotlin.math.min

// TODO: Split by feature
@Mod.EventBusSubscriber(modid = BlueRPG.MODID)
object CommonEventHandler {

    @ConfigDoubleRange(.0, java.lang.Double.MAX_VALUE)
    @ConfigProperty("general", "Pet mount speed bonus")
    var petMountSpeedBonus = 50.0
        internal set

    @SubscribeEvent
    @JvmStatic
    fun playerTick(event: TickEvent.PlayerTickEvent) {
        if (event.phase != TickEvent.Phase.END) return
        event.player.cooldowns.tick()
        event.player.world onServer {
            val stats = event.player.stats
            if (stats.dirty) stats.sync()
            if (event.player.ticksExisted > 10) {
                if (event.player.health > event.player.maxHealth) event.player.health = event.player.maxHealth
                if (event.player.mana > event.player.maxMana) event.player.mana = event.player.maxMana
            }
            val time = event.player.world.totalWorldTime
            if (time % 10 == 0L) {
                event.player.playerClass.iterator().asSequence().mapNotNull { SkillRegistry[it.key] to it.value }
                    .filter { (skill, _) -> skill?.passive == true }
                    .forEach { (skill, level) ->
                        skill?.processor?.cast(SkillContext(event.player, level))
                    }
                if (time % 100 == 0L) {
                    val combat = event.player.combatTracker.inCombat
                    event.player.heal((event.player[SecondaryStat.REGEN] * if (combat) 0.2 else 1.0).toFloat())
                    event.player.mana += ((event.player[SecondaryStat.SPIRIT] * if (combat) 0.2 else 1.0).toFloat()) // TODO: event
                }
            }

        }
    }

    @SubscribeEvent
    @JvmStatic
    fun combatEvent(event: CombatEvent) {
        val e = event.entityLiving
        if (event.action == CombatEvent.Action.START && e is EntityPlayer) e.petStorage.killPet()
    }

    @SubscribeEvent
    @JvmStatic
    fun skillCastEvent(event: SkillEvent.Cast) {
        if (event.skill.trigger != Passive) (event.entityLiving.combatTracker as RPGCombatTracker).enterCombat()
    }

    @SubscribeEvent
    @JvmStatic
    fun changeGear(event: LivingEquipmentChangeEvent) {
        val p = event.entityLiving as? EntityPlayer ?: return

        @Suppress("NON_EXHAUSTIVE_WHEN")
        when (event.slot) {
            EntityEquipmentSlot.MAINHAND -> {
                val offHand = p.heldItemOffhand
                if (!offHand.isEmpty) {
                    val from = (event.from.item as? IRPGGear)?.type as? WeaponType
                    val to = (event.to.item as? IRPGGear)?.type as? WeaponType
                    if (to?.twoHander == true && from?.twoHander != true) {
                        offHand.setTagInfo("bluerpg:twohandflag", NBTTagByte(1))
                    } else if (from?.twoHander == true && to?.twoHander != true) {
                        offHand.setTagInfo("bluerpg:twohandflag", NBTTagByte(0))
                    }
                }
            }
            EntityEquipmentSlot.OFFHAND -> {
                val mainhand = (p.heldItemMainhand.item as? IRPGGear)?.type as? WeaponType
                val to = event.to
                if (to.item is IRPGGear) to.setTagInfo(
                    "bluerpg:twohandflag",
                    NBTTagByte(if (mainhand?.twoHander == true) 1 else 0)
                )
            }
        }
    }

    @SubscribeEvent
    @JvmStatic
    fun postChangeGear(event: LivingEquipmentPostChangeEvent) {
        (event.entityLiving as? EntityPlayer)?.apply {
            equipmentAndArmor.forEach {
                val item = it.item
                if (item is IRPGGear) {
                    if (item.gearSlot == event.slot && (item is ArmorItem || item is OffHandItem)) {
                        val stats = it.stats!!
                        if (stats.bound == null && stats.binding == Binding.BOE) it.stats!!.bindTo(this)
                    }
                    it.enabled = it.requirementMet(this)
                }
            }
        }
    }

    @SubscribeEvent
    @JvmStatic
    fun entityConstructing(event: EntityEvent.EntityConstructing) {
        val e = event.entity
        if (e is EntityPlayer) {
            val m = e.attributeMap
            PrimaryStat.values().forEach {
                if (it.shouldRegister) m.registerAttribute(it.attribute)
            }
            SecondaryStat.values().forEach {
                if (it.shouldRegister) m.registerAttribute(it.attribute)
            }
            FixedStat.values().forEach {
                if (it.shouldRegister) m.registerAttribute(it.attribute)
            }
            WeaponAttribute.values().forEach {
                if (it.shouldRegister) m.registerAttribute(it.attribute)
            }
            e.dataManager.register(PlayerStats.MANA, 1f)
        }
    }

    @SubscribeEvent
    @JvmStatic
    fun hitEntity(event: LivingAttackEvent) = DamageHandler(event)

    @SubscribeEvent
    @JvmStatic
    fun livingHurt(event: LivingHurtEvent) = DamageHandler(event)

    @SubscribeEvent
    @JvmStatic
    fun entityHit(event: LivingDamageEvent) = DamageHandler(event)

    @SubscribeEvent
    @JvmStatic
    fun knockBack(event: LivingKnockBackEvent) {
        val a = event.attacker as? EntityPlayer ?: return
        event.strength += a[WeaponAttribute.KNOCKBACK].toFloat() * a.entityData.getFloat("bluerpg:lastweaponcd")
    }

    @SubscribeEvent
    @JvmStatic
    fun vanillaCrit(event: CriticalHitEvent) {
        event.result = Event.Result.DENY
    }

    @SubscribeEvent
    @JvmStatic
    fun pickupXp(event: PlayerPickupXpEvent) {
        event.entityPlayer.stats.level += event.orb.xpValue.toLong()
    }

    @SubscribeEvent
    @JvmStatic
    fun newRegistry(event: RegistryEvent.NewRegistry) {
        SkillRegistry
        PlayerClassRegistry
//        GatheringRegistry
    }

    @SubscribeEvent
    @JvmStatic
    fun registerPlayerClasses(event: RegistryEvent.Register<PlayerClass>) {
        event.registry.registerAll(
            PlayerClass(
                ResourceLocation(BlueRPG.MODID, "class_0"),
                skills = mapOf(
                    ResourceLocation(BlueRPG.MODID, "skill_0") to 0,
                    ResourceLocation(BlueRPG.MODID, "skill_4") to 0,
                    ResourceLocation(BlueRPG.MODID, "skill_1") to 1,
                    ResourceLocation(BlueRPG.MODID, "skill_2") to 1
                ),
                baseStats = mapOf(
                    PrimaryStat.CONSTITUTION to 5,
                    PrimaryStat.STRENGTH to 5
                )
            ),
            PlayerClass(
                ResourceLocation(BlueRPG.MODID, "class_1"),
                skills = mapOf(
                    ResourceLocation(BlueRPG.MODID, "skill_1") to 0,
                    ResourceLocation(BlueRPG.MODID, "skill_2") to 0,
                    ResourceLocation(BlueRPG.MODID, "skill_3") to 1,
                    ResourceLocation(BlueRPG.MODID, "skill_0") to 1
                ),
                baseStats = mapOf(
                    PrimaryStat.INTELLIGENCE to 5,
                    PrimaryStat.WISDOM to 5
                )
            ),
            PlayerClass(
                ResourceLocation(BlueRPG.MODID, "class_2"),
                skills = mapOf(
                    ResourceLocation(BlueRPG.MODID, "skill_2") to 0,
                    ResourceLocation(BlueRPG.MODID, "skill_3") to 0,
                    ResourceLocation(BlueRPG.MODID, "skill_1") to 1,
                    ResourceLocation(BlueRPG.MODID, "skill_4") to 1
                ),
                baseStats = mapOf(
                    PrimaryStat.CHARISMA to 5,
                    PrimaryStat.DEXTERITY to 5
                )
            )
        )
    }

    @SubscribeEvent
    @JvmStatic
    fun playerInventoryCreated(event: CreatePlayerInventoryEvent) {
        if (event.player !is FakePlayer) event.inventoryPlayer = RPGInventory(event.player)
    }

    @SubscribeEvent
    @JvmStatic
    fun playerContainerCreated(event: CreatePlayerContainerEvent) {
        if (event.player !is FakePlayer) event.container =
            RPGInventoryContainer(event.player, event.container as ContainerPlayer).impl
    }

    @SubscribeEvent
    @JvmStatic
    fun combatTrackerCreated(event: CreateCombatTrackerEvent) {
        if (event.entityLiving !is FakePlayer) event.tracker = RPGCombatTracker(event.entityLiving)
    }

    fun loadInteractionLimit() {
        var s = interactionLimit
        limitIsWhitelist = !s.startsWith('!')
        if (!limitIsWhitelist) s = s.substring(1)
        interactionLimitBlocks = s.split(',')
            .mapNotNull {
                if (it.isNotBlank()) Block.getBlockFromName(it.trim())
                    ?: null.apply { BlueRPG.LOGGER.warn("Invalid ID: `$it`") } else null
            }
    }

    @ConfigProperty(
        "security",
        "Comma-separated list with the registry names of all permitted blocks. Prefix with ! to turn into a blacklist instead."
    )
    var interactionLimit: String = "!"
    private lateinit var interactionLimitBlocks: List<Block>
    private var limitIsWhitelist = false

    @SubscribeEvent
    @JvmStatic
    fun playerInteractWithBlock(event: PlayerInteractEvent.RightClickBlock) {
        if (!event.entityPlayer.isCreative && limitIsWhitelist != event.entityPlayer.world.getBlockState(event.pos).block in interactionLimitBlocks) {
            event.useBlock = Event.Result.DENY
        }
    }

    @ConfigProperty("security", "Whether to protect farmland from trampling")
    var protectFarmland = false

    @SubscribeEvent
    @JvmStatic
    fun farmlandTrample(event: BlockEvent.FarmlandTrampleEvent) {
        if (protectFarmland) event.isCanceled = true
    }

    @SubscribeEvent
    @JvmStatic
    fun openEnderchest(event: OpenEnderChestEvent) {
        event.isCanceled = true
        GuiHandler.open(RPGEnderChestContainer.NAME, event.entityPlayer, BlockPos.ORIGIN)
    }

    @ConfigProperty("security", "Whether to protect paintings from breaking")
    var protectPaintings = false

    @SubscribeEvent
    @JvmStatic
    fun spawnPainting(event: EntityJoinWorldEvent) {
        val e = event.entity
        if (e is EntityLivingBase) e.maxHurtResistantTime = 0
        if (protectPaintings && e is EntityPainting) e.setEntityInvulnerable(true)
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    @JvmStatic
    fun itemPickup(event: EntityItemPickupEvent) {
        val stats = event.item.item.stats ?: return
        if (!stats.checkBinding(event.entityPlayer)) event.isCanceled = true
    }

    @ConfigProperty("general", "Whether to automatically open gear tokens when dropped")
    var autoOpen = false

    @ConfigProperty("general", "Whether to automatically identify gear when picked up")
    var autoIdentifyPickup = false

    @ConfigProperty("general", "Whether to automatically identify gear when dropped")
    var autoIdentifyDrop = false

    @SubscribeEvent(priority = EventPriority.LOWEST)
    @JvmStatic
    fun itemPickupPost(event: EntityItemPickupEvent) {
        val item = event.item.item.item
        if (autoIdentifyPickup && item is IRPGGear) item(event.item.item, event.entityPlayer.world, event.entityPlayer)
        val stats = event.item.item.stats ?: return
        stats.generateNameIfNeeded(event.entityPlayer)
        if (stats.bound == null && stats.binding == Binding.BOP) stats.bindTo(event.entityPlayer)
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    @JvmStatic
    fun onLivingDrops(e: LivingDropsEvent) {
        val s = e.drops.asSequence()
        if (autoOpen) s.filter { it.item.item is GearTokenItem }.forEach {
            it.item = it.item.tokenStats?.open(e.entity.world, null) ?: it.item
        }
        if (autoIdentifyDrop) s.filter { it.item.item is IRPGGear }.forEach {
            it.item.stats?.generate(e.entity.world, null)
        }
    }
}

@SideOnly(Side.CLIENT)
@Mod.EventBusSubscriber(modid = BlueRPG.MODID, value = [Side.CLIENT])
object ClientEventHandler {

    @SubscribeEvent
    @JvmStatic
    fun hitEmpty(event: PlayerInteractEvent.LeftClickEmpty) = PacketHandler.NETWORK.sendToServer(PacketAttack())

    @SubscribeEvent
    @JvmStatic
    fun onTextureStitchEvent(event: TextureStitchEvent) {
        event.map.registerSprite(Textures.PARTICLE)
    }

    @SubscribeEvent
    @JvmStatic
    fun registerItemHandlers(event: ColorHandlerEvent.Item) {
        event.itemColors.registerItemColorHandler(
            IItemColor { stack, tintIndex ->
                val eggData = stack.eggData ?: return@IItemColor -1
                if (tintIndex == 0) eggData.primaryColor else eggData.secondaryColor
            }, EggItem
        )
        for (it in DynItem) event.itemColors.registerItemColorHandler(
            IItemColor { stack, tintIndex ->
                val dynamicData = stack.dynamicData ?: return@IItemColor -1
                if (tintIndex == 0) dynamicData.primaryColor else dynamicData.secondaryColor
            }, it
        )
    }

    @SubscribeEvent
    @JvmStatic
    fun registerModels(event: ModelRegistryEvent) {
        EggItem.tileEntityItemStackRenderer = object : TileEntityItemStackRenderer() {
            private val skinRenderer = RenderEggItem()

            override fun renderByItem(itemStackIn: ItemStack) =
                if (itemStackIn.eggData?.isHatched != true) TileEntityItemStackRenderer.instance.renderByItem(
                    itemStackIn
                )
                else skinRenderer.renderByItem(itemStackIn)
        }
    }

    @SubscribeEvent
    @JvmStatic
    fun openGui(event: GuiOpenEvent) {
        val g = event.gui
        when (g) {
            is GuiInventory -> event.gui =
                RpgInventoryGui((g.inventorySlots as ContainerImpl).container as RPGInventoryContainer)
        }
    }

    private fun renderCastBar() {
        val mc = Client.minecraft
        val iss = mc.player.activeItemStack
        val charge = if (iss.item == SkillItem) {
            if (iss.maxItemUseDuration > 0) min(1 - mc.player.itemInUseCount / iss.maxItemUseDuration.toFloat(), 1f)
            else -1f
        } else {
            val cd = mc.player.cooldowns
            if (cd.slotInUse >= 0) {
                val pc = mc.player.playerClass
                val rs = pc.getSelectedSkill(cd.slotInUse) ?: return
                val skill = SkillRegistry[rs] ?: return
                val useTime = skill.processor.trigger.castTimeTicks(SkillContext(mc.player, pc[skill]))
                if (useTime <= 0) return
                min(cd.currentCastTime / useTime.toFloat(), 1f)
            } else return
        }
        if (charge >= 0) {
            GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f)
            GlStateManager.disableBlend()

            mc.profiler.startSection("castBar")
            val barWidth = 182
            val res = ScaledResolution(mc)
            val x = res.scaledWidth / 2 - barWidth / 2
            val filled = (charge * barWidth).toInt()
            val top = res.scaledHeight - 80

            mc.textureManager.bindTexture(Gui.ICONS)

            drawTexturedModalRect(x, top, 0, 84, barWidth, 5, 1f)
            if (filled > 0) drawTexturedModalRect(x, top, 0, 89, filled, 5, 1f)

            GlStateManager.enableBlend()
            mc.profiler.endSection()
        }
    }

    private fun renderManaBar() {
        val mc = Client.minecraft
        val mana = mc.player.mana
        val maxMana = mc.player.maxMana
        GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f)

        mc.profiler.startSection("manaBar")
        val charge = min(mana / maxMana, 1f)
        val barWidth = 128.0
        val x = 2.0
        val filled = charge * barWidth
        val top = 20.0

        mc.textureManager.bindTexture(Textures.MANA_BAR)

        GLCore.glTexturedRectV2(
            x = x,
            y = top,
            width = barWidth,
            height = barWidth / 4,
            srcX = .0,
            srcY = .0,
            srcWidth = 256.0,
            srcHeight = 64.0,
            textureW = 256,
            textureH = 128
        )
        if (filled > 0) {
            GLCore.color(0x089fe5ff)
            GLCore.glTexturedRectV2(
                x = x,
                y = top,
                width = filled,
                height = barWidth / 4,
                srcX = .0,
                srcY = 64.0,
                srcWidth = 256.0,
                srcHeight = 64.0,
                textureW = 256,
                textureH = 128
            )
        }

        mc.profiler.endSection()
        GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f)
    }

    @SubscribeEvent
    @JvmStatic
    fun renderHuds(event: RenderGameOverlayEvent.Post) { // TODO: Skin with SAOUI
        if (event.type == RenderGameOverlayEvent.ElementType.HOTBAR) {
            renderCastBar()
            renderManaBar()
        }
    }
}

@SideOnly(Side.SERVER)
@Mod.EventBusSubscriber(modid = BlueRPG.MODID, value = [Side.SERVER])
object ServerEventHandler {
    private val regex = "[\\[(](i|item)[])]".toRegex()

    @SubscribeEvent
    @JvmStatic
    fun messageSent(event: ServerChatEvent) {
        // [i],(i),[item] and (item)
        if (event.message.contains(regex)) {
            val s = event.component.formattedText.split(regex, 2)
            val component = TextComponentString(s[0])
            component.appendSibling(event.player.heldItemMainhand.textComponent)
            s.asSequence().drop(1).forEach { component.appendText(it) }
            event.component = component
        }
    }
}