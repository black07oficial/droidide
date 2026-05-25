package com.example.ui

import android.app.Application
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.database.*
import com.example.network.*
import com.example.server.LocalWebServer
import com.example.terminal.AndroidTerminalSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class FileItem(
    val file: File,
    val name: String,
    val isDirectory: Boolean,
    val isExpanded: Boolean = false,
    val depth: Int = 0
)

class IdeViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)
    private val projectDao = database.projectDao()
    private val chatMessageDao = database.chatMessageDao()
    private val deploymentDao = database.deploymentDao()
    private val settingDao = database.settingDao()

    // --- State Declarations ---

    // Project selection
    val projectsFlow: Flow<List<ProjectEntity>> = projectDao.getAllProjectsFlow()
    var currentProject by mutableStateOf<ProjectEntity?>(null)
        private set
    var currentStack by mutableStateOf<StackDefinition?>(null)
        private set

    // File Explorer State
    var workspaceFiles by mutableStateOf<List<FileItem>>(emptyList())
        private set

    // Tabs Manager
    var openTabs by mutableStateOf<List<File>>(emptyList())
        private set
    var selectedFile by mutableStateOf<File?>(null)
        private set
    var currentCodeContent by mutableStateOf("")
    
    // Editor preferences
    var useMonacoEditor by mutableStateOf(true)
    var editorFontSize by mutableStateOf(14)
    var isSaving by mutableStateOf(false)

    // Local Web Server
    private var webServer: LocalWebServer? = null
    var serverPort by mutableStateOf(8080)
        private set
    var isServerRunning by mutableStateOf(false)
        private set

    // Native custom Terminal Session
    var terminalSession by mutableStateOf<AndroidTerminalSession?>(null)
        private set

    // AI Assist (Gemini) State
    var chatMessages by mutableStateOf<List<ChatMessageEntity>>(emptyList())
        private set
    var aiModel by mutableStateOf("gemini-2.5-flash")
    var inputApiKey by mutableStateOf("")
    var isGeneratingAi by mutableStateOf(false)
    var aiErrorMessage by mutableStateOf<String?>(null)

    // Bootstrapping State
    var isBootstrapping by mutableStateOf(false)
    var bootstrapProgress by mutableStateOf("")

    // Deploy panel state
    var deploymentsList by mutableStateOf<List<DeploymentEntity>>(emptyList())
        private set

    private var autoSaveJob: Job? = null
    private var chatObserveJob: Job? = null
    private var deployObserveJob: Job? = null

    init {
        // Load settings
        viewModelScope.launch(Dispatchers.IO) {
            settingDao.getSetting("editor_font_size")?.let {
                withContext(Dispatchers.Main) {
                    editorFontSize = it.value.toIntOrNull() ?: 14
                }
            }
            settingDao.getSetting("use_monaco")?.let {
                withContext(Dispatchers.Main) {
                    useMonacoEditor = it.value.toBoolean()
                }
            }
            settingDao.getSetting("ai_model")?.let {
                withContext(Dispatchers.Main) {
                    aiModel = it.value
                }
            }
            val savedApiKey = settingDao.getSetting("gemini_api_key")?.value
            withContext(Dispatchers.Main) {
                if (!savedApiKey.isNullOrEmpty()) {
                    inputApiKey = savedApiKey
                } else {
                    inputApiKey = BuildConfig.GEMINI_API_KEY
                    if (inputApiKey == "MY_GEMINI_API_KEY" || inputApiKey.isEmpty()) {
                        inputApiKey = ""
                    }
                }
            }
        }
    }

    // --- Project Operations ---

    fun createProject(name: String, stackId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val cleanName = name.trim().replace(Regex("[^a-zA-Z0-9_-]"), "_")
            val baseDir = getApplication<Application>().filesDir.resolve("projects").resolve(cleanName)
            if (!baseDir.exists()) baseDir.mkdirs()

            // Find the stack definition
            val stackDef = StackRegistry.findById(stackId) ?: StackRegistry.findById("html-css-js")!!

            // Create offlineTemplate files immediately
            stackDef.offlineTemplate?.forEach { (filename, content) ->
                val destFile = baseDir.resolve(filename)
                destFile.parentFile?.mkdirs()
                destFile.writeText(content)
            }

            // Save the .droidide_stack file
            try {
                baseDir.resolve(".droidide_stack").writeText(stackDef.id)
            } catch (e: Exception) {
                // silent
            }

            val projectDir = ProjectEntity(name = name, path = baseDir.absolutePath)
            projectDao.insertProject(projectDir)
        }
    }

    fun selectProject(project: ProjectEntity) {
        // Stop current structures
        stopWebServer()
        terminalSession?.destroy()

        currentProject = project

        // Detect stack on folder
        val pDir = File(project.path)
        val stackIdFile = File(pDir, ".droidide_stack")
        val detectedStackId = if (stackIdFile.exists()) {
            try { stackIdFile.readText().trim() } catch (e: Exception) { "" }
        } else {
            if (File(pDir, "main.py").exists()) "python"
            else if (File(pDir, "index.js").exists() || File(pDir, "package.json").exists()) "node-express"
            else "html-css-js"
        }
        val stack = StackRegistry.findById(detectedStackId) ?: StackRegistry.findById("html-css-js")!!
        currentStack = stack

        // Observe chat
        chatObserveJob?.cancel()
        chatObserveJob = viewModelScope.launch {
            chatMessageDao.getMessagesForProjectFlow(project.id).collect {
                chatMessages = it
            }
        }

        // Observe deploys
        deployObserveJob?.cancel()
        deployObserveJob = viewModelScope.launch {
            deploymentDao.getDeploymentsForProjectFlow(project.id).collect {
                deploymentsList = it
            }
        }

        // Init terminal in project directory
        val session = AndroidTerminalSession(pDir)
        terminalSession = session

        // Load project files
        refreshFileTree()

        // Auto bootstrapping if needed
        val bootstrapDoneFile = File(pDir, ".droidide_bootstrapped")
        if (!bootstrapDoneFile.exists() && stack.requiresInternet != InternetRequirement.NONE) {
            viewModelScope.launch {
                isBootstrapping = true
                bootstrapProgress = "Verificando ambiente..."
                delay(1000)
                val isOnline = NetworkChecker(getApplication()).isConnected()
                if (!isOnline && stack.requiresInternet == InternetRequirement.ALWAYS) {
                    bootstrapProgress = "⚠️ Sem Internet! Este projeto precisa estar online."
                    delay(3000)
                    isBootstrapping = false
                    openSelectedFileOrDefault(pDir)
                } else {
                    val bootstrapper = ProjectBootstrapper(session, NetworkChecker(getApplication()))
                    val result = bootstrapper.bootstrap(stack, pDir) { progress ->
                        bootstrapProgress = progress
                    }
                    when (result) {
                        is ProjectBootstrapper.BootstrapResult.Success -> {
                            try {
                                bootstrapDoneFile.createNewFile()
                            } catch (e: Exception) {}
                            bootstrapProgress = "✅ Projeto pronto!"
                            delay(1500)
                            isBootstrapping = false
                            refreshFileTree()
                            openSelectedFileOrDefault(pDir)
                        }
                        is ProjectBootstrapper.BootstrapResult.NoInternet -> {
                            bootstrapProgress = "⚠️ Sem Internet! Iniciando com estrutura offline..."
                            delay(3000)
                            isBootstrapping = false
                            refreshFileTree()
                            openSelectedFileOrDefault(pDir)
                        }
                        is ProjectBootstrapper.BootstrapResult.Error -> {
                            bootstrapProgress = "❌ Erro: ${(result as ProjectBootstrapper.BootstrapResult.Error).message}"
                            delay(3000)
                            isBootstrapping = false
                            refreshFileTree()
                            openSelectedFileOrDefault(pDir)
                        }
                    }
                }
            }
        } else {
            openSelectedFileOrDefault(pDir)
        }
    }

    private fun openSelectedFileOrDefault(pDir: File) {
        val filesToTry = listOf(
            File(pDir, "index.html"),
            File(pDir, "src/main.tsx"),
            File(pDir, "src/main.jsx"),
            File(pDir, "src/App.tsx"),
            File(pDir, "src/App.jsx"),
            File(pDir, "src/index.ts"),
            File(pDir, "index.js"),
            File(pDir, "main.py")
        )
        val fileToOpen = filesToTry.firstOrNull { it.exists() }
        
        if (fileToOpen != null) {
            openFile(fileToOpen)
        } else {
            // fallback to any first file in directory
            val fallbackFile = pDir.listFiles()?.firstOrNull { !it.isDirectory && !it.name.startsWith(".") }
            if (fallbackFile != null) {
                openFile(fallbackFile)
            } else {
                selectedFile = null
                currentCodeContent = ""
            }
        }
    }

    fun deleteProject(project: ProjectEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            projectDao.deleteProject(project.id)
            withContext(Dispatchers.Main) {
                if (currentProject?.id == project.id) {
                    currentProject = null
                }
            }
            // Delete folder recursively
            File(project.path).deleteRecursively()
        }
    }

    fun exitProject() {
        stopWebServer()
        terminalSession?.destroy()
        terminalSession = null
        currentProject = null
        openTabs = emptyList()
        selectedFile = null
        currentCodeContent = ""
    }

    // --- File operations ---

    fun refreshFileTree() {
        val proj = currentProject ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val root = File(proj.path)
            val filesList = mutableListOf<FileItem>()

            fun scanDir(dir: File, depth: Int) {
                val list = dir.listFiles()?.sortedWith(compareBy({ !it.isDirectory }, { it.name })) ?: return
                for (f in list) {
                    if (f.name.startsWith(".")) continue // Skip internal git/settings directories
                    filesList.add(FileItem(file = f, name = f.name, isDirectory = f.isDirectory, depth = depth))
                }
            }

            scanDir(root, 0)
            withContext(Dispatchers.Main) {
                workspaceFiles = filesList
            }
        }
    }

    fun createFile(name: String, isFolder: Boolean) {
        val proj = currentProject ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val target = File(proj.path, name)
            if (isFolder) {
                target.mkdirs()
            } else {
                target.parentFile?.mkdirs()
                target.createNewFile()
                target.writeText("")
            }
            refreshFileTree()
            if (!isFolder) {
                openFile(target)
            }
        }
    }

    fun deleteFile(file: File) {
        viewModelScope.launch(Dispatchers.IO) {
            file.deleteRecursively()
            refreshFileTree()
            // If open in tab, close it
            closeFile(file)
        }
    }

    fun openFile(file: File) {
        if (!openTabs.contains(file)) {
            openTabs = openTabs + file
        }
        selectedFile = file
        viewModelScope.launch(Dispatchers.IO) {
            val content = try { file.readText() } catch (e: Exception) { "" }
            withContext(Dispatchers.Main) {
                currentCodeContent = content
            }
        }
    }

    fun closeFile(file: File) {
        val newSelected = if (selectedFile == file) openTabs.lastOrNull() else selectedFile
        openTabs = openTabs.filter { it != file }
        selectedFile = newSelected
        if (newSelected != null) {
            viewModelScope.launch(Dispatchers.IO) {
                val content = try { newSelected.readText() } catch (e: Exception) { "" }
                withContext(Dispatchers.Main) {
                    currentCodeContent = content
                }
            }
        } else {
            currentCodeContent = ""
        }
    }

    fun selectTab(file: File) {
        selectedFile = file
        viewModelScope.launch(Dispatchers.IO) {
            val content = try { file.readText() } catch (e: Exception) { "" }
            withContext(Dispatchers.Main) {
                currentCodeContent = content
            }
        }
    }

    fun deselectFile() {
        selectedFile = null
    }

    fun updateCode(newCode: String) {
        currentCodeContent = newCode
        val current = selectedFile ?: return

        // Debounce autosave: 800ms
        autoSaveJob?.cancel()
        autoSaveJob = viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) { isSaving = true }
            delay(800)
            try {
                current.writeText(newCode)
            } catch (e: Exception) {
                Log.e("IdeViewModel", "Autosave failed", e)
            } finally {
                withContext(Dispatchers.Main) { isSaving = false }
            }
        }
    }

    // --- Web Server controls ---

    fun startWebServer() {
        val proj = currentProject ?: return
        if (isServerRunning) return
        val stack = currentStack ?: StackRegistry.findById("html-css-js")!!

        viewModelScope.launch(Dispatchers.IO) {
            if (stack.previewStrategy == PreviewStrategy.STATIC_FILE) {
                val ws = LocalWebServer(File(proj.path))
                webServer = ws
                val port = ws.start()
                withContext(Dispatchers.Main) {
                    serverPort = port
                    isServerRunning = true
                }
            } else if (stack.previewStrategy == PreviewStrategy.DEV_SERVER) {
                val ws = LocalWebServer(File(proj.path))
                webServer = ws
                val port = ws.start(stack.devServerPort ?: 5173)
                withContext(Dispatchers.Main) {
                    terminalSession?.executeCommand(stack.devCommand ?: "npm run dev")
                    serverPort = port
                    isServerRunning = true
                }
            } else {
                withContext(Dispatchers.Main) {
                    terminalSession?.executeCommand(stack.devCommand ?: "python3 main.py")
                    isServerRunning = true
                }
            }
        }
    }

    fun stopWebServer() {
        webServer?.stop()
        webServer = null
        isServerRunning = false
    }

    // --- AI Integration ---

    private fun getProjectFilesContextText(projectDir: File): String {
        if (!projectDir.exists() || !projectDir.isDirectory) return "Nenhum arquivo no projeto."
        val sb = java.lang.StringBuilder()
        fun traverse(dir: File) {
            val files = dir.listFiles()?.sortedWith(compareBy({ !it.isDirectory }, { f -> f.name })) ?: return
            for (f in files) {
                if (f.name.startsWith(".")) continue
                if (f.name == "node_modules" || f.name == "build" || f.name == "bin" || f.name == "dist") continue
                if (f.isDirectory) {
                    traverse(f)
                } else {
                    val relativePath = f.relativeTo(projectDir).path
                    val ext = f.extension.lowercase()
                    if (ext !in listOf("png", "jpg", "jpeg", "gif", "ico", "keystore", "jks", "zip", "tar", "gz")) {
                        val content = try {
                            val text = f.readText()
                            if (text.length > 6000) {
                                text.take(6000) + "\n... [truncado devido ao tamanho]"
                            } else {
                                text
                            }
                        } catch (e: Exception) {
                            "[Erro ao ler conteúdo]"
                        }
                        sb.append("=== ARQUIVO: $relativePath ===\n```\n$content\n```\n\n")
                    }
                }
            }
        }
        traverse(projectDir)
        return if (sb.isEmpty()) "O projeto está vazio." else sb.toString()
    }

    fun sendChatMessage(text: String) {
        val proj = currentProject ?: return
        if (text.trim().isEmpty()) return

        viewModelScope.launch(Dispatchers.IO) {
            val userMsg = ChatMessageEntity(
                projectId = proj.id,
                sender = "user",
                content = text
            )
            chatMessageDao.insertMessage(userMsg)

            withContext(Dispatchers.Main) {
                isGeneratingAi = true
                aiErrorMessage = null
            }

            // Build full context prompt
            val conversationContext = chatMessages.joinToString("\n") {
                "${if (it.sender == "user") "Usuário" else "Assistente"}: ${it.content}"
            }

            val projectDir = File(proj.path)
            val allFilesContext = getProjectFilesContextText(projectDir)

            val systemInstruction = """
                Você é a IA oficial do DroidIDE, uma IDE mobile inteligente rodando 100% offline ou híbrida em Android.
                Responda em português de modo técnico, amigável e focado.
                Você possui poderes de AGENTE DE CÓDIGO. Se o usuário pedir para criar, ler, modificar ou corrigir arquivos, emita o comando estruturado opcional ao final do seu texto.
                
                Instruções de comando do Agente:
                - Para Criar, Sobrescrever ou Modificar um arquivo:
                  Adicione ao final do seu texto o bloco:
                  @@@WRITE_FILE:nome_do_arquivo@@@
                  CONTEÚDO COMPLETO DO ARQUIVO
                  @@@END_WRITE@@@

                - Para Deletar um arquivo:
                  Adicione ao final do seu texto o bloco:
                  @@@DELETE_FILE:nome_do_arquivo@@@
                  
                Use os comandos com precisão. Você tem visibilidade de todo o projeto abaixo.
                
                Código e Arquivos Atuais do Projeto:
                $allFilesContext
                
                Arquivo Ativo Aberto no Editor (${selectedFile?.name ?: "Nenhum"}):
                ${selectedFile?.let { "```\n" + currentCodeContent + "\n```" } ?: "Nenhum arquivo aberto."}
            """.trimIndent()

            val apiKeyToUse = inputApiKey.ifEmpty { BuildConfig.GEMINI_API_KEY }
            if (apiKeyToUse == "MY_GEMINI_API_KEY" || apiKeyToUse.isEmpty()) {
                val errorMsg = "API Key do Gemini não configurada! Vá no painel de segredos do AI Studio ou insira a chave nas configurações do DroidIDE."
                val errorEntity = ChatMessageEntity(projectId = proj.id, sender = "ai", content = errorMsg)
                chatMessageDao.insertMessage(errorEntity)
                withContext(Dispatchers.Main) {
                    isGeneratingAi = false
                }
                return@launch
            }

            try {
                val req = GenerateContentRequest(
                    contents = listOf(
                        Content(parts = listOf(Part(text = "Histórico de Conversação:\n$conversationContext\n\nNova pergunta:\n$text")))
                    ),
                    systemInstruction = Content(parts = listOf(Part(text = systemInstruction))),
                    generationConfig = GenerationConfig(temperature = 0.2f)
                )

                val response = GeminiApiClient.service.generateContent(
                    model = aiModel,
                    apiKey = apiKeyToUse,
                    request = req
                )

                val replyText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                    ?: "Nenhuma resposta do modelo."

                // Parse and execute Agent Write/Edit commands in background
                val cleanReply = executeAgentCommands(replyText, projectDir)

                val aiMsg = ChatMessageEntity(
                    projectId = proj.id,
                    sender = "ai",
                    content = cleanReply
                )
                chatMessageDao.insertMessage(aiMsg)

            } catch (e: Exception) {
                Log.e("IdeViewModel", "Gemini call failed", e)
                val errEntity = ChatMessageEntity(
                    projectId = proj.id,
                    sender = "ai",
                    content = "Erro ao processar: ${e.localizedMessage ?: "Conexão de rede falhou."}"
                )
                chatMessageDao.insertMessage(errEntity)
            } finally {
                withContext(Dispatchers.Main) {
                    isGeneratingAi = false
                }
            }
        }
    }

    private suspend fun executeAgentCommands(reply: String, projectDir: File): String {
        var mutableReply = reply
        try {
            val writeFileRegex = Regex("@@@WRITE_FILE:(.*?)@@@([\\s\\S]*?)@@@END_WRITE@@@")
            val writeMatches = writeFileRegex.findAll(reply)

            var commandExecuted = false
            for (match in writeMatches) {
                val fileName = match.groupValues[1].trim()
                val fileContent = match.groupValues[2]

                val targetWritableFile = File(projectDir, fileName)
                targetWritableFile.parentFile?.mkdirs()
                targetWritableFile.writeText(fileContent)
                
                if (selectedFile?.absolutePath == targetWritableFile.absolutePath) {
                    withContext(Dispatchers.Main) {
                        currentCodeContent = fileContent
                    }
                }
                commandExecuted = true
            }

            val deleteFileRegex = Regex("@@@DELETE_FILE:(.*?)@@@")
            val deleteMatches = deleteFileRegex.findAll(reply)
            for (match in deleteMatches) {
                val fileName = match.groupValues[1].trim()
                val targetFile = File(projectDir, fileName)
                if (targetFile.exists()) {
                    targetFile.delete()
                }
                if (selectedFile?.absolutePath == targetFile.absolutePath) {
                    withContext(Dispatchers.Main) {
                        selectedFile = null
                        currentCodeContent = ""
                    }
                }
                commandExecuted = true
            }

            if (commandExecuted) {
                refreshFileTree()
                mutableReply = mutableReply.replace(writeFileRegex, "")
                mutableReply = mutableReply.replace(deleteFileRegex, "")
                mutableReply += "\n\n*(🛡️ **Agente de Código**: Arquivos do projeto atualizados com sucesso conforme sugestão!)*"
            }
        } catch (e: Exception) {
            Log.e("IdeViewModel", "Agent command parser failed", e)
        }
        return mutableReply
    }

    fun clearChat() {
        val proj = currentProject ?: return
        viewModelScope.launch(Dispatchers.IO) {
            chatMessageDao.clearMessagesForProject(proj.id)
        }
    }

    // --- Deploy Operations ---

    fun performDeploy(platform: String, deploymentName: String) {
        val proj = currentProject ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val initialDeploy = DeploymentEntity(
                projectId = proj.id,
                platform = platform,
                projectName = deploymentName,
                url = "",
                status = "building"
            )
            val deployId = deploymentDao.insertDeployment(initialDeploy).toInt()

            // Simulate building pipeline
            delay(3000)

            val successUrl = if (platform == "Vercel") {
                "https://${deploymentName.lowercase().replace(" ", "-")}.vercel.app"
            } else {
                "https://${deploymentName.lowercase().replace(" ", "-")}.netlify.app"
            }

            val completedDeploy = DeploymentEntity(
                id = deployId,
                projectId = proj.id,
                platform = platform,
                projectName = deploymentName,
                url = successUrl,
                status = "deployed"
            )
            deploymentDao.insertDeployment(completedDeploy)
        }
    }

    // --- Settings Preferences ---

    fun updateFontSize(newSize: Int) {
        editorFontSize = newSize
        viewModelScope.launch(Dispatchers.IO) {
            settingDao.insertSetting(SettingEntity("editor_font_size", newSize.toString()))
        }
    }

    fun toggleEditorEngine(monaco: Boolean) {
        useMonacoEditor = monaco
        viewModelScope.launch(Dispatchers.IO) {
            settingDao.insertSetting(SettingEntity("use_monaco", monaco.toString()))
        }
    }

    fun updateApiKey(key: String) {
        inputApiKey = key
        viewModelScope.launch(Dispatchers.IO) {
            settingDao.insertSetting(SettingEntity("gemini_api_key", key))
        }
    }

    fun updateAiModel(model: String) {
        aiModel = model
        viewModelScope.launch(Dispatchers.IO) {
            settingDao.insertSetting(SettingEntity("ai_model", model))
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopWebServer()
        terminalSession?.destroy()
    }
}
