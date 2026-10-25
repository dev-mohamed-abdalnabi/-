package com.next.files

import android.os.Environment
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.SecureRandom
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** سيرفر HTTP بسيط: تتصفح التخزين وتنزّل وترفع ملفات من متصفح الكمبيوتر (محمي ببن) */
object WifiServer {
    const val PORT = 8080

    var running by mutableStateOf(false)
    var starting by mutableStateOf(false)
    var url by mutableStateOf("")
    var pin by mutableStateOf("")

    @Volatile private var fails = 0
    @Volatile private var ss: ServerSocket? = null
    @Volatile private var pool: ExecutorService? = null
    private val root: File get() = Environment.getExternalStorageDirectory()

    fun start() {
        if (running || starting) return
        starting = true
        fails = 0
        pin = (1000 + SecureRandom().nextInt(9000)).toString()
        Thread {
            try {
                val s = ServerSocket(PORT, 50)
                ss = s
                pool = Executors.newFixedThreadPool(6)
                val ip = localIp()
                Ctl.scope.launch {
                    url = "http://${ip ?: "عنوان-الجهاز"}:$PORT"
                    running = true
                    starting = false
                    if (ip == null) Ctl.toast("مش لاقي عنوان واي فاي - اتأكد إنك متصل بشبكة")
                }
                while (!s.isClosed) {
                    val c = try { s.accept() } catch (e: Exception) { break }
                    pool?.execute {
                        try { handle(c) } catch (e: Exception) { } finally { try { c.close() } catch (e: Exception) { } }
                    }
                }
            } catch (e: Exception) {
                Ctl.scope.launch {
                    running = false
                    starting = false
                    Ctl.toast("تعذر تشغيل السيرفر")
                }
            }
        }.start()
    }

    fun stop() {
        running = false
        starting = false
        try { ss?.close() } catch (e: Exception) { }
        ss = null
        pool?.shutdownNow()
        pool = null
    }

    private fun localIp(): String? = try {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }
            .firstOrNull { it is Inet4Address && it.isSiteLocalAddress }?.hostAddress
    } catch (e: Exception) {
        null
    }

    // ---------- HTTP ----------
    private fun readLine(i: InputStream): String? {
        val sb = StringBuilder()
        while (sb.length < 16384) {
            val b = i.read()
            if (b < 0) return if (sb.isEmpty()) null else sb.toString()
            if (b == '\n'.code) break
            if (b != '\r'.code) sb.append(b.toChar())
        }
        return sb.toString()
    }

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("'", "&#39;").replace("\"", "&quot;")
    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    private fun send(o: OutputStream, code: Int, status: String, type: String, body: ByteArray, extra: String = "") {
        o.write(("HTTP/1.1 $code $status\r\nContent-Type: $type\r\nContent-Length: ${body.size}\r\nConnection: close\r\n$extra\r\n").toByteArray())
        o.write(body)
        o.flush()
    }

    private const val HTML = "text/html; charset=utf-8"

    private fun page(title: String, body: String): ByteArray = (
        "<!doctype html><html dir=rtl lang=ar><head><meta charset=utf-8>" +
            "<meta name=viewport content='width=device-width,initial-scale=1'><title>" + esc(title) + "</title>" +
            "<style>body{font-family:system-ui,sans-serif;margin:0;background:#f4f5f9;color:#1b1b1f}" +
            ".w{max-width:860px;margin:0 auto;padding:16px}h1{font-size:20px;word-break:break-all}" +
            "a{color:#3b5bdb;text-decoration:none;word-break:break-all}" +
            ".r{display:flex;justify-content:space-between;gap:12px;padding:10px 12px;background:#fff;border-radius:12px;margin:6px 0}" +
            ".m{color:#777;font-size:13px;white-space:nowrap}button,input{font:inherit}" +
            ".u{background:#fff;border-radius:12px;padding:12px;margin:12px 0}</style></head><body><div class=w>" +
            body + "</div></body></html>"
        ).toByteArray()

    private fun loginPage(wrong: Boolean) = page(
        "Next",
        "<h1>Next · نقل لاسلكي</h1><div class=u><form action='/login'>أدخل الكود الظاهر على الموبايل:<br><br>" +
            "<input name=pin inputmode=numeric autofocus> <button>دخول</button>" +
            (if (wrong) "<p style='color:#c00'>الكود غلط</p>" else "") + "</form></div>"
    )

    private fun listing(rel: String, dir: File): ByteArray {
        val kids = (dir.listFiles() ?: emptyArray()).filter { !it.name.startsWith(".") }
            .sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
        val sb = StringBuilder()
        sb.append("<h1>/").append(esc(rel)).append("</h1>")
        if (rel.isNotEmpty()) {
            sb.append("<div class=r><a href='/?p=").append(enc(rel.substringBeforeLast('/', ""))).append("'>⬆ المجلد الأعلى</a></div>")
        }
        for (k in kids) {
            val r = if (rel.isEmpty()) k.name else rel + "/" + k.name
            if (k.isDirectory) sb.append("<div class=r><a href='/?p=").append(enc(r)).append("'>📁 ").append(esc(k.name)).append("</a></div>")
            else sb.append("<div class=r><a href='/dl?p=").append(enc(r)).append("'>📄 ").append(esc(k.name))
                .append("</a><span class=m>").append(FS.fmtSize(k.length())).append("</span></div>")
        }
        sb.append(
            "<div class=u><b>رفع ملفات هنا</b><br><br><input type=file id=f multiple> <button onclick='up()'>رفع</button>" +
                "<div id=s class=m></div></div><script>var P=decodeURIComponent('" + enc(rel) + "');" +
                "async function up(){var fs=document.getElementById('f').files,s=document.getElementById('s');" +
                "if(!fs.length){s.textContent='اختار ملفات الأول';return}" +
                "for(var i=0;i<fs.length;i++){var f=fs[i];s.textContent='جاري رفع '+f.name+' ...';" +
                "var r=await fetch('/up?p='+encodeURIComponent(P)+'&n='+encodeURIComponent(f.name),{method:'POST',body:f});" +
                "if(!r.ok){s.textContent='فشل رفع '+f.name;return}}location.reload()}</script>"
        )
        return page("Next", sb.toString())
    }

    private fun safe(rel: String): File? {
        val f = File(root, rel).canonicalFile
        val r = root.canonicalPath
        return if (f.path == r || f.path.startsWith("$r/")) f else null
    }

    private fun handle(c: Socket) {
        c.soTimeout = 60000
        val inp = BufferedInputStream(c.getInputStream(), 65536)
        val out = BufferedOutputStream(c.getOutputStream(), 65536)
        val reqLine = readLine(inp) ?: return
        val parts = reqLine.split(' ')
        if (parts.size < 2) return
        val method = parts[0].uppercase()
        val target = parts[1]
        val h = HashMap<String, String>()
        while (true) {
            val l = readLine(inp) ?: break
            if (l.isEmpty()) break
            val i = l.indexOf(':')
            if (i > 0) h[l.substring(0, i).trim().lowercase()] = l.substring(i + 1).trim()
        }
        val path = target.substringBefore('?')
        val q = HashMap<String, String>()
        target.substringAfter('?', "").split('&').filter { it.isNotEmpty() }.forEach { kv ->
            q[URLDecoder.decode(kv.substringBefore('='), "UTF-8")] = URLDecoder.decode(kv.substringAfter('=', ""), "UTF-8")
        }

        if (fails >= 10) {
            send(out, 429, "Too Many Requests", HTML, page("Next", "<h1>اتوقف السيرفر مؤقتًا بسبب محاولات غلط</h1>"))
            return
        }
        if (path == "/login") {
            if (q["pin"] == pin) {
                send(out, 302, "Found", "text/plain", ByteArray(0), "Location: /\r\nSet-Cookie: k=$pin; Path=/; HttpOnly\r\n")
            } else {
                if (q.containsKey("pin")) fails++
                send(out, 200, "OK", HTML, loginPage(q.containsKey("pin")))
            }
            return
        }
        val cookie = h["cookie"] ?: ""
        val auth = Regex("(^|;\\s*)k=([^;]*)").find(cookie)?.groupValues?.get(2) == pin
        if (!auth) {
            send(out, 200, "OK", HTML, loginPage(false))
            return
        }

        when {
            path == "/" && method == "GET" -> {
                val rel = (q["p"] ?: "").trim('/')
                val dir = safe(rel)
                if (dir == null || !dir.isDirectory) send(out, 404, "Not Found", HTML, page("Next", "<h1>المجلد مش موجود</h1>"))
                else send(out, 200, "OK", HTML, listing(rel, dir))
            }
            path == "/dl" && method == "GET" -> {
                val f = safe((q["p"] ?: "").trim('/'))
                if (f == null || !f.isFile || !f.canRead()) {
                    send(out, 404, "Not Found", HTML, page("Next", "<h1>الملف مش موجود</h1>"))
                } else {
                    out.write(
                        ("HTTP/1.1 200 OK\r\nContent-Type: application/octet-stream\r\nContent-Length: ${f.length()}\r\n" +
                            "Content-Disposition: attachment; filename*=UTF-8''${enc(f.name)}\r\nConnection: close\r\n\r\n").toByteArray()
                    )
                    FileInputStream(f).use { it.copyTo(out, 64 * 1024) }
                    out.flush()
                }
            }
            path == "/up" && method == "POST" -> {
                val len = h["content-length"]?.toLongOrNull()
                val dir = safe((q["p"] ?: "").trim('/'))
                val name = File(q["n"] ?: "").name
                if (len == null || len < 0 || dir == null || !dir.isDirectory || name.isEmpty() || name == "." || name == "..") {
                    send(out, 400, "Bad Request", "text/plain", "bad request".toByteArray())
                    return
                }
                if (h["expect"]?.contains("100", true) == true) {
                    out.write("HTTP/1.1 100 Continue\r\n\r\n".toByteArray())
                    out.flush()
                }
                val target = FS.unique(dir, name, false)
                var left = len
                val buf = ByteArray(64 * 1024)
                try {
                    FileOutputStream(target).use { o ->
                        while (left > 0) {
                            val n = inp.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
                            if (n < 0) break
                            o.write(buf, 0, n)
                            left -= n
                        }
                    }
                } catch (e: Exception) {
                    left = 1
                }
                if (left != 0L) {
                    target.delete()
                    send(out, 400, "Bad Request", "text/plain", "incomplete".toByteArray())
                } else {
                    Ctl.scope.launch { FS.scan(Ctl.app, listOf(target.path)); Ctl.bump() }
                    send(out, 200, "OK", "text/plain", "ok".toByteArray())
                }
            }
            else -> send(out, 404, "Not Found", HTML, page("Next", "<h1>مش موجود</h1>"))
        }
    }
}

@Composable
fun WifiScreen() {
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxSize().statusBarsPadding().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton({ Ctl.pop() }) { Icon(Ic.Back, "رجوع") }
            Text("نقل لاسلكي", style = MaterialTheme.typography.headlineMedium)
        }
        Text(
            "شغّل السيرفر وافتح العنوان من متصفح الكمبيوتر (لازم يكونوا على نفس شبكة الواي فاي). " +
                "هتقدر تتصفح التخزين وتنزّل ملفات وترفع ملفات.",
            color = cs.onSurfaceVariant
        )
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("تشغيل السيرفر", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            Switch(WifiServer.running || WifiServer.starting, { if (it) WifiServer.start() else WifiServer.stop() })
        }
        if (WifiServer.running) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(cs.secondaryContainer).padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("العنوان", color = cs.onSecondaryContainer, style = MaterialTheme.typography.labelLarge)
                SelectionContainer { Text(WifiServer.url, color = cs.onSecondaryContainer, style = MaterialTheme.typography.titleLarge) }
                Text("كود الدخول", color = cs.onSecondaryContainer, style = MaterialTheme.typography.labelLarge)
                Text(WifiServer.pin, color = cs.onSecondaryContainer, style = MaterialTheme.typography.headlineMedium)
            }
            Text(
                "السيرفر بيشتغل طول ما التطبيق شغال. اقفله لما تخلص. الكود بيتغير كل مرة تشغّله.",
                color = cs.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
