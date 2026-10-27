package com.lexia.player.data

import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.security.SecureRandom
import kotlin.concurrent.thread

/**
 * Petit serveur web local : le téléphone (même Wi-Fi) scanne le QR code affiché sur la TV,
 * remplit ses identifiants sur une page web servie par la Fire TV, et les envoie directement.
 * Rien ne transite par Internet. Le serveur ne tourne que tant que l'écran de connexion est affiché.
 */
class PhoneLoginServer(private val onReceived: (name: String, config: LoginConfig) -> Unit) {

    private var server: ServerSocket? = null
    private val token: String = buildString {
        val chars = "abcdefghjkmnpqrstuvwxyz23456789"
        val random = SecureRandom()
        repeat(8) { append(chars[random.nextInt(chars.length)]) }
    }

    /** Adresse à encoder dans le QR code, ou null si la Fire TV n'est pas sur un réseau local. */
    var url: String? = null
        private set

    fun start(): String? {
        if (server != null) return url
        val ip = localIpAddress() ?: return null
        val socket = runCatching { ServerSocket(PORT) }.getOrElse { runCatching { ServerSocket(0) }.getOrNull() }
            ?: return null
        server = socket
        url = "http://$ip:${socket.localPort}/?t=$token"
        thread(name = "phone-login", isDaemon = true) {
            while (!socket.isClosed) {
                val client = runCatching { socket.accept() }.getOrNull() ?: break
                thread(isDaemon = true) { runCatching { handle(client) }; runCatching { client.close() } }
            }
        }
        return url
    }

    fun stop() {
        runCatching { server?.close() }
        server = null
        url = null
    }

    private fun handle(client: Socket) {
        client.soTimeout = 15_000
        val input = BufferedInputStream(client.getInputStream())
        val headerBytes = ByteArrayOutputStream()
        // Lecture des en-têtes jusqu'à la ligne vide.
        var last4 = 0
        while (headerBytes.size() < 16_384) {
            val b = input.read()
            if (b < 0) break
            headerBytes.write(b)
            last4 = (last4 shl 8) or b
            if (last4 == 0x0D0A0D0A) break
        }
        val headers = headerBytes.toString("UTF-8").split("\r\n")
        val requestLine = headers.firstOrNull().orEmpty().split(" ")
        val method = requestLine.getOrNull(0).orEmpty()
        val target = requestLine.getOrNull(1).orEmpty()
        val path = target.substringBefore('?')
        val query = parseForm(target.substringAfter('?', ""))
        val length = headers.firstOrNull { it.startsWith("Content-Length:", ignoreCase = true) }
            ?.substringAfter(':')?.trim()?.toIntOrNull()?.coerceIn(0, 16_384) ?: 0
        val out = client.getOutputStream()

        when {
            method == "GET" && path == "/" && query["t"] == token -> send(out, 200, formPage())
            method == "POST" && path == "/send" -> {
                val body = ByteArray(length)
                var read = 0
                while (read < length) {
                    val n = input.read(body, read, length - read)
                    if (n < 0) break
                    read += n
                }
                val form = parseForm(String(body, 0, read, Charsets.UTF_8))
                if (form["t"] != token) {
                    send(out, 403, page("Lien expiré", "Rescannez le QR code affiché sur la TV."))
                    return
                }
                val config = LoginConfig(
                    mode = if (form["mode"] == LoginConfig.MODE_M3U) LoginConfig.MODE_M3U else LoginConfig.MODE_XTREAM,
                    server = form["server"].orEmpty().trim(),
                    username = form["username"].orEmpty().trim(),
                    password = form["password"].orEmpty(),
                    m3uUrl = form["m3u"].orEmpty().trim(),
                )
                val complete = if (config.mode == LoginConfig.MODE_M3U) config.m3uUrl.isNotBlank()
                else config.server.isNotBlank() && config.username.isNotBlank() && config.password.isNotBlank()
                if (!complete) {
                    send(out, 400, page("Champs manquants", "Revenez en arrière et remplissez tous les champs."))
                    return
                }
                onReceived(form["name"].orEmpty().trim(), config)
                send(out, 200, page("✅ Envoyé !", "Regardez votre TV : Alf TV Pro se connecte à votre abonnement."))
            }
            else -> send(out, 404, page("Lien invalide", "Rescannez le QR code affiché sur la TV."))
        }
    }

    private fun send(out: OutputStream, code: Int, html: String) {
        val body = html.toByteArray(Charsets.UTF_8)
        val status = when (code) { 200 -> "OK"; 400 -> "Bad Request"; 403 -> "Forbidden"; else -> "Not Found" }
        val head = "HTTP/1.1 $code $status\r\nContent-Type: text/html; charset=utf-8\r\n" +
            "Content-Length: ${body.size}\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n"
        out.write(head.toByteArray(Charsets.UTF_8))
        out.write(body)
        out.flush()
    }

    private fun parseForm(text: String): Map<String, String> =
        text.split('&').filter { it.contains('=') }.associate {
            val key = URLDecoder.decode(it.substringBefore('='), "UTF-8")
            val value = URLDecoder.decode(it.substringAfter('='), "UTF-8")
            key to value
        }

    private fun page(title: String, message: String) = """
        <!doctype html><html lang="fr"><head><meta charset="utf-8">
        <meta name="viewport" content="width=device-width,initial-scale=1">
        <title>Alf TV Pro</title><style>$CSS</style></head><body><main>
        <h1>$title</h1><p>$message</p></main></body></html>
    """.trimIndent()

    private fun formPage() = """
        <!doctype html><html lang="fr"><head><meta charset="utf-8">
        <meta name="viewport" content="width=device-width,initial-scale=1">
        <title>Alf TV Pro – Connexion</title><style>$CSS</style></head><body><main>
        <h1>Alf TV Pro</h1>
        <p>Saisissez votre abonnement : il sera envoyé directement à votre TV, sans passer par Internet.</p>
        <form method="post" action="/send">
          <input type="hidden" name="t" value="$token">
          <div class="tabs">
            <label><input type="radio" name="mode" value="xtream" checked onchange="sw()"> Xtream Codes</label>
            <label><input type="radio" name="mode" value="m3u" onchange="sw()"> Lien M3U</label>
          </div>
          <label>Nom du compte (facultatif)<input name="name" placeholder="Salon"></label>
          <div id="xtream">
            <label>Adresse du serveur<input name="server" inputmode="url" autocapitalize="off" autocorrect="off" placeholder="http://exemple.com:8080"></label>
            <label>Nom d'utilisateur<input name="username" autocapitalize="off" autocorrect="off"></label>
            <label>Mot de passe<input name="password" type="password"></label>
          </div>
          <div id="m3u" hidden>
            <label>URL de la playlist M3U<input name="m3u" inputmode="url" autocapitalize="off" autocorrect="off" placeholder="http://…/get.php?username=…"></label>
          </div>
          <button type="submit">Envoyer à la TV</button>
        </form>
        <script>
          function sw(){var m=document.querySelector('input[name=mode]:checked').value;
            document.getElementById('xtream').hidden=(m!=='xtream');
            document.getElementById('m3u').hidden=(m!=='m3u');}
        </script>
        </main></body></html>
    """.trimIndent()

    companion object {
        private const val PORT = 8765

        private const val CSS = """
            body{margin:0;font-family:-apple-system,system-ui,sans-serif;background:#0e1117;color:#edf1f7}
            main{max-width:480px;margin:0 auto;padding:28px 20px}
            h1{font-size:26px;margin:0 0 8px;color:#fff}
            p{color:#9aa4b5;line-height:1.5}
            label{display:block;margin:14px 0 4px;font-size:15px;color:#c9d1de}
            input:not([type=radio]){display:block;width:100%;box-sizing:border-box;margin-top:6px;padding:14px;
              font-size:17px;border-radius:10px;border:1px solid #2e3a52;background:#161b26;color:#fff}
            .tabs{display:flex;gap:16px;margin:18px 0 6px}
            .tabs label{margin:0;font-size:16px}
            button{width:100%;margin-top:24px;padding:16px;font-size:18px;font-weight:700;border:0;border-radius:12px;
              color:#fff;background:linear-gradient(90deg,#2e9bff,#8b5cf6)}
        """

        /** Première adresse IPv4 privée (Wi-Fi ou Ethernet). */
        fun localIpAddress(): String? = runCatching {
            NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.toList() }
                .filterIsInstance<Inet4Address>()
                .firstOrNull { it.isSiteLocalAddress }
                ?.hostAddress
        }.getOrNull()
    }
}
