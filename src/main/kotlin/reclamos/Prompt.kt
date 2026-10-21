package reclamos
import java.time.format.TextStyle
import java.util.Locale

/**
 * Las instrucciones y el esquema JSON que le mandamos al modelo.
 * El esquema obliga a que la respuesta tenga exactamente esta forma
 * (salida estructurada), así no hay que "adivinar" cómo leer el texto.
 */
object Prompt {

    val SISTEMA = """
        Trabajas en el área de operaciones de una empresa que administra el pago de casetas
        para flotillas de tractocamiones en México. Te llegan correos de clientes que reclaman
        cobros de casetas que, según ellos, sus camiones no hicieron.

        Tu tarea: extraer cada reclamo del correo en el formato indicado.

        Reglas:
        - Un reclamo por camión. Si el correo menciona dos camiones, son dos reclamos.
        - unidad: el número económico tal como aparece, en mayúsculas (ej. "T-145"). Null si no aparece.
        - placas: en mayúsculas, tal como aparecen. Null si no aparecen.
        - tag: solo el número del tag (sin "IAVE", "TAG", etc.). Null si no aparece.
        - caseta: solo el nombre de la caseta (ej. "Palmillas"). Null si no aparece.
        - fecha_inicio y fecha_fin en formato YYYY-MM-DD. Si es un solo día, ambas iguales.
          Resuelve fechas relativas ("el sábado pasado") usando la fecha de recepción.
          Si el correo no permite saber las fechas, null.
        - monto: el monto reclamado por ese camión, en pesos. Null si no aparece.
        - motivo: en una frase corta, por qué el cliente dice que el cobro está mal.
        - cita: copia LITERALMENTE el fragmento del correo que respalda el reclamo. No lo parafrasees.
        - dudas: si algo es ambiguo o tuviste que suponer, explícalo en una frase. Si no, null.
        - NO inventes datos. Si algo no está en el correo, va null.
        - Si el correo no es un reclamo de cobros (por ejemplo, pide una factura), es_reclamo = false
          y reclamos = [].
    """.trimIndent()

    // Le damos el día de la semana ya calculado: el modelo se equivocaba resolviendo
    // "el sábado pasado" (la evaluación lo detectó). Las cuentas de fechas, mejor en código.
    // Sin trimIndent: el texto del correo trae sus propias líneas y lo arruinaría.
    fun mensajeUsuario(correo: Correo): String {
        val dia = correo.fechaRecepcion.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.forLanguageTag("es"))
        return "Fecha de recepción: ${correo.fechaRecepcion} ($dia)\n\n<correo>\n${correo.texto.trim()}\n</correo>"
    }

    val ESQUEMA = """
        {
          "type": "object",
          "additionalProperties": false,
          "required": ["es_reclamo", "reclamos"],
          "properties": {
            "es_reclamo": { "type": "boolean" },
            "reclamos": {
              "type": "array",
              "items": {
                "type": "object",
                "additionalProperties": false,
                "required": ["unidad", "placas", "tag", "caseta", "fecha_inicio", "fecha_fin",
                             "monto", "motivo", "cita", "dudas"],
                "properties": {
                  "unidad":       { "type": ["string", "null"] },
                  "placas":       { "type": ["string", "null"] },
                  "tag":          { "type": ["string", "null"] },
                  "caseta":       { "type": ["string", "null"] },
                  "fecha_inicio": { "type": ["string", "null"] },
                  "fecha_fin":    { "type": ["string", "null"] },
                  "monto":        { "type": ["number", "null"] },
                  "motivo":       { "type": "string" },
                  "cita":         { "type": "string" },
                  "dudas":        { "type": ["string", "null"] }
                }
              }
            }
          }
        }
    """.trimIndent()
}
