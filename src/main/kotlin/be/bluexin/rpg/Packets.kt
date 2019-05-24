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

package be.bluexin.rpg

import be.bluexin.rpg.classes.PlayerClassCollection
import be.bluexin.rpg.classes.PlayerClassRegistry
import be.bluexin.rpg.classes.playerClass
import be.bluexin.rpg.containers.ContainerEditor
import be.bluexin.rpg.items.DynamicData
import be.bluexin.rpg.pets.EggData
import be.bluexin.rpg.skills.SkillData
import be.bluexin.rpg.skills.SkillRegistry
import be.bluexin.rpg.skills.cooldowns
import be.bluexin.rpg.skills.glitter.AoE
import be.bluexin.rpg.skills.glitter.BeamLightningSystem
import be.bluexin.rpg.skills.glitter.Heal
import be.bluexin.rpg.stats.*
import be.bluexin.rpg.util.get
import com.teamwizardry.librarianlib.features.autoregister.PacketRegister
import com.teamwizardry.librarianlib.features.container.internal.ContainerImpl
import com.teamwizardry.librarianlib.features.kotlin.Minecraft
import com.teamwizardry.librarianlib.features.network.PacketBase
import com.teamwizardry.librarianlib.features.saving.Save
import net.minecraft.util.ResourceLocation
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext
import net.minecraftforge.fml.relauncher.Side
import java.awt.Color

@PacketRegister(Side.SERVER)
class PacketRaiseStat(stat: PrimaryStat, amount: Int) : PacketBase() {
    @Save
    var stat = stat
        internal set
    @Save
    var amount = amount
        internal set

    @Suppress("unused")
    internal constructor() : this(PrimaryStat.STRENGTH, 0)

    override fun handle(ctx: MessageContext) {
        val stats = ctx.serverHandler.player.stats
        if (stats.attributePoints >= amount) {
            if (stats.baseStats.set(stat, stats.baseStats[stat] + amount)) {
                stats.attributePoints -= amount
            }
        }
    }
}

@PacketRegister(Side.SERVER)
class PacketSetEditorStats(pos: BlockPos, stats: StatCapability?) : PacketBase() {
    @Save
    var stats = stats
        internal set

    @Save
    var pos = pos
        internal set

    @Suppress("unused")
    internal constructor() : this(BlockPos.ORIGIN, null)

    override fun handle(ctx: MessageContext) {
        val player = ctx.serverHandler.player
        val te = if (player.world.isBlockLoaded(pos)) player.world.getTileEntity(pos) else return
        if (te != null && ((player.openContainer as? ContainerImpl)?.container as? ContainerEditor)?.te == te) {
            when (stats) {
                is TokenStats -> te.tokenStats = stats as TokenStats
                is GearStats -> te.gearStats = stats as GearStats
                is EggData -> te.eggStats = stats as EggData
                is DynamicData -> te.dynStats = stats as DynamicData
            }
        }
    }
}

@PacketRegister(Side.SERVER)
class PacketSaveLoadEditorItem(pos: BlockPos, saving: Boolean) : PacketBase() {
    @Save
    var saving = saving
        internal set

    @Save
    var pos = pos
        internal set

    @Suppress("unused")
    internal constructor() : this(BlockPos.ORIGIN, false)

    override fun handle(ctx: MessageContext) {
        val player = ctx.serverHandler.player
        val te = if (player.world.isBlockLoaded(pos)) player.world.getTileEntity(pos) else return
        if (te != null && ((player.openContainer as? ContainerImpl)?.container as? ContainerEditor)?.te == te) {
            if (saving) te.saveStats()
            else te.loadStats()
        }
    }

}

@PacketRegister(Side.SERVER)
class PacketAttack : PacketBase() {
    override fun handle(ctx: MessageContext) = DamageHandler.handleCustomAttack(ctx.serverHandler.player)
}

@PacketRegister(Side.CLIENT)
class PacketGlitter(type: Type, pos: Vec3d, color1: Int, color2: Int, spread: Double) : PacketBase() {
    @Save
    var type = type
        internal set

    @Save
    var pos = pos
        internal set

    @Save
    var color1 = color1
        internal set

    @Save
    var color2 = color2
        internal set

    @Save
    var spread = spread
        internal set

    @Suppress("unused")
    internal constructor() : this(Type.AOE, Vec3d.ZERO, 0, 0, .0)

    constructor(type: Type, pos: Vec3d, color1: Color, color2: Color, spread: Double) : this(
        type,
        pos,
        color1.rgb,
        color2.rgb,
        spread
    )

    enum class Type {
        AOE,
        HEAL
    }

    override fun handle(ctx: MessageContext) {
        when (type) {
            Type.AOE -> AoE.burst(pos, Color(color1, true), Color(color2, true), spread)
            Type.HEAL -> Heal.burst(pos, Color(color1, true), Color(color2, true), spread)
        }
    }
}

@PacketRegister(Side.CLIENT)
class PacketLightning(from: Vec3d, to: Vec3d) : PacketBase() {

    @Save
    var from = from
        internal set
    @Save
    var to = to
        internal set

    @Suppress("unused")
    internal constructor() : this(Vec3d.ZERO, Vec3d.ZERO)

    override fun handle(ctx: MessageContext) = BeamLightningSystem.lightItUp(from, to)
}

@PacketRegister(Side.CLIENT)
class PacketCooldown(skill: SkillData?, ticks: Int) : PacketBase() {
    @Save
    var skill = if (skill == null) 0 else SkillRegistry.getId(skill)
        internal set

    @Save
    var ticks = ticks
        internal set

    @Suppress("unused")
    internal constructor() : this(null, 0)

    override fun handle(ctx: MessageContext) =
        if (ticks == 0) Minecraft().player.cooldowns -= SkillRegistry.getValue(skill)!!
        else Minecraft().player.cooldowns[SkillRegistry.getValue(skill)!!] = ticks
}

@PacketRegister(Side.SERVER)
class PacketSetClass(@Save var clazz: ResourceLocation?, @Save var slot: Int) : PacketBase() {

    @Suppress("unused")
    internal constructor() : this(null, 0)

    override fun handle(ctx: MessageContext) {
        val pc = ctx.serverHandler.player.playerClass
        if (PlayerClassCollection.removeClassWhenever || pc[slot] == null) pc[slot] =
            if (clazz == null) null else PlayerClassRegistry[clazz!!]
    }
}

@PacketRegister(Side.SERVER)
class PacketChangeSkill(@Save var skill: ResourceLocation?, @Save var increase: Boolean) : PacketBase() {

    @Suppress("unused")
    internal constructor() : this(null, false)

    override fun handle(ctx: MessageContext) {
        if (increase) ++ctx.serverHandler.player.playerClass[skill!!]
        else if (PlayerClassCollection.removeSkillsWhenever) --ctx.serverHandler.player.playerClass[skill!!]
    }
}
