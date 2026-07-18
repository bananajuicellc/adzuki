package tech.bananajuice.adzuki.android

import android.content.Context
import android.content.SharedPreferences
import tech.bananajuice.shared.profilepicker.Profile
import tech.bananajuice.shared.profilepicker.ProfileRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AndroidProfileRepository(context: Context) : ProfileRepository<Profile> {
    private val prefs: SharedPreferences = context.getSharedPreferences("adzuki_prefs", Context.MODE_PRIVATE)

    override suspend fun getProfiles(): List<Profile> = withContext(Dispatchers.IO) {
        val savedProfilesJson = prefs.getStringSet("profiles", emptySet()) ?: emptySet()
        savedProfilesJson.mapNotNull { jsonString ->
            try {
                val obj = org.json.JSONObject(jsonString)
                val customSettingsObj = obj.optJSONObject("customSettings")
                val customSettingsMap = mutableMapOf<String, String>()
                customSettingsObj?.keys()?.forEach { key ->
                    customSettingsMap[key] = customSettingsObj.getString(key)
                }
                Profile(
                    id = obj.getString("id"),
                    name = obj.getString("name"),
                    folderUri = obj.getString("folderUri"),
                    customSettings = customSettingsMap
                )
            } catch (e: Exception) { null }
        }
    }

    override suspend fun getDefaultProfileId(): String? = withContext(Dispatchers.IO) {
        prefs.getString("default_profile_id", null)
    }

    override suspend fun saveProfile(profile: Profile, isDefault: Boolean) = withContext(Dispatchers.IO) {
        val profiles = getProfiles().toMutableList()
        val index = profiles.indexOfFirst { it.id == profile.id }
        if (index != -1) {
            profiles[index] = profile
        } else {
            profiles.add(profile)
        }

        val editor = prefs.edit()
        editor.putStringSet("profiles", profiles.map { p ->
            org.json.JSONObject().apply {
                put("id", p.id)
                put("name", p.name)
                put("folderUri", p.folderUri)
                val customObj = org.json.JSONObject()
                p.customSettings.forEach { (key, value) ->
                    customObj.put(key, value)
                }
                put("customSettings", customObj)
            }.toString()
        }.toSet())

        if (isDefault) {
            editor.putString("default_profile_id", profile.id)
        } else if (getDefaultProfileId() == profile.id) {
            editor.remove("default_profile_id")
        }

        editor.apply()
    }

    override suspend fun deleteProfile(profileId: String) = withContext(Dispatchers.IO) {
        val profiles = getProfiles().filter { it.id != profileId }
        val editor = prefs.edit()
        editor.putStringSet("profiles", profiles.map { p ->
             org.json.JSONObject().apply {
                put("id", p.id)
                put("name", p.name)
                put("folderUri", p.folderUri)
                val customObj = org.json.JSONObject()
                p.customSettings.forEach { (key, value) ->
                    customObj.put(key, value)
                }
                put("customSettings", customObj)
            }.toString()
        }.toSet())

        if (getDefaultProfileId() == profileId) {
            editor.remove("default_profile_id")
        }

        editor.apply()
    }
}
