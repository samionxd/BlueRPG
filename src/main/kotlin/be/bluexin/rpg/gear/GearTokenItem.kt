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

import be.bluexin.rpg.devutil.IUsable
import be.bluexin.rpg.devutil.ItemCapabilityWrapper
import be.bluexin.rpg.stats.TokenStats
import be.bluexin.rpg.stats.tokenStats
import com.teamwizardry.librarianlib.features.base.item.ItemMod
import com.teamwizardry.librarianlib.features.kotlin.Client
import com.teamwizardry.librarianlib.features.kotlin.localize
import net.minecraft.client.util.ITooltipFlag
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.util.ActionResult
import net.minecraft.util.EnumActionResult
import net.minecraft.util.EnumHand
import net.minecraft.world.World
import net.minecraftforge.common.capabilities.ICapabilityProvider
import net.minecraftforge.items.ItemHandlerHelper

class GearTokenItem private constructor(val type: TokenType) : ItemMod("gear_token_${type.key}"),
    IUsable<ItemStack> {

    companion object {
        private val pieces = Array(TokenType.values().size) { typeIdx ->
            val type = TokenType.values()[typeIdx]
            GearTokenItem(type)
        }

        operator fun get(type: TokenType) = pieces[type.ordinal]
    }

    override fun onItemRightClick(worldIn: World, playerIn: EntityPlayer, handIn: EnumHand) =
        this(playerIn.getHeldItem(handIn), worldIn, playerIn)

    override fun invoke(stack: ItemStack, world: World, player: EntityPlayer): ActionResult<ItemStack> {
        val iss = if (world.isRemote) stack else {
            val r = stack.tokenStats!!.open(world, player)
            if (stack.isEmpty) r else {
                ItemHandlerHelper.giveItemToPlayer(player, r)
                stack
            }
        }
        return ActionResult.newResult(EnumActionResult.SUCCESS, iss)
    }

    override fun addInformation(stack: ItemStack, worldIn: World?, tooltip: MutableList<String>, flagIn: ITooltipFlag) {
        val stats = stack.tokenStats ?: return
        tooltip.add("rpg.display.itemlevel".localize(stats.ilvl))
        tooltip.add("rpg.display.levelreq".localize(stats.levelReq))
        if (stats.rarity != null) tooltip.add(stats.rarity!!.localized)
        tooltip.add("rpg.display.open".localize(Client.minecraft.gameSettings.keyBindPickBlock.displayName))
    }

    override fun initCapabilities(stack: ItemStack, nbt: NBTTagCompound?): ICapabilityProvider? {
        return ItemCapabilityWrapper(TokenStats(stack), TokenStats.Capability)
    }

    override fun getNBTShareTag(stack: ItemStack): NBTTagCompound? {
        val nbt = super.getNBTShareTag(stack)?.copy() ?: NBTTagCompound()
        val cap = stack.tokenStats ?: return nbt
        val capNbt = TokenStats.Storage.writeNBT(TokenStats.Capability, cap, null)
        nbt.setTag("capabilities", capNbt)
        return nbt
    }

    override fun readNBTShareTag(stack: ItemStack, nbt: NBTTagCompound?) {
        val capNbt = nbt?.getCompoundTag("capabilities") ?: return
        val cap = stack.tokenStats ?: return
        TokenStats.Storage.readNBT(TokenStats.Capability, cap, null, capNbt)
        nbt.removeTag("capabilities")
        super.readNBTShareTag(stack, if (nbt.isEmpty) null else nbt)
    }
}