package tech.bananajuice.shared.profilepicker

interface BaseProfile {
    val id: String
    val name: String
    val folderUri: String
    val customSettings: Map<String, String>
}

data class Profile(
    override val id: String = java.util.UUID.randomUUID().toString(),
    override val name: String,
    override val folderUri: String,
    override val customSettings: Map<String, String> = emptyMap()
) : BaseProfile
