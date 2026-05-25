package com.example.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Color
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import com.example.terminal.AndroidTerminalSession

enum class InternetRequirement {
    NONE,           // 100% offline (HTML/CSS/JS estático)
    FIRST_TIME,     // só na criação — depois usa cache npm
    ALWAYS          // sempre (ex: Next.js com ISR remoto)
}

enum class PreviewStrategy {
    STATIC_FILE,    // abre index.html direto no WebView (file://)
    DEV_SERVER,     // proxy para localhost:PORT no WebView
    TERMINAL_ONLY,  // só output no terminal (Python scripts, Node CLI)
}

enum class StackCategory {
    STATIC, FRONTEND, FULLSTACK, BACKEND, MOBILE, DATA_SCIENCE
}

data class StackDefinition(
    val id: String,
    val name: String,
    val description: String,
    val icon: ImageVector,
    val category: StackCategory,

    // Precisa de internet para criar o projeto?
    val requiresInternet: InternetRequirement,

    // Pacotes Alpine necessários (instalados no bootstrap)
    val alpinePackages: List<String>,

    // Comandos para criar o projeto do zero
    val initCommands: List<String>,

    // Comando para rodar o servidor de dev
    val devCommand: String?,

    // Como o preview funciona
    val previewStrategy: PreviewStrategy,

    // Porta padrão do dev server
    val devServerPort: Int? = null,

    // Arquivos criados no modo OFFLINE (sem internet)
    val offlineTemplate: Map<String, String>? = null,

    // Extensões de arquivo reconhecidas
    val fileExtensions: List<String>,
)

object StackRegistry {

    val all: List<StackDefinition> = listOf(

        // ── STATIC ──────────────────────────────────────────────────
        StackDefinition(
            id = "html-css-js",
            name = "HTML / CSS / JS",
            description = "Projeto web estático puro, sem dependências",
            icon = Icons.Default.Article,
            category = StackCategory.STATIC,
            requiresInternet = InternetRequirement.NONE,
            alpinePackages = emptyList(),
            initCommands = emptyList(), // cria via template offline
            devCommand = null,
            devServerPort = null,
            previewStrategy = PreviewStrategy.STATIC_FILE,
            fileExtensions = listOf("html", "css", "js"),
            offlineTemplate = mapOf(
                "index.html" to """
                    <!DOCTYPE html>
                    <html lang="pt-BR">
                    <head>
                      <meta charset="UTF-8">
                      <meta name="viewport" content="width=device-width, initial-scale=1.0">
                      <title>Meu Projeto</title>
                      <link rel="stylesheet" href="style.css">
                    </head>
                    <body>
                      <div class="card">
                        <h1>Olá, mundo!</h1>
                        <p>Projeto estático criado com sucesso via Stack Engine.</p>
                        <button id="btn">Disparar Ação</button>
                      </div>
                      <script src="main.js"></script>
                    </body>
                    </html>
                """.trimIndent(),
                "style.css" to """
                    body {
                      font-family: system-ui, -apple-system, sans-serif;
                      margin: 0;
                      background-color: #030712;
                      color: #f3f4f6;
                      display: flex;
                      align-items: center;
                      justify-content: center;
                      height: 100vh;
                    }
                    .card {
                      background-color: #0b0f19;
                      border: 1px solid #111827;
                      padding: 40px;
                      border-radius: 24px;
                      text-align: center;
                      max-width: 380px;
                      box-shadow: 0 20px 40px -15px rgba(0, 0, 0, 0.7);
                    }
                    h1 {
                      font-size: 24px;
                      font-weight: 800;
                      color: #ffffff;
                      margin-top: 0;
                      margin-bottom: 8px;
                    }
                    p {
                      font-size: 13px;
                      color: #9ca3af;
                      line-height: 1.6;
                      margin-bottom: 24px;
                    }
                    button {
                      background-color: #2563eb;
                      color: #ffffff;
                      border: none;
                      padding: 12px 24px;
                      border-radius: 14px;
                      font-weight: 750;
                      cursor: pointer;
                      width: 100%;
                    }
                """.trimIndent(),
                "main.js" to """
                    document.getElementById('btn').addEventListener('click', () => {
                        alert('Projeto iniciado em HTML/CSS/JS Estático!');
                    });
                """.trimIndent()
            )
        ),

        // ── VITE + REACT ─────────────────────────────────────────────
        StackDefinition(
            id = "vite-react",
            name = "React + Vite",
            description = "React moderno com Vite como bundler",
            icon = Icons.Default.Dashboard,
            category = StackCategory.FRONTEND,
            requiresInternet = InternetRequirement.FIRST_TIME,
            alpinePackages = listOf("nodejs", "npm"),
            initCommands = listOf(
                "npm create vite@latest . -- --template react",
                "npm install"
            ),
            devCommand = "npm run dev -- --host 0.0.0.0 --port 5173",
            devServerPort = 5173,
            previewStrategy = PreviewStrategy.DEV_SERVER,
            fileExtensions = listOf("jsx", "js", "css", "html"),
            offlineTemplate = mapOf(
                "index.html" to """
                    <!DOCTYPE html>
                    <html lang="pt-BR">
                    <head>
                        <meta charset="UTF-8">
                        <meta name="viewport" content="width=device-width, initial-scale=1.0">
                        <title>React + Vite</title>
                        <script src="https://cdn.tailwindcss.com"></script>
                    </head>
                    <body class="bg-[#030712] text-slate-100 min-h-screen">
                        <div id="root"></div>
                        <script type="module" src="/src/main.jsx"></script>
                    </body>
                    </html>
                """.trimIndent(),
                "package.json" to """
                    {
                      "name": "react-vite-app",
                      "private": true,
                      "version": "0.0.0",
                      "type": "module",
                      "scripts": {
                        "dev": "vite",
                        "build": "vite build",
                        "preview": "vite preview"
                      },
                      "dependencies": {
                        "react": "^18.2.0",
                        "react-dom": "^18.2.0"
                      },
                      "devDependencies": {
                        "@types/react": "^18.2.0",
                        "@types/react-dom": "^18.2.0",
                        "@vitejs/plugin-react": "^4.2.0",
                        "vite": "^5.0.0"
                      }
                    }
                """.trimIndent(),
                "vite.config.js" to """
                    import { defineConfig } from 'vite'
                    import react from '@vitejs/plugin-react'

                    export default defineConfig({
                      plugins: [react()],
                      server: {
                        host: '0.0.0.0',
                        port: 5173
                      }
                    })
                """.trimIndent(),
                "src/main.jsx" to """
                    import React from 'react'
                    import ReactDOM from 'react-dom/client'
                    import App from './App.jsx'
                    import './index.css'

                    ReactDOM.createRoot(document.getElementById('root')).render(
                      <React.StrictMode>
                        <App />
                      </React.StrictMode>
                    )
                """.trimIndent(),
                "src/App.jsx" to """
                    import React, { useState } from 'react'

                    export default function App() {
                      const [count, setCount] = useState(0)

                      return (
                        <div class="flex flex-col items-center justify-center min-h-screen p-6">
                          <div class="max-w-md w-full bg-slate-900 border border-slate-800 rounded-2xl p-8 text-center shadow-2xl">
                            <h1 class="text-3xl font-extrabold text-[#38BDF8] mb-2">React + Vite</h1>
                            <p class="text-slate-400 text-sm mb-6">Iniciado com sucesso via DroidIDE Stack Engine!</p>
                            
                            <div class="bg-slate-950 p-6 rounded-xl border border-slate-800 mb-6">
                              <p class="text-lg text-slate-200 mb-4 font-semibold">Contador: <span class="text-[#38BDF8]">{count}</span></p>
                              <button 
                                onClick={() => setCount(count + 1)}
                                class="w-full bg-[#38BDF8] text-slate-950 hover:bg-[#0EA5E9] font-bold py-3 px-6 rounded-lg transition duration-200 active:scale-95"
                              >
                                Incrementar Contador
                              </button>
                            </div>
                            
                            <p class="text-xs text-slate-500">Edite <code class="text-[#38BDF8]">src/App.jsx</code> para começar seu desenvolvimento.</p>
                          </div>
                        </div>
                      )
                    }
                """.trimIndent(),
                "src/index.css" to """
                    body {
                      margin: 0;
                      font-family: system-ui, -apple-system, sans-serif;
                      background-color: #030712;
                      color: #f3f4f6;
                    }
                """.trimIndent()
            )
        ),

         // ── VITE + REACT + TS ────────────────────────────────────────
        StackDefinition(
            id = "vite-react-ts",
            name = "React + Vite + TypeScript",
            description = "React com TypeScript e Vite",
            icon = Icons.Default.Code,
            category = StackCategory.FRONTEND,
            requiresInternet = InternetRequirement.FIRST_TIME,
            alpinePackages = listOf("nodejs", "npm"),
            initCommands = listOf(
                "npm create vite@latest . -- --template react-ts",
                "npm install"
            ),
            devCommand = "npm run dev -- --host 0.0.0.0 --port 5173",
            devServerPort = 5173,
            previewStrategy = PreviewStrategy.DEV_SERVER,
            fileExtensions = listOf("tsx", "ts", "css", "html"),
            offlineTemplate = mapOf(
                "index.html" to """
                    <!DOCTYPE html>
                    <html lang="pt-BR">
                    <head>
                        <meta charset="UTF-8">
                        <meta name="viewport" content="width=device-width, initial-scale=1.0">
                        <title>React + Vite + TS</title>
                        <script src="https://cdn.tailwindcss.com"></script>
                    </head>
                    <body class="bg-[#030712] text-slate-100 min-h-screen">
                        <div id="root"></div>
                        <script type="module" src="/src/main.tsx"></script>
                    </body>
                    </html>
                """.trimIndent(),
                "package.json" to """
                    {
                      "name": "react-vite-ts-app",
                      "private": true,
                      "version": "0.0.0",
                      "type": "module",
                      "scripts": {
                        "dev": "vite",
                        "build": "tsc && vite build",
                        "preview": "vite preview"
                      },
                      "dependencies": {
                        "react": "^18.2.0",
                        "react-dom": "^18.2.0"
                      },
                      "devDependencies": {
                        "@types/react": "^18.2.0",
                        "@types/react-dom": "^18.2.0",
                        "@typescript-eslint/eslint-plugin": "^6.0.0",
                        "@typescript-eslint/parser": "^6.0.0",
                        "@vitejs/plugin-react": "^4.2.0",
                        "typescript": "^5.0.0",
                        "vite": "^5.0.0"
                      }
                    }
                """.trimIndent(),
                "vite.config.ts" to """
                    import { defineConfig } from 'vite'
                    import react from '@vitejs/plugin-react'

                    export default defineConfig({
                      plugins: [react()],
                      server: {
                        host: '0.0.0.0',
                        port: 5173
                      }
                    })
                """.trimIndent(),
                "tsconfig.json" to """
                    {
                      "compilerOptions": {
                        "target": "ES2020",
                        "useDefineForClassFields": true,
                        "lib": ["DOM", "DOM.Iterable", "ES2020"],
                        "module": "ESNext",
                        "skipLibCheck": true,
                        "moduleResolution": "bundler",
                        "allowImportingTsExtensions": true,
                        "resolveJsonModule": true,
                        "isolatedModules": true,
                        "noEmit": true,
                        "jsx": "react-jsx",
                        "strict": true,
                        "noUnusedLocals": true,
                        "noUnusedParameters": true,
                        "noFallthroughCasesInSwitch": true
                      },
                      "include": ["src"]
                    }
                """.trimIndent(),
                "src/main.tsx" to """
                    import React from 'react'
                    import ReactDOM from 'react-dom/client'
                    import App from './App.tsx'
                    import './index.css'

                    ReactDOM.createRoot(document.getElementById('root')!).render(
                      <React.StrictMode>
                        <App />
                      </React.StrictMode>
                    )
                """.trimIndent(),
                "src/App.tsx" to """
                    import React, { useState } from 'react'

                    export default function App() {
                      const [count, setCount] = useState(0)

                      return (
                        <div class="flex flex-col items-center justify-center min-h-screen p-6">
                          <div class="max-w-md w-full bg-slate-900 border border-slate-800 rounded-2xl p-8 text-center shadow-2xl">
                            <h1 class="text-3xl font-extrabold text-[#38BDF8] mb-2">React + Vite + TS</h1>
                            <p class="text-slate-400 text-sm mb-6">Iniciado com sucesso via DroidIDE Stack Engine com TypeScript!</p>
                            
                            <div class="bg-slate-950 p-6 rounded-xl border border-slate-800 mb-6">
                              <p class="text-lg text-slate-200 mb-4 font-semibold">Contador: <span class="text-[#38BDF8]">{count}</span></p>
                              <button 
                                onClick={() => setCount(count + 1)}
                                class="w-full bg-[#38BDF8] text-slate-950 hover:bg-[#0EA5E9] font-bold py-3 px-6 rounded-lg transition duration-200 active:scale-95"
                              >
                                Incrementar Contador
                              </button>
                            </div>
                            
                            <p class="text-xs text-slate-500">Edite <code class="text-[#38BDF8]">src/App.tsx</code> para começar seu desenvolvimento.</p>
                          </div>
                        </div>
                      )
                    }
                """.trimIndent(),
                "src/index.css" to """
                    body {
                      margin: 0;
                      font-family: system-ui, -apple-system, sans-serif;
                      background-color: #030712;
                      color: #f3f4f6;
                    }
                """.trimIndent()
            )
        ),

        // ── NEXT.JS ──────────────────────────────────────────────────
        StackDefinition(
            id = "nextjs",
            name = "Next.js",
            description = "React full-stack com SSR e App Router",
            icon = Icons.Default.Web,
            category = StackCategory.FULLSTACK,
            requiresInternet = InternetRequirement.FIRST_TIME,
            alpinePackages = listOf("nodejs", "npm"),
            initCommands = listOf(
                "npx create-next-app@latest . --ts --tailwind --eslint --app --src-dir --no-git"
            ),
            devCommand = "npm run dev -- -H 0.0.0.0 -p 3000",
            devServerPort = 3000,
            previewStrategy = PreviewStrategy.DEV_SERVER,
            fileExtensions = listOf("tsx", "ts", "css"),
            offlineTemplate = mapOf(
                "src/app/page.tsx" to """
                    export default function Home() {
                      return (
                        <main className="flex min-h-screen flex-col items-center justify-center p-24 bg-[#030712] text-white">
                          <h1 className="text-4xl font-extrabold">Next.js Project</h1>
                          <p className="mt-4 text-slate-400">Criado com o Next.js Stack Engine.</p>
                        </main>
                      )
                    }
                """.trimIndent()
            )
        ),

        // ── REACT NATIVE WEB ─────────────────────────────────────────
        StackDefinition(
            id = "react-native-web",
            name = "React Native Web",
            description = "Componentes React Native rodando no browser",
            icon = Icons.Default.PhoneAndroid,
            category = StackCategory.MOBILE,
            requiresInternet = InternetRequirement.FIRST_TIME,
            alpinePackages = listOf("nodejs", "npm"),
            initCommands = listOf(
                "npm create vite@latest . -- --template react-ts",
                "npm install react-native-web react-dom",
                "npm install -D @babel/preset-react babel-plugin-react-native-web"
            ),
            devCommand = "npm run dev -- --host 0.0.0.0 --port 5173",
            devServerPort = 5173,
            previewStrategy = PreviewStrategy.DEV_SERVER,
            fileExtensions = listOf("tsx", "ts", "jsx", "js"),
            offlineTemplate = mapOf(
                "index.html" to """
                    <!DOCTYPE html>
                    <html lang="pt-BR">
                    <head>
                        <meta charset="UTF-8">
                        <meta name="viewport" content="width=device-width, initial-scale=1.0">
                        <title>React Native Web</title>
                    </head>
                    <body>
                        <div id="root"></div>
                        <script type="module" src="/src/main.tsx"></script>
                    </body>
                    </html>
                """.trimIndent()
            )
        ),

        // ── NODE.JS / EXPRESS ────────────────────────────────────────
        StackDefinition(
            id = "node-express",
            name = "Node.js + Express",
            description = "API REST com Express e TypeScript",
            icon = Icons.Default.Terminal,
            category = StackCategory.BACKEND,
            requiresInternet = InternetRequirement.FIRST_TIME,
            alpinePackages = listOf("nodejs", "npm"),
            initCommands = listOf(
                "npm init -y",
                "npm install express",
                "npm install -D typescript @types/node @types/express ts-node nodemon"
            ),
            devCommand = "npx nodemon --exec ts-node src/index.ts",
            devServerPort = 3000,
            previewStrategy = PreviewStrategy.TERMINAL_ONLY,
            fileExtensions = listOf("ts", "js", "json"),
            offlineTemplate = mapOf(
                "package.json" to """
                    {
                      "name": "node-express-backend",
                      "version": "1.0.0",
                      "main": "src/index.ts",
                      "scripts": {
                        "start": "ts-node src/index.ts",
                        "dev": "nodemon src/index.ts"
                      }
                    }
                """.trimIndent(),
                "src/index.ts" to """
                    import express from 'express';
                    const app = express();
                    const port = 3000;
                    app.get('/', (req, res) => {
                      res.send('API Node + Express rodando pelo Stack Engine!');
                    });
                    app.listen(port, () => {
                      console.log('Servidor Express iniciado na porta ' + port);
                    });
                """.trimIndent()
            )
        ),

        // ── PYTHON ───────────────────────────────────────────────────
        StackDefinition(
            id = "python",
            name = "Python 3",
            description = "Script ou projeto Python puro",
            icon = Icons.Default.Code,
            category = StackCategory.DATA_SCIENCE,
            requiresInternet = InternetRequirement.NONE,
            alpinePackages = listOf("python3", "py3-pip"),
            initCommands = emptyList(),
            devCommand = "python3 main.py",
            devServerPort = null,
            previewStrategy = PreviewStrategy.TERMINAL_ONLY,
            fileExtensions = listOf("py"),
            offlineTemplate = mapOf(
                "main.py" to """
                    # Python Script Workspace
                    # DroidIDE local terminal playground

                    class Workspace:
                        def __init__(self, name):
                            self.name = name
                            self.engine = "Python 3"

                        def configure(self):
                            return f"Ambiente {self.engine} para '{self.name}' pronto!"

                    if __name__ == "__main__":
                        app = Workspace("Projeto")
                        print("----------------------------------------")
                        print(app.configure())
                        print("Como testar:")
                        print(" -> Execute 'python3 main.py' no terminal abaixo.")
                        print("----------------------------------------")
                """.trimIndent()
            )
        ),

        // ── PYTHON + FLASK ───────────────────────────────────────────
        StackDefinition(
            id = "python-flask",
            name = "Python + Flask",
            description = "API web leve com Flask",
            icon = Icons.Default.Share,
            category = StackCategory.BACKEND,
            requiresInternet = InternetRequirement.FIRST_TIME,
            alpinePackages = listOf("python3", "py3-pip"),
            initCommands = listOf("pip3 install flask"),
            devCommand = "flask --app main run --host 0.0.0.0 --port 5000 --debug",
            devServerPort = 5000,
            previewStrategy = PreviewStrategy.DEV_SERVER,
            fileExtensions = listOf("py", "html", "css"),
            offlineTemplate = mapOf(
                "main.py" to """
                    from flask import Flask
                    app = Flask(__name__)

                    @app.route('/')
                    def hello_world():
                        return 'Bem-vindo ao app Flask criado na DroidIDE via Python Stack!'

                    if __name__ == '__main__':
                        app.run(host='0.0.0.0', port=5000, debug=True)
                """.trimIndent()
            )
        ),
    )

    fun findById(id: String) = all.find { it.id == id }
    fun byCategory(cat: StackCategory) = all.filter { it.category == cat }
}

class NetworkChecker(private val context: Context) {
    fun isConnected(): Boolean {
        return try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (e: Exception) {
            true // fallback para evitar travar caso não tenha permissão de rede declarada
        }
    }
}

class ProjectBootstrapper(
    private val terminalSession: AndroidTerminalSession?,
    private val networkChecker: NetworkChecker
) {
    sealed class BootstrapResult {
        object Success : BootstrapResult()
        data class Error(val message: String) : BootstrapResult()
        object NoInternet : BootstrapResult()
    }

    suspend fun bootstrap(
        stack: StackDefinition,
        projectDir: File,
        onProgress: (String) -> Unit
    ): BootstrapResult = withContext(Dispatchers.IO) {

        // 1. Checa internet se necessário
        if (stack.requiresInternet != InternetRequirement.NONE) {
            if (!networkChecker.isConnected()) {
                return@withContext BootstrapResult.NoInternet
            }
        }

        // 2. Instala pacotes Alpine necessários
        if (stack.alpinePackages.isNotEmpty() && terminalSession != null) {
            onProgress("Instalando dependências do sistema (${stack.alpinePackages.joinToString(", ")})...")
            // Proot session simulation or direct terminal trigger
            terminalSession.executeCommand("apk add --no-cache ${stack.alpinePackages.joinToString(" ")}")
            delay(1200)
        }

        // 3. Cria diretório e entra nele
        projectDir.mkdirs()
        terminalSession?.executeCommand("cd /root/projects/${projectDir.name}")

        // Save stack metadata file so we know what stack this project belongs to
        try {
            File(projectDir, ".droidide_stack").writeText(stack.id)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 4. Modo OFFLINE — cria arquivos do template offline
        onProgress("Criando estrutura de arquivos...")
        stack.offlineTemplate?.forEach { (filename, content) ->
            val targetFile = File(projectDir, filename)
            targetFile.parentFile?.mkdirs()
            targetFile.writeText(content)
        }

        // 5. Modo ONLINE — roda comandos de init se aplicável
        if (stack.requiresInternet != InternetRequirement.NONE && stack.initCommands.isNotEmpty()) {
            onProgress("Inicializando projeto com npm/pip (requer internet)...")
            // Configura cache npm para não baixar duas vezes
            terminalSession?.executeCommand("npm config set cache /root/.npm-cache")

            stack.initCommands.forEachIndexed { i, cmd ->
                onProgress("Passo ${i + 1}/${stack.initCommands.size}: $cmd")
                terminalSession?.executeCommand(cmd)
                delay(1800)
            }
        }

        onProgress("✅ Projeto criado com sucesso!")
        BootstrapResult.Success
    }
}

sealed class PreviewState {
    data class StaticFile(val uri: Uri) : PreviewState()
    data class DevServer(val url: String) : PreviewState()
    object TerminalOnly : PreviewState()
}

class PreviewManager(
    private val terminalSession: AndroidTerminalSession?
) {
    fun startPreview(stack: StackDefinition, projectDir: File): PreviewState {
        return when (stack.previewStrategy) {

            // HTML puro — abre direto no WebView
            PreviewStrategy.STATIC_FILE -> {
                val indexFile = File(projectDir, "index.html")
                PreviewState.StaticFile(uri = Uri.fromFile(indexFile))
            }

            // Dev server — proxy localhost no WebView
            PreviewStrategy.DEV_SERVER -> {
                stack.devCommand?.let { cmd ->
                    terminalSession?.executeCommand(cmd)
                }
                val port = stack.devServerPort ?: 3000
                PreviewState.DevServer(url = "http://localhost:$port")
            }

            // Só terminal — sem preview visual
            PreviewStrategy.TERMINAL_ONLY -> {
                stack.devCommand?.let { cmd ->
                    terminalSession?.executeCommand(cmd)
                }
                PreviewState.TerminalOnly
            }
        }
    }
}
