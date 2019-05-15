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

package be.bluexin.rpg.skills

import be.bluexin.rpg.BlueRPG
import be.bluexin.rpg.util.get
import com.teamwizardry.librarianlib.features.base.IExtraVariantHolder
import com.teamwizardry.librarianlib.features.base.item.ItemMod
import com.teamwizardry.librarianlib.features.helpers.getNBTString
import com.teamwizardry.librarianlib.features.kotlin.Minecraft
import com.teamwizardry.librarianlib.features.kotlin.localize
import net.minecraft.client.renderer.block.model.ModelResourceLocation
import net.minecraft.client.util.ITooltipFlag
import net.minecraft.entity.EntityLivingBase
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.EnumAction
import net.minecraft.item.ItemStack
import net.minecraft.util.ActionResult
import net.minecraft.util.EnumActionResult
import net.minecraft.util.EnumHand
import net.minecraft.util.ResourceLocation
import net.minecraft.world.World
import net.minecraftforge.fml.relauncher.Side
import net.minecraftforge.fml.relauncher.SideOnly

object SkillItem : ItemMod("skill_item"), IExtraVariantHolder {

    private val ItemStack.skillName
        get() = ResourceLocation(
            this.getNBTString("skill") ?: "${BlueRPG.MODID}:unknown_skill"
        )
    private val ItemStack.skill get() = SkillRegistry[this.skillName]

    override fun getTranslationKey(stack: ItemStack) = stack.skill?.name ?: "${BlueRPG.MODID}:unknown_skill"

    override val extraVariants by lazy { arrayOf("unknown_skill", *SkillRegistry.allSkillStrings) }

    override val meshDefinition: ((stack: ItemStack) -> ModelResourceLocation)? =
        { ModelResourceLocation(it.skillName, "inventory") }

    override fun addInformation(stack: ItemStack, worldIn: World?, tooltip: MutableList<String>, flagIn: ITooltipFlag) {
        stack.skill?.apply {
            tooltip += "rpg.tooltip.${stack.skillName}"
            tooltip += "rpg.tooltip.manacost".localize(mana)
            tooltip += "rpg.tooltip.cooldown".localize(cooldown)
        }
    }

    override fun getMaxItemUseDuration(stack: ItemStack) = stack.skill?.processor?.trigger?.castTimeTicks ?: 0

    override fun onItemRightClick(worldIn: World, playerIn: EntityPlayer, handIn: EnumHand): ActionResult<ItemStack> {
        val stack = playerIn.getHeldItem(handIn)
        val skill = stack.skill ?: return ActionResult(EnumActionResult.FAIL, stack)

        val r = skill.startUsing(playerIn)
        if (r == EnumActionResult.PASS) playerIn.activeHand = handIn
        return ActionResult(r, stack)
    }

    override fun onItemUseFinish(stack: ItemStack, worldIn: World, entityLiving: EntityLivingBase): ItemStack {
        val skill = stack.skill ?: return stack
        skill.stopUsing(entityLiving, skill.processor.trigger.castTimeTicks)
        return stack
    }

    override fun getItemUseAction(stack: ItemStack) = EnumAction.BOW

    @SideOnly(Side.CLIENT)
    override fun showDurabilityBar(stack: ItemStack): Boolean {
        return (stack.skill ?: return false) in Minecraft().player.cooldowns
    }

    @SideOnly(Side.CLIENT)
    override fun getDurabilityForDisplay(stack: ItemStack): Double {
        return 1.0 - Minecraft().player.cooldowns[(stack.skill ?: return .0), Minecraft().renderPartialTicks]
    }

    /*override fun onPlayerStoppedUsing(stack: ItemStack, worldIn: World, entityLiving: EntityLivingBase, timeLeft: Int) {
        super.onPlayerStoppedUsing(stack, worldIn, entityLiving, timeLeft)
    }*/ // Could be used for special effect when stopping spell before the end?
}