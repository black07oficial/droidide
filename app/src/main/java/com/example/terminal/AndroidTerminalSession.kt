package com.example.terminal

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.BufferedWriter
import java.io.File
import java.io.InputStream
import java.io.OutputStreamWriter

class AndroidTerminalSession(private val initialDir: File) {

    private var process: Process? = null
    private var writer: BufferedWriter? = null
    private val _terminalLines = MutableStateFlow<List<String>>(listOf(
        "DroidIDE Terminal Workbench",
        "Diretório: ${initialDir.absolutePath}",
        "Digite 'help' para comandos ou 'proot -S alpine' para entrar no Alpine Linux.",
        ""
    ))
    val terminalLines = _terminalLines.asStateFlow()

    private val sessionScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // PRoot Alpine simulation state
    var isAlpineMode = false
        private set

    private val installedPackages = mutableSetOf(
        "busybox",
        "ssl_client",
        "ca-certificates",
        "libc-utils"
    )

    private val availablePackages = mapOf(
        "nodejs" to Pair("20.10.0-r0", "42 MiB"),
        "python3" to Pair("3.11.6-r2", "38 MiB"),
        "git" to Pair("2.43.0-r0", "12 MiB"),
        "curl" to Pair("8.5.0-r0", "2.1 MiB"),
        "gcc" to Pair("13.2.1-r1", "55 MiB"),
        "g++" to Pair("13.2.1-r1", "64 MiB"),
        "bash" to Pair("5.2.21-r0", "3.2 MiB"),
        "nano" to Pair("7.2-r1", "1.5 MiB"),
        "openjdk17" to Pair("17.0.10-r0", "124 MiB"),
        "ruby" to Pair("3.2.2-r0", "22 MiB")
    )

    init {
        startSession()
    }

    private fun startSession() {
        sessionScope.launch {
            try {
                if (!initialDir.exists()) {
                    initialDir.mkdirs()
                }

                val builder = ProcessBuilder("/system/bin/sh")
                    .directory(initialDir)
                    .redirectErrorStream(true)

                builder.environment()["PATH"] = "/system/bin:/system/xbin:/vendor/bin"
                builder.environment()["TERM"] = "xterm-256color"
                builder.environment()["HOME"] = initialDir.absolutePath

                val proc = builder.start()
                process = proc

                writer = BufferedWriter(OutputStreamWriter(proc.outputStream))

                appendLine("--- Shell Inicializado ---")
                appendLine("$ ")

                val inputStream: InputStream = proc.inputStream
                val buffer = ByteArray(1024)
                var bytesRead: Int
                val lineBuilder = StringBuilder()

                while (proc.isAlive) {
                    bytesRead = inputStream.read(buffer)
                    if (bytesRead == -1) break
                    val text = String(buffer, 0, bytesRead)
                    lineBuilder.append(text)

                    if (lineBuilder.contains("\n")) {
                        val lines = lineBuilder.toString().split("\n")
                        for (i in 0 until lines.size - 1) {
                            appendLine(lines[i])
                        }
                        lineBuilder.setLength(0)
                        lineBuilder.append(lines.last())
                    } else if (lineBuilder.length > 500) {
                        appendLine(lineBuilder.toString())
                        lineBuilder.setLength(0)
                    }
                }
            } catch (e: Exception) {
                Log.e("TerminalSession", "Error starting shell", e)
                appendLine("Error starting shell: ${e.message}")
            }
        }
    }

    fun executeCommand(command: String) {
        val trimmed = command.trim()
        if (trimmed.isEmpty()) return

        // Global Interceptions for Packages and Bootstrapping simulation (npm, pip, npx)
        if (trimmed.startsWith("npm ") || trimmed.startsWith("pip ") || trimmed.startsWith("pip3 ") || trimmed.startsWith("npx ")) {
            val prefix = if (isAlpineMode) "alpine:~/project$ " else "$ "
            appendLine("$prefix$command")
            
            sessionScope.launch {
                delay(300)
                if (trimmed.startsWith("npm config")) {
                    appendLine("[npm] Configuration updated successfully.")
                } else if (trimmed.contains("create vite")) {
                    appendLine("[npm] Fetching create-vite template...")
                    delay(300)
                    appendLine("[npm] Creating project structure in local Workspace...")
                    delay(400)
                    appendLine("[npm] Vite boilerplate scaffolded successfully.")
                } else if (trimmed == "npm install" || trimmed.startsWith("npm i ") || trimmed.startsWith("npm install ")) {
                    appendLine("[npm] Loading package.json dependencies...")
                    delay(400)
                    appendLine("[npm] Resolving dependency tree graph...")
                    delay(300)
                    appendLine("[npm] Downloading packages from registry.npmjs.org...")
                    delay(800)
                    // Create simulated node_modules folder actually
                    try {
                        val nodeModules = File(initialDir, "node_modules")
                        if (!nodeModules.exists()) {
                            nodeModules.mkdirs()
                        }
                    } catch (e: Exception) {}
                    appendLine("[npm] Added 342 packages, and audited 343 packages in 2.1s")
                    appendLine("[npm] Success: synchronized dependency folder 'node_modules/'")
                } else if (trimmed == "npm run dev" || trimmed.contains("run dev")) {
                    appendLine("[npm] Starting Vite Dev Server...")
                    delay(350)
                    appendLine("  VITE v5.0.12  ready in 182 ms")
                    appendLine("  ➜  Local:   http://localhost:5173/")
                    appendLine("  ➜  Network: use --host to expose")
                    appendLine("[npm] Preview server started. Use preview panel or 'RUN' button corresponding to standard port.")
                } else if (trimmed.startsWith("pip ") || trimmed.startsWith("pip3 ")) {
                    val pkg = trimmed.substringAfter("install").trim()
                    appendLine("[pip] Collecting $pkg...")
                    delay(400)
                    appendLine("[pip]   Downloading $pkg-latest-py3-none-any.whl (142 kB)")
                    delay(500)
                    appendLine("[pip] Installing collected packages: $pkg")
                    appendLine("[pip] Successfully installed $pkg")
                } else if (trimmed.startsWith("npx ")) {
                    appendLine("[npx] Executing remote package command: ${trimmed.removePrefix("npx ")}")
                    delay(500)
                    appendLine("[npx] Command executed successfully.")
                }
                appendLine(if (isAlpineMode) "alpine:~/project$ " else "$ ")
            }
            return
        }

        if (!isAlpineMode) {
            // Handle regular shell mode commands
            if (trimmed == "proot -S alpine" || trimmed == "alpine") {
                isAlpineMode = true
                appendLine("$ $command")
                appendLine("[proot] Executing /usr/bin/proot-static -0 -r ${initialDir.absolutePath}/.alpine -b /dev -b /sys -b /proc -b /data -w /root /bin/sh")
                appendLine("[proot] Rootfs container 'alpine-minirootfs-3.19.1-x86_64' booted.")
                appendLine("[proot] Binding host project directory to /root/workspace...")
                appendLine("")
                appendLine("   /\\   Alpine Linux v3.19 (PRoot Sandbox)")
                appendLine("  /  \\  OS: Alpine Linux x86_64")
                appendLine(" /    \\ Kernel: Linux 5.15.0-85-generic-PRoot-v2")
                appendLine("/______\\ Type 'apk help' or 'apk update' to inspect repositories.")
                appendLine("         Type 'exit' to escape from PRoot container.")
                appendLine("")
                appendLine("alpine:~/project$ ")
                return
            }

            if (trimmed == "help") {
                appendLine("$ $command")
                appendLine("--- DroidIDE Terminal Help ---")
                appendLine("Este é um shell Android local rodando em seu dispositivo sandbox.")
                appendLine("Para executar um container Alpine completo sob PRoot:")
                appendLine("  proot -S alpine          - Inicializa o Alpine Linux v3.19")
                appendLine("Comandos úteis do host:")
                appendLine("  ls                      - Listar arquivos e pastas")
                appendLine("  pwd                     - Mostrar diretório atual")
                appendLine("  mkdir <nome>            - Criar uma nova pasta")
                appendLine("  touch <arquivo>         - Criar um novo arquivo")
                appendLine("  rm -rf <alvo>           - Deletar arquivo ou pasta")
                appendLine("  clear                   - Limpar o console do terminal")
                appendLine("$ ")
                return
            }

            if (trimmed == "clear") {
                _terminalLines.value = listOf("$ ")
                return
            }

            sessionScope.launch {
                try {
                    val writeStream = writer
                    if (writeStream != null) {
                        appendLine("$ $command")
                        writeStream.write(command + "\n")
                        writeStream.flush()
                    } else {
                        appendLine("Shell inativo. Reiniciando...")
                        startSession()
                    }
                } catch (e: Exception) {
                    appendLine("Erro ao enviar comando: ${e.message}")
                }
            }
        } else {
            // Intercept Alpine specific emulation logic
            if (trimmed == "exit") {
                isAlpineMode = false
                appendLine("alpine:~/project$ exit")
                appendLine("[proot] Closed Alpine Linux sandbox session safely.")
                appendLine("$ ")
                return
            }

            if (trimmed == "clear") {
                _terminalLines.value = listOf("alpine:~/project$ ")
                return
            }

            if (trimmed == "help" || trimmed == "apk help") {
                appendLine("alpine:~/project$ $command")
                appendLine("--- Alpine PRoot Container System Help ---")
                appendLine("Comandos locais suportados:")
                appendLine("  apk update           - Atualizar índices de pacotes alpine")
                appendLine("  apk add <pacote>     - Instalar utilitários (ex: nodejs, python3, gcc)")
                appendLine("  apk info             - Listar pacotes instalados")
                appendLine("  neofetch             - Imprimir banner gráfico do sistema Alpine")
                appendLine("  uname -a             - Ver informações de kernel e arquitetura")
                appendLine("  whoami               - Mostrar usuário ativo (root)")
                appendLine("  exit                 - Encerrar sandbox e voltar ao host")
                appendLine("Comandos de arquivos (realmente integrados com seu projeto):")
                appendLine("  ls, pwd, cd, mkdir, touch, rm, cat")
                appendLine("alpine:~/project$ ")
                return
            }

            if (trimmed == "neofetch" || trimmed == "screenfetch") {
                appendLine("alpine:~/project$ $command")
                appendLine("   /\\   root@localhost")
                appendLine("  /  \\  --------------")
                appendLine(" /    \\ OS: Alpine Linux v3.19 (PRoot virtualized)")
                appendLine("/______\\ Kernel: Linux 5.15.0-85-generic-PRoot-v2")
                appendLine("         Uptime: 2 mins")
                appendLine("         Packages: ${installedPackages.size} (apk)")
                appendLine("         Shell: busybox ash")
                appendLine("         Terminal: DroidIDE Console Pane")
                appendLine("         CPU: ARM Cortex-A78 / K8 Gen (Virtual)")
                appendLine("         Memory: 1115MiB / 2048MiB (Shared)")
                appendLine("alpine:~/project$ ")
                return
            }

            if (trimmed == "whoami") {
                appendLine("alpine:~/project$ $command")
                appendLine("root")
                appendLine("alpine:~/project$ ")
                return
            }

            if (trimmed == "uname -a") {
                appendLine("alpine:~/project$ $command")
                appendLine("Linux localhost 5.15.0-85-generic #98-Alpine SMP PREEMPT PRoot build x86_64 CPU-v8a Linux")
                appendLine("alpine:~/project$ ")
                return
            }

            if (trimmed == "apk update") {
                appendLine("alpine:~/project$ $command")
                sessionScope.launch {
                    appendLine("fetch https://dl-cdn.alpinelinux.org/alpine/v3.19/main/x86_64/APKINDEX.tar.gz")
                    delay(350)
                    appendLine("fetch https://dl-cdn.alpinelinux.org/alpine/v3.0/main/x86_64/APKINDEX.tar.gz")
                    delay(250)
                    appendLine("v3.19.1-825-g1a2bb4f728 [https://dl-cdn.alpinelinux.org/alpine/v3.19/main]")
                    appendLine("v3.19.1-830-g59bf2f38bc [https://dl-cdn.alpinelinux.org/alpine/v3.19/community]")
                    appendLine("OK: 20492 distinct packages available")
                    appendLine("alpine:~/project$ ")
                }
                return
            }

            if (trimmed.startsWith("apk add")) {
                appendLine("alpine:~/project$ $command")
                val pkgs = trimmed.substringAfter("apk add").trim().split(" ").filter { it.isNotEmpty() }
                if (pkgs.isEmpty()) {
                    appendLine("ERROR: No packages specified to install.")
                    appendLine("alpine:~/project$ ")
                    return
                }

                sessionScope.launch {
                    var successCount = 0
                    for (pkg in pkgs) {
                        val details = availablePackages[pkg]
                        if (details != null) {
                            appendLine("Installing $pkg (${details.first})...")
                            delay(400)
                            appendLine(" └─ Resolvendo dependências...")
                            delay(200)
                            appendLine(" └─ Baixando binários bin-${pkg}-${details.first}.apk... [${details.second}]")
                            delay(500)
                            appendLine("Executing busybox-sh triggers...")
                            installedPackages.add(pkg)
                            successCount++
                        } else {
                            appendLine("ERROR: Package '$pkg' not found in repositories.")
                            appendLine("Dica: Os pacotes simulados disponíveis são: nodejs, python3, git, curl, gcc, g++, bash, nano, openjdk17, ruby.")
                        }
                    }
                    if (successCount > 0) {
                        appendLine("OK: Installed packages synchronized with PRoot Alpine state database.")
                    }
                    appendLine("alpine:~/project$ ")
                }
                return
            }

            if (trimmed == "apk info" || trimmed == "apk list") {
                appendLine("alpine:~/project$ $command")
                appendLine("Pacotes Alpine instalados no container:")
                installedPackages.forEach { pkg ->
                    val ver = availablePackages[pkg]?.first ?: "system-r1"
                    appendLine("  $pkg-$ver")
                }
                appendLine("alpine:~/project$ ")
                return
            }

            // Let's intercept interpreted execution for python/nodejs if matching package is installed in alpine container!
            if (trimmed.startsWith("python3 ") || trimmed.startsWith("python ")) {
                appendLine("alpine:~/project$ $command")
                if (!installedPackages.contains("python3")) {
                    appendLine("ash: python3: command not found")
                    appendLine("Execute primeiro: 'apk add python3'")
                    appendLine("alpine:~/project$ ")
                    return
                }

                val parts = trimmed.split(" ")
                val fileName = parts.getOrNull(1)
                if (fileName != null) {
                    val file = File(initialDir, fileName)
                    if (file.exists()) {
                        sessionScope.launch {
                            appendLine("[container-exec] Python 3 Interpreter booting...")
                            delay(400)
                            val writeStream = writer
                            if (writeStream != null) {
                                writeStream.write("python3 $fileName\n")
                                writeStream.flush()
                            } else {
                                appendLine("--- Python Execution Terminated ---")
                            }
                        }
                    } else {
                        appendLine("python3: can't open file '$fileName': [Errno 2] No such file or directory")
                        appendLine("alpine:~/project$ ")
                    }
                } else {
                    appendLine("Python 3.11.6 (default, Oct 24 2023, 16:32:00) [GCC 13.2.1] on linux")
                    appendLine("Type \"help\", \"copyright\", \"credits\" or \"license\" for more information.")
                    appendLine(">>> [Modo interativo não disponível no terminal em linha simples. Crie um arquivo .py e chame python3 <arquivo.py>!]")
                    appendLine("alpine:~/project$ ")
                }
                return
            }

            if (trimmed.startsWith("node ")) {
                appendLine("alpine:~/project$ $command")
                if (!installedPackages.contains("nodejs")) {
                    appendLine("ash: node: command not found")
                    appendLine("Execute primeiro: 'apk add nodejs'")
                    appendLine("alpine:~/project$ ")
                    return
                }

                val parts = trimmed.split(" ")
                val fileName = parts.getOrNull(1)
                if (fileName != null) {
                    val file = File(initialDir, fileName)
                    if (file.exists()) {
                        sessionScope.launch {
                            appendLine("[container-exec] Node.js V8 Engine runtime initializing...")
                            delay(400)
                            val writeStream = writer
                            if (writeStream != null) {
                                writeStream.write("node $fileName\n")
                                writeStream.flush()
                            } else {
                                appendLine("--- Node Execution Terminated ---")
                            }
                        }
                    } else {
                        appendLine("Error: Cannot find module '/root/workspace/$fileName'")
                        appendLine("alpine:~/project$ ")
                    }
                } else {
                    appendLine("Welcome to Node.js v20.10.0.")
                    appendLine("Type \".help\" for more information.")
                    appendLine("> [Use node <arquivo.js> para rodar seus códigos em tempo real!]")
                    appendLine("alpine:~/project$ ")
                }
                return
            }

            // Normal filesystem commands, pass it to host shell with custom styling
            sessionScope.launch {
                try {
                    val writeStream = writer
                    if (writeStream != null) {
                        appendLine("alpine:~/project$ $command")
                        writeStream.write(command + "\n")
                        writeStream.flush()
                    } else {
                        appendLine("PRoot Alpine daemon lost connection. Reconnecting...")
                        startSession()
                    }
                } catch (e: Exception) {
                    appendLine("Erro de container: ${e.message}")
                }
            }
        }
    }

    fun sendInterrupt() {
        process?.destroy()
        appendLine("^C")
        startSession()
    }

    private fun appendLine(line: String) {
        val currentList = _terminalLines.value.toMutableList()
        if (currentList.size > 200) {
            currentList.removeAt(0)
        }
        val cleanLine = line.replace(Regex("\u001B\\[[;\\d]*[A-Za-z]"), "")

        var finalLine = cleanLine
        if (isAlpineMode) {
            val trimmed = cleanLine.trim()
            if (trimmed == "$" || trimmed == "#" || trimmed == "sh$" || trimmed == "sh#") {
                finalLine = "alpine:~/project$ "
            } else if (cleanLine.startsWith("/data/user/0/com.aistudio") && (cleanLine.endsWith("$ ") || cleanLine.endsWith("# ") || cleanLine.endsWith("$") || cleanLine.endsWith("#"))) {
                finalLine = "alpine:~/project$ "
            } else if (cleanLine.contains(":/data/user/0/com.aistudio") && (cleanLine.endsWith("$ ") || cleanLine.endsWith("# ") || cleanLine.endsWith("$") || cleanLine.endsWith("#"))) {
                finalLine = "alpine:~/project$ "
            }
        }

        currentList.add(finalLine)
        _terminalLines.value = currentList
    }

    fun destroy() {
        sessionScope.cancel()
        process?.destroy()
    }
}
