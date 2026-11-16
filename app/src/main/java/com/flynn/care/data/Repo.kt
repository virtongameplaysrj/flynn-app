package com.flynn.care.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
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
 *   households/flynn                   { totalFeedings }
 *   households/flynn/feedings/{id}     { at, by }
 *   households/flynn/items/{id}        CareItem
 * As regras (firestore.rules) só liberam esse caminho para os 2 e-mails de vocês.
 */
object Repo {
    private val db get() = FirebaseFirestore.getInstance()
    private val household get() = db.collection("households").document("flynn")
    private val feedings get() = household.collection("feedings")
    private val items get() = household.collection("items")
    private val weights get() = household.collection("weights")

    /** Último erro de sincronização (ex.: e-mail sem permissão), mostrado num aviso. */
    val lastError = MutableStateFlow<String?>(null)
    private fun report(e: Exception?) {
        if (e != null) lastError.value = e.localizedMessage ?: e.toString()
    }

    fun totalFeedings(): Flow<Long> = callbackFlow {
        val reg = household.addSnapshotListener { snap, e ->
            report(e)
            if (snap != null) trySend(snap.getLong("totalFeedings") ?: 0L)
        }
        awaitClose { reg.remove() }
    }

    /** Refeições dos últimos [days] dias, mais recente primeiro. */
    fun recentFeedings(days: Long = 3): Flow<List<Feeding>> = callbackFlow {
        val since = Timestamp(Date.from(Instant.now().minus(Duration.ofDays(days))))
        val reg = feedings.whereGreaterThan("at", since)
            .orderBy("at", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, e ->
                report(e)
                if (snap != null) trySend(snap.documents.mapNotNull { d ->
                    val at = d.getTimestamp("at") ?: return@mapNotNull null
                    Feeding(d.id, at.toDate().toInstant(), d.getString("by") ?: "")
                })
            }
        awaitClose { reg.remove() }
    }

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
     * Registra uma refeição. O incremento é atômico no servidor, então cliques
     * simultâneos nos 2 celulares somam corretamente. Funciona offline (sincroniza depois).
     * Retorna o id do registro, para o "Desfazer".
     */
    fun feed(by: String): String {
        val ref = feedings.document()
        db.batch()
            .set(ref, mapOf("at" to Timestamp.now(), "by" to by))
            .set(household, mapOf("totalFeedings" to FieldValue.increment(1)), SetOptions.merge())
            .commit()
            .addOnFailureListener { report(it) }
        return ref.id
    }

    fun deleteFeeding(id: String) {
        db.batch()
            .delete(feedings.document(id))
            .set(household, mapOf("totalFeedings" to FieldValue.increment(-1)), SetOptions.merge())
            .commit()
            .addOnFailureListener { report(it) }
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
