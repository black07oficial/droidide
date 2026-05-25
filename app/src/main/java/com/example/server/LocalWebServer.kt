package com.example.server

import android.util.Log
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

class LocalWebServer(private val rootDir: File) {

    private var serverSocket: ServerSocket? = null
    var activePort: Int = 8080
        private set
    var isRunning: Boolean = false
        private set

    fun start(portRangeStart: Int = 8080): Int {
        if (isRunning) return activePort
        var port = portRangeStart
        while (port < portRangeStart + 100) {
            try {
                serverSocket = ServerSocket(port)
                activePort = port
                break
            } catch (e: Exception) {
                port++
            }
        }

        val socket = serverSocket ?: return -1
        isRunning = true
        Log.d("LocalWebServer", "Started running on port $activePort")

        thread(name = "WebServerThread") {
            try {
                while (isRunning) {
                    val clientSocket = socket.accept()
                    handleClient(clientSocket)
                }
            } catch (e: Exception) {
                Log.d("LocalWebServer", "Server stopped: ${e.message}")
            }
        }
        return activePort
    }

    fun stop() {
        isRunning = false
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            Log.e("LocalWebServer", "Error closing server socket", e)
        }
        serverSocket = null
    }

    private fun handleClient(clientSocket: Socket) {
        thread {
            try {
                val input = clientSocket.getInputStream()
                val output = clientSocket.getOutputStream()
                val requestHeader = readHeader(input) ?: return@thread

                val requestLine = requestHeader.firstOrNull() ?: return@thread
                val parts = requestLine.split(" ")
                if (parts.size < 2) return@thread

                val method = parts[0]
                var path = parts[1].split("?")[0] // ignore query params

                if (method != "GET") {
                    sendShortResponse(output, "501 Not Implemented", "text/plain", "Only GET requests supported.")
                    return@thread
                }

                if (path == "/" || path.isEmpty()) {
                    path = "/index.html"
                }

                // Security: Prevent path traversal
                if (path.contains("..")) {
                    sendShortResponse(output, "403 Forbidden", "text/plain", "Directory traversal forbidden.")
                    return@thread
                }

                if (path == "/sw.js") {
                    val swCode = """
                        importScripts('https://unpkg.com/@babel/standalone/babel.min.js');

                        self.addEventListener('install', event => {
                          self.skipWaiting();
                        });

                        self.addEventListener('activate', event => {
                          event.waitUntil(self.clients.claim());
                        });

                        self.addEventListener('fetch', event => {
                          const url = new URL(event.request.url);
                          if (url.origin === self.location.origin) {
                            const path = url.pathname;
                            
                            // 1. Intercept CSS imports inside JS/TS files to inline them dynamically
                            if (path.endsWith('.css')) {
                              if (event.request.destination === 'style' || (event.request.headers.get('Accept') && event.request.headers.get('Accept').includes('text/css'))) {
                                return; // let normal fetch handle it
                              }
                              
                              event.respondWith(
                                fetch(event.request)
                                  .then(response => {
                                    if (!response.ok) return response;
                                    return response.text().then(text => {
                                      const escapedCss = JSON.stringify(text);
                                      const injectJs = "const style = document.createElement('style'); style.innerHTML = " + escapedCss + "; document.head.appendChild(style); export default {};";
                                      return new Response(injectJs, {
                                        headers: { 'Content-Type': 'application/javascript; charset=utf-8' }
                                      });
                                    });
                                  })
                              );
                              return;
                            }
                            
                            // 2. Intercept modular JS, JSX, TS, TSX and transpile using Babel
                            if (path.endsWith('.js') || path.endsWith('.jsx') || path.endsWith('.ts') || path.endsWith('.tsx')) {
                              event.respondWith(
                                fetch(event.request)
                                  .then(response => {
                                    if (!response.ok) return response;
                                    return response.text().then(text => {
                                      try {
                                        const presets = ['react'];
                                        if (path.endsWith('.ts') || path.endsWith('.tsx')) {
                                          presets.push('typescript');
                                        }
                                        
                                        const transpiled = Babel.transform(text, {
                                          presets: presets,
                                          filename: path,
                                          sourceMaps: false
                                        }).code;
                                        
                                        return new Response(transpiled, {
                                          headers: { 'Content-Type': 'application/javascript; charset=utf-8' }
                                        });
                                      } catch (err) {
                                        console.error("Transpilation error in " + path + ":", err);
                                        return new Response("console.error('Transpilation Error in " + path + ":', " + JSON.stringify(err.message) + ");", {
                                          headers: { 'Content-Type': 'application/javascript; charset=utf-8' }
                                        });
                                      }
                                    });
                                  })
                                  .catch(err => {
                                    return new Response("console.error('Fetch Error in " + path + ":', " + JSON.stringify(err.message) + ");", {
                                      headers: { 'Content-Type': 'application/javascript; charset=utf-8' }
                                    });
                                  })
                              );
                            }
                          }
                        });
                    """.trimIndent()
                    sendShortResponse(output, "200 OK", "application/javascript", swCode)
                    return@thread
                }

                var requestedFile = File(rootDir, path.removePrefix("/"))

                if (!requestedFile.exists()) {
                    // Try adding extensions for extension-less imports (e.g. ./App -> ./App.tsx)
                    val extensionsToTry = listOf("tsx", "jsx", "ts", "js", "css")
                    val foundFile = extensionsToTry.asSequence()
                        .map { ext -> File(rootDir, "${path.removePrefix("/")}.$ext") }
                        .firstOrNull { it.exists() }

                    if (foundFile != null) {
                        requestedFile = foundFile
                    } else {
                        // Fallback to index.html for Single Page Apps (SPA fallback)
                        if (path == "/index.html") {
                            val welcomePage = buildWelcomeHtml()
                            sendShortResponse(output, "200 OK", "text/html", welcomePage)
                            return@thread
                        } else {
                            // If index.html exists in root and we got a 404 on some deep route, return index.html for SPA router compatibility
                            val rootIndex = File(rootDir, "index.html")
                            if (rootIndex.exists()) {
                                requestedFile = rootIndex
                            } else {
                                sendShortResponse(output, "404 Not Found", "text/plain", "File not found: $path")
                                return@thread
                            }
                        }
                    }
                }

                if (requestedFile.isDirectory) {
                    val fileListHtml = buildFileListHtml(requestedFile)
                    sendShortResponse(output, "200 OK", "text/html", fileListHtml)
                    return@thread
                }

                val contentType = getMimeType(requestedFile.extension)

                // Inject Service Worker registration automatically into HTML files
                if (requestedFile.extension.lowercase() == "html") {
                    var htmlContent = requestedFile.readText()
                    val swSnippet = """
                        <script>
                            if ('serviceWorker' in navigator) {
                                navigator.serviceWorker.register('/sw.js').then(reg => {
                                    if (!navigator.serviceWorker.controller) {
                                        navigator.serviceWorker.addEventListener('controllerchange', () => {
                                            window.location.reload();
                                        });
                                    }
                                }).catch(err => {
                                    console.error('Service Worker Registration failed:', err);
                                });
                            }
                        </script>
                    """.trimIndent()

                    if (htmlContent.contains("</head>")) {
                        htmlContent = htmlContent.replace("</head>", "$swSnippet\n</head>")
                    } else if (htmlContent.contains("</body>")) {
                        htmlContent = htmlContent.replace("</body>", "$swSnippet\n</body>")
                    } else {
                        htmlContent += "\n$swSnippet"
                    }

                    val fileBytes = htmlContent.toByteArray()
                    output.write("HTTP/1.1 200 OK\r\n".toByteArray())
                    output.write("Content-Type: ${contentType}\r\n".toByteArray())
                    output.write("Content-Length: ${fileBytes.size}\r\n".toByteArray())
                    output.write("Connection: close\r\n".toByteArray())
                    output.write("\r\n".toByteArray())
                    output.write(fileBytes)
                    output.flush()
                } else {
                    val fileBytes = requestedFile.readBytes()
                    output.write("HTTP/1.1 200 OK\r\n".toByteArray())
                    output.write("Content-Type: ${contentType}\r\n".toByteArray())
                    output.write("Content-Length: ${fileBytes.size}\r\n".toByteArray())
                    output.write("Connection: close\r\n".toByteArray())
                    output.write("\r\n".toByteArray())
                    output.write(fileBytes)
                    output.flush()
                }

            } catch (e: Exception) {
                Log.e("LocalWebServer", "Error handling client", e)
            } finally {
                try {
                    clientSocket.close()
                } catch (e: Exception) {
                    // ignore
                }
            }
        }
    }

    private fun readHeader(inputStream: InputStream): List<String>? {
        val reader = inputStream.bufferedReader()
        val headers = mutableListOf<String>()
        var line: String?
        try {
            while (reader.readLine().also { line = it } != null) {
                if (line.isNullOrEmpty()) break
                headers.add(line!!)
            }
        } catch (e: Exception) {
            return null
        }
        return headers
    }

    private fun sendShortResponse(output: OutputStream, status: String, contentType: String, content: String) {
        val bytes = content.toByteArray()
        output.write("HTTP/1.1 $status\r\n".toByteArray())
        output.write("Content-Type: $contentType\r\n".toByteArray())
        output.write("Content-Length: ${bytes.size}\r\n".toByteArray())
        output.write("Connection: close\r\n".toByteArray())
        output.write("\r\n".toByteArray())
        output.write(bytes)
        output.flush()
    }

    private fun getMimeType(extension: String): String {
        return when (extension.lowercase()) {
            "html", "htm" -> "text/html"
            "css" -> "text/css"
            "js" -> "application/javascript"
            "json" -> "application/json"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "gif" -> "image/gif"
            "svg" -> "image/svg+xml"
            "txt" -> "text/plain"
            else -> "application/octet-stream"
        }
    }

    private fun buildWelcomeHtml(): String {
        return """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>DroidIDE Server</title>
                <style>
                    body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; background-color: #0f172a; color: #cbd5e1; text-align: center; padding: 50px 20px; }
                    h1 { color: #38bdf8; font-size: 2.5rem; margin-bottom: 10px; }
                    p { font-size: 1.2rem; color: #94a3b8; }
                    .tip { display: inline-block; margin-top: 30px; background-color: #1e293b; padding: 15px 25px; border-radius: 8px; border: 1px solid #334155; }
                    code { color: #f43f5e; font-family: monospace; }
                </style>
            </head>
            <body>
                <h1>DroidIDE Sandbox server</h1>
                <p>Welcome to your local preview environment!</p>
                <div class="tip">
                    To start previewing your project, create an <code>index.html</code> file inside this project directory.
                </div>
            </body>
            </html>
        """.trimIndent()
    }

    private fun buildFileListHtml(dir: File): String {
        val files = dir.listFiles() ?: emptyArray()
        val listItems = files.joinToString("\n") { file ->
            val name = file.name + if (file.isDirectory) "/" else ""
            "<li><a href=\"/$name\">$name</a></li>"
        }
        return """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <title>Index of /</title>
                <style>
                    body { font-family: sans-serif; padding: 20px; background-color: #0f172a; color: #cbd5e1; }
                    a { color: #38bdf8; text-decoration: none; }
                    a:hover { text-decoration: underline; }
                    li { margin-bottom: 5px; }
                </style>
            </head>
            <body>
                <h1>Index of requested directory</h1>
                <ul>
                    $listItems
                </ul>
            </body>
            </html>
        """.trimIndent()
    }
}
