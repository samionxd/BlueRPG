/*
 * Copyright (C) 2018.  Arnaud 'Bluexin' Solé
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

package be.bluexin.rpg.stats

import be.bluexin.rpg.BlueRPG
import be.bluexin.rpg.events.StatChangeEvent
import be.bluexin.rpg.gear.GearType
import be.bluexin.rpg.gear.Rarity
import be.bluexin.rpg.util.RNG
import be.bluexin.rpg.util.fire
import com.teamwizardry.librarianlib.features.kotlin.localize
import com.teamwizardry.librarianlib.features.saving.NamedDynamic
import com.teamwizardry.librarianlib.features.saving.Savable
import com.teamwizardry.librarianlib.features.saving.Save
import com.teamwizardry.librarianlib.features.saving.SaveInPlace
import net.minecraft.entity.SharedMonsterAttributes
import net.minecraft.entity.ai.attributes.IAttribute
import net.minecraft.entity.ai.attributes.RangedAttribute
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.inventory.EntityEquipmentSlot
import net.minecraftforge.fml.common.eventhandler.Event
import net.minecraftforge.fml.relauncher.Side
import net.minecraftforge.fml.relauncher.SideOnly
import java.lang.ref.WeakReference
import java.util.*

@SaveInPlace
class StatsCollection(private val reference: WeakReference<out Any>, @Save internal var collection: HashMap<Stat, Int> = HashMap()) {
    // TODO: move collection to `val` and `withDefault` (instead of getOrDefault) once liblib updates

    operator fun get(stat: Stat): Int = collection.getOrDefault(stat, 0)
    operator fun set(stat: Stat, value: Int): Boolean {
        val r = reference.get()
        val evt = if (r is EntityPlayer) {
            StatChangeEvent(r, stat, collection.getOrDefault(stat, 0), value)
        } else null

        return if (evt == null || (fire(evt) && evt.result != Event.Result.DENY)) {
            if (evt?.newValue ?: value != 0) collection[stat] = evt?.newValue ?: value
            else collection.remove(stat)
            if (r is EntityPlayer) {
                r.getEntityAttribute(stat.attribute).baseValue = evt!!.newValue.toDouble()
            }
            dirty()
            true
        } else false
    }

    operator fun invoke() = collection.asSequence()

    operator fun iterator() = (collection as Map<Stat, Int>).iterator()

    fun copy() = StatsCollection(WeakReference<Any>(null), HashMap(collection))

    fun load(other: StatsCollection) {
        this.collection = other.collection
    }

    internal var dirty = false
        private set

    internal fun clean() {
        dirty = false
    }

    internal fun dirty() {
        dirty = true
    }

    fun clear() = collection.clear()

    fun isEmpty() = collection.isEmpty()
}

/*
Modifier operations :
    - 0: flat add
    - 1: multiply
    - 2: multiply by (1.0 + x)
 */

@NamedDynamic(resourceLocation = "b:s")
@Savable
interface Stat {

    val name: String

    val uuid: Array<UUID>

    fun uuid(slot: EntityEquipmentSlot) = when (slot) {
        EntityEquipmentSlot.MAINHAND -> uuid[5]
        EntityEquipmentSlot.OFFHAND -> uuid[4]
        EntityEquipmentSlot.FEET -> uuid[0]
        EntityEquipmentSlot.LEGS -> uuid[1]
        EntityEquipmentSlot.CHEST -> uuid[2]
        EntityEquipmentSlot.HEAD -> uuid[3]
    }

    val attribute: IAttribute

    val hasTransform get() = false

    val shouldRegister get() = true

    operator fun invoke(from: Int) = from.toDouble()

    @SideOnly(Side.CLIENT)
    fun longName(): String {
        return "rpg.${name.toLowerCase()}.long".localize()
    }

    @SideOnly(Side.CLIENT)
    fun shortName(): String {
        return "rpg.${name.toLowerCase()}.short".localize()
    }

    fun getRoll(ilvl: Int, rarity: Rarity, gearType: GearType, slot: EntityEquipmentSlot): Int
}

@NamedDynamic(resourceLocation = "b:ps")
enum class PrimaryStat(uuid: Array<String>) : Stat {
    STRENGTH(arrayOf(
            "b31893e3-08c7-463a-9240-cef6e89a7bc0",
            "85edcb6d-cd6a-4dc5-8f9b-c58a44ee9ca7",
            "b42e78d9-07e7-4b49-9712-f12c8b9f3825",
            "fdc7f52a-0b5b-4fd6-a59c-ad8799341e10",
            "2d91cf71-186a-4395-9d95-23294bfee38b",
            "ed4f1ee7-afcf-4879-be1c-d009dc0c0aab"
    )),
    CONSTITUTION(arrayOf(
            "2e83cd49-1166-45a8-95b7-a08a44e2768d",
            "b8775d3b-cc9e-454a-9592-257666bb928c",
            "aa9385aa-2104-4524-b360-ee1fae77fe0c",
            "42d44b03-d001-4a0a-af29-4eebe1d3df02",
            "13d3fadb-0a7f-4bee-9763-d87790397580",
            "970813d3-7704-4c51-a559-cd74e2be6da7"
    )),
    DEXTERITY(arrayOf(
            "9df6b30f-e0b1-43c4-9008-69024116e8df",
            "8de7d267-5a06-4511-837a-20ef368bd088",
            "471f114c-6521-48ce-8b13-1e6dce4aea02",
            "db4667db-b133-4293-8ec1-587d6955ded5",
            "6f9659cb-b50a-4bd9-aa8c-24f5a58ce75e",
            "2699519d-0f9f-493b-82b4-d3c90167ef87"
    )),
    INTELLIGENCE(arrayOf(
            "b7ac00d8-8a30-4e60-a651-1132fdaa5a27",
            "f0ad0105-e57a-4714-89c1-ce28277218e5",
            "b1b6effb-3a07-43eb-aed7-4279c0bc2bb7",
            "221117fe-1a96-4175-9ece-e031d2119107",
            "6469bd46-4307-4251-80b4-352b52cece93",
            "cc93037d-94ef-425f-a487-ce4fb6ed0652"
    )),
    WISDOM(arrayOf(
            "63f17582-142d-4a82-bd1c-0848e27d51b0",
            "a63dd4b9-5783-4bac-ab29-77803624254d",
            "38016a19-84ed-4fbe-ad21-f0b70d1adaeb",
            "e89cf1ba-8aae-4b93-8b2e-baad3b7da3cf",
            "5a49481f-d521-434c-9439-07f65a4ab2ac",
            "66b1929f-0186-4202-b2ec-df4457f9ce44"
    )),
    CHARISMA(arrayOf(
            "2f7e523b-728d-4a87-aea0-3bfbc07652f4",
            "c221bd4f-bf24-4d2f-bd8a-255ccdc8181d",
            "1840d568-b793-497a-a439-4e2118d642e7",
            "0e752198-d170-47a5-b5f6-8875b46d2434",
            "a3a64f77-3779-4ded-b1a6-883572d30bc6",
            "bb9c6505-a00c-49fb-bfb2-f25d2f8db3fe"
    ));

    override val attribute: IAttribute = RangedAttribute(null, "${BlueRPG.MODID}.${this.name.toLowerCase()}", 0.0, 0.0, Double.MAX_VALUE).setShouldWatch(true)

    override val uuid = uuid.map { UUID.fromString(it) }.toTypedArray()

    override fun getRoll(ilvl: Int, rarity: Rarity, gearType: GearType, slot: EntityEquipmentSlot): Int {
        return RNG.nextInt(10) + ilvl // TODO: formulae
    }
}

@NamedDynamic(resourceLocation = "b:ss")
enum class SecondaryStat(uuid: Array<String>, attribute: IAttribute? = null) : Stat {
    PSYCHE(arrayOf(
            "596b8658-6e31-479b-9d8c-75312bf7fde9",
            "3a44ded3-8f78-4e0f-a889-9a3943235bbb",
            "f502aa88-f9e7-4140-b257-a8d5470537c3",
            "cf9d3c18-5acf-4e78-8464-5c3840a2b952",
            "45779a50-cf0b-44fa-b972-17dda742287c",
            "48c52ea4-9a41-461e-a565-75b7d8aa987a"
    )),
    REGEN(arrayOf(
            "6bd4b8bf-d010-4a12-8cff-326bd4f9a377",
            "3ac37444-6228-4a66-8bde-f5917f164df3",
            "8ab77c60-14fc-4a0e-8834-2f4d26d5fffc",
            "d6f13896-287b-4d42-8a3f-47d12b12441e",
            "82bb57a2-f46e-434d-b078-ed24cfc362b1",
            "3cc65b37-8cac-4677-ab1b-d2fd5703a30e"
    )),
    SPIRIT(arrayOf(
            "57b18a8e-3999-4704-9f2e-af95d4a000fe",
            "a17e6e2e-bd75-4bf5-baf1-a0d8a02c1eee",
            "92bcccfa-94e2-4d1e-b668-7b5072b7ccf9",
            "bee3503f-14ec-4032-9844-5b15b0654c06",
            "ea0c929a-da3e-40df-88c0-8c8e17648073",
            "fe36dd53-9dd5-43b0-9ef2-ab2e91e177e3"
    )),
    REFLECT(arrayOf(
            "984d0c7a-b406-47ce-b956-2193d0146184",
            "21e8de74-b983-4949-8415-bbfb42816d2f",
            "c22565af-a5ec-4635-9b09-ea9cc2dbf8b0",
            "fa6f2aff-7e4a-46b4-8a4c-fad1653a1840",
            "6a37c0f5-d53c-4749-a901-9324a28b69af",
            "dae6972d-5722-4bf2-a85b-1917e1350535"
    )),
    BLOCK(arrayOf(
            "05a6f1a0-20e7-4cc0-97fe-4700f4647f23",
            "91e17900-377c-490a-86ac-a8a349bf183e",
            "d4fa48e5-c03d-4073-9690-e9253f5386c3",
            "70a243e7-d9d3-423a-bb7e-ee28f2189eb6",
            "53d80a13-143d-4ddf-bdb8-eb4c652c483e",
            "beac044d-822a-4e98-b050-e6f4620bc44c"
    )),
    DODGE(arrayOf(
            "676fef05-9038-4bb1-8fd7-037c2f124b4e",
            "84a9c875-4067-46ab-ba51-6a539ea61da6",
            "53c5cead-888c-428b-b4b8-4b2e2710d19c",
            "450b04eb-6a64-42f3-8fbe-7b1f59692231",
            "6bef0de3-a540-4cb1-9257-0b9a00b36565",
            "b3e605ff-18c3-4455-8799-a18d1f4a6e76"
    )),
    SPEED(arrayOf(
            "ffb8012b-289a-45ed-b8cb-a477366b58b0",
            "44f0e78f-b319-47b8-b489-47227056ea4e",
            "d46ceb87-57c9-4c82-9ac2-204280628c0d",
            "1b87c3ae-ac43-4e97-98b5-fb21a5d7a5dd",
            "b3969cc1-de3f-484e-bea2-b4107706f191",
            "fdf24012-758d-4c7b-8672-d2f67751c4c8"
    ), SharedMonsterAttributes.MOVEMENT_SPEED) {
        override val hasTransform = true

        override fun invoke(from: Int) = from / 1000.0
    },
    LIFE_STEAL_CHANCE(arrayOf(
            "b70eaf8a-2237-4310-bb03-84f3a174119f",
            "dc420c58-5f59-4809-acd5-6f1f4cc5a611",
            "6d6945cc-9d4d-4c65-91f5-37d2f30df1ac",
            "5ea503a6-ab12-4562-9ae0-ee9844cd7d29",
            "105e4c8f-5e25-4198-b0ac-8f33902d6843",
            "58bbd385-f64f-4798-b9a2-5ecd6465662f"
    )),
    LIFE_STEAL(arrayOf(
            "c294068d-66e8-43ec-a4f9-e09e4a6ef18c",
            "e853b15a-f5cf-4b05-9df9-083366a2cdf0",
            "e6518c6a-b2c2-46a6-b3c6-eb1a61dd5238",
            "46aebbed-8392-4f23-94ab-923dd8bb45c2",
            "6a97bbcb-3c98-4c23-9169-818c0d00706f",
            "8f753b5a-1b3d-44f5-ba78-356af8092463"
    )),
    MANA_LEECH_CHANCE(arrayOf(
            "7b0414b7-f7ac-4f14-acd1-4f05dc80b409",
            "2eb18cf9-ba4a-451f-89d0-d7fac4a4ac7a",
            "de67156a-3b47-492e-abba-aff362475a25",
            "e58e8669-fe93-4dc6-ad73-1e5ca0eda273",
            "a2078044-46ca-4678-ba0d-48d9d72504eb",
            "300636a6-781a-430e-90dd-b7f8ece76af3"
    )),
    MANA_LEECH(arrayOf(
            "6cbedbfb-f646-4d52-bb36-3f9c0700a6dd",
            "fea17355-3191-41db-aa7c-d630de48db19",
            "c412512d-bd43-4073-8ace-f2c35a6f03de",
            "21dc8175-9d69-4e19-88fb-5aaa08e36ca6",
            "1cf5d2c7-3a1a-4f7d-8bb3-744ee612f236",
            "9e470c3c-c6e5-4d3c-8275-133842e22c38"
    )),
    CRIT_CHANCE(arrayOf(
            "a7d38693-a05a-4897-99a9-abb6197af11f",
            "c7c29237-ee03-44d0-b4e4-28d5943962cb",
            "af7e8480-4480-41a9-b794-dabdfaa96078",
            "a269aabb-228e-4c81-8fbd-f1bb01fedf3b",
            "7015dcee-1b3c-49cb-8c3b-2469e1cbc645",
            "100b159f-d86c-48d2-ba15-79e083ae5665"
    )),
    CRIT_DAMAGE(arrayOf(
            "4d760921-f4be-47a6-b002-3fac0a668afa",
            "b362a556-95e6-4c4d-8be2-68968f1cf4e2",
            "d757b41f-6680-40b5-91f4-aea0c8e8a074",
            "18477507-b7f3-4035-b083-1602f6662528",
            "b222a806-4534-420a-b1b6-f981f63164cf",
            "b73977f3-4bc2-4bae-98fc-d44da61610f2"
    )),
    BONUS_TO_SKILL(arrayOf(
            "f0653db6-8aad-4476-970e-f0d8f85ab83c",
            "f4ff68f9-b44f-4242-adfd-c584fb2fb493",
            "0c7ab09c-e0e9-4ed6-ac41-e77fa0bdb0a8",
            "03924abc-22b4-406a-9662-41d1fd6d2e69",
            "844f40b9-d9fc-46e4-a9c6-88db501891f1",
            "50dc62d1-9d19-421e-aeac-500f131e5527"
    )),
    BONUS_DAMAGE(arrayOf(
            "30c2c5fb-00a4-4a56-b0bc-5dcecb7a58b2",
            "a1c68b27-983a-406a-be29-b6ebba4cb6bc",
            "fafb013a-74f6-45a6-8388-3493bfeab27b",
            "69349aa5-b2ee-49d6-b82d-16defa8d2cf0",
            "e8efd1ca-ef46-4801-8fe6-16cb558213c8",
            "eb9bc66f-f665-46d5-8edc-42e6b7773de6"
    ), SharedMonsterAttributes.ATTACK_DAMAGE),
    RESISTANCE(arrayOf(
            "d9a1485c-1c8a-43ca-9055-f59665064402",
            "dad33cc5-ccba-4db7-82cb-955260261e4b",
            "866c3b01-881d-4f87-b99d-ce5bc503f6ad",
            "1ae9c08c-967f-4fb0-af8f-b34bb1d61d4e",
            "81b44d11-d95a-4e4e-acad-8cd09dca83f5",
            "09130b59-4136-4187-9f93-f87cc5726c69"
    )),
    ROOT(arrayOf(
            "f35a9dd9-e88b-4e0b-b915-dd49ec1b0c3d",
            "15a600b3-d2a1-4f7e-9406-d9ce1648d92c",
            "5c83c42d-49e9-4d8e-951f-b025de59ee4b",
            "97a593eb-0d66-4057-861e-9f92c210f542",
            "4e74d178-096f-4a17-856d-4de7dd6fbd4e",
            "1e0cc3a1-44de-4768-bf87-40b38e2cecfe"
    )),
    SLOW(arrayOf(
            "03aee4f1-b4fb-44aa-9024-fa7a3a052121",
            "b063c763-c5d4-4011-bc7f-63d7d01a1d12",
            "37be1c6e-8682-4c3c-a4c5-1385f1bbc41c",
            "26fb4414-ff43-442f-8af2-1eb899749fa2",
            "373cd837-6597-494c-b09b-2c6cccdfa2ab",
            "e2414446-bd51-44eb-b7a0-b6a05cad159d"
    )),
    COOLDOWN_REDUCTION(arrayOf(
            "fe29948b-176f-49ba-b36b-3dbd3be1136e",
            "a4158d5f-b7d8-4802-a91e-86318e2d0774",
            "7f3dc837-21cc-4cd9-af7c-d1d13cfb3fb5",
            "61183fa7-788e-4c7e-95ca-be20da54e24c",
            "e589ab2a-df8a-4514-ba0a-f212c8935c65",
            "963e0750-a4bf-40b9-a35f-1e859e20f789"
    )),
    MANA_REDUCTION(arrayOf(
            "8fdf1c14-7677-4622-8979-5a639df7b258",
            "23d5fad1-2b3f-4d06-a318-364cebc67e82",
            "ca810c86-30f4-44dd-a763-a03109be778d",
            "1cace5d1-2970-4b83-b6d8-e9386fb61644",
            "26514f55-6b5b-4384-ad3a-10f48ece3c13",
            "8942fb83-0b0f-4d59-a306-90032ebb7cd9"
    ));

    override val shouldRegister = attribute == null

    override val attribute: IAttribute = attribute
            ?: RangedAttribute(null, "${BlueRPG.MODID}.${this.name.toLowerCase()}", 0.0, 0.0, Double.MAX_VALUE).setShouldWatch(true)

    override val uuid = uuid.map { UUID.fromString(it) }.toTypedArray()

    override fun getRoll(ilvl: Int, rarity: Rarity, gearType: GearType, slot: EntityEquipmentSlot): Int {
        return RNG.nextInt(10) + ilvl // TODO: formulae
    }
}

@NamedDynamic(resourceLocation = "b:fs")
enum class FixedStat(uuid: Array<String>, attribute: IAttribute? = null): Stat {
    HEALTH(arrayOf(
            "31aec51b-dd4b-43a1-8a86-861f56ae39f1",
            "32600f64-53a6-4ada-aa0a-7e46c33dc9d4",
            "032b4961-3b99-4d56-bf85-3e34c001b2a8",
            "292d56f2-038d-495e-aeac-7f5e4cdcc9bc",
            "c984640c-be61-4684-b671-b7e5840304db"
    ), SharedMonsterAttributes.MAX_HEALTH),
    ARMOR(arrayOf(
            "8c43be5a-c46b-4122-b80e-609163cac079",
            "57a37153-8f94-4738-b348-a5b6bd3ee078",
            "1bff0b30-b2b3-4759-856d-3d6e0945a2f2",
            "6800cfd6-d1f9-49ac-8059-9c22a3d34ed5"
    )),
    BASE_DAMAGE(arrayOf(
            "ddb9e209-25a2-418c-b5a9-1707e6d54638"
    ), SharedMonsterAttributes.ATTACK_DAMAGE) {
        override fun uuid(slot: EntityEquipmentSlot): UUID =
                if (slot == EntityEquipmentSlot.MAINHAND) uuid[0] else throw IllegalArgumentException("`${this}` is not applicable to slot `$slot` !")
    };

    override val shouldRegister = attribute == null

    override val attribute: IAttribute = attribute
            ?: RangedAttribute(null, "${BlueRPG.MODID}.${this.name.toLowerCase()}", 0.0, 0.0, Double.MAX_VALUE).setShouldWatch(true)

    override val uuid = uuid.map { UUID.fromString(it) }.toTypedArray()

    override fun getRoll(ilvl: Int, rarity: Rarity, gearType: GearType, slot: EntityEquipmentSlot): Int {
        return RNG.nextInt(10) + ilvl // TODO: formulae
    }
}

@Savable
@NamedDynamic(resourceLocation = "b:sc")
interface StatCapability {
    fun copy(): StatCapability
}

/**
 * Generates us some UUIDs
 */
fun main(args: Array<String>) {
    println(PrimaryStat.values().joinToString(separator = ",\n", postfix = ";") {
        """$it(arrayOf(
        |"${UUID.randomUUID()}",
        |"${UUID.randomUUID()}",
        |"${UUID.randomUUID()}",
        |"${UUID.randomUUID()}",
        |"${UUID.randomUUID()}",
        |"${UUID.randomUUID()}",
        |"${UUID.randomUUID()}"
        |))
    """.trimMargin()
    })
    println(SecondaryStat.values().joinToString(separator = ",\n", postfix = ";") {
        """$it(arrayOf(
        |"${UUID.randomUUID()}",
        |"${UUID.randomUUID()}",
        |"${UUID.randomUUID()}",
        |"${UUID.randomUUID()}",
        |"${UUID.randomUUID()}",
        |"${UUID.randomUUID()}"
        |))
    """.trimMargin()
    })
    println(FixedStat.values().joinToString(separator = ",\n", postfix = ";") {
        """$it(arrayOf(
        |"${UUID.randomUUID()}",
        |"${UUID.randomUUID()}",
        |"${UUID.randomUUID()}",
        |"${UUID.randomUUID()}",
        |"${UUID.randomUUID()}"
        |))
    """.trimMargin()
    })
}