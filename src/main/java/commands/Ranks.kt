package commands

import RealmGuild
import RealmUtils.create
import commands.Ranks.Guides.BASELINE_RANK_H
import commands.Ranks.Guides.BASELINE_TOTAL_XP_H
import commands.Ranks.Guides.RANK_LEFT_W
import commands.Ranks.Guides.RANK_RIGHT_W
import commands.Ranks.Guides.RIGHT_ALIGN_BUFFER
import dev.minn.jda.ktx.Embed
import dev.minn.jda.ktx.messages.send
import embedutils.NeoSuperEmbed
import embedutils.NeoSuperThrow
import embedutils.SuperEmbed
import embedutils.permissionCheck
import io.realm.kotlin.Realm
import io.realm.kotlin.RealmConfiguration
import io.realm.kotlin.UpdatePolicy
import io.realm.kotlin.ext.query
import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.types.RealmList
import io.realm.kotlin.types.RealmObject
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.GuildChannel
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import org.json.JSONObject
import java.awt.Font
import java.awt.RenderingHints
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.util.*
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO
import kotlin.math.pow
import kotlin.random.Random


object Ranks {

    // Create an enum with constants for the ranks
    object Guides {
        const val BASELINE_SCORE_H = 116
        const val BASELINE_NEXT_LEVEL_H = 221
        const val BASELINE_TOTAL_XP_H = 466
        const val BASELINE_RANK_H = 725
        const val RANK_LEFT_W = 407
        const val RANK_RIGHT_W = 601
        const val RIGHT_ALIGN_BUFFER = 64
    }

    private val cooldowns: ScheduledDeque<Pair<String, String>> = ScheduledDeque(ConcurrentLinkedDeque())
    // Contains the user ID and the guild ID of the user who is currently under the effect of cooldown

    class RankMember : RealmObject {
        var id: String = "" //The user ID
        var exp: Long = 0 // Starts with 0. Long supports up to 9,223,372,036,854,775,807
        var realmGuild: RealmGuild? = null
    }

    class XPRankRoles : RealmObject {
        var id: String? = null //The role id
        var rankLevel: Int? = null
        var specialLevelUpMessage = ""
    }

    class RankSettings : RealmObject {
        var toggle: Boolean = false //If the rank system is enabled or not. By default, it is disabled
        var showMessage: Boolean =
            false //If the message should be shown to the user when they rank up. By default, it is disabled - but is enabled if a message is set.
        var xpInterval: Int = 30 //The interval in seconds. By default, it is 30.
        var xpUpMessage: String =
            "Congratulations, {mention}! You ranked up to Rank {level}!" //The message that will be sent when a user levels up.
        var xpPerMessage: Int = 10 //The amount of xp per message. By default, it is 1.
        var xpRankRoles: RealmList<XPRankRoles> = realmListOf()//The ranks roles and their xp.
        var antiBotBehaviour: Boolean =
            false //Message XP gain is only 33% effective when a bot-behaviour is detected. By default, it is disabled.
        var ignoredChannels: RealmList<String> =
            realmListOf() // Channels were messages should not count towards the rank
        var ignoredRoles: RealmList<String> = realmListOf() // Roles were messages should not count towards the rank
        var realmGuild: RealmGuild? = null
    }

    internal val configuration =
        RealmConfiguration.Builder(schema = setOf(RealmGuild::class,
            XPRankRoles::class,
            RankSettings::class,
            RankMember::class)).name("ranks.realm").build()

    /**
     * Bliss dog
     *
     * @return A random dog image
     */
    fun blissDog(): String {
        val json = JSONObject(File("src/main/resources/rank/dogs/dog_images.json").readText()).getJSONArray("dog_urls")
        val randomIndex = Random.nextInt(0, json.length())
        return json[randomIndex] as String
    }


    /**
     * Message replacement method used for rank level up.
     * Replaces {user}, {level}, {blissdog} (the latter depends on the guild id)
     *
     * @param message
     * @param level the member current level as a string
     * @param event
     * @return the message with the proper replacements
     */
    fun messageReplacement(message: String, level: String, event: MessageReceivedEvent): String {
        var msg = message
        //todo:: i need to test this again tomorrow
        var allChecked = false
        // Make a
            if (msg.contains("{user}")) {
                msg = msg.replace("{user}", event.author.asMention)
            }
            if (msg.contains("{level}")) {
                msg = msg.replace("{level}", level)
            }
            if (msg.contains("{blissdog}")) { // This line of code has been modified for the public edition.
                msg = msg.replace("{blissdog}", blissDog())
            }
        return msg
    }


    /**
     * Gets the member exp. If the member does not exist, or it's XP is null, it will return 0.
     * The member won't be created if it doesn't exist.
     *
     * @param memberId The member id
     * @param guildId The guild id
     * @return The member exp or zero if not found
     */
    fun getMemberExp(memberId: String, guildId: String): Long {
        val realm = Realm.open(configuration)
        return try {
            val memberXP = realm.query<RankMember>("realmGuild.id = $0 AND id = $1", guildId, memberId).first().find()
            memberXP?.exp ?: 0
        } finally {
            realm.close()
        }
    }

    /**
     * Gets all the members exp of a guild as represented in Realms. **This does not check if the members are actually still in the Discord guild**, and as such, it will show past members exp.
     * Current Discord members that have not gained any exp will not count.
     *
     * @param guildId The guild id
     * @return The members exp in a list, without the member ids
     */
    fun getAllMembersExps(guildId: String): List<Long> {
        val realm = Realm.open(configuration)
        return try {
            val query = realm.query<RankMember>("realmGuild.id = $0", guildId).find()
            val allMembersExp = mutableListOf<Long>()
            query.forEach {
                allMembersExp.add(it.exp)
            }
            allMembersExp
        } finally {
            realm.close()
        }
    }

    /**
     * Get available rank banners stored in /resources/rank/banners/
     *
     * **Warning**: It will only get the rank banners with a png extension
     * @throws NumberFormatException if the filename is not a valid representation of a number
     *
     * @return A list of integers containing the available rank banners without extension (e.g. [00, 05, 10, 20])
     */
    fun getAvailableRankBanners(): List<Int> {
        val availableRankBanners = mutableListOf<Int>()
        File("/resources/rank/banners").walkTopDown().forEach {
            if (it.extension == "png") {
                availableRankBanners.add(it.nameWithoutExtension.toInt())
            }
        }
        return availableRankBanners
    }

    /**
     * Get the rank position of a member in a guild. It's sorted descending by exp.
     *
     * @param member The member to get the rank position of
     * @return The rank position of the member
     */
    fun getRankPosition(member: Member): Long {
        val guildId = member.guild.id
        val memberExp = getMemberExp(member.id, guildId) // get the member's exp
        val exps = getAllMembersExps(guildId) // get all the members' exp

        // Sort the exps from biggest to smallest
        exps.sortedDescending().toSortedSet()
        // Get the index of the member's exp in the sorted list
        return exps.indexOf(memberExp).toLong() + 1 // +1 because the index starts at 0
    }

    class CalculateLevel(exp: Long) {
        var level: Long
        var leftover: Long
        var total: Long

        init {
            var level = 0.0
            var neoExp = exp

            while (neoExp > 0) {
                neoExp -= (5 * (level.pow(2)) + 50 * level + 100).toLong()
                level++
            }
            if (neoExp < 0) {
                level--
            }

            this.level = level.toLong()
            this.total = (5 * (level.pow(2)) + 50 * level + 100).toLong()
            this.leftover = total + neoExp
        }
        //todo: perhaps formatNumber can be moved here
    }

    private fun formatNumber(num: Long, shorten: Boolean = true): String {
        if (shorten) {
            if (num > 1000000000) return "${num / 100000000 / 10}b"
            if (num > 1000000) return "${num / 100000 / 10}m"
            if (num > 1000) return "${num / 100 / 10}k"
        }
        return num.toString().format(",").trim()
    }

    //this is the point of entrance for the rank system
    fun levelCheck(event: MessageReceivedEvent) {
        //todo: this might lack optimization
        val exp = getMemberExp(event.member!!.id, event.guild.id)
        val calculate = CalculateLevel(exp)
        val rankPosition = getRankPosition(event.member!!)

        // calculate.level
        // calculate.leftover
        if (calculate.level >= 100 && event.member!!.guild.id == "182551016309260288") {
            event.channel.sendMessage("<a:SuperDance:667049342326145025>").queue()
        }
        //compare calculate.level against a list of numbers and get the closest one that is not higher than the current level
        // There's might not be an image for levels defined by the guild. As such, we'll compare against the list of available rank banners.
        //
        // The desirable line of code:
        // val closestLevel = getAllGuildRankRoles(event.guild.id).filter { it.rankLevel!! <= calculate.level }.maxByOrNull { it.rankLevel!! }?.rankLevel ?: 0

        //todo: this needs to be added to the docs
        val closestRankBanner =
            String.format("%02d", (getAvailableRankBanners().filter { it <= calculate.level }.maxByOrNull { it } ?: 0))

        val image = ImageIO.read(File("src/main/resources/rank/banners/$closestRankBanner.png"))
        // get image width and height
        val w = image.width
        val h = image.height
        // We create a list of triples, where each triple is a text and the position of the text
        val fLeftover = formatNumber(calculate.leftover)
        val fTotal = formatNumber(calculate.total)

        val vipnagorgialla: Font =
            Font.createFont(Font.TRUETYPE_FONT, File("src/main/resources/rank/vipnagorgialla.ttf"))
        val regFont = vipnagorgialla.deriveFont(50f)
        val miniFont = vipnagorgialla.deriveFont(43f)
        val bigFont = vipnagorgialla.deriveFont(56f)

        val graphics2D = image.createGraphics()

        fun wtext(text: String): Int {
            return graphics2D.font.createGlyphVector(graphics2D.fontRenderContext, text).outline.bounds.width
        }


        fun rightPos(text: String): Int {
            return w - RIGHT_ALIGN_BUFFER - wtext(text)
        }


        fun centerPos(text: String): Int {
            return RANK_LEFT_W + (RANK_RIGHT_W - RANK_LEFT_W) / 2 - wtext(text) / 2
        }



        graphics2D.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY)
        graphics2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        graphics2D.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY)
        graphics2D.setRenderingHint(RenderingHints.KEY_DITHERING, RenderingHints.VALUE_DITHER_ENABLE)
        graphics2D.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON)
        graphics2D.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        graphics2D.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        graphics2D.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)


        graphics2D.font = regFont
        graphics2D.drawString("# $rankPosition", rightPos("# $rankPosition"), Guides.BASELINE_SCORE_H)
        graphics2D.font = miniFont
        graphics2D.drawString("${fLeftover}/${fTotal}", rightPos("${fLeftover}/${fTotal}"), Guides.BASELINE_NEXT_LEVEL_H)
        graphics2D.font = regFont
        graphics2D.drawString("$exp XP", rightPos("$exp XP"), BASELINE_TOTAL_XP_H)
        graphics2D.font = bigFont
        graphics2D.drawString("${calculate.level}", centerPos("${calculate.level}"), BASELINE_RANK_H)
        graphics2D.color = java.awt.Color.WHITE
        graphics2D.dispose()

        // create an InputStream to the image
        val file = ByteArrayOutputStream().use {
            ImageIO.write(image, "png", it)
            ByteArrayInputStream(it.toByteArray())
        }

        event.message.reply(file, "${event.author.asTag}.png").queue()
    }

    fun leaderboard(event: MessageReceivedEvent) {
        //todo: i need to find some sort of embed paginator

        //This gets the top 100 members by exp sorted by highest to lowest. Now, i need to put this in a paginator.
        //limit to the first 10

        val realm = Realm.open(configuration)
        try {
            val top100 = realm.query<RankMember>("realmGuild.id = $0 SORT(exp DESC) LIMIT(100)", event.guild.id).find().toSet().take(10)
        val rank1 = Embed {
            title = "Top 10"
            description = "The top 10 members by experience"
            top100.forEach {
                field {
                    name = event.jda.getUserById(it.id)?.asTag ?: "Unknown User"
                    value = "${it.exp} XP"
                }
            }
            color = 0x00ff00
            footer {
                name = event.guild.name
                iconUrl = event.guild.iconUrl
            }
        }
        event.channel.sendMessageEmbeds(rank1).queue()
        } finally {
            realm.close()
        }
    }

    suspend fun toggle(event: SlashCommandInteractionEvent) {
        if (!permissionCheck(event, Permission.ADMINISTRATOR)) {
            return
        }
        //toggle Ranks gain ON and OFF
        val realm = Realm.open(configuration)

        var newToggleStatus = true
        try {
            var rankSettings: RankSettings?
            // Realm objects can only be changed inside realm writes
            realm.write {
                rankSettings = this.query<RankSettings>("realmGuild.id == $0", event.guild!!.id).first().find()
                    ?: RankSettings().create(this, event.guild!!.id)
                val localRS = findLatest(rankSettings!!)!!.apply {
                    newToggleStatus = !toggle
                    toggle = newToggleStatus
                }
                copyToRealm(localRS, UpdatePolicy.ALL)
            }
        } catch (e: Throwable) {
            e.printStackTrace()
            event.hook.sendMessageEmbeds(
                NeoSuperThrow {
                    throwable = e
                }).queue()
            return
        } finally {
            realm.close()
        }

        val toggleAsString = if (newToggleStatus) {
            "ON"
        } else {
            "OFF"
        }
        event.hook.sendMessageEmbeds(
            NeoSuperEmbed {
                type = SuperEmbed.ResultType.SUCCESS
                text = "**Ranks** is now $toggleAsString"
            }).queue()
    }

    suspend fun addSpecialRank(event: SlashCommandInteractionEvent, rank: Int, message: String, role: Role?) {
        //todo: it does not check currently if the the same rank level (eg: 10) already exists. It creates duplicates.
        // I've made it so it does not duplicate but I DID NOT TEST.
        if (!permissionCheck(event, Permission.ADMINISTRATOR)) {
            return
        }
        //add a special rank to the database
        val realm = Realm.open(configuration)

        // This contains the best logic for creating a new realm object when it doesn't exist and edit if it does.
        try {
            realm.write {
                val rankSettings = this.query<RankSettings>("realmGuild.id == $0", event.guild!!.id).first().find()
                    ?: RankSettings().create(this, event.guild!!.id)
                rankSettings.apply {
                    //add a special rank.
                    xpRankRoles.firstOrNull { it.rankLevel == rank }?.let {
                        it.id = role?.id
                        it.specialLevelUpMessage = message
                    } ?: xpRankRoles.add(
                        XPRankRoles().apply {
                            id = role?.id
                            rankLevel = rank
                            specialLevelUpMessage = message
                        }
                    )
                }
                copyToRealm(rankSettings, UpdatePolicy.ALL)
            }
        } catch (e: Throwable) {
            e.printStackTrace()
            event.hook.sendMessageEmbeds(
                NeoSuperThrow {
                    throwable = e
                }).queue()
            return
        } finally {
            realm.close()
        }
        event.hook.sendMessageEmbeds(
            NeoSuperEmbed {
                type = SuperEmbed.ResultType.SUCCESS
                text = "Added successfully!"
            }).queue()
    }

    fun manualSetRank(event: SlashCommandInteractionEvent) {
        //set rank manually. It changes their rank, it does not add or remove XP.
    }

    suspend fun setLevelUpMessage(event: SlashCommandInteractionEvent, message: String) {
        if (!permissionCheck(event, Permission.ADMINISTRATOR)) {
            return
        }
        //set the message that is sent when a user levels up
        val realm = Realm.open(configuration)

        try {
            realm.write {
                val rankSettings = this.query<RankSettings>("realmGuild.id == $0", event.guild!!.id).first().find()
                    ?: RankSettings().create(this, event.guild!!.id)
                rankSettings.apply {
                    showMessage = true
                    xpUpMessage = message
                }
                copyToRealm(rankSettings, UpdatePolicy.ALL)
            }
        } catch (e: Throwable) {
            e.printStackTrace()
            event.hook.sendMessageEmbeds(
                NeoSuperThrow {
                    throwable = e
                }).queue()
            return
        } finally {
            realm.close()
        }
        event.hook.sendMessageEmbeds(
            NeoSuperEmbed {
                type = SuperEmbed.ResultType.SUCCESS
                text = "Level up message set successfully!"
            }).queue()
    }

    suspend fun toggleLevelUpMessage(event: SlashCommandInteractionEvent) {
        if (!permissionCheck(event, Permission.ADMINISTRATOR)) {
            return
        }
        //toggle the message that is sent when a user levels up
        val realm = Realm.open(configuration)

        var newToggleStatus = true
        try {
            realm.write {
                val rankSettings = this.query<RankSettings>("realmGuild.id == $0", event.guild!!.id).first().find()
                    ?: RankSettings().create(this, event.guild!!.id)
                rankSettings.apply {
                    newToggleStatus = !showMessage
                    showMessage = newToggleStatus
                }
                copyToRealm(rankSettings, UpdatePolicy.ALL)
            }
        } catch (e: Throwable) {
            e.printStackTrace()
            event.hook.sendMessageEmbeds(
                NeoSuperThrow {
                    throwable = e
                }).queue()
            return
        } finally {
            realm.close()
        }

        val toggleAsString = if (newToggleStatus) {
            "ON"
        } else {
            "OFF"
        }
        event.hook.sendMessageEmbeds(
            NeoSuperEmbed {
                type = SuperEmbed.ResultType.SUCCESS
                text = "**Ranks** level up message is now $toggleAsString"
            }).queue()
    }

    suspend fun setXPGain(event: SlashCommandInteractionEvent, xp: Int, cooldown: Int) {
        if (!permissionCheck(event, Permission.ADMINISTRATOR)) {
            return
        }
        //set the amount of XP gained when a user levels up
        // xp needs to be Min: 1 | Max: 1000
        // cooldown needs to be Min: 1 | Max: 120
        if (xp < 1 || xp > 1000) {
            event.hook.sendMessageEmbeds(
                NeoSuperEmbed {
                    type = SuperEmbed.ResultType.ERROR
                    text = "XP needs to be between 1 and 1000"
                }).queue()
            return
        }
        if (cooldown < 1 || cooldown > 120) {
            event.hook.sendMessageEmbeds(
                NeoSuperEmbed {
                    type = SuperEmbed.ResultType.ERROR
                    text = "Cooldown needs to be between 1 and 120"
                }).queue()
            return
        }
        val realm = Realm.open(configuration)

        try {
            realm.write {
                val rankSettings = this.query<RankSettings>("realmGuild.id == $0", event.guild!!.id).first().find()
                    ?: RankSettings().create(this, event.guild!!.id)
                rankSettings.apply {
                    xpPerMessage = xp
                    xpInterval = cooldown
                }
                copyToRealm(rankSettings, UpdatePolicy.ALL)
                event.hook.sendMessageEmbeds(
                    NeoSuperEmbed {
                        type = SuperEmbed.ResultType.SUCCESS
                        text = "XP gain now set to $xp!\nCooldown now set to $cooldown second(s)!"
                    }).queue()
            }
        } catch (e: Throwable) {
            e.printStackTrace()
            event.hook.sendMessageEmbeds(
                NeoSuperThrow {
                    throwable = e
                }).queue()
            return
        } finally {
            realm.close()
        }
    }

    suspend fun addIgnoreRole(event: SlashCommandInteractionEvent, role: Role) {
        if (!permissionCheck(event, Permission.ADMINISTRATOR)) {
            return
        }
        //add a role to the list of roles that do not gain XP
        val realm = Realm.open(configuration)

        try {
            realm.write {
                val rankSettings = this.query<RankSettings>("realmGuild.id == $0", event.guild!!.id).first().find()
                    ?: RankSettings().create(this, event.guild!!.id)
                if (role.id in rankSettings.ignoredRoles) {
                    event.hook.sendMessageEmbeds(
                        NeoSuperEmbed {
                            type = SuperEmbed.ResultType.ERROR
                            text = "Role is already ignored!"
                        }).queue()
                    return@write
                }
                rankSettings.apply {
                    ignoredRoles.add(role.id)
                }
                copyToRealm(rankSettings, UpdatePolicy.ALL)
                event.hook.sendMessageEmbeds(
                    NeoSuperEmbed {
                        type = SuperEmbed.ResultType.SUCCESS
                        text = "Added the role to ignored roles!"
                    }).queue()
            }
        } catch (e: Throwable) {
            e.printStackTrace()
            event.hook.sendMessageEmbeds(
                NeoSuperThrow {
                    throwable = e
                }).queue()
            return
        } finally {
            realm.close()
        }
    }

    suspend fun addIgnoreChannel(event: SlashCommandInteractionEvent, channel: GuildChannel) {
        if (!permissionCheck(event, Permission.ADMINISTRATOR)) {
            return
        }
        //add a channel to the list of channels that do not gain XP
        val realm = Realm.open(configuration)

        try {
            realm.write {
                val rankSettings = this.query<RankSettings>("realmGuild.id == $0", event.guild!!.id).first().find()
                    ?: RankSettings().create(this, event.guild!!.id)
                if (channel.id in rankSettings.ignoredChannels) {
                    event.hook.sendMessageEmbeds(
                        NeoSuperEmbed {
                            type = SuperEmbed.ResultType.ERROR
                            text = "Channel is already ignored!"
                        }).queue()
                    return@write
                }
                rankSettings.apply {
                    ignoredChannels.add(channel.id)
                }
                copyToRealm(rankSettings, UpdatePolicy.ALL)
                event.hook.sendMessageEmbeds(
                    NeoSuperEmbed {
                        type = SuperEmbed.ResultType.SUCCESS
                        text = "Added the channel to ignored channels!"
                    }).queue()
            }
        } catch (e: Throwable) {
            e.printStackTrace()
            event.hook.sendMessageEmbeds(
                NeoSuperThrow {
                    throwable = e
                }).queue()
            return
        } finally {
            realm.close()
        }
    }

    suspend fun removeSpecialRank(event: SlashCommandInteractionEvent, rank: Int) {
        if (!permissionCheck(event, Permission.ADMINISTRATOR)) {
            return
        }
        //remove a special rank from the list of special ranks
        val realm = Realm.open(configuration)

        try {
            realm.write {
                val rankSettings = this.query<RankSettings>("realmGuild.id == $0", event.guild!!.id).first().find()
                    ?: RankSettings().create(this, event.guild!!.id)
                if (!rankSettings.xpRankRoles.any { it.rankLevel == rank }) {
                    event.hook.sendMessageEmbeds(
                        NeoSuperEmbed {
                            type = SuperEmbed.ResultType.ERROR
                            text = "Rank is not a special rank!"
                        }).queue()
                    realm.close()
                    return@write
                }
                rankSettings.apply {
                    xpRankRoles.firstOrNull { it.rankLevel == rank }?.let {
                        xpRankRoles.remove(it)
                    }
                }
                copyToRealm(rankSettings, UpdatePolicy.ALL)
                event.hook.sendMessageEmbeds(
                    NeoSuperEmbed {
                        type = SuperEmbed.ResultType.SUCCESS
                        text = "Removed special rank successfully!"
                    }).queue()
            }
        } catch (e: Throwable) {
            e.printStackTrace()
            event.hook.sendMessageEmbeds(
                NeoSuperThrow {
                    throwable = e
                }).queue()
            return
        } finally {
            realm.close()
        }

    }

    suspend fun removeIgnoreRole(event: SlashCommandInteractionEvent, role: Role) {
        if (!permissionCheck(event, Permission.ADMINISTRATOR)) {
            return
        }
        //remove a role from the list of roles that do not gain XP
        val realm = Realm.open(configuration)

        try {
            realm.write {
                val rankSettings = this.query<RankSettings>("realmGuild.id == $0", event.guild!!.id).first().find()
                    ?: RankSettings().create(this, event.guild!!.id)
                if (role.id !in rankSettings.ignoredRoles) {
                    event.hook.sendMessageEmbeds(
                        NeoSuperEmbed {
                            type = SuperEmbed.ResultType.ERROR
                            text = "Role is not ignored!"
                        }).queue()
                    return@write
                }
                rankSettings.apply {
                    ignoredRoles.remove(role.id)
                }
                copyToRealm(rankSettings, UpdatePolicy.ALL)
                event.hook.sendMessageEmbeds(
                    NeoSuperEmbed {
                        type = SuperEmbed.ResultType.SUCCESS
                        text = "Removed the role from ignored roles!"
                    }).queue()
            }
        } catch (e: Throwable) {
            e.printStackTrace()
            event.hook.sendMessageEmbeds(
                NeoSuperThrow {
                    throwable = e
                }).queue()
            return
        } finally {
            realm.close()
        }
    }

    suspend fun removeIgnoreChannel(event: SlashCommandInteractionEvent, channel: GuildChannel) {
        if (!permissionCheck(event, Permission.ADMINISTRATOR)) {
            return
        }
        //remove a channel from the list of channels that do not gain XP
        val realm = Realm.open(configuration)

        try {
            realm.write {
                val rankSettings = this.query<RankSettings>("realmGuild.id == $0", event.guild!!.id).first().find()
                    ?: RankSettings().create(this, event.guild!!.id)
                if (channel.id !in rankSettings.ignoredChannels) {
                    event.hook.sendMessageEmbeds(
                        NeoSuperEmbed {
                            type = SuperEmbed.ResultType.ERROR
                            text = "Channel is not ignored!"
                        }).queue()
                    return@write
                }
                rankSettings.apply {
                    ignoredChannels.remove(channel.id)
                }
                copyToRealm(rankSettings, UpdatePolicy.ALL)
                event.hook.sendMessageEmbeds(
                    NeoSuperEmbed {
                        type = SuperEmbed.ResultType.SUCCESS
                        text = "Removed the channel from ignored channels!"
                    }).queue()
            }
        } catch (e: Throwable) {
            e.printStackTrace()
            event.hook.sendMessageEmbeds(
                NeoSuperThrow {
                    throwable = e
                }).queue()
            return
        } finally {
            realm.close()
        }
    }

    fun getXP(event: SlashCommandInteractionEvent) {
        if (!permissionCheck(event, Permission.ADMINISTRATOR)) {
            return
        }
        //get the XP gain of the guild
        val realm = Realm.open(configuration)

        try {
            val rankSettings = realm.query<RankSettings>("realmGuild.id == $0", event.guild!!.id).first().find()
            if (rankSettings == null) {
                event.hook.sendMessageEmbeds(
                    NeoSuperEmbed {
                        type = SuperEmbed.ResultType.ERROR
                        text = "There are no rank settings for this guild!"
                    }).queue()
                return
            }
            event.hook.sendMessageEmbeds(
                NeoSuperEmbed {
                    type = SuperEmbed.ResultType.INFORMATIVE
                    text =
                        "`${rankSettings.xpPerMessage}` XP per message\n${rankSettings.xpInterval} seconds between messages"
                }).queue()
        } catch (e: Throwable) {
            e.printStackTrace()
            event.hook.sendMessageEmbeds(
                NeoSuperThrow {
                    throwable = e
                }).queue()
            return
        } finally {
            realm.close()
        }
    }

    fun getMessage(event: SlashCommandInteractionEvent) {
        if (!permissionCheck(event, Permission.ADMINISTRATOR)) {
            return
        }
        //get the message that is sent when a user gains XP
        val realm = Realm.open(configuration)

        try {
            val rankSettings = realm.query<RankSettings>("realmGuild.id == $0", event.guild!!.id).first().find()
            if (rankSettings == null) {
                event.hook.sendMessageEmbeds(
                    NeoSuperEmbed {
                        type = SuperEmbed.ResultType.ERROR
                        text = "There are no rank settings for this guild!"
                    }).queue()
                realm.close()
                return
            }
            event.hook.sendMessageEmbeds(
                NeoSuperEmbed {
                    type = SuperEmbed.ResultType.INFORMATIVE
                    text = "The message is: `${rankSettings.xpUpMessage}`"
                    if (rankSettings.xpUpMessage.contains("{mention}")) {
                        text += "\n`{mention}` will be replaced with the user's mention"
                    }
                    if (rankSettings.xpUpMessage.contains("{level}")) {
                        text += "\n`{level}` will be replaced with the user's level"
                    }
                    if (rankSettings.xpUpMessage.isEmpty()) {
                        text = "There is no message set."
                    }
                }).queue()
        } catch (e: Throwable) {
            e.printStackTrace()
            event.hook.sendMessageEmbeds(
                NeoSuperThrow {
                    throwable = e
                }).queue()
            return
        } finally {
            realm.close()
        }
    }

    fun getSpecialRank(event: SlashCommandInteractionEvent) {
        if (!permissionCheck(event, Permission.ADMINISTRATOR)) {
            return
        }
        //get the special ranks
        val realm = Realm.open(configuration)
        //todo: i need to test this

        try {
            val rankSettings = realm.query<RankSettings>("realmGuild.id == $0", event.guild!!.id).first().find()
            if (rankSettings == null) {
                event.hook.sendMessageEmbeds(
                    NeoSuperEmbed {
                        type = SuperEmbed.ResultType.ERROR
                        text = "There are no rank settings for this guild!"
                    }).queue()
                realm.close()
                return
            }
            lateinit var inputStream: InputStream
            event.hook.sendMessageEmbeds(
                NeoSuperEmbed {
                    type = SuperEmbed.ResultType.INFORMATIVE
                    text =
                        "Information not suitable to be displayed here. I've prepared a JSON file with the information."
                    // Translate rankSettings.xpRankRoles to a JSON format
                    val json = JSONObject().apply {
                        put("specialranks", rankSettings.xpRankRoles.map {
                            JSONObject().apply {
                                put("id", it.id)
                                put("rankLevel", it.rankLevel)
                                put("specialLevelUpMessage", it.specialLevelUpMessage)
                            }
                        })
                    }
                    // Write the JSON to an input stream
                    inputStream = ByteArrayInputStream(
                        json.toString(2).toByteArray(Charsets.UTF_8)
                    )
                }).addFile(inputStream, "specialranks.json").queue()
        } catch (e: Throwable) {
            e.printStackTrace()
            event.hook.sendMessageEmbeds(
                NeoSuperThrow {
                    throwable = e
                }).queue()
        } finally {
            realm.close()
        }
    }

    fun getIgnoreRole(event: SlashCommandInteractionEvent) {
        if (!permissionCheck(event, Permission.ADMINISTRATOR)) {
            return
        }
        //get the ignored roles
        val realm = Realm.open(configuration)

        try {
            val rankSettings = realm.query<RankSettings>("realmGuild.id == $0", event.guild!!.id).first().find()
            if (rankSettings == null) {
                event.hook.sendMessageEmbeds(
                    NeoSuperEmbed {
                        type = SuperEmbed.ResultType.ERROR
                        text = "There are no rank settings for this guild!"
                    }).queue()
                realm.close()
                return
            }
            val roleMentionList = mutableListOf<String>()
            rankSettings.ignoredRoles.forEach {
                event.guild!!.getRoleById(it)?.asMention?.let { it1 -> roleMentionList.add(it1) }
            }
            event.hook.sendMessageEmbeds(
                NeoSuperEmbed {
                    type = SuperEmbed.ResultType.INFORMATIVE
                    text = "Here's the list of ignored roles: ${roleMentionList.joinToString(" ")}"
                }).queue()
        } catch (e: Throwable) {
            e.printStackTrace()
            event.hook.sendMessageEmbeds(
                NeoSuperThrow {
                    throwable = e
                }).queue()
        } finally {
            realm.close()
        }
    }

    fun getIgnoreChannel(event: SlashCommandInteractionEvent) {
        if (!permissionCheck(event, Permission.ADMINISTRATOR)) {
            return
        }
        //get the ignored channels
        val realm = Realm.open(configuration)

        try {
            val rankSettings = realm.query<RankSettings>("realmGuild.id == $0", event.guild!!.id).first().find()
            if (rankSettings == null) {
                event.hook.sendMessageEmbeds(
                    NeoSuperEmbed {
                        type = SuperEmbed.ResultType.ERROR
                        text = "There are no rank settings for this guild!"
                    }).queue()
                realm.close()
                return
            }
            val channelMentionList = mutableListOf<String>()
            rankSettings.ignoredChannels.forEach {
                event.guild!!.getGuildChannelById(it)?.asMention?.let { it1 -> channelMentionList.add(it1) }
            }
            event.hook.sendMessageEmbeds(
                NeoSuperEmbed {
                    type = SuperEmbed.ResultType.INFORMATIVE
                    text = "Here's the list of ignored channels: ${channelMentionList.joinToString(" ")}"
                }).queue()
        } catch (e: Throwable) {
            e.printStackTrace()
            event.hook.sendMessageEmbeds(
                NeoSuperThrow {
                    throwable = e
                }).queue()
            return
        } finally {
            realm.close()
        }
    }

    fun getUserInfo(event: SlashCommandInteractionEvent, userId: String) {
        if (!permissionCheck(event, Permission.ADMINISTRATOR)) {
            return
        }
        //get the user info
        val realm = Realm.open(configuration)

        try {
            val rankMember =
                realm.query<RankMember>("realmGuild.id == $0 AND id == $1", event.guild!!.id, userId).first().find()
            if (rankMember == null) {
                event.hook.sendMessageEmbeds(
                    NeoSuperEmbed {
                        type = SuperEmbed.ResultType.ERROR
                        text = "There's no data for this user!"
                    }).queue()
                realm.close()
                return
            }
            event.hook.sendMessageEmbeds(
                NeoSuperEmbed {
                    type = SuperEmbed.ResultType.INFORMATIVE
                    text = // If the user isn't in the guild, it will just show the user's id
                        "${event.guild!!.getMemberById(rankMember.id)?.asMention ?: rankMember.id} has ${rankMember.exp} XP and is level ${
                            CalculateLevel(rankMember.exp).level
                        }"
                }).queue()
        } catch (e: Throwable) {
            e.printStackTrace()
            event.hook.sendMessageEmbeds(
                NeoSuperThrow {
                    throwable = e
                }).queue()
            return
        } finally {
            realm.close()
        }
    }

    fun giveRank(event: SlashCommandInteractionEvent, rank: Int, user: User) {
        if (!permissionCheck(event, Permission.ADMINISTRATOR)) {
            return
        }
        event.hook.send("This command is not yet implemented!").queue()
    }

    suspend fun giveXP(event: SlashCommandInteractionEvent, xp: Long, user: User) {
        if (!permissionCheck(event, Permission.ADMINISTRATOR)) {
            return
        }
        // give xp to a user
        val realm = Realm.open(configuration)

        try {
            // add xp to the user
            realm.write {
                val rankMember =
                    this.query<RankMember>("realmGuild.id == $0 AND id == $1", event.guild!!.id, user.id).first().find()
                if (rankMember == null) {
                    event.hook.sendMessageEmbeds(
                        NeoSuperEmbed {
                            type = SuperEmbed.ResultType.ERROR
                            text = "There's no data for this user - therefore you can't give XP to them."
                        }).queue()
                    return@write
                }
                val previousLevel = CalculateLevel(rankMember.exp).level
                rankMember.apply {
                    exp += xp
                }
                val newLevel = CalculateLevel(rankMember.exp).level
                copyToRealm(rankMember, UpdatePolicy.ALL)
                event.hook.sendMessageEmbeds(
                    NeoSuperEmbed {
                        type = SuperEmbed.ResultType.SUCCESS
                        text = "Added $xp XP to ${user.asMention}! Now they have ${rankMember.exp} XP"
                        if (newLevel > previousLevel) {
                            text += " and went up to level $newLevel"
                        }
                        text += "!"
                    }).queue()
            }
        } catch (e: Throwable) {
            e.printStackTrace()
            event.hook.sendMessageEmbeds(
                NeoSuperThrow {
                    throwable = e
                }).queue()
            return
        } finally {
            realm.close()
        }
    }

    suspend fun onMessageReceived(event: MessageReceivedEvent) {
        //when a message is received
        if (event.author.isBot || !event.isFromGuild) { //if it's a bot or isn't from a guild
            return
        }
        val authorId = event.author.id
        val guildId = event.guild.id
        val realm = Realm.open(configuration)
        val settings =
            realm.query<RankSettings>("realmGuild.id == $0", guildId).first().find() //if there are no settings, return
        if ((settings == null)
            || !(settings.toggle)
            || (settings.ignoredChannels.contains(event.channel.id))
            || (event.member?.roles?.any { settings.ignoredRoles.contains(it.id) } == true)
            || !(cooldowns.none { it == Pair(authorId, guildId) })
        ) {
            // checks if ranks are on,
            // if the message isn't from an ignored channels,
            // if the member doesn't have an ignored role
            // if the user hasn't gained XP in the last X seconds (is on a cooldown)
            realm.close()
            return
        }
        // the user attends all conditions to rank up with this message.

        // The cooldown logic for each user.
        // Uses a Deque to store the IDs at the bottom of the deque.
        // A scheduled executor deletes each element at the top 15 second after being added.
        cooldowns.addScheduled(Pair(authorId, guildId), settings.xpInterval.toLong())
        val exp = getMemberExp(authorId, guildId)
        val calc = CalculateLevel(exp)
        println("Previous level: ${calc.level}")
        val newExp = exp + settings.xpPerMessage
        val membersRealm = Realm.open(configuration)
        realm.write {
            val rankMember =
                this.query<RankMember>("realmGuild.id == $0 AND id == $1", guildId, authorId).first().find()
                    ?: RankMember().create(this, guildId, authorId)
            rankMember.apply {
                this.exp = newExp
            }
            copyToRealm(rankMember, UpdatePolicy.ALL)
        }

        //todo: I need to add this trick to the docs. You can set it to an invalid role id (like 0) if you just want to send a message and not give any role.
        // Please note: It might be possible in the future to warn moderators abour invalid role ids. Although this is only a possible feature, it's recommended to use set the roleID to 0 if you want to use the aforementioned trick in order to avoid conflit.

        //todo: add dogs

        CalculateLevel(newExp).also { calcLevel ->
            println("New level: ${calcLevel.level}")
            if (calcLevel.level > calc.level) {
                println("Level up!")
                //if the user levels up
                var rankUpMessage = settings.xpUpMessage
                //todo: Its not sending the special level up message. It can recognize when they leveled up.
                //It seems that xpRankRoles is empty.

                // if the user reached a special level, we need to add the special message to the rankUpMessage.
                settings.xpRankRoles.firstOrNull { role -> calcLevel.level.toInt() == role.rankLevel }
                    ?.also { role ->
                        //if the user levels up
                        val roleToAdd = role.id?.let { event.guild.getRoleById(it) }
                        if (roleToAdd != null) {
                            event.guild.addRoleToMember(event.member!!, roleToAdd).queue()
                        }
                        //Ignore if the roleid isn't valid. Either if It's intentional or not.
                        rankUpMessage += "\n${role.specialLevelUpMessage}"
                    }
                rankUpMessage = messageReplacement(rankUpMessage, calcLevel.level.toString(), event)
                event.channel.sendMessage(rankUpMessage).queue()
            }
        }
        membersRealm.close()
    }

    suspend fun onMemberJoin(event: GuildMemberJoinEvent) {
        //todo: this lacks testing
        val memberId = event.member.id
        val guildId = event.guild.id
        val realm = Realm.open(configuration)
        try {
            val settings =
                realm.query<RankSettings>("realmGuild.id == $0", guildId).first().find() //if there are no settings, return
            if ((settings == null) || !(settings.toggle)) {
                return
            }
            val exp = getMemberExp(memberId, guildId)
            if (exp == 0L) {
                return
            }
            val level = CalculateLevel(exp).level
            realm.write {
                settings.xpRankRoles.forEach { role ->
                        if ((role.rankLevel != null) && (level >= role.rankLevel!!)) {
                            event.guild.getRoleById(role.id!!)?.let { event.guild.addRoleToMember(event.member, it).queue() }
                        }
                }
            }
        } catch (e: Throwable) {
            e.printStackTrace()
            return
        } finally {
            realm.close()
        }
    }
}


// Extend Deque to add an element and delete it after 15 seconds using a scheduled executor.
class ScheduledDeque<T>(private val deque: Deque<T>) : Deque<T> by deque {
    private val scheduledExecutor = Executors.newSingleThreadScheduledExecutor()

    fun addScheduled(T: T, delay: Long) {
        deque.add(T)
        println("Added the following userID and guildID to the deque: $T")
        scheduledExecutor.schedule({
            deque.removeFirstOccurrence(T)
            println("Removed the following userID and guildID from the deque: $T")
        }, delay, TimeUnit.SECONDS)
    }
}

