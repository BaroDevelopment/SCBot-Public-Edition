package embedutils

import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent

// This is extremly incomplete.


fun NeoSuperEmbed(block: SuperEmbed.() -> Unit): MessageEmbed = SuperEmbed().apply(block).build()

fun NeoSuperThrow(block: SuperThrow.() -> Unit): MessageEmbed = SuperThrow().apply(block).build()

fun NeoSuperCommand(block: SuperCommand.() -> Unit): MessageEmbed = SuperCommand().apply(block).build()

class SuperThrow : SuperEmbed() {
    init {
        this.type = ResultType.ERROR
    }

    var throwable: Throwable? = null
    override var text = ""
        set(value) {
            field = "$value\n```${throwable?.message ?: "No error message."}```"
        }
}

open class SuperEmbed : EmbedBuilder() {

    enum class ResultType {
        ERROR,
        INFORMATIVE,
        SUCCESS
    }

    private var title: String = ""
    private var color: Int = 0
    internal var name: String = ""

    // the following variables must be set in order to work
    open var text: String = ""
    open var type: ResultType = ResultType.INFORMATIVE
        set(value) {
            field = value
            when (value) {
                ResultType.ERROR -> {
                    title = "<a:deniedbox:755935838851956817>"
                    color = 0xFF0000
                    name = "Error"
                }
                ResultType.INFORMATIVE -> {
                    title = "\u2754"
                    color = 0x00FF00
                    name = "Informative"
                }
                ResultType.SUCCESS -> {
                    title = "<a:acceptedbox:755935963875901471>"
                    color = 0x43b481
                    name = "Success"
                }
            }
        }

    override fun build(): MessageEmbed = EmbedBuilder().apply {
        setTitle(title)
        setColor(color)
        addField(name, text, false)
    }.build()

}

class SuperCommand : EmbedBuilder() {
    var triggers: Array<String> = arrayOf()
    var name: String = ""
    var description = ""
    private var subCommands: MutableMap<String, String> = mutableMapOf()

    fun subcommands(init: MutableMap<String, String>.() -> Unit): MutableMap<String, String> {
        subCommands = mutableMapOf()
        subCommands.init()
        return subCommands
    }

    override fun build(): MessageEmbed = EmbedBuilder().apply {
        setTitle(name.capitalize())
        setDescription(description)
        setColor(0x04ab04) // This is SCBot's green color
        for (command in subCommands) {
            addField(command.key, "${triggers[0]} ${command.value}", false)
        }
        setAuthor("$name Command Menu",
            null,
            "https://cdn.discordapp.com/avatars/294972852837548034/6aa8a8898ea313787fda95a9cf450274.webp")
    }.build()

}

fun permissionCheck(event: MessageReceivedEvent, permission: Permission): Boolean {
    return if (event.member?.hasPermission(permission) == false) {
        event.message.replyEmbeds(
            NeoSuperEmbed {
                text = "You don't have the **${permission.name}** permission to use this command."
                type = SuperEmbed.ResultType.ERROR
            }
        ).queue()
        false
    } else {
        true
    }
}

fun permissionCheck(event: SlashCommandInteractionEvent, permission: Permission): Boolean {
    return if (event.member?.hasPermission(permission) == false) {
        event.hook.sendMessageEmbeds(
            NeoSuperEmbed {
                text = "You don't have the **${permission.name}** permission to use this command."
                type = SuperEmbed.ResultType.ERROR
            }
        ).queue()
        false
    } else {
        true
    }

}
