
import embedutils.NeoSuperEmbed
import embedutils.SuperEmbed
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.exceptions.ErrorResponseException
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.utils.TimeFormat
import org.ocpsoft.prettytime.PrettyTime
import java.lang.management.ManagementFactory
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.time.format.DateTimeFormatter
import java.util.*


object CommandsGeneral : ListenerAdapter() {

    fun ping(event: SlashCommandInteractionEvent) {
        event.deferReply().queue()
        event.jda.restPing.queue { time ->
            event.hook.sendMessageFormat("Ping: %d ms | Websocket: ${event.jda.gatewayPing} ms",
                time).queue()
        }
    }

    fun uptime(event: SlashCommandInteractionEvent) {
        event.deferReply().queue()
        val runtimeMXBean = ManagementFactory.getRuntimeMXBean()
        val dateFormat: DateFormat = SimpleDateFormat("HH:mm:ss")
        dateFormat.timeZone = TimeZone.getTimeZone("UTC")
        val uptime = runtimeMXBean.uptime
        val finalUptime = (uptime / (3600 * 1000 * 24)).toString() + ":" + dateFormat.format(uptime)
        event.hook.sendMessage(("Online for: $finalUptime")).queue()
    }


    fun userinfo(event: MessageReceivedEvent) {
        val inputMessage = event.message.contentRaw
        val userID = inputMessage.substring(inputMessage.indexOf(" ") + 1)
        val theUser: User = try {
            event.jda.retrieveUserById(userID).complete()
        } catch (e: Throwable) {
            event.channel.sendMessageEmbeds(
            NeoSuperEmbed {
                type = SuperEmbed.ResultType.ERROR
                text = "You must provide a valid userID."
            }).queue()
            return
        }
        //TheUserAsMember se forem nulls nao vao ser utilizadas no Embed.
        //RetrieveMemberById nao retorna null por padrão — dá um erro. Este Try and Catch transforma o erro em um resultado válido.
        var theUserAsMember: Member? = null
        try {
            theUserAsMember = event.guild.retrieveMemberById(userID).complete()
        } catch (error: ErrorResponseException) {
            println("Error: Not a guild member.")
        }
        val theMemberTimeBoosted = theUserAsMember?.timeBoosted
        val userBotInfo = if (theUser.isBot) {
            "Yes"
        } else {
            "No"
        }
        val userinfoMessage = EmbedBuilder().setTitle(theUser.name)
            .setColor(0x43b481)
            .setThumbnail(theUser.effectiveAvatarUrl)
            .setDescription(theUser.asMention + " \n" + theUser.asTag)
            .addField(
                "Creation Date",
                theUser.timeCreated.format(DateTimeFormatter.ofPattern("dd MMMM yyyy  HH:mm O",
                    Locale.getDefault())),
                true
            )
        if (theUserAsMember != null) {
            userinfoMessage.addField(
                "Join Date",
                theUserAsMember.timeJoined.format(
                    DateTimeFormatter.ofPattern(
                        "dd MMMM yyyy  HH:mm O",
                        Locale.getDefault()
                    )
                ),
                true
            )
        }
        if (theMemberTimeBoosted != null) {
            userinfoMessage.addField(
                "Boost Date",
                theMemberTimeBoosted.format(DateTimeFormatter.ofPattern("dd MMMM yyyy  HH:mm O",
                    Locale.getDefault())),
                false
            )
        }
        userinfoMessage
            .addField("Badges", theUser.flags.toString(), false)
            .addField("Bot", userBotInfo, false)
            .setFooter("Command executed by @" + event.author.asTag)
        event.channel.sendMessageEmbeds(userinfoMessage.build()).queue()
    }

    fun serverinfo(event: SlashCommandInteractionEvent) {
        event.deferReply().queue()
        val hook = event.hook

        //The guild check is done in SlashCommands.kt so there's no need to worry about getting a null pointer exception here
        val guild: Guild = event.guild!!
        val approximateOnlineMemberCount = guild.retrieveMetaData().complete().approximatePresences
        val owner = guild.retrieveOwner().complete()
        var boostTier = guild.boostTier.toString()
        when (boostTier) {
            "NONE" -> boostTier = "0"
            "TIER_1" -> boostTier = "1"
            "TIER_2" -> boostTier = "2"
            "TIER_3" -> boostTier = "3"
        }

        //TODO: This can be replaced with PrettyTime 
        val createdInstant = guild.timeCreated.toInstant()
        val preciseDuration = PrettyTime().calculatePreciseDuration(createdInstant)
        val formattedTimeCreated = PrettyTime().formatDurationUnrounded(preciseDuration)
        var botCount = 0
        guild.loadMembers().onSuccess { membersList ->
            membersList.forEach { member ->
                if (member.user.isBot) {
                    botCount++
                }
            }
        }
        // Complete the solve function below.
        val guildMaxFileSize = guild.maxFileSize / 1024 / 1024
        val guildMaxBitrate = guild.maxBitrate / 1000
        val serverinfoMessage = EmbedBuilder().setTitle(guild.name)
            .setDescription(guild.description)
            .setColor(0x43b481)
            .setThumbnail(guild.iconUrl)
            .addField(
                "Creation Date",
                "" + TimeFormat.DATE_TIME_LONG.format(guild.timeCreated.toZonedDateTime()) + "\n($formattedTimeCreated ago)",
                true
            ).addField(
                "Online Members",
                "${approximateOnlineMemberCount}/${guild.memberCount}",
                true
            )
            .addField(
                "Channels",
                "Text: ${guild.textChannels.size}\nVoice: ${guild.voiceChannels.size}",
                true
            )
            .addField(
                "Onwer",
                owner.user.asTag + " <:owner:823661010932989952>",
                true
            )
            .addField(
                "Emotes",
                guild.emotes.size.toString(), false
            )
            .addField(
                "Stickers",
                "*Soon*", false
            )
            .addField(
                "Nitro Boosts",
                "Level: ${boostTier}\nMax file size: ${guildMaxFileSize}MBs\nMax emotes: ${guild.maxEmotes}\nMax bitrate: ${guildMaxBitrate}kbps",
                false
            )
            .setFooter("ServerID: ${guild.id}")
        hook.sendMessageEmbeds(serverinfoMessage.build()).queue()
    }
}