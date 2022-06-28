package commands

import RealmGuild
import dev.minn.jda.ktx.interactions.SelectMenu
import embedutils.permissionCheck
import io.realm.kotlin.Realm
import io.realm.kotlin.RealmConfiguration
import io.realm.kotlin.UpdatePolicy
import io.realm.kotlin.ext.query
import io.realm.kotlin.types.RealmObject
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent


object SelfAssignRolesMenu {

    private val configuration =
        RealmConfiguration.Builder(schema = setOf(RealmGuild::class, SelfAssignableRole::class)).name("selfassignroles")
            .schemaVersion(1.1.toLong()).build()

    //todo: i should try an approach similar to InstantBan
    class SelfAssignableRole : RealmObject {
        var name: String = ""
        var id =
            "" // The role id is what the bot is going to send and then get onSelectMenuInteraction event.getValues()
        var description = ""
        var realmGuild: RealmGuild? = null
    }

    fun getRoles(event: SlashCommandInteractionEvent) {
        event.deferReply().queue()
        val hook = event.hook
        hook.sendMessage("This command is still in development.").queue()
        return
        // todo: add remove roles
        val realm = Realm.open(configuration)
        val rolesMutableList =
            realm.query<SelfAssignableRole>("realmGuild.id == $0", event.guild?.id).find()
        val menu = SelectMenu("assignable-roles") {
            placeholder = "Select a role to assign to yourself"
            setRequiredRange(0,
                rolesMutableList.size) // sets the minimum and maximum amount of selections that can be made
            rolesMutableList.forEach {
                addOption(it.name, it.id, it.description)
            }
            build()
        }
        hook.sendMessage(
            if (rolesMutableList.isEmpty()) {
                "There are no roles to assign to yourself"
            } else {
                "Select a role to assign to yourself"
            }
        ).addActionRow(menu).queue()
    }

    suspend fun addMenuRole(event: SlashCommandInteractionEvent, role: Role) {
            event.deferReply().queue()
            val hook = event.hook
            permissionCheck(event, Permission.ADMINISTRATOR)
            val realm = Realm.open(configuration)
            try {
                realm.write {
                val foundRoles = this.query<SelfAssignableRole>("realmGuild.id == $0", event.guild?.id).find()
                foundRoles.forEach {
                    if (it.id == role.id) {
                        hook.sendMessage("Role already exists").queue()
                        return@write //if the role already exists, return
                    }
                }
                if (foundRoles.isEmpty()) { //means no roles were found
                    val newRole = SelfAssignableRole().apply {
                        name = role.name
                        id = role.id
                        description = role.name
                        realmGuild = RealmGuild().apply {
                            id = event.guild?.id!!
                        }
                    }
                    copyToRealm(newRole, UpdatePolicy.ALL)
                    hook.sendMessage("Role added").queue()
                }
            }
            } finally {
                realm.close()
            }

    }
        //todo: need to do the removeMenuRole

}

