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

package be.bluexin.rpg.gear

import be.bluexin.rpg.stats.FixedStat
import be.bluexin.rpg.stats.GearStats
import be.bluexin.rpg.stats.stats
import com.teamwizardry.librarianlib.features.base.item.ItemModArmor
import com.teamwizardry.librarianlib.features.kotlin.localize
import net.minecraft.client.util.ITooltipFlag
import net.minecraft.entity.Entity
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.inventory.EntityEquipmentSlot
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.util.ActionResult
import net.minecraft.util.EnumActionResult
import net.minecraft.util.EnumHand
import net.minecraft.world.World

class ArmorItem private constructor(override val type: ArmorType, material: ArmorMaterial, slot: EntityEquipmentSlot) :
    ItemModArmor("${slot.getName()}_${type.key}", material, slot), IRPGGear {

    companion object {
        private val pieces = Array(ArmorType.values().size) { typeIdx ->
            val type = ArmorType.values()[typeIdx]
            val armorSlots = EntityEquipmentSlot.values().filter {
                it.slotType == EntityEquipmentSlot.Type.ARMOR
            }.sortedBy { it.index }
            Array(armorSlots.size) { slotIdx ->
                val armorSlot = armorSlots[slotIdx]
                ArmorItem(
                    type,
                    type.armorMaterial,
                    armorSlot
                )
            }
        }

        operator fun get(type: ArmorType, slot: EntityEquipmentSlot) = pieces[type.ordinal][slot.index]
        operator fun get(slot: EntityEquipmentSlot, type: ArmorType) = pieces[type.ordinal][slot.index]
    }

    override fun getAttributeModifiers(slot: EntityEquipmentSlot, stack: ItemStack) =
        super<IRPGGear>.getAttributeModifiers(slot, stack)

    override fun initCapabilities(stack: ItemStack, nbt: NBTTagCompound?) =
        super<IRPGGear>.initCapabilities(stack, nbt)

    override fun addInformation(stack: ItemStack, worldIn: World?, tooltip: MutableList<String>, flagIn: ITooltipFlag) =
        super<IRPGGear>.addInformation(stack, worldIn, tooltip, flagIn)

    override fun getNBTShareTag(stack: ItemStack): NBTTagCompound? =
        super.addNBTShare(stack, super.getNBTShareTag(stack) ?: NBTTagCompound())

    override fun readNBTShareTag(stack: ItemStack, nbt: NBTTagCompound?) =
        super.readNBTShareTag(stack, super.readNBTShare(stack, nbt))

    override fun getShareTag() = super<IRPGGear>.getShareTag()

    override fun getMaxDamage(stack: ItemStack) = super<IRPGGear>.getMaxDamage(stack)

    override fun setDamage(stack: ItemStack, damage: Int) {
        if (stack.stats?.generated == true) super.setDamage(stack, damage)
    }

    override fun onItemRightClick(worldIn: World, playerIn: EntityPlayer, handIn: EnumHand): ActionResult<ItemStack> {
        val r = super<IRPGGear>.onItemRightClick(worldIn, playerIn, handIn)
        return if (r.type == EnumActionResult.PASS) super<ItemModArmor>.onItemRightClick(
            worldIn,
            playerIn,
            handIn
        ) else r
    }

    override fun getItemStackDisplayName(stack: ItemStack) = super<IRPGGear>.getItemStackDisplayName(stack)

    override fun tooltipizeFixedStats(stats: GearStats) =
        FixedStat.values().asSequence().filter { stats[it] != 0 }
            .map { "rpg.tooltip.fstat".localize(it.localize(stats[it]), it.longName()) }

    override fun getUnlocalizedNameInefficientlyTrick(stack: ItemStack): String =
        super.getUnlocalizedNameInefficiently(stack)

    override val key = "${slot.getName()}_${type.key}"

    override val gearSlot: EntityEquipmentSlot
        get() = armorType

    override val item: Item
        get() = this

    override fun createEntity(world: World, location: Entity, itemstack: ItemStack) =
        super<IRPGGear>.createEntity(world, location, itemstack)

    override fun hasCustomEntity(stack: ItemStack) = super<IRPGGear>.hasCustomEntity(stack)
}