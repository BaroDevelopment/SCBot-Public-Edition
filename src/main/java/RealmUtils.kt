
import commands.Ranks
import commands.TimeOut
import io.realm.kotlin.MutableRealm
import io.realm.kotlin.UpdatePolicy
import io.realm.kotlin.types.RealmObject

object RealmUtils {


    /**
     * Meant to create a Realm object if it does not exist, applies the realmGuild with the corresponding guild id, and writes it to the database.
     *
     * @param T Any type of RealmObject
     * @param transaction The current realm.write being used.
     * @param guildId The guild id - required because we will always need to set the guild id
     * @param userId The user id - only needed for RankMember
     * @return A new RealmObject freshly inserted in the Realm
     */
    inline fun <reified T : RealmObject> T.create(
        transaction: MutableRealm,
        guildId: String,
        userId: String? = null // the userID is only used in RankMember
    ): T {

        val clazz = this::class.java
        var newInstance = clazz.getDeclaredConstructor().newInstance()

        // It doesn't make sense to add SelfAssignableRole, Ping nor Word.
        when (newInstance) {
            is Ranks.RankSettings -> newInstance.apply {
                realmGuild = RealmGuild().apply {
                    id = guildId
                }
            }
            // RankMember is the only one that uses args[1] and it uses it for the user id
            is Ranks.RankMember -> newInstance.apply {
                id = userId!!
                realmGuild = RealmGuild().apply {
                    id = guildId
                }
            }
            is TimeOut.Configuration -> newInstance.apply {
                realmGuild = RealmGuild().apply {
                    id = guildId
                }
            }
            is NeoModLog.Channels -> newInstance.apply {
                realmGuild = RealmGuild().apply {
                    id = guildId
                }
            }
        }
        newInstance = transaction.copyToRealm(newInstance, UpdatePolicy.ALL)
        return newInstance as T
    }
}