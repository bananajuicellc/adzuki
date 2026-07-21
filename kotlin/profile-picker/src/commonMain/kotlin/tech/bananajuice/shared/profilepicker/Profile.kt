package tech.bananajuice.shared.profilepicker

interface BaseProfile {
    val id: String
    val name: String
    val folderUri: String
    val customSettings: Map<String, String>
}

fun generateUuid(): String {
    val chars = "0123456789abcdef"
    return buildString {
        for (i in 0 until 36) {
            when (i) {
                8, 13, 18, 23 -> append('-')
                14 -> append('4')
                19 -> append(chars[kotlin.random.Random.nextInt(8, 12)])
                else -> append(chars[kotlin.random.Random.nextInt(16)])
            }
        }
    }
}

data class Profile(
    override val id: String = generateUuid(),
    override val name: String,
    override val folderUri: String,
    override val customSettings: Map<String, String> = emptyMap()
) : BaseProfile
