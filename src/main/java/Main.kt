
import commands.*
import dev.minn.jda.ktx.CoroutineEventManager
import dev.minn.jda.ktx.injectKTX
import dev.minn.jda.ktx.interactions.*
import dev.minn.jda.ktx.listener
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.entities.ChannelType
import net.dv8tion.jda.api.entities.GuildChannel
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.events.guild.GuildBanEvent
import net.dv8tion.jda.api.events.guild.GuildUnbanEvent
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent
import net.dv8tion.jda.api.events.guild.member.update.GuildMemberUpdateNicknameEvent
import net.dv8tion.jda.api.events.guild.member.update.GuildMemberUpdateTimeOutEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.SelectMenuInteractionEvent
import net.dv8tion.jda.api.events.message.MessageBulkDeleteEvent
import net.dv8tion.jda.api.events.message.MessageDeleteEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.events.message.MessageUpdateEvent
import net.dv8tion.jda.api.requests.GatewayIntent
import net.dv8tion.jda.api.utils.MemberCachePolicy
import net.dv8tion.jda.api.utils.cache.CacheFlag
import java.util.*


class RealmGuild : RealmObject {
    @PrimaryKey
    var id: String = ""
}

val commandList = listOf("!timeout", "!m", "!untimeout", "!um", "!instantban", "!ib", "!modlog", "!avatar", "!a")


object Main {
    @JvmStatic
    fun main(args: Array<String>) {
        val token = System.getenv("BOT_TOKEN") ?: Throwable("No token found")
            println("Token set.")

        val jda = JDABuilder.create(
            GatewayIntent.GUILD_MEMBERS,
            GatewayIntent.GUILD_BANS,
            GatewayIntent.GUILD_EMOJIS,
            GatewayIntent.GUILD_INVITES,
            GatewayIntent.GUILD_MESSAGES,
            GatewayIntent.DIRECT_MESSAGES
        )
            .setMemberCachePolicy(MemberCachePolicy.ALL) //necessary to make GuildMemberRemove work
            .setBulkDeleteSplittingEnabled(false) //required in order to enable MessageBulkDeleteEvent
            .disableCache(CacheFlag.ACTIVITY, CacheFlag.VOICE_STATE, CacheFlag.CLIENT_STATUS, CacheFlag.ONLINE_STATUS)
            .injectKTX()
            .setToken(token.toString())
            //.setActivity(Activity.playing("/help"))
            .setEventManager(CoroutineEventManager())
            .build()
            .awaitReady()

        Locale.setDefault(Locale("en", "US"))
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))

        jda.listener<MessageReceivedEvent> {
            onMessageReceived(it)
        }
        jda.listener<MessageReceivedEvent> {
            MessageCache.onMessageReceived(it)
        }
        jda.listener<MessageReceivedEvent> {
            try {
                InstantBan.onMessageReceived(it)
            } catch (t: Throwable){
                t.printStackTrace()
            }
            try {
                Ranks.onMessageReceived(it)
            } catch (t: Throwable){
                t.printStackTrace()
            }
        }
        jda.listener<GuildMemberJoinEvent> {
            Ranks.onMemberJoin(it)
        }



        jda.listener<SlashCommandInteractionEvent> {
            SlashCommands.onSlashCommandInteraction(it)
        }
        jda.listener<SelectMenuInteractionEvent> {
            SlashCommands.onSelectMenuInteraction(it)
        }
        jda.listener<ButtonInteractionEvent> {
            SlashCommands.onButtonInteraction(it)
        }



        jda.listener<MessageDeleteEvent> {
            NeoModLog.onMessageDelete(it)
        }
        jda.listener<MessageBulkDeleteEvent> {
            NeoModLog.onMessageBulkDelete(it)
        }
        jda.listener<GuildMemberRemoveEvent> {
            NeoModLog.onGuildMemberRemove(it)
        }
        jda.listener<GuildBanEvent> {
            NeoModLog.onGuildBan(it)
        }
        jda.listener<GuildMemberJoinEvent> {
            NeoModLog.onGuildMemberJoin(it)
        }
        jda.listener<GuildUnbanEvent> {
            NeoModLog.onGuildUnban(it)
        }
        jda.listener<GuildMemberUpdateNicknameEvent> {
            NeoModLog.onGuildMemberUpdateNickname(it)
        }
        jda.listener<GuildMemberUpdateTimeOutEvent> {
            NeoModLog.onGuildMemberUpdateTimeOut(it)
        }
        jda.listener<MessageUpdateEvent> {
            NeoModLog.onMessageUpdate(it)
        }


        jda.updateCommands {
            slash("ping", "Shows the latency (in ms) between Discord and SCBot")
            slash("uptime", "Returns SCBot-Kotlin uptime")
            // the below will be the first command using selection menus
            slash("getrole", "Shows a menu with available self-assignable roles")
            slash("addrole", "Adds a role to the member's list of self-assignable roles") {
                option<Role>("role", "The role to add to the list", required = true)
            }
            slash("serverinfo", "Show important information about this server")
            slash("rank", "Settings for rank system") {
                subcommand("toggle", "Toggle rank system on/off")
                subcommand("togglemessage", "Toggle rank messages on/off (does not affect special rank messages)")
                group("set" , "Set rank system settings"){
                    subcommand("xp","Control XP system") {
                        option<Int>("amount", "The amount of XP gained per message. Default: 10 | Max: 1000", required = true)
                        option<Int>("cooldown", "The cooldown in seconds between messages. Default: 30 | Max: 120", required = true)
                    }
                    subcommand("message", "Set the message that is sent when a user gains a new rank"){
                        option<String>("message", "The message to send. Please check docs.scbot.net for more info.", required = true)
                    }
                }
                group("add","Add to rank system"){
                    subcommand("specialrank", "Set a special rank that will trigger special actions"){
                        option<Int>("rank", "The rank to set the message for.", required = true)
                        option<String>("message", "The message to send. Please check docs.scbot.net for more info.", required = true)
                        option<Role>("role", "The role to give to the user. Leave blank to not give a role.", required = false)
                    }
                    subcommand("ignorerole", "Add a role to the list of roles where xp is not gained."){
                        option<Role>("role", "The role to add to the list", required = true)
                    }
                    subcommand("ignorechannel", "Add a channel to the list of channels where xp is not gained."){
                        option<GuildChannel>("channel", "The channel to add to the list", required = true)
                    }
                }
                group("remove","Remove from rank system"){
                    subcommand("specialrank", "Remove a special rank that will trigger special actions"){
                        option<Int>("rank", "The rank to be removed from the list", required = true)
                    }
                    subcommand("ignorerole", "Remove a role from the list of roles where xp is not gained."){
                        option<Role>("role", "The role to remove from the list", required = true)
                    }
                    subcommand("ignorechannel", "Remove a channel from the list of channels where xp is not gained."){
                        option<GuildChannel>("channel", "The channel to remove from the list", required = true)
                    }
                }
                group("get", "Get rank system settings") {
                    subcommand("xp", "Get XP system settings.")
                    subcommand("message", "Get the message that is sent when a user gains a new rank.")
                    subcommand("specialrank", "Get the special ranks that trigger special actions.")
                    subcommand("ignorerole", "Get the list of roles where xp is not gained.")
                    subcommand("ignorechannel", "Get the list of channels where xp is not gained.")
                    subcommand("userinfo", "Get all rank information for a user (even if they're not in the server anymore)."){
                        option<String>("userid", "The user ID to get the info from.", required = true)
                    }
                }
                group("give", "Give a member a certain rank or XP"){
                    subcommand("rank", "Give a member a certain rank (it will override any XP they have)"){
                        option<Int>("rank", "The rank to give the user.", required = true)
                        option<User>("user", "The user to give the rank to.", required = true)
                    }
                    subcommand("xp", "Give a member XP"){
                        option<Long>("xp", "The amount of XP to give the user.", required = true)
                        option<User>("user", "The user to give XP to.", required = true)
                    }
                }
            }
        }.queue()
    }

    private suspend fun onMessageReceived(event: MessageReceivedEvent) {
        if (event.author.isBot) {
            return
        }
        if (event.isFromType(ChannelType.PRIVATE)) {
            onPrivateCommand(event)
        }
        if (event.isFromGuild) {
            onGuildCommand(event)
        }
    }

    private fun onPrivateCommand(event: MessageReceivedEvent) {
        return
    }

    private suspend fun onGuildCommand(event: MessageReceivedEvent) {
        val content = event.message.contentRaw
        when (content.substringBefore(" ")) {
            //For the current time being, the prefix is hardcoded.
            "!kick" -> kick(event)
            "!ban" -> ban(event)
            "!unban" -> unban(event)
            "!timeout" -> TimeOut.timeOut(event)
            "!m" -> TimeOut.timeOut(event)
            "!untimeout" -> TimeOut.removeTimeOut(event)
            "!um" -> TimeOut.removeTimeOut(event)
            "!instantban" -> InstantBan.instantBan(event)
            "!ib" -> InstantBan.instantBan(event)
            "!modlog" -> NeoModLog.modLogConfiguration(event)
            "!avatar" -> getAvatar(event)
            "!a" -> getAvatar(event)
            "!rank" -> Ranks.levelCheck(event)
            "!leaderboard" -> Ranks.leaderboard(event)
            "!eval" -> eval(event)
            else -> return
        }
    }
}