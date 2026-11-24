package com.flynn.care.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.time.Duration
import java.time.Instant
import java.util.Date

/**
 * Tudo fica em households/flynn no Firestore:
 *   households/flynn                   { totalFeedings (legado), eyedropGoal, eyedropReminderHour }
 *   households/flynn/feedings/{id}     { at, by }   comida
 *   households/flynn/eyedrops/{id}     { at, by }   colírio
 *   households/flynn/brushings/{id}    { at, by }   escovação
 *   households/flynn/items/{id}        CareItem
 *   households/flynn/weights/{id}      WeightEntry
 * As regras (firestore.rules) liberam households/flynn e tudo abaixo para os 2 e-mails de vocês.
 */
object Repo {
    private val db get() = FirebaseFirestore.getInstance()
    private val household get() = db.collection("households").document("flynn")
    private fun events(type: ActionType) = household.collection(type.collection)
    private val items get() = household.collection("items")
    private val weights get() = household.collection("weights")

    /** Último erro de sincronização (ex.: e-mail sem permissão), mostrado num aviso. */
    val lastError = MutableStateFlow<String?>(null)
    private fun report(e: Exception?) {
        if (e != null) lastError.value = e.localizedMessage ?: e.toString()
    }

    // ---- Meta do colírio (campos no documento households/flynn)

    fun eyedropSettings(): Flow<EyedropSettings> = callbackFlow {
        val reg = household.addSnapshotListener { snap, e ->
            report(e)
            if (snap != null) trySend(snap.toEyedropSettings())
        }
        awaitClose { reg.remove() }
    }

    suspend fun fetchEyedropSettings(): EyedropSettings = household.get().await().toEyedropSettings()

    private fun DocumentSnapshot.toEyedropSettings() = EyedropSettings(
        goal = getLong("eyedropGoal")?.toInt() ?: 0,
        reminderHour = getLong("eyedropReminderHour")?.toInt() ?: -1,
    )

    fun saveEyedropSettings(s: EyedropSettings) {
        household.set(
            mapOf("eyedropGoal" to s.goal, "eyedropReminderHour" to s.reminderHour),
            SetOptions.merge(),
        ).addOnFailureListener { report(it) }
    }

    // ---- Registros dos botões (comida, colírio, dentes)

    /** Registros de [type] dos últimos [days] dias, mais recente primeiro. */
    fun recentEvents(type: ActionType, days: Long): Flow<List<CareEvent>> = callbackFlow {
        val since = Timestamp(Date.from(Instant.now().minus(Duration.ofDays(days))))
        val reg = events(type).whereGreaterThan("at", since)
            .orderBy("at", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, e ->
                report(e)
                if (snap != null) trySend(snap.documents.mapNotNull { d ->
                    val at = d.getTimestamp("at") ?: return@mapNotNull null
                    CareEvent(d.id, type, at.toDate().toInstant(), d.getString("by") ?: "")
                })
            }
        awaitClose { reg.remove() }
    }

    /** Quantos registros de [type] desde [since] — usado pelo lembrete do colírio, fora da tela. */
    suspend fun countSince(type: ActionType, since: Instant): Int =
        events(type).whereGreaterThanOrEqualTo("at", Timestamp(Date.from(since))).get().await().size()

    fun items(): Flow<List<CareItem>> = callbackFlow {
        val reg = items.addSnapshotListener { snap, e ->
            report(e)
            if (snap != null) trySend(snap.documents.map { CareItem.fromMap(it.id, it.data ?: emptyMap()) })
        }
        awaitClose { reg.remove() }
    }

    suspend fun fetchItems(): List<CareItem> =
        items.get().await().documents.map { CareItem.fromMap(it.id, it.data ?: emptyMap()) }

    /**
     * Registra um clique. Cada clique é um documento próprio, então cliques simultâneos
     * nos 2 celulares nunca se sobrescrevem. Funciona offline (sincroniza depois).
     * Retorna o id do registro, para o "Desfazer".
     */
    fun record(type: ActionType, by: String): String {
        val ref = events(type).document()
        ref.set(mapOf("at" to Timestamp.now(), "by" to by)).addOnFailureListener { report(it) }
        return ref.id
    }

    fun deleteEvent(type: ActionType, id: String) {
        events(type).document(id).delete().addOnFailureListener { report(it) }
    }

    fun save(item: CareItem) {
        val ref = if (item.id.isBlank()) items.document() else items.document(item.id)
        ref.set(item.toMap()).addOnFailureListener { report(it) }
    }

    fun delete(item: CareItem) {
        if (item.id.isNotBlank()) items.document(item.id).delete().addOnFailureListener { report(it) }
    }

    // ---- Peso: households/flynn/weights/{id} { date: "yyyy-MM-dd", kg, by }

    fun weights(): Flow<List<WeightEntry>> = callbackFlow {
        val reg = weights.addSnapshotListener { snap, e ->
            report(e)
            if (snap != null) trySend(snap.documents.mapNotNull { d ->
                val kg = d.getDouble("kg") ?: return@mapNotNull null
                WeightEntry(d.id, d.getString("date") ?: "", kg, d.getString("by") ?: "")
            })
        }
        awaitClose { reg.remove() }
    }

    fun saveWeight(date: java.time.LocalDate, kg: Double, by: String) {
        weights.document()
            .set(mapOf("date" to date.toString(), "kg" to kg, "by" to by))
            .addOnFailureListener { report(it) }
    }

    fun deleteWeight(id: String) {
        weights.document(id).delete().addOnFailureListener { report(it) }
    }
}
