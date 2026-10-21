package reclamos

import java.time.LocalDate

/** Un correo tal como llega del cliente, más la fecha en que lo recibimos. */
data class Correo(
    val archivo: String,
    val fechaRecepcion: LocalDate,
    val texto: String,
)

/** Un reclamo de cobro de casetas, tal como lo extrajo el modelo (todavía sin validar). */
data class Reclamo(
    val unidad: String?,        // número económico, ej. "T-145"
    val placas: String?,
    val tag: String?,           // número del tag de telepeaje
    val caseta: String?,
    val fechaInicio: String?,   // "YYYY-MM-DD"
    val fechaFin: String?,      // "YYYY-MM-DD"
    val monto: Double?,
    val motivo: String,         // por qué el cliente dice que el cobro está mal
    val cita: String,           // fragmento literal del correo que respalda el reclamo
    val dudas: String?,         // lo que el modelo no tuvo claro
)

enum class Estado { LISTO, REVISAR }

/** Un reclamo después de pasar por el validador. */
data class ReclamoRevisado(
    val reclamo: Reclamo,
    val estado: Estado,
    val motivosRevision: List<String>,
)

/** Lo que salió de procesar un correo completo. */
data class Resultado(
    val archivo: String,
    val esReclamo: Boolean,
    val reclamos: List<ReclamoRevisado>,
    val error: String? = null,
)
