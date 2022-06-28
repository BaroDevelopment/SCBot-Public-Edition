

import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter

object CommandsMisc : ListenerAdapter() {

    // Code reminescent of Mubeppod. It has not been changed and

    fun getGuilds(event: MessageReceivedEvent){
        event.jda.guilds.size
        val guildNames = mutableListOf<String>()
        for (item in event.jda.guilds) {
            guildNames.add(item.name + " `ID: ${item.id}`")
        }
        event.channel.sendMessage("O bot está atualmente em " + event.jda.guilds.size + " guildas com os seguintes nomes: " + guildNames.toString()).queue()
    }

    fun leaveGuild(guildID: String?, event: MessageReceivedEvent){
        if (guildID.isNullOrEmpty()){
            event.channel.sendMessage("ID inválido ou não consegui encontrar a guilda.").queue()
        }else{
            val guild = event.jda.getGuildById(guildID)!!
            val guildName = guild.name
            guild.leave().flatMap{
                event.channel.sendMessage("O abandono da guilda \"$guildName\" foi bem sucedido.")
            }.onErrorFlatMap {
                event.channel.sendMessage("Não foi possível abandonar a guilda \"$guildName\".")
            }

        }
    }

}
