
import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.events.message.MessageBulkDeleteEvent
import net.dv8tion.jda.api.events.message.MessageDeleteEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.events.message.MessageUpdateEvent
import java.time.Duration

val messageCacheCaffeine: Cache<String, MessageLite> =
    Caffeine.newBuilder().maximumSize(30000) // SCBOT Python had a 30k message cache limit
        .expireAfterWrite(Duration.ofDays(15)).build()

class MessageLite(
    val id: String = "",
    val authorID: String,
    val effectiveAvatarUrl: String,
    val contentDisplay: String,
    val attachmentsURLs: List<String>
)
// store only the following message properties: id, channel, author, contentDisplay, guild, attachments.
// this is to reduce the memory usage of the cache

object MessageCache {

    //custom lighter message objects for the cache
    private fun convertToLite(message: Message) = MessageLite(
        id = message.id,
        authorID = message.author.id,
        effectiveAvatarUrl = message.author.effectiveAvatarUrl,
        contentDisplay = message.contentDisplay,
        attachmentsURLs = message.attachments.map {
            it.url
        })

    //This is the only that gets directly fom the listener. The others are called/handled by ModLogs first.
    fun onMessageReceived(event: MessageReceivedEvent) {
        if (!event.isFromGuild) return
        if (event.author.isBot) return
        messageCacheCaffeine.put(event.messageId, convertToLite(event.message))
    }

    fun onMessageDeleteEvent(event: MessageDeleteEvent) {
        messageCacheCaffeine.getIfPresent(event.messageId)?.let {
            messageCacheCaffeine.invalidate(it.id)
        }
    }

    fun onMessageBulkDeleteEvent(event: MessageBulkDeleteEvent) {
        event.messageIds.forEach {
            messageCacheCaffeine.invalidate(it)
        }
    }

    fun onMessageUpdateEvent(event: MessageUpdateEvent) {
        messageCacheCaffeine.getIfPresent(event.messageId)?.let {
            messageCacheCaffeine.put(event.message.id, convertToLite(event.message))
        }
    }
}

