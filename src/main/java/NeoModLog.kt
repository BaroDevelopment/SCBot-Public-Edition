
import RealmUtils.create
import commands.Ranks
import dev.minn.jda.ktx.EmbedBuilder
import dev.minn.jda.ktx.InlineEmbed
import dev.minn.jda.ktx.await
import embedutils.*
import io.realm.kotlin.Realm
import io.realm.kotlin.RealmConfiguration
import io.realm.kotlin.UpdatePolicy
import io.realm.kotlin.ext.query
import io.realm.kotlin.types.RealmObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.audit.ActionType
import net.dv8tion.jda.api.audit.AuditLogEntry
import net.dv8tion.jda.api.entities.*
import net.dv8tion.jda.api.events.guild.GuildBanEvent
import net.dv8tion.jda.api.events.guild.GuildUnbanEvent
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent
import net.dv8tion.jda.api.events.guild.member.update.GuildMemberUpdateNicknameEvent
import net.dv8tion.jda.api.events.guild.member.update.GuildMemberUpdateTimeOutEvent
import net.dv8tion.jda.api.events.message.MessageBulkDeleteEvent
import net.dv8tion.jda.api.events.message.MessageDeleteEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.events.message.MessageUpdateEvent
import net.dv8tion.jda.api.interactions.components.buttons.Button
import net.dv8tion.jda.api.utils.TimeFormat
import org.jetbrains.kotlinx.dataframe.DataFrame
import org.jetbrains.kotlinx.dataframe.io.readJson
import org.ocpsoft.prettytime.nlp.PrettyTimeParser
import org.ocpsoft.prettytime.shade.net.fortuna.ical4j.model.TimeZone
import java.time.Instant


object NeoModLog {
    internal val configuration =
        RealmConfiguration.Builder(schema = setOf(RealmGuild::class, Channels::class, Ping::class)).name("modlog.realm")
            .schemaVersion(1.3.toLong()).build()

    class Ping : RealmObject {
        var userToPing: String = "" // The user that is going to be pinged
        var userToReturn: String = "" // The user that triggers this ping
        var realmGuild: RealmGuild? = null
    }

    class Channels : RealmObject {
        var toggle: Boolean = false
        var one: String = "" // Channel for joins, leaves, kicks, bans and unbans.
        var two: String = "" // General Modlog channel for everything not included in getChannel2
        var realmGuild: RealmGuild? = null
    }


    /**
     * Checks if the message contains more than 1024 characters (the max allowed in embed fields) and reduces it to 1024 characters if it does.
     * The last 3 characters are replaced with "..." to indicate that the message was shortened.
     *
     * @param string The message to check
     */
    private fun checkAndReduce(string: String): String {
        return if (string.length > 1024) {
            string.substring(0, 1020) + "..."
        } else {
            string
        }
    }

    /**
     * Get the latest entry from a RealmGuild's Audit Log Entry inside a coroutine.
     * If the latest entry is not equal to the defined type, it will return null.
     *
     * @param guild The guild to get the latest entry from
     * @param actionType The action type the latest entry should be
     * @return Nullable AuditLogEntry
     */
    private suspend fun latestAuditLogEntry(guild: Guild, actionType: ActionType): Collection<AuditLogEntry>? =
        runBlocking {
            guild.retrieveAuditLogs().limit(1).type(actionType).await()
        }

    /**
     * Get a member's roles in a pretty list used in ModLogs embeds.
     *
     * @param member Member
     * @return List-like roles as mentions with a line for each one
     */
    private suspend fun roles(member: Member?): String {
        if (member == null) {
            return "\n**Roles Not Cached:**"
        }
        var roles = ""
        val forLoop = CoroutineScope(Dispatchers.Default).launch {
            for (i in member.roles) {
                roles += "${i.asMention}\n"
            }
        }
        forLoop.join()
        return "\n**Roles (${member.roles.size}):**\n${roles}"
    }

    /**
     * Get a returning member's roles in a pretty list used in ModLogs embeds.
     * Works by bridging SCBot's data from ReJoined cog.
     * **Note: It will be empty if it's the first time the member joins the guild.**
     *
     * @param member Member
     * @return List-like roles as mentions with a line for each one
     */
    private suspend fun getStoredRoles(member: Member): String {
        //It gets uses the path of the ReJoined json. It only reads, so it is no biggie.
        kotlin.runCatching {
            val df =
                DataFrame.readJson("/home/ubuntu/.local/share/Red-DiscordBot/data/scbot/cogs/ReJoined/settings.json")
            val newDataFrame = df["1593748908"]["MEMBER"][member.guild.id][member.id]
            val rolesDF = newDataFrame["member_roles"][0].toString().trim().split(" ")
            //The role IDs are separated by a single whitespace.
            var roles = ""
            val forLoop = CoroutineScope(Dispatchers.Default).launch {
                for (i in rolesDF) {
                    val asRole = member.guild.getRoleById(i)
                    roles += "${asRole?.asMention}\n"
                }
            }
            forLoop.join()
            return "\n**Roles (${rolesDF.size}):**\n${roles}"
        }
        return ""
    }

    /**
     * Get a returning member's last time in the guild.
     * Works by bridging SCBot's data from ReJoined cog.
     * **Note: It will be null if it's the first time the member joins the guild.**
     *
     * @param member Member
     * @return Nullable String meant to be parsed
     */
    private fun getLastTime(member: Member): String? {
        //works just like getStoredRoles
        kotlin.runCatching {
            val df =
                DataFrame.readJson("/home/ubuntu/.local/share/Red-DiscordBot/data/scbot/cogs/ReJoined/settings.json")
            val newDataFrame = df["1593748908"]["MEMBER"][member.guild.id][member.id]
            return newDataFrame["member_last_time"][0].toString()
            //String example: "2021-05-07 10:24:24.818039"
        }
        return null
    }

    /**
     * Get the author's tag from an Audit Log Entry reason.
     *
     * @param reason String containing the author's tag
     * @return Non-null String containing the tag for this user, for example DV8FromTheWorld#6297
     */
    private fun getModeratorTag(reason: String): String {
        var tag = reason
        tag = tag.replace("Action requested by ".toRegex(), "")
        tag = tag.replace("\\(ID \\w+\\).$".toRegex(), "")
        return tag
    }

    private fun embedBuilder(member: Member?, user: User?): InlineEmbed {
        return EmbedBuilder {
            timestamp = Instant.now()
            thumbnail = member?.user?.effectiveAvatarUrl ?: user?.effectiveAvatarUrl
            footer {
                name = "User ID: " + (member?.id ?: user?.id)
            }
        }
    }

    private fun embedBuilder(member: Member): InlineEmbed {
        return EmbedBuilder {
            timestamp = Instant.now()
            thumbnail = member.user.effectiveAvatarUrl
            footer {
                name = "User ID: ${member.id}"
            }
        }
    }

    private fun embedBuilder(message: Message): InlineEmbed {
        return EmbedBuilder {
            timestamp = Instant.now()
            thumbnail = message.author.effectiveAvatarUrl
            footer {
                name = "User ID: ${message.author.id} • Message ID: ${message.id}"
            }
        }
    }

    private fun embedBuilder(message: MessageLite?): InlineEmbed {
        return EmbedBuilder {
            timestamp = Instant.now()
            thumbnail = message?.effectiveAvatarUrl
            footer {
                name = "User ID: ${message?.authorID ?: "Unknown"} • Message ID: ${message?.id ?: "Unknown"}"
            }
        }
    }

    suspend fun toggle(event: MessageReceivedEvent) {
        //toggle Channels gain ON and OFF
        val realm = Realm.open(Ranks.configuration)

        var newToggleStatus = true
        try {
            var channels = realm.query<Channels>("realmGuild.id == $0", event.guild.id).first().find()
            // Realm objects can only be changed inside realm writes
            realm.write {
                channels = channels ?: Channels().create(this, event.guild.id)
                channels?.apply {
                    newToggleStatus = !toggle
                    toggle = newToggleStatus
                }
                copyToRealm(channels!!, UpdatePolicy.ALL)
            }
        } catch (e: Throwable) {
            e.printStackTrace()
            event.channel.sendMessageEmbeds(
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
        event.channel.sendMessageEmbeds(
            NeoSuperEmbed {
                type = SuperEmbed.ResultType.SUCCESS
                text = "**Modlogs** is now $toggleAsString"
            }).queue()
    }

    private fun checkModlogStatus(guild: Guild): Boolean {
        val realm = Realm.open(configuration)
        return try {
            val channels = realm.query<Channels>("realmGuild.id == $0", guild.id).first().find()
            channels!!.toggle
        } catch (e: Throwable) {
            false
        } finally {
            realm.close()
        }
    }

    class Channel(private val guildId: String) {
        enum class ChannelNumber { ONE, TWO }

        val one: String
            get() {
                val realm = Realm.open(configuration)
                try {
                    return realm.query<Channels>("realmGuild.id == $0", guildId).first().find()?.one ?: ""
                } finally {
                    realm.close()
                }
            }
        val two: String
            get() {
                val realm = Realm.open(configuration)
                try {
                    return realm.query<Channels>("realmGuild.id == $0", guildId).first().find()?.two ?: ""
                } finally {
                    realm.close()
                }
            }


        suspend fun one(channelId: String) = setChannel(ChannelNumber.ONE, channelId, guildId)
        suspend fun two(channelId: String) = setChannel(ChannelNumber.TWO, channelId, guildId)


        private suspend fun setChannel(channelNumber: ChannelNumber, channelId: String, guildId: String) {
            val realm = Realm.open(configuration)
            try {
                realm.query<Channels>("realmGuild.id == $0", guildId).first().find().also {
                    realm.write {
                        when (channelNumber) {
                            ChannelNumber.ONE -> findLatest(it!!)!!.one = channelId
                            ChannelNumber.TWO -> findLatest(it!!)!!.two = channelId
                        }
                    }
                }
            } catch (e: Throwable) {
                val channels = Channels().apply {
                    when (channelNumber) {
                        ChannelNumber.ONE -> one = channelId
                        ChannelNumber.TWO -> two = channelId
                    }
                    realmGuild = RealmGuild().apply {
                        id = guildId
                    }
                }
                realm.write {
                    copyToRealm(channels, UpdatePolicy.ALL)
                }
            } finally {
                realm.close()
            }
        }
    }

    private val censoredWords = listOf("nigg", "retard", "chink", "kike", "fag", "tard") // swear words

    private fun censor(string: String): String {
        // Example: nigga bitch lasagna
        // What should happen: n--ga bitch lasagna
        // Return an empty string if msg is empty. Example: empty nicknames
        if (string.isEmpty()) {
            return ""
        }
        var newString: String = string
        censoredWords.forEach { swear ->
            //Example: nigga bitch lasagna nigga
            //Should return: n--ga bitch lasagna n--ga
            val matches = Regex("($swear)", RegexOption.IGNORE_CASE).findAll(string)
            matches.forEach {
                val replacementStart = it.range.first + 1
                val replaceAmount = it.value.length - 2
                val replaceWithThis = "-".repeat(replaceAmount)
                newString = newString.replaceRange(replacementStart, it.range.last, replaceWithThis)
                //replaceRange has an inclusive startIndex but an exclusive endIndex
            }
        }
        return newString
    }

    suspend fun onGuildMemberRemove(event: GuildMemberRemoveEvent) {
        // Only appliable to kicks and leaves. Bans have their own event.
        if (!checkModlogStatus(event.guild)) return


            // When members leave, no info is left on the embed. It's sent empty.
            val channel: TextChannel = event.guild.getTextChannelById(Channel(event.guild.id).one)
                ?: return //If the channel is not in the realmGuild, it will return null
            val guild = event.guild
            val member = event.member
            var moderator = ""
            val ifMemberNickname = fun(): String {
                return if (member?.nickname != null) {
                    "- " + censor(member.nickname!!)
                } else {
                    ""
                }
            }
            val emb = embedBuilder(member!!)

            val auditEntryKick = latestAuditLogEntry(guild, ActionType.KICK)?.first()
            val auditEntryBan = latestAuditLogEntry(guild, ActionType.BAN)?.first()


            if ((auditEntryKick?.targetId != member.id) && (auditEntryBan?.targetId != member.id)) {
                // the member left
                emb.apply {
                    title = "Member Left"
                    description =
                        "**${member.asMention} - ${member.user.asTag} ${ifMemberNickname()}** left the server ${
                            roles(member)
                        }"
                    color = 0xF8E71C
                }
            }

            if ((auditEntryKick != null) && (auditEntryKick.targetId == member.id)) {
                // the member was kicked
                if (auditEntryKick.user?.isBot == true) {
                    moderator =
                        auditEntryKick.reason?.let { getModeratorTag(it) } ?: auditEntryKick.user?.asTag ?: "Unknown"
                }
                emb.apply {
                    title = "Member Kicked"
                    description =
                        "**${member.asMention} - ${member.user.asTag} ${ifMemberNickname()}** got kicked by $moderator ${
                            roles(member)
                        }"
                    color = 0xF5A623
                }
            }

            if (emb.title.isNullOrBlank()) return //the member was banned, so we don't want to send an empty embed
            channel.sendMessageEmbeds(emb.build()).setActionRow(
                Button.primary("ping-on-return", "Ping on return")).queue()
    }

    suspend fun onGuildBan(event: GuildBanEvent) {
        if (!checkModlogStatus(event.guild)) return
            val channel: TextChannel = event.guild.getTextChannelById(Channel(event.guild.id).one)
                ?: return//If the channel is not in the realmGuild, it will return null
            val guild = event.guild
            val user = event.user
            var moderator = ""
            val possibleMember: Member? =
                event.jda.guildCache.getElementById(guild.id)?.memberCache?.getElementById(user.id)
            // Should get from the cache. This is more "manual" than in Discord.py, but its better this way.
            val ifMemberNickname = fun(): String {
                return if (possibleMember?.nickname != null) {
                    "- " + censor(possibleMember.nickname!!)
                } else {
                    ""
                }
            }
            val auditEntryBan = latestAuditLogEntry(guild, ActionType.BAN)?.first()
            val emb = embedBuilder(possibleMember, user)

            if ((auditEntryBan != null) && (auditEntryBan.targetId == user.id)) {
                // the member was banned
                if (auditEntryBan.user?.isBot == true) {
                    moderator =
                        auditEntryBan.reason?.let { getModeratorTag(it) } ?: auditEntryBan.user?.asTag ?: "Unknown"
                }
                emb.apply {
                    title = "Member Banned"
                    description =
                        "**${user.asMention} - ${user.asTag} ${ifMemberNickname()}** got banned by $moderator ${
                            roles(possibleMember)
                        }"
                    color = 0xD0021B
                }
            }
            channel.sendMessageEmbeds(emb.build()).queue()
    }

    private suspend fun pingUsersOnReturn(userReturnedId: String, guildId: String, channel: TextChannel) {
        val realm = Realm.open(configuration)
        try {
            var mentionsList = ""
            realm.write {
                val thisQuery =
                    this.query<Ping>("userToReturn = \"${userReturnedId}\" AND realmGuild.id = \"${guildId}\"")
                val pingsList = thisQuery.find().toList()
                pingsList.forEach { ping ->
                    mentionsList += channel.jda.getUserById(ping.userToPing)!!.asMention
                }
                delete(thisQuery)
            }
            // Can return various results. All pending notifications are valid only once.
            if (mentionsList.isNotBlank()) {
                channel.sendMessage("${mentionsList}\nA user you wanted to be notified about has returned").queue()
            }
        } finally {
            realm.close()
        }
    }

    suspend fun onGuildMemberJoin(event: GuildMemberJoinEvent) {
        if (!checkModlogStatus(event.guild)) return
            val channel: TextChannel = event.guild.getTextChannelById(Channel(event.guild.id).one)
                ?: return //If the channel is not in the realmGuild, it will return null

            val member = event.member
            val guildId = event.guild.id

            //todo: When a member on Ping when Returns comes back it does not trigger said function.
            pingUsersOnReturn(userReturnedId = member.id, guildId = guildId, channel = channel)

            getStoredRoles(member) // It wil throw an error if the member was not in the guild before.
            // I need to make it ignore if there are no roles stored

            val timeCreatedDTS = TimeFormat.DATE_TIME_SHORT.format(member.timeCreated)
            val timeCreatedR = TimeFormat.RELATIVE.format(member.timeCreated)
            // Getting time from ReJoined. It will say "New member" if it is the first time the member joined.
            val lastTimeString = getLastTime(member)
            var lastTimeDTS = "New member"
            var lastTimeR = "Never"
            if (lastTimeString != null) {
                val lastTime = PrettyTimeParser(TimeZone.getDefault()).parse(lastTimeString)[0].toInstant()
                lastTimeDTS = TimeFormat.DATE_TIME_SHORT.format(lastTime)
                lastTimeR = TimeFormat.RELATIVE.format(lastTime)
            }

            val emb = embedBuilder(member)


            emb.apply {
                title = "Member Joined"
                description =
                    "**${member.asMention} - ${member.user.asTag}** joined the server" + "\n**Creation Date:**\n${timeCreatedDTS} (${timeCreatedR})" + "\n**Last Time Here:**\n${lastTimeDTS} (${lastTimeR})" + getStoredRoles(
                        member)
                color = 0x7ED321
            }
            channel.sendMessageEmbeds(emb.build()).queue()
    }

    suspend fun onGuildUnban(event: GuildUnbanEvent) {
        if (!checkModlogStatus(event.guild)) return
            val channel: TextChannel = event.guild.getTextChannelById(Channel(event.guild.id).one)
                ?: return //If the channel is not in the realmGuild, it will return null

            val user = event.user
            val guild = event.guild
            var moderator = ""

            val cenTag = censor(event.user.asTag)

            val auditEntryUnban = latestAuditLogEntry(guild, ActionType.UNBAN)?.first()
            val emb = embedBuilder(null, user)

            if ((auditEntryUnban != null) && (auditEntryUnban.targetId == user.id)) {
                // the member was banned
                if (auditEntryUnban.user?.isBot == true) {
                    moderator =
                        auditEntryUnban.reason?.let { getModeratorTag(it) } ?: auditEntryUnban.user?.asTag ?: "Unknown"
                }
                emb.apply {
                    title = "User Unbanned"
                    description =
                        "**${user.asMention} - ${cenTag}** got unbanned by $moderator"
                    color = 0x4A90E2
                }
            }
            channel.sendMessageEmbeds(emb.build()).queue()

    }


    fun onGuildMemberUpdateNickname(event: GuildMemberUpdateNicknameEvent) {
        if (!checkModlogStatus(event.guild)) return
            val channel: TextChannel = event.guild.getTextChannelById(Channel(event.guild.id).two)
                ?: return //If the channel is not in the realmGuild, it will return null
            //the above still throws an error, but it should not.
            val cenTag = censor(event.user.asTag)
            val cenOldNick = event.oldNickname?.let { censor(it) }
            val cenNewNick = event.newNickname?.let { censor(it) }

            val member = event.member
            if (event.oldNickname != event.newNickname) {

                val emb = embedBuilder(member)
                emb.apply {
                    title = "Nickname Changed"
                    description =
                        "**${member.asMention} - ${cenTag}** nickname has been changed\n**Nickname Before:**\n${cenOldNick}\n**Nickname After:**\n${cenNewNick}"
                    color = 0xF5A623
                }
                channel.sendMessageEmbeds(emb.build()).queue()
            } else {
                return
            }
    }

    fun onGuildMemberUpdateTimeOut(event: GuildMemberUpdateTimeOutEvent) {
        if (!checkModlogStatus(event.guild)) return
        // todo: implement this
        // this will not get triggered by automatic timeout expiration
            val channel: TextChannel = event.guild.getTextChannelById(Channel(event.guild.id).two)
                ?: return //If the channel is not in the realmGuild, it will return null

            val oldTimeOutEndR = event.oldTimeOutEnd?.let { TimeFormat.RELATIVE.format(it) }
            val newTimeOutEndR = event.newTimeOutEnd?.let { TimeFormat.RELATIVE.format(it) }


            val member = event.member
            val emb = embedBuilder(member)
            emb.apply {
                title = "Timeout Ended Early"
                description =
                    "**${member.asMention} - ${member.user.asTag}** had it's timeout end earlier. \n**Expected End:**\n${oldTimeOutEndR}\n**Actual End:**\n${newTimeOutEndR}"
                color = 0xF5A623
            }
            channel.sendMessageEmbeds(emb.build()).queue()
    }

    //todo: this will trigger on pinned messages. i need to split edited from pinned in the future
    fun onMessageUpdate(event: MessageUpdateEvent) {
        if (!event.isFromGuild) return // ignore events that are not from guild
        if (event.author.isBot || !checkModlogStatus(event.guild)) return
            val channel: TextChannel = event.guild.getTextChannelById(Channel(event.guild.id).two)
                ?: return //If the channel is not in the realmGuild, it will return null
            val messageBefore = messageCacheCaffeine.getIfPresent(event.messageId) // this is nullable
            MessageCache.onMessageUpdateEvent(event) // update the cache with the new message
            val messageAfter = event.message
            val member = event.member


            val emb = embedBuilder(messageAfter)

            emb.apply {
                title = "Message Edited"
                description =
                    "**${member?.asMention} - ${member?.user?.asTag}** edited a [message](${messageAfter.jumpUrl}) in ${event.channel.asMention}."
                color = 0x417505
                field {
                    name = "Before message"
                    value = messageBefore?.contentDisplay?.let { checkAndReduce(censor(it)) } ?: "`Message not cached.`"
                    inline = false
                }
                if (messageBefore != null) {
                    if (messageBefore.attachmentsURLs.isNotEmpty()) {
                        field {
                            name = "Before Attachments"
                            value = messageBefore.attachmentsURLs.joinToString("\n")
                            inline = false
                        }
                    }
                }
                field {
                    name = "After message"
                    value = checkAndReduce(censor(messageAfter.contentDisplay))
                    inline = false
                }
                if (messageAfter.attachments.isNotEmpty()) {
                    field {
                        name = "After Attachments"
                        value = messageAfter.attachments.joinToString("\n") { it.url }
                        inline = false
                    }
                }
            }
            channel.sendMessageEmbeds(emb.build()).queue()
    }

    suspend fun onMessageDelete(event: MessageDeleteEvent) {
        if (!event.isFromGuild) return // ignore events that are not from guild
        if (!checkModlogStatus(event.guild)) return
        // We can not check yet if the message was from a bot or not. We need to check cache first.
        val channel: TextChannel = event.guild.getTextChannelById(Channel(event.guild.id).two)
            ?: return //If the channel is not in the realmGuild, it will return null
        val messageCached = messageCacheCaffeine.getIfPresent(event.messageId) // this is nullable
        MessageCache.onMessageDeleteEvent(event) // delete the message from the cache
        val user = messageCached?.authorID?.let { event.jda.retrieveUserById(it).await() }

        val emb = embedBuilder(messageCached)
        emb.apply {
            title = "Message Deleted"
            color = 0x9013FE

        }

        if (messageCached != null) {
            emb.apply {
                description =
                    "**${user?.asMention} - ${user?.asTag}** had it's message deleted in ${event.channel.asMention}."
                field {
                    name = "Message"
                    value = checkAndReduce(censor(messageCached.contentDisplay))
                }
                if (messageCached.attachmentsURLs.isNotEmpty()) {
                    field {
                        name = "Attachments"
                        value = messageCached.attachmentsURLs.joinToString("\n")
                    }
                }
            }
        } else {
            // The message is not cached. No log should be sent to the channel.
            return
        }
        channel.sendMessageEmbeds(emb.build()).queue()
    }


    suspend fun onMessageBulkDelete(event: MessageBulkDeleteEvent) {
        if (!checkModlogStatus(event.guild)) return
        // We can not check yet if the message was from a bot or not. We need to check cache first.
        val channel: TextChannel = event.guild.getTextChannelById(Channel(event.guild.id).two)
            ?: return //If the channel is not in the realmGuild, it will return null
        val deletedQuantity = event.messageIds.size // this is the amount of messages deleted
        var messageNumber = 0
        var uncachedMessagesQuantity = 0

        event.messageIds.forEach { messageID ->
            val messageCached = messageCacheCaffeine.getIfPresent(messageID) // this is nullable
            // we will only delete from the cache afterwards to avoid doing 2 tasks at the same time
            val user = messageCached?.authorID?.let { event.jda.retrieveUserById(it).await() }

            val emb = embedBuilder(messageCached)
            emb.apply {
                title = "Message Deleted in Bulk (${messageNumber++}/$deletedQuantity)"
                color = 0x9013FE
            }

            if (messageCached != null) {
                emb.apply {
                    description =
                        "**${user?.asMention} - ${user?.asTag}** had it's message deleted in ${event.channel.asMention}."
                    field {
                        name = "Message"
                        value = checkAndReduce(censor(messageCached.contentDisplay))
                    }
                    if (messageCached.attachmentsURLs.isNotEmpty()) {
                        field {
                            name = "Attachments"
                            value = messageCached.attachmentsURLs.joinToString("\n")
                        }
                    }
                }
            } else {
                uncachedMessagesQuantity++
            }
            channel.sendMessageEmbeds(emb.build()).queue()
        }
        val newEmb = embedBuilder(message = null)
        newEmb.apply {
            title = "Message Deleted in Bulk ($deletedQuantity/$deletedQuantity)"
            color = 0x9013FE
            description =
                "$uncachedMessagesQuantity out of $deletedQuantity messages were deleted in ${event.channel.asMention} but were not in the cache."
        }
        MessageCache.onMessageBulkDeleteEvent(event)
        channel.sendMessageEmbeds(newEmb.build()).queue()

    }


    suspend fun modLogConfiguration(event: MessageReceivedEvent) {
        //todo: migrate this to slash commands
        if (!permissionCheck(event, Permission.ADMINISTRATOR)) {
            return
        }
        if (event.message.contentDisplay == "!modlog") { //If it only has "!modlog"
            event.message.replyEmbeds(
                NeoSuperCommand {
                    triggers = arrayOf("!modlog")
                    name = "Modlogs"
                    description =
                        "Advanced logging functionality split in two channels:\n1 - Channel exclusively for joins, leaves, kicks, bans and unbans\n2 - General modlog channel\n*Note: Bots messages are not cached.*"
                    subcommands {
                        put("set [channel1] [channel2]", "Set channel 1 and channel 2")
                        put("toggle", "Toggle modlogs on/off")
                    }
                }).queue()
            return
        }
        val parts = event.message.contentRaw.split(" ")

        //example: !modlog set [channel1] [channel2]
        //example: !modlog toggle

        when (parts[1]) {
            "set" -> {
                val channel = Channel(event.guild.id)
                channel.one(event.message.mentions.channels[0].id)
                channel.two(event.message.mentions.channels[1].id)
                val emb = NeoSuperEmbed {
                    type = SuperEmbed.ResultType.SUCCESS
                    text = "Modlog channels set!"
                }
                event.message.replyEmbeds(emb).await()
            }
            "toggle" -> toggle(event)
            else -> {
                event.message.reply("That was an unknown command.").queue()
            }
        }
    }
}