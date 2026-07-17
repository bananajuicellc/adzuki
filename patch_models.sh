#!/bin/bash
cat << 'INNER_EOF' > /tmp/patch_models.txt
<<<<<<< SEARCH
sealed class Screen {
    object SelectFolder : Screen()
    object JournalList : Screen()
    data class FileList(val folderUri: String) : Screen()
    data class Editor(val fileUri: String) : Screen()
}
=======
import org.json.JSONObject

data class Profile(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val folderUri: String
) {
    fun toJson(): String {
        return JSONObject().apply {
            put("id", id)
            put("name", name)
            put("folderUri", folderUri)
        }.toString()
    }

    companion object {
        fun fromJson(json: String): Profile {
            val obj = JSONObject(json)
            return Profile(
                id = obj.getString("id"),
                name = obj.getString("name"),
                folderUri = obj.getString("folderUri")
            )
        }
    }
}

sealed class Screen {
    object ProfilePicker : Screen()
    data class ProfileEditor(val profileId: String?) : Screen()
    data class FileList(val folderUri: String) : Screen()
    data class Editor(val fileUri: String) : Screen()
}
>>>>>>> REPLACE
INNER_EOF
