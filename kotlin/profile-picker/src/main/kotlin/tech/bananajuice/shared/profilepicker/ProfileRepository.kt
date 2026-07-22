package tech.bananajuice.shared.profilepicker

interface ProfileRepository<T : BaseProfile> {
    suspend fun getProfiles(): List<T>
    suspend fun getDefaultProfileId(): String?
    suspend fun saveProfile(profile: T, isDefault: Boolean)
    suspend fun deleteProfile(profileId: String)
}
