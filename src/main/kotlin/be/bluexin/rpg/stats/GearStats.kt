package be.bluexin.rpg.stats

import be.bluexin.rpg.BlueRPG
import be.bluexin.rpg.gear.Binding
import be.bluexin.rpg.gear.IRPGGear
import be.bluexin.rpg.gear.Rarity
import be.bluexin.saomclib.capabilities.Key
import com.teamwizardry.librarianlib.features.saving.AbstractSaveHandler
import com.teamwizardry.librarianlib.features.saving.Save
import com.teamwizardry.librarianlib.features.saving.SaveInPlace
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTBase
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.util.EnumFacing
import net.minecraft.util.ResourceLocation
import net.minecraftforge.common.capabilities.Capability
import net.minecraftforge.common.capabilities.CapabilityInject
import java.lang.ref.WeakReference

@SaveInPlace
class GearStats(val itemStackIn: ItemStack) {

    @Save
    var rarity = Rarity.COMMON // TODO

    @Save
    var binding = Binding.BOE // TODO

    @Save
    var bound = false

    @Save
    var ilvl = 1

    @Save
    var stats: StatsCollection = StatsCollection(WeakReference(itemStackIn))
        internal set

    fun generate() {
        this.stats.clear()
        val gear = itemStackIn.item as? IRPGGear ?: return
        val stats = rarity.rollStats()
        stats.forEach {
            this.stats[it] += it.getRoll(ilvl, rarity, gear.type, gear.gearSlot)
        }
        BlueRPG.LOGGER.warn("Generated ${this.stats().joinToString()}\n\tFor $itemStackIn")
    }

    operator fun get(stat: Stat) = stats[stat]

    internal object Storage : Capability.IStorage<GearStats> {
        override fun readNBT(capability: Capability<GearStats>, instance: GearStats, side: EnumFacing?, nbt: NBTBase) {
            val nbtTagCompound = nbt as? NBTTagCompound ?: return
//            instance.stats.clear()
            try {
                AbstractSaveHandler.readAutoNBT(instance, nbtTagCompound.getTag(KEY.toString()), false)
                if (instance.stats.isEmpty()) BlueRPG.LOGGER.warn("Read empty stats from $nbt !")
                BlueRPG.LOGGER.warn("Read nbt. nbt: $nbt")
            } catch (e: Exception) {
                BlueRPG.LOGGER.warn("Failed to read gear stats.", e)
                // Resetting bad data is fine
            }
        }

        override fun writeNBT(capability: Capability<GearStats>, instance: GearStats, side: EnumFacing?): NBTBase {
//            BlueRPG.LOGGER.warn("${Thread.currentThread().name} write nbt. stats: ${instance.stats().joinToString()}")
            val nbt = NBTTagCompound().also { it.setTag(KEY.toString(), AbstractSaveHandler.writeAutoNBT(instance, false)) }
//            BlueRPG.LOGGER.warn("${Thread.currentThread().name} write nbt. nbt: $nbt")
//            if (nbt.getCompoundTag("bluerpg:gear_stats").getCompoundTag("stats").getTagList("collection", 10).isEmpty)
//                BlueRPG.LOGGER.warn("${Thread.currentThread().name} wrote empty nbt. Stats: ${instance.stats().joinToString()}")
            return nbt
        }
    }

    companion object {
        @Key
        val KEY = ResourceLocation(BlueRPG.MODID, "gear_stats")

        @CapabilityInject(GearStats::class)
        lateinit var Capability: Capability<GearStats>
            internal set
    }
}