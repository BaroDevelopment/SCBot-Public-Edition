package commands

import RealmGuild
import commandList
import embedutils.*
import io.realm.kotlin.Realm
import io.realm.kotlin.RealmConfiguration
import io.realm.kotlin.UpdatePolicy
import io.realm.kotlin.ext.query
import io.realm.kotlin.types.RealmObject
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.MessageChannel
import net.dv8tion.jda.api.events.message.MessageReceivedEvent


object InstantBan {

    class Word : RealmObject {
        var name: String = "" //The word to be censored
        var action: String = "ban" //By default, is "ban". Can only be ban and kick
        var realmGuild: RealmGuild? = null
    }

    private val configuration =
        RealmConfiguration.Builder(
            schema = setOf(RealmGuild::class, Word::class)
        ).schemaVersion(1.1.toLong()).build()

    private fun findWords(event: MessageReceivedEvent) {
        if (event.author.isBot) {
            return
        }
        val realm = Realm.open(configuration)
        //todo: it should get all words first
        try {
        val wordObjects = realm.query<Word>("realmGuild.id = \"${event.guild.id}\"").find()
        wordObjects.forEach { swear ->
            val matched =
                Regex("(${swear.name})", RegexOption.IGNORE_CASE).containsMatchIn(event.message.contentDisplay)
            if (matched) {
                when (swear.action) {
                    "ban" -> {
                        kotlin.runCatching {
                            event.member?.ban(0, "Filtered by InstantBan (prohibited link or word)")?.queue()
                        }.onSuccess {
                            event.channel.sendMessage("A user was banned for posting a prohibited link or word.")
                                .queue()
                        }.onFailure {
                            event.channel.sendMessageEmbeds(NeoSuperEmbed {
                                type = SuperEmbed.ResultType.ERROR
                                text = "I was unable to ban due to:\n```${it.message.toString()}```"
                            }).queue()
                        }
                    }
                    "kick" -> {
                            event.member?.kick()?.queue( {
                                event.channel.sendMessage("A user was kicked for posting a prohibited link or word.").queue()
                            },
                            {
                                queueFailure(event.channel, "I was unable to kick due to:")
                            })
                    }
                }
                event.message.delete().queue()
            }
        }
        } finally {
            realm.close()
        }
    }


    fun onMessageReceived(event: MessageReceivedEvent) {
        if (event.author.isBot) {
            return
        }
        if (!event.isFromGuild) {
            return
        }
        commandList.forEach {
            if (event.message.contentDisplay.startsWith(it)) {
                return
            }
        }
            findWords(event)
    }

    suspend fun instantBan(event: MessageReceivedEvent) {
        if (!permissionCheck(event, Permission.MODERATE_MEMBERS)) {
            return
        }
        val parts = event.message.contentRaw.split(" ")
        if ((event.message.contentDisplay == "!ib") || (event.message.contentDisplay == "!instantban")) { //If it only has "!modlog"
            event.message.replyEmbeds(
                NeoSuperCommand {
                    triggers = arrayOf("instantban", "ib")
                    name = "InstantBan"
                    description =
                        "Requires ${Permission.MODERATE_MEMBERS} permisson to use. Whenever a member sends a message with a filtered word, said message will be deleted and the member will be either kicked or banned.\nAdding 'upserts' (either inserts or updates)"
                    subcommands {
                        put("list", "Sends a list of all words and their actions.")
                        put("add [word] [action]", "Adds a word to the instantban list with the specified action")
                        put("addmulti [word] [word] [word] [word]",
                            "Adds multiple words to the instantban list with the default action")
                        put("del [word]", "Removes a word from the instantban list")
                        put("delmulti [word] [word] [word] [word]", "Removes multiple words from the instantban list")
                    }
                }).queue()
            return
        }

        val channel = event.channel
        val guildId = event.guild.id
        if (parts.size > 2) {
            when (parts[1]) {
                "add" -> {
                    val actionToSave = kotlin.runCatching {
                        parts[3]
                    }.getOrNull()
                    addWord(parts[2], actionToSave, guildId, channel)
                }
                "addmulti" -> addMultipleWords(parts.drop(2), guildId, channel)
                "del" -> deleteWord(parts[2], guildId, channel)
                "delmulti" -> deleteMultipleWords(parts.drop(2), guildId, channel)
                else -> event.message.reply("That was an unknown command")
            }
        }
        if (parts.size == 2 && parts[1] == "list") {
            returnAllWords(event)
        }
    }

    private fun returnAllWords(event: MessageReceivedEvent) {
        val realm = Realm.open(configuration)

        //todo: it should get all words first
        try{
            
        val wordObjects = realm.query<Word>("realmGuild.id = \"${event.guild.id}\"").find()
        var wordsForTheMessage = ""
        wordObjects.forEach {
            wordsForTheMessage += "``${it.name}`` => ${it.action}\n"
        }
        event.jda.openPrivateChannelById(event.author.id).queue({
            it.sendMessage("There are ${wordObjects.count()} word(s) in InstantBan." + if (wordsForTheMessage.isNotBlank()) "\n``$wordsForTheMessage``" else "")
                .queue({ queueSuccess(event.channel, "Sent list of words") },
                    { queueFailure(event.channel, "Failed to send list of words") })
        }, {
            queueFailure(event.channel,
                "Failed to open private channel. You don't allow others to send you private messages.")
        })
        } finally {
         realm.close()   
        }
    }


    suspend fun deleteMultipleWords(words: List<String>, guildId: String, channel: MessageChannel) {
        for (each in words) {
            deleteWord(each, guildId, channel)
        }
    }

    suspend fun deleteWord(word: String, guildId: String, channel: MessageChannel) {
        val realm = Realm.open(configuration)

        try {
            realm.write {
                val toDeleteQuery =
                    this.query<Word>("name = \"${word}\" AND realmGuild.id = \"${guildId}\"").first().find()
                if (toDeleteQuery != null) {
                    delete(toDeleteQuery)
                }
                else {
                    channel.sendMessageEmbeds(
                        NeoSuperEmbed {
                            text = "That word is not in the list."
                            type = SuperEmbed.ResultType.ERROR
                        }
                    ).queue()
                }
            }
            channel.sendMessageEmbeds(
                NeoSuperEmbed {
                    text = "Word successfully deleted."
                    type = SuperEmbed.ResultType.SUCCESS
                }).queue()
        }catch(e: Throwable) {
            channel.sendMessageEmbeds(NeoSuperThrow {
                throwable = e
            }).queue()
        } finally {
            realm.close()
        }
    }

    suspend fun addMultipleWords(words: List<String>, guildId: String, channel: MessageChannel) {
        for (each in words) {
                addWord(each, null, guildId, channel)
            }
        }

    suspend fun addWord(wordToAdd: String, actionToSave: String?, guildId: String, channel: MessageChannel) {
        val realm = Realm.open(configuration)
        
        var updated = false
        try {
                realm.write {
                    var word: Word? =
                        this.query<Word>("name = \"${wordToAdd}\" AND realmGuild.id = \"${guildId}\"")
                            .first()
                            .find()
                    if (word != null) {
                        word.apply {
                            action = actionToSave ?: "ban"
                        }
                        updated = true
                    } else {
                        word = Word().apply {
                            name = wordToAdd
                            action = actionToSave ?: "ban"
                            realmGuild = RealmGuild().apply {
                                id = guildId
                            }
                        }
                    }
                    copyToRealm(word, UpdatePolicy.ALL)
                }
                if (updated) {
                    channel.sendMessageEmbeds(NeoSuperEmbed {
                        type = SuperEmbed.ResultType.SUCCESS
                        text = "The word already existed and was updated successfully"
                    }).queue()
                } else {
                    channel.sendMessageEmbeds(NeoSuperEmbed {
                        type = SuperEmbed.ResultType.SUCCESS
                        text = "Word added successfully"
                    }).queue()
                }
            }catch(e: Throwable) {
                channel.sendMessageEmbeds(NeoSuperThrow {
                    throwable = e
                }).queue()
                return
            } finally {
                realm.close()
            }
    }
}
