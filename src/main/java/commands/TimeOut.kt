package commands

import RealmGuild
import TimeUtil
import embedutils.*
import io.realm.kotlin.Realm
import io.realm.kotlin.RealmConfiguration
import io.realm.kotlin.UpdatePolicy
import io.realm.kotlin.ext.query
import io.realm.kotlin.types.BaseRealmObject
import io.realm.kotlin.types.RealmObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.utils.TimeFormat
import org.ocpsoft.prettytime.PrettyTime
import org.ocpsoft.prettytime.nlp.PrettyTimeParser
import java.time.Duration
import java.time.Instant
import kotlin.reflect.KClass


object TimeOut {

    class Configuration : RealmObject {
        var default: String = "10m" //by default. Can only be ban and kick
        var realmGuild: RealmGuild? = null
    }

    private val configuration =
        RealmConfiguration.Builder(schema = setOf(RealmGuild::class, Configuration::class) as Set<KClass<out BaseRealmObject>>).schemaVersion(1.1.toLong()).build()

    fun timeOut(event: MessageReceivedEvent) {
        if (!permissionCheck(event, Permission.MODERATE_MEMBERS)) {
            return
        }
        val parts = event.message.contentRaw.split(" ")
        if (event.message.contentDisplay.substringAfter(" ").startsWith("default")){
            CoroutineScope(Dispatchers.Default).launch {
                setDefault(event)
            }
            return
        }
        if (event.message.mentions.members.isEmpty()) { //If it only has "!timeout" or has no members mentioned.
            event.channel.sendMessageEmbeds(
                NeoSuperCommand {
                    triggers = arrayOf("!timeout", "!m", "!um")
                    name = "TimeOut"
                    description = "Requires moderate members permission\nDefault: 10 minutes\nUsage:\n!timeout [@user] until [human-readable date]\n" +
                            "!timeout [@user] [quantity]s/m/h/d\nUse !untimeout or !um to end a timeout\n" +
                            "You can end timeouts from multiple members at once."
                        subcommands {
                        put("default [time]", "Change the default amount of time")
}
                }).queue()
            return
        }

        //TODO: It is now possible and i should try it out.

        // Due to a bug it is not possible to implement the line below. This is what WAS and IS intended.
        // var messageToSend = "Done. Muted for ${prettyTimeDuration}\nUntil ${TimeFormat.DATE_TIME_LONG.format(theTime.time)}")
        val timeInTimeFormat: Instant
        var prettyTimeDuration: String

        if (parts.size > 2) {
            if (parts[2] == "until") {
                val dateContent = event.message.contentRaw.substringAfter("until")
                //example: !timeout [user] until 29th of January 2022
                // dateContent = " 29th of January 2022"
                val theTime = PrettyTimeParser().parse(dateContent)
                timeInTimeFormat = theTime[0].toInstant()
                prettyTimeDuration = PrettyTime().format(PrettyTime().calculatePreciseDuration(timeInTimeFormat))

                kotlin.runCatching {
                    event.message.mentions.members[0].timeoutUntil(timeInTimeFormat).queue(
                        {
                            event.channel.sendMessageEmbeds(
                                NeoSuperEmbed {
                                    type = SuperEmbed.ResultType.SUCCESS
                                    text = "Done. Muted until ${TimeFormat.DATE_TIME_LONG.format(timeInTimeFormat)}. Muted for $prettyTimeDuration"
                                }).queue()
                        },
                        {
                            event.message.replyEmbeds(
                                NeoSuperThrow {
                                    throwable = it
                                }).queue()
                        }
                    )
                }.onFailure {
                    event.message.replyEmbeds(NeoSuperThrow { throwable = it }).queue()
                }
            } else {
                val dateContent = event.message.contentRaw.substringAfterLast(" ")
                //example: !timeout [user] 2m
                //needs to support s, m, h, d, w
                val parsedTime = TimeUtil().parse(dateContent).toMillisecond
                timeInTimeFormat = Instant.now().plusMillis(parsedTime)
                prettyTimeDuration = PrettyTime().format(PrettyTime().calculatePreciseDuration(timeInTimeFormat))
                kotlin.runCatching {
                    event.message.mentions.members[0].timeoutFor(Duration.ofMillis(parsedTime)).queue(
                        {
                            event.channel.sendMessageEmbeds(
                                NeoSuperEmbed {
                                    type = SuperEmbed.ResultType.SUCCESS
                                    text = "Done. Muted until ${TimeFormat.DATE_TIME_LONG.format(timeInTimeFormat)}. Muted for $prettyTimeDuration"
                                }).queue()
                        },
                        {
                            event.message.replyEmbeds(
                                NeoSuperThrow {
                                    throwable = it
                                }).queue()
                        }
                    )
                }.onFailure {
                    event.message.replyEmbeds(
                        NeoSuperThrow {
                            throwable = it
                        }).queue()
                }
            }
        } else {
            //example: !timeout [user]
            //needs to get a default value from Realms
            val realm = Realm.open(configuration)
            var defaultValue = "10m"
            try{
                val guildConfiguration = realm.query<Configuration>("realmGuild.id == $0", event.guild.id).first().find()
                if (guildConfiguration != null) {
                    defaultValue = guildConfiguration.default
                }
            } finally {
                realm.close()
            }

            val parsedTime = TimeUtil().parse(defaultValue).toMillisecond
            timeInTimeFormat = Instant.now().plusMillis(parsedTime)
            prettyTimeDuration = PrettyTime().format(PrettyTime().calculatePreciseDuration(timeInTimeFormat))
            kotlin.runCatching {
                event.message.mentions.members[0].timeoutFor(Duration.ofMillis(parsedTime)).queue(
                    {
                        event.channel.sendMessageEmbeds(
                            NeoSuperEmbed {
                                type = SuperEmbed.ResultType.SUCCESS
                                text = "Done. Muted until ${TimeFormat.DATE_TIME_LONG.format(timeInTimeFormat)}. Muted for $prettyTimeDuration"
                            }).queue()
                    },
                    {
                        event.message.replyEmbeds(
                            NeoSuperThrow {
                                throwable = it
                            }).queue()
                    }
                )
            }.onFailure {
                event.message.replyEmbeds(
                    NeoSuperThrow {
                        throwable = it
                    }).queue()
            }
        }
    }

    fun removeTimeOut(event: MessageReceivedEvent) {
        val parts = event.message.contentRaw.split(" ")
        if (!permissionCheck(event, Permission.MODERATE_MEMBERS)) {
            return
        }
        if (parts.size == 2) {
            kotlin.runCatching {
                event.message.mentions.members.forEach {
                    it.removeTimeout().queue() //If the member is not in the server this will cause an error.
                }
            }
            val thisEmbed =
                NeoSuperEmbed {
                    type = SuperEmbed.ResultType.SUCCESS
                    text = "Done. Removed timeouts for ${event.message.mentions.members.size} member(s)."
                }
            event.channel.sendMessageEmbeds(thisEmbed).queue()
        } else {
            val thisEmbed =
                NeoSuperEmbed {
                    type = SuperEmbed.ResultType.ERROR
                    text = "Please mention a member to remove the timeout from."
                }
            event.channel.sendMessageEmbeds(thisEmbed).queue()
            return
        }
    }

    private suspend fun setDefault(event: MessageReceivedEvent) {
        val newDefault = event.message.contentDisplay.substringAfter("default").trim()
        try {
            //This is purely to know if its written properly: a number followed by 's','m','h' or 'd'
            TimeUtil().parse(newDefault).toString()
        } catch (t: Throwable) {
            event.message.replyEmbeds(
                NeoSuperEmbed {
                    type = SuperEmbed.ResultType.ERROR
                    text =
                        "The time you entered is not valid. Please enter a number followed by either `s`, `m`, `h` or `d`"
                }).queue()
            return
        }
        val realm = Realm.open(configuration)
        try {
            realm.write {
                var guild = realm.query<Configuration>("realmGuild.id == ${event.guild.id}").first().find()
                if (guild == null) {
                    guild = Configuration()
                    guild.apply {
                        default = newDefault
                        realmGuild = RealmGuild().apply {
                            id = event.guild.id
                        }
                    }
                } else {
                    guild.default = newDefault
                }
                copyToRealm(guild, UpdatePolicy.ALL)
            }
        } finally {
            realm.close()
        }
    }

}
