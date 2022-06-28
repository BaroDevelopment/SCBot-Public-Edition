
import commands.Ranks
import commands.SelfAssignRolesMenu
import io.realm.kotlin.Realm
import io.realm.kotlin.UpdatePolicy
import io.realm.kotlin.ext.query
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.SelectMenuInteractionEvent

object SlashCommands {

    suspend fun onSlashCommandInteraction(event: SlashCommandInteractionEvent) {
        // Only accept slash commands from guilds
        if (event.guild == null) return
        event.deferReply().queue()
        when (event.commandPath) {
            "ping" -> CommandsGeneral.ping(event)
            "uptime" -> CommandsGeneral.uptime(event)
            "serverinfo" -> CommandsGeneral.serverinfo(event)
            "getrole" -> SelfAssignRolesMenu.getRoles(event)
            "addrole" -> {
                event.getOption("role")?.let { SelfAssignRolesMenu.addMenuRole(event, it.asRole) }
            }
            //Rank base commands
            "rank/toggle" -> Ranks.toggle(event)
            "rank/togglemessage" -> Ranks.toggleLevelUpMessage(event)
            //Rank set commands
            "rank/set/xp" -> Ranks.setXPGain(event, event.getOption("amount")!!.asInt, event.getOption("cooldown")!!.asInt)
            "rank/set/message" -> Ranks.setLevelUpMessage(event, event.getOption("message")!!.asString)
            //Rank add commands
            "rank/add/specialrank" -> Ranks.addSpecialRank(event,
                event.getOption("rank")!!.asInt,
                event.getOption("message")!!.asString,
                event.getOption("role")?.asRole)
            "rank/add/ignorerole" -> Ranks.addIgnoreRole(event, event.getOption("role")!!.asRole)
            "rank/add/ignorechannel" -> Ranks.addIgnoreChannel(event,
                event.getOption("channel")!!.asGuildChannel)
            //Rank remove commands
            "rank/remove/specialrank" -> Ranks.removeSpecialRank(event, event.getOption("rank")!!.asInt)
            "rank/remove/ignorerole" -> Ranks.removeIgnoreRole(event, event.getOption("role")!!.asRole)
            "rank/remove/ignorechannel" -> Ranks.removeIgnoreChannel(event,
                event.getOption("channel")!!.asGuildChannel)
            //Rank get commands
            "rank/get/xp" -> Ranks.getXP(event)
            "rank/get/message" -> Ranks.getMessage(event)
            "rank/get/specialrank" -> Ranks.getSpecialRank(event)
            "rank/get/ignorerole" -> Ranks.getIgnoreRole(event)
            "rank/get/ignorechannel" -> Ranks.getIgnoreChannel(event)
            "rank/get/userinfo" -> Ranks.getUserInfo(event, event.getOption("userid")!!.asString)
            //Rank give commands
            "rank/give/rank" -> Ranks.giveRank(event,
                event.getOption("rank")!!.asInt,
                event.getOption("user")!!.asUser)
            "rank/give/xp" -> Ranks.giveXP(event,
                event.getOption("xp")!!.asLong,
                event.getOption("user")!!.asUser)
        }
    }

    fun onSelectMenuInteraction(event: SelectMenuInteractionEvent) {
        if (event.componentId == "assignable-roles") {
            val roleIds: List<String> = event.values
            val invalidRoleIds = mutableListOf<String>()
            val validRoleIds = mutableListOf<String>()
            roleIds.forEach {
                val role = event.guild?.getRoleById(it)
                if (role != null) {
                    event.guild!!.addRoleToMember(event.member!!.user, role).queue()
                    validRoleIds.add(role.name)
                } else {
                    invalidRoleIds.add(it)
                }
            }
            val choosenRoles = "You chose ${validRoleIds.joinToString(", ")}"
            if (invalidRoleIds.isNotEmpty()) {
                event.reply("$choosenRoles\nI could not find the following role IDs: ${invalidRoleIds.joinToString(" ")}")
                    .queue()
            } else {
                event.reply(choosenRoles).queue()
            }
        }
    }

    suspend fun onButtonInteraction(event: ButtonInteractionEvent) {
        event.deferEdit().queue() // acknowledge the button was clicked, otherwise the interaction will fail
        if (!event.isFromGuild) return
        when (event.component.id) {
            "ping-on-return" -> {
                val realm = Realm.open(NeoModLog.configuration)
                try {
                    val userToReturnId = event.message.embeds[0].footer?.text?.replace("User ID: ", "")
                    val userToPingId = event.user.id
                    val guildId = event.guild!!.id

                    realm.write {
                        var ping: NeoModLog.Ping? =
                            this.query<NeoModLog.Ping>("userToPing = \"${userToPingId}\" AND userToReturn = \"${userToReturnId}\" AND realmGuild.id = \"${guildId}\"")
                                .first().find()
                        if (ping != null) {
                            delete(ping)
                            event.hook.setEphemeral(true)
                                .sendMessage("You will no longer be notified when this user returns").queue()
                        } else {
                            ping = NeoModLog.Ping().apply {
                                userToPing = userToPingId
                                userToReturn = userToReturnId!!
                                realmGuild = RealmGuild().apply {
                                    id = guildId
                                }
                            }
                            copyToRealm(ping, UpdatePolicy.ALL)
                            event.hook.setEphemeral(true).sendMessage("You will be notified when this user returns")
                                .queue()
                        }
                    }
                } finally {
                    realm.close()
                }
            }
        }


    }
}
