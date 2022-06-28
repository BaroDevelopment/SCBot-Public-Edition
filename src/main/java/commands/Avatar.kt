package commands

import embedutils.NeoSuperEmbed
import embedutils.SuperEmbed
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.events.message.MessageReceivedEvent

fun getAvatar(event: MessageReceivedEvent) {
    if (event.message.mentions.members.isNotEmpty()) {
        val member = event.message.mentions.members[0]
        getAvatar(event, member)
    } else {
        //If it does not have an userID after a whitespace, then it will return the original message
        //the original message should then be either "!a" or "!avatar"
        when (val messageContent = event.message.contentDisplay.substringAfter(" ")) {
            "!avatar" -> getAvatar(event, event.member!!)
            "!a" -> getAvatar(event, event.member!!)
            else -> getAvatar(event, messageContent)
        }
    }
}

private fun getAvatar(event: MessageReceivedEvent, user: String) {
    runCatching {
        val actualUser = event.jda.getUserById(user)!!
        event.message.reply("${actualUser.asTag}'s avatar: ${actualUser.effectiveAvatarUrl + "?size=2048"}").queue()
    }.onFailure {
        event.message.replyEmbeds(
            NeoSuperEmbed {
                type = SuperEmbed.ResultType.ERROR
                text = "Invalid UserID"
            }).queue()
    }
}

private fun getAvatar(event: MessageReceivedEvent, member: Member) {

    //member.effectiveAvatarUrl will show the perguild avatar if the member has one.
    //In case it has, the below 'if' will show the users regular avatar too.
    val msg : String = if (member.avatarUrl != null) {
        "${member.user.asTag}'s server avatar: ${member.effectiveAvatarUrl + "?size=2048"}\n" +
                "${member.user.asTag}'s avatar: ${member.user.effectiveAvatarUrl + "?size=2048"}"
    } else {
        "${member.user.asTag}'s avatar: ${member.effectiveAvatarUrl + "?size=2048"}\n"
    }
    event.message.reply(msg).queue()
    }
