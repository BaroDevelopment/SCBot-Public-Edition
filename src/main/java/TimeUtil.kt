class TimeUtil {

    private var realInt: Long = 0
    private var lastChar: Char = '\u0000'
    private val allowedChars: List<Char> = listOf('s', 'm', 'h', 'd')


    /**
     *
     * Returns the time unit given to parse() in a human-readable format
     *
     * Example: 's' -> "seconds"
     * @return String
     */
    val humanReadableTimeUnit: String
        get() =
            when (lastChar) {
                's' -> "second(s)"
                'm' -> "minute(s)"
                'h' -> "hour(s)"
                'd' -> "day(s)"
                else -> "" //This shouldn't be returned but "when" statements need to be exhaustive.
            }

    /**
     *
     * Converts value given to parse() in millisecond
     * @return Long
     */
    val toMillisecond: Long
        get() =
            when (lastChar) {
                's' -> realInt * 1000
                'm' -> realInt * 60 * 1000
                'h' -> realInt * 60 * 60 * 1000
                'd' -> realInt * 24 * 60 * 60 * 1000
                else -> 0 //This shouldn't be returned but "when" statements need to be exhaustive.
            }

    /**
     * Parses the given string
     *
     * @param time
     * @throws NumberFormatException if string does not represent a number when the last character is dropped
     * @throws IllegalArgumentException if the last character is not 's', 'm', 'h' or 'd'
     */
    fun parse(time: String): TimeUtil {
        realInt = time.dropLast(1).toLongOrNull()
            ?: throw NumberFormatException("The string \"${time.dropLast(1)}\" cannot be cast to Long because it is not a number.")
        // realInt is the number used.
        // Example: If the input is "20s" then realInt is "20". The convertion is done afterwards.

        if (time.last() !in allowedChars) {
            // If it's not one of the recognized characters then it's useless to continue.
            // This check allows "toMillisecond" and "humanReadableTimeUnit" to not return null values.
            throw IllegalArgumentException("The last character in the string \"${time}\" is not one of the following: 's', 'm', 'h', 'd'.")
        }
        lastChar = time.last()
        return this
    }
}