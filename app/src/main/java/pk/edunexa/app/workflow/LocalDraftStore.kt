package pk.edunexa.app.workflow

import android.content.Context
import org.json.JSONObject

/** Device-private recovery only. Firestore remains authoritative; credentials are never persisted. */
class LocalDraftStore(context:Context,uid:String,schoolId:String,kind:String,id:String) {
    private val prefs=context.applicationContext.getSharedPreferences("edunexa_private_drafts",Context.MODE_PRIVATE)
    private val key=listOf(uid,schoolId,kind,id).joinToString("|"){it.length.toString()+":"+it}
    fun load():Map<String,String> = runCatching {val json=JSONObject(prefs.getString(key,"{}").orEmpty());json.keys().asSequence().associateWith{json.optString(it)}}.getOrDefault(emptyMap())
    fun save(values:Map<String,String>) {check(prefs.edit().putString(key,JSONObject(values).toString()).commit()) {"Could not save draft on this phone."}}
    fun clear() {prefs.edit().remove(key).apply()}
}
