package embedutils

enum class Standard(val description: String) {
    ERROR_GENERIC("An error has occurred. Please check if you typed the command correctly and try again."),
    ERROR_MANAGE_RULES( "You don't have permission to manage rules."),
    ERROR_NO_MENTIONED_MEMBERS("No members mentioned."),
    ERROR_NO_MENTIONED_USERS("No users mentioned."),
    ERROR_BOT_INSUFFICIENT_PERMISSION("I don't have enough permissions to do that."),
    ERROR_AUTHOR_INSUFFICIENT_PERMISSION("You don't have enough permissions to do that."),
    ERROR_MEMBER_NOT_IN_GUILD("The member you mentioned is not in this guild at the moment."),
    ERROR_NO_ADMIN_PERMISSION("You don't have admin permissions."),
}