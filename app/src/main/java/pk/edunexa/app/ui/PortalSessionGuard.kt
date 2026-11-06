package pk.edunexa.app.ui

import androidx.compose.runtime.*
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import pk.edunexa.app.auth.PortalRole
import pk.edunexa.app.auth.PortalSession
import pk.edunexa.app.users.UserAccessPolicy

@Composable
internal fun PortalSessionGuard(session: PortalSession, onBlocked: () -> Unit) {
    val latestCallback by rememberUpdatedState(onBlocked)
    DisposableEffect(session.uid, session.schoolId, session.role, session.profileId) {
        val db = FirebaseFirestore.getInstance()
        var fired = false
        fun block() { if(!fired) { fired = true; latestCallback() } }
        val owner = session.role == PortalRole.OWNER
        val user = db.collection("users").document(session.uid).addSnapshotListener { doc, error ->
            if(error?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) block()
            else if(error == null && doc != null && (!doc.exists() || doc.getBoolean("active") != true || doc.getBoolean("portalDisabled") == true || (!owner && doc.getString("schoolId") != session.schoolId) || (if(owner) doc.getString("role") !in setOf("owner", "admin") else doc.getString("role") != session.role.value) || (!owner && session.role != PortalRole.ADMIN && doc.getString("profileId") != session.profileId))) block()
        }
        val profile = if(owner || session.role == PortalRole.ADMIN || session.profileId.isNullOrBlank()) null else db.collection("schools").document(session.schoolId).collection(if(session.role == PortalRole.TEACHER) "teachers" else "students").document(session.profileId).addSnapshotListener { doc,error ->
            if(error?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) block()
            else if(error == null && doc != null && (!doc.exists() || !UserAccessPolicy.eligible(session.role.name.lowercase(),doc.getString("status").orEmpty()))) block()
        }
        val school=if(owner) null else db.collection("schools").document(session.schoolId).addSnapshotListener { doc,error ->
            if(error?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) block()
            else if(error==null && doc!=null && (!doc.exists() || (doc.getBoolean("portalAccessEnabled")==false && !session.isPlatformOwner))) block()
        }
        onDispose { user.remove(); profile?.remove(); school?.remove() }
    }
}
