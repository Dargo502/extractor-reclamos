package reclamos

import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

internal val httpCliente: HttpClient =
    HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build()

/** Reintenta solo errores temporales: 429 (límite de uso) y 5xx (servidor). Lo usan todos los clientes. */
internal fun enviarConReintentos(solicitud: HttpRequest, intentos: Int = 3): HttpResponse<String> {
    repeat(intentos) { intento ->
        val respuesta = httpCliente.send(solicitud, HttpResponse.BodyHandlers.ofString())
        val codigo = respuesta.statusCode()
        when {
            codigo == 200 -> return respuesta
            (codigo == 429 || codigo >= 500) && intento < intentos - 1 ->
                Thread.sleep(2000L * (intento + 1))   // espera 2s, 4s...
            else -> error("La API respondió $codigo: ${respuesta.body()}")
        }
    }
    error("Sin respuesta después de $intentos intentos")
}
