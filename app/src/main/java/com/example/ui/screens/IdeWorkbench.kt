package com.example.ui.screens

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.database.ChatMessageEntity
import com.example.ui.FileItem
import com.example.ui.IdeViewModel
import com.example.ui.components.MonacoWebView
import java.io.File

enum class MobileTab {
    WORKSPACE,
    TERMINAL,
    PREVIEW,
    DEPLOY,
    SETTINGS
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun IdeWorkbench(
    viewModel: IdeViewModel,
    onExit: () -> Unit
) {
    var activeTab by remember { mutableStateOf(MobileTab.WORKSPACE) }
    var showCreateFileDialog by remember { mutableStateOf(false) }
    var fileCreationIsFolder by remember { mutableStateOf(false) }

    val activeProject = viewModel.currentProject ?: return

    BackHandler {
        if (viewModel.selectedFile != null) {
            viewModel.deselectFile()
        } else if (activeTab != MobileTab.WORKSPACE) {
            activeTab = MobileTab.WORKSPACE
        } else {
            onExit()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF10131A))
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // TOP HEADER BAR
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .background(Color(0xFF1D2026))
                    .border(1.dp, Color(0xFF404751))
                    .padding(horizontal = 16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = "Sair",
                        tint = Color(0xFF9FCAFF),
                        modifier = Modifier
                            .size(24.dp)
                            .clickable { onExit() }
                    )
                    Text(
                        text = "DroidIDE",
                        color = Color(0xFFE1E2EB),
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (viewModel.isSaving) {
                        Text("Salvando...", color = Color(0xFF9FCAFF), fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    } else {
                        Box(modifier = Modifier.size(6.dp).background(Color(0xFF22C55E), CircleShape))
                    }

                    TextButton(
                        onClick = {
                            if (viewModel.isServerRunning) {
                                viewModel.stopWebServer()
                            } else {
                                viewModel.startWebServer()
                                activeTab = MobileTab.PREVIEW
                            }
                        },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = if (viewModel.isServerRunning) Color(0xFFEF4444) else Color(0xFF9FCAFF)
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text(
                            text = if (viewModel.isServerRunning) "STOP" else "RUN",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }

            // MIDDLE CONVERSATION FRAMEWORK VIEWPORT
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when (activeTab) {
                    MobileTab.WORKSPACE -> {
                        Column(modifier = Modifier.fillMaxSize()) {
                            // TOP HALF: Explorer & Code Editor
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .background(Color(0xFF0B0E14))
                            ) {
                                val openedFile = viewModel.selectedFile
                                if (openedFile == null) {
                                    FileExplorerPanel(
                                        viewModel = viewModel,
                                        onCreateFileRequested = { isFolder ->
                                            fileCreationIsFolder = isFolder
                                            showCreateFileDialog = true
                                        }
                                    )
                                } else {
                                    CodeEditorPanel(
                                        viewModel = viewModel,
                                        openedFile = openedFile
                                    )
                                }
                            }

                            // Thin Workspace Separator
                            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF404751)))

                            // BOTTOM HALF: AI Assistant Panel
                            Box(
                                modifier = Modifier
                                    .weight(1.2f)
                                    .fillMaxWidth()
                                    .background(Color(0xFF10131A))
                            ) {
                                AiAssistantPanel(viewModel = viewModel)
                            }
                        }
                    }
                    MobileTab.TERMINAL -> FullTerminalView(viewModel)
                    MobileTab.PREVIEW -> FullPreviewView(viewModel)
                    MobileTab.DEPLOY -> FullDeployGitView(viewModel)
                    MobileTab.SETTINGS -> FullSettingsView(viewModel)
                }
            }

            // BOTTOM NAVIGATION BAR
            Row(
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .background(Color(0xFF191C22))
                    .border(1.dp, Color(0xFF404751))
            ) {
                val navItems = listOf(
                    MobileTab.WORKSPACE to Icons.Default.Code,
                    MobileTab.TERMINAL to Icons.Default.Terminal,
                    MobileTab.PREVIEW to Icons.Default.Visibility
                )
                navItems.forEach { (tab, icon) ->
                    val isSelected = activeTab == tab
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .width(64.dp)
                            .height(36.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) Color(0xFF007ACC) else Color.Transparent)
                            .clickable { activeTab = tab }
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = tab.name,
                            tint = if (isSelected) Color.White else Color(0xFFC0C7D3),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Expand settings access icon
                val isSettings = activeTab == MobileTab.SETTINGS
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = if (isSettings) Color.White else Color(0xFF8A919D),
                    modifier = Modifier
                        .size(20.dp)
                        .clickable { activeTab = MobileTab.SETTINGS }
                )
            }
        }

        // Bootstrapping Progress Overlay
        if (viewModel.isBootstrapping) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xE60A0D14))
                    .clickable(enabled = false) {}, // prevent click-through
                contentAlignment = Alignment.Center
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF191C22)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .padding(24.dp)
                        .border(1.dp, Color(0xFF32353C), RoundedCornerShape(12.dp))
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        CircularProgressIndicator(
                            color = Color(0xFF007ACC),
                            strokeWidth = 4.dp,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Text(
                            text = "Configurando Ambiente",
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = viewModel.bootstrapProgress,
                            color = Color(0xFF8A919D),
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }

        // New Item Creation Dialog
        if (showCreateFileDialog) {
            var fileNameInput by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { showCreateFileDialog = false },
                title = { Text(text = if (fileCreationIsFolder) "Criar Pasta" else "Criar Arquivo", color = Color.White) },
                text = {
                    Column {
                        Text(
                            text = if (fileCreationIsFolder) "Nome da pasta de destino:" else "Nome do arquivo (ex: index.html):",
                            color = Color(0xFFC0C7D3),
                            fontSize = 13.sp,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        OutlinedTextField(
                            value = fileNameInput,
                            onValueChange = { fileNameInput = it },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFF007ACC),
                                unfocusedBorderColor = Color(0xFF404751),
                                focusedContainerColor = Color(0xFF10131A),
                                unfocusedContainerColor = Color(0xFF10131A)
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (fileNameInput.trim().isNotEmpty()) {
                                viewModel.createFile(fileNameInput.trim(), fileCreationIsFolder)
                                fileNameInput = ""
                                showCreateFileDialog = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF007ACC))
                    ) {
                        Text("Criar", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCreateFileDialog = false }) {
                        Text("Cancelar", color = Color(0xFF8A919D))
                    }
                },
                containerColor = Color(0xFF1D2026),
                shape = RoundedCornerShape(12.dp)
            )
        }
    }
}

@Composable
fun FileExplorerPanel(
    viewModel: IdeViewModel,
    onCreateFileRequested: (Boolean) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1D2026))
                .border(1.dp, Color(0xFF32353C))
                .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            Text(
                text = "PROJECT EXPLORER",
                color = Color(0xFFC0C7D3),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(
                    imageVector = Icons.Default.NoteAdd,
                    contentDescription = "New File",
                    tint = Color(0xFFC0C7D3),
                    modifier = Modifier
                        .size(18.dp)
                        .clickable { onCreateFileRequested(false) }
                )
                Icon(
                    imageVector = Icons.Default.CreateNewFolder,
                    contentDescription = "New Folder",
                    tint = Color(0xFFC0C7D3),
                    modifier = Modifier
                        .size(18.dp)
                        .clickable { onCreateFileRequested(true) }
                )
            }
        }

        if (viewModel.workspaceFiles.isEmpty()) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Folder, null, tint = Color(0xFF404751), modifier = Modifier.size(36.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Espaço de trabalho vazio", color = Color(0xFF8A919D), fontSize = 12.sp)
                }
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(viewModel.workspaceFiles) { fileItem ->
                    FileTreeItem(
                        fileItem = fileItem,
                        onClick = {
                            if (!fileItem.isDirectory) {
                                viewModel.openFile(fileItem.file)
                            }
                        },
                        onDelete = { viewModel.deleteFile(fileItem.file) }
                    )
                }
            }
        }
    }
}

@Composable
fun FileTreeItem(
    fileItem: FileItem,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(34.dp)
            .clickable(onClick = onClick)
            .padding(end = 12.dp)
    ) {
        val indent = (fileItem.depth * 14).dp
        Spacer(modifier = Modifier.width(indent + 12.dp))

        val icon = if (fileItem.isDirectory) {
            if (fileItem.isExpanded) Icons.Default.FolderOpen else Icons.Default.Folder
        } else {
            Icons.Default.Description
        }

        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (fileItem.isDirectory) Color(0xFFC0C7D3) else Color(0xFF9FCAFF),
            modifier = Modifier.size(16.dp)
        )

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = fileItem.name,
            color = Color(0xFFC0C7D3),
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )

        IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
            Icon(Icons.Default.Close, null, tint = Color(0xFFEF4444).copy(alpha = 0.5f), modifier = Modifier.size(11.dp))
        }
    }
}

@Composable
fun CodeEditorPanel(
    viewModel: IdeViewModel,
    openedFile: File
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
                .background(Color(0xFF0B0E14))
                .border(1.dp, Color(0xFF404751))
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxHeight()
                    .background(Color(0xFF1D2026))
                    .clickable { viewModel.deselectFile() }
                    .padding(horizontal = 10.dp)
            ) {
                Icon(Icons.Default.ArrowBack, "Voltar", tint = Color(0xFF9FCAFF), modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Explorer", color = Color(0xFFC0C7D3), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }

            Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(Color(0xFF404751)))

            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .horizontalScroll(rememberScrollState())
            ) {
                viewModel.openTabs.forEach { tabFile ->
                    val isSelected = tabFile == openedFile
                    Column(
                        modifier = Modifier
                            .fillMaxHeight()
                            .background(if (isSelected) Color(0xFF000000) else Color(0xFF0B0E14))
                            .clickable { viewModel.selectTab(tabFile) }
                    ) {
                        // Top line indicator matching active border-t-2 border-primary
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(2.dp)
                                .background(if (isSelected) Color(0xFF9FCAFF) else Color.Transparent)
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = null,
                                tint = if (isSelected) Color(0xFF9FCAFF) else Color(0xFF8A919D),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = tabFile.name,
                                color = if (isSelected) Color(0xFF9FCAFF) else Color(0xFFC0C7D3),
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Fechar Tab",
                                tint = if (isSelected) Color(0xFF9FCAFF).copy(alpha = 0.6f) else Color(0xFF8A919D).copy(alpha = 0.6f),
                                modifier = Modifier
                                    .size(12.dp)
                                    .clickable { viewModel.closeFile(tabFile) }
                            )
                        }
                    }
                    Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(Color(0xFF404751)))
                }
            }
        }

        Box(modifier = Modifier.weight(1f)) {
            if (viewModel.useMonacoEditor) {
                key(openedFile.absolutePath) {
                    MonacoWebView(
                        code = viewModel.currentCodeContent,
                        language = openedFile.extension,
                        onCodeChanged = { viewModel.updateCode(it) }
                    )
                }
            } else {
                ComposeCodeTextArea(
                    code = viewModel.currentCodeContent,
                    fontSize = viewModel.editorFontSize,
                    onCodeChanged = { viewModel.updateCode(it) }
                )
            }

            // AI Copilot Floating Indicator (Subtle)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF272A31).copy(alpha = 0.9f))
                    .border(1.dp, Color(0xFF404751), RoundedCornerShape(16.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SmartToy,
                        contentDescription = null,
                        tint = Color(0xFFFFB784),
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "Copilot active",
                        color = Color(0xFFC0C7D3),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
fun ComposeCodeTextArea(
    code: String,
    fontSize: Int,
    onCodeChanged: (String) -> Unit
) {
    val lineCount = remember(code) { code.split("\n").size.coerceAtLeast(1) }
    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF000000))
    ) {
        Column(
            horizontalAlignment = Alignment.End,
            modifier = Modifier
                .width(48.dp)
                .fillMaxHeight()
                .background(Color(0xFF0B0E14))
                .padding(vertical = 16.dp, horizontal = 12.dp)
        ) {
            for (i in 1..lineCount) {
                val isHighlighted = i == 1
                Text(
                    text = i.toString(),
                    color = if (isHighlighted) Color(0xFFE1E2EB) else Color(0xFF8A919D),
                    fontFamily = FontFamily.Monospace,
                    fontSize = fontSize.sp,
                    lineHeight = (fontSize * 1.5).sp,
                    fontWeight = if (isHighlighted) FontWeight.Bold else FontWeight.Normal
                )
            }
        }

        Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(Color(0xFF404751)))

        Column(
            modifier = Modifier
                .fillMaxHeight()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .horizontalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            BasicTextField(
                value = code,
                onValueChange = onCodeChanged,
                textStyle = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = fontSize.sp,
                    color = Color(0xFFE1E2EB),
                    lineHeight = (fontSize * 1.5).sp
                ),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    autoCorrectEnabled = false,
                    capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.None
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun AiAssistantPanel(viewModel: IdeViewModel) {
    val listState = rememberLazyListState()
    var chatInput by remember { mutableStateOf("") }
    val openedFile = viewModel.selectedFile

    LaunchedEffect(viewModel.chatMessages.size, viewModel.isGeneratingAi) {
        if (viewModel.chatMessages.isNotEmpty()) {
            listState.animateScrollToItem(viewModel.chatMessages.size)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF0B0E14))
                .border(1.dp, Color(0xFF32353C))
                .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.SmartToy,
                    contentDescription = null,
                    tint = Color(0xFF9FCAFF),
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "AI ASSISTANT",
                    color = Color(0xFFC0C7D3),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }

            val contextText = if (openedFile != null) "Context: ${openedFile.name}" else "No active file"
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF191C22))
                    .border(1.dp, Color(0xFF32353C), RoundedCornerShape(10.dp))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    text = contextText,
                    color = Color(0xFFC0C7D3),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            items(viewModel.chatMessages) { msg ->
                RichChatBubble(msg)
            }

            if (viewModel.isGeneratingAi) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Start
                    ) {
                        TypingIndicator()
                    }
                }
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 4.dp)
        ) {
            val prompts = listOf(
                "/explain" to "Explicar Código",
                "/fix" to "Corrigir Erros",
                "/refactor" to "Otimizar Código",
                "/clear" to "Limpar Chat"
            )
            prompts.forEach { (cmd, label) ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF272A31))
                        .border(1.dp, Color(0xFF404751), RoundedCornerShape(12.dp))
                        .clickable {
                            if (cmd == "/clear") {
                                viewModel.clearChat()
                            } else {
                                val contextSuffix = if (openedFile != null) " no arquivo ${openedFile.name}" else ""
                                viewModel.sendChatMessage("$cmd$contextSuffix")
                            }
                        }
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(text = label, color = Color(0xFF9FCAFF), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF0B0E14))
                .border(1.dp, Color(0xFF32353C))
                .padding(8.dp)
        ) {
            OutlinedTextField(
                value = chatInput,
                onValueChange = { chatInput = it },
                placeholder = { Text("Pergunte à DroidIDE AI...", color = Color(0xFF8A919D), fontSize = 12.sp) },
                maxLines = 3,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFF007ACC),
                    unfocusedBorderColor = Color(0xFF404751),
                    focusedContainerColor = Color(0xFF10131A),
                    unfocusedContainerColor = Color(0xFF10131A)
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 40.dp, max = 100.dp)
            )

            Spacer(modifier = Modifier.width(8.dp))

            IconButton(
                onClick = {
                    if (chatInput.trim().isNotEmpty()) {
                        viewModel.sendChatMessage(chatInput)
                        chatInput = ""
                    }
                },
                enabled = !viewModel.isGeneratingAi,
                modifier = Modifier
                    .background(
                        if (chatInput.trim().isNotEmpty() && !viewModel.isGeneratingAi) Color(0xFF007ACC) else Color(0xFF404751),
                        RoundedCornerShape(8.dp)
                    )
                    .size(40.dp)
            ) {
                Icon(Icons.Default.Send, "Enviar", tint = Color.White, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
fun TypingIndicator() {
    val infiniteTransition = rememberInfiniteTransition()
    val pulse1 by infiniteTransition.animateFloat(
        initialValue = 0.2f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(600, delayMillis = 0), RepeatMode.Reverse)
    )
    val pulse2 by infiniteTransition.animateFloat(
        initialValue = 0.2f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(600, delayMillis = 150), RepeatMode.Reverse)
    )
    val pulse3 by infiniteTransition.animateFloat(
        initialValue = 0.2f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(600, delayMillis = 300), RepeatMode.Reverse)
    )

    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(Color(0xFF1D2026), RoundedCornerShape(8.dp))
            .border(1.dp, Color(0xFF404751), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Box(modifier = Modifier.size(6.dp).background(Color(0xFF9FCAFF).copy(alpha = pulse1), CircleShape))
        Box(modifier = Modifier.size(6.dp).background(Color(0xFF9FCAFF).copy(alpha = pulse2), CircleShape))
        Box(modifier = Modifier.size(6.dp).background(Color(0xFF9FCAFF).copy(alpha = pulse3), CircleShape))
    }
}

@Composable
fun RichChatBubble(msg: ChatMessageEntity) {
    val isUser = msg.sender == "user"
    Column(
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
        modifier = Modifier.fillMaxWidth()
    ) {
        if (isUser) {
            Box(
                modifier = Modifier
                    .widthIn(max = 280.dp)
                    .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp, bottomStart = 12.dp, bottomEnd = 2.dp))
                    .background(Color(0xFF272A31))
                    .border(1.dp, Color(0xFF404751), RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp, bottomStart = 12.dp, bottomEnd = 2.dp))
                    .padding(10.dp)
            ) {
                Text(text = msg.content, color = Color(0xFFE1E2EB), fontSize = 13.sp, lineHeight = 17.sp)
            }
        } else {
            Box(
                modifier = Modifier
                    .widthIn(max = 310.dp)
                    .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp, bottomStart = 2.dp, bottomEnd = 12.dp))
                    .background(Color(0xFF1D2026))
                    .border(1.dp, Color(0xFF404751), RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp, bottomStart = 2.dp, bottomEnd = 12.dp))
                    .padding(10.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val parts = msg.content.split("```")
                    parts.forEachIndexed { index, part ->
                        if (index % 2 == 1) {
                            val lines = part.trim().split("\n")
                            val language = if (lines.firstOrNull()?.all { it.isLetterOrDigit() } == true) lines.first() else "code"
                            val codeContent = if (language != "code") lines.drop(1).joinToString("\n") else part
                            
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFF0B0E14))
                                    .border(1.dp, Color(0xFF32353C), RoundedCornerShape(6.dp))
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFF1A1D24))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(text = language, color = Color(0xFFC0C7D3), fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    Icon(Icons.Default.ContentCopy, "Copiar", tint = Color(0xFF8A919D), modifier = Modifier.size(12.dp).clickable {})
                                }
                                Box(modifier = Modifier.padding(8.dp).horizontalScroll(rememberScrollState())) {
                                    Text(text = codeContent.trim(), color = Color(0xFF9FCAFF), fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                                }
                            }
                        } else {
                            if (part.trim().isNotEmpty()) {
                                Text(text = part.trim(), color = Color(0xFFE1E2EB), fontSize = 13.sp, lineHeight = 17.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FullTerminalView(viewModel: IdeViewModel) {
    var commandInput by remember { mutableStateOf("") }
    val session = viewModel.terminalSession
    val lines = if (session != null) {
        session.terminalLines.collectAsState().value
    } else {
        emptyList()
    }
    val commandHistory = remember { mutableStateListOf<String>() }
    var historyIndex by remember { mutableStateOf(-1) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
        ) {
            Text(
                text = "ALPINE LINUX CONSOLE",
                color = Color(0xFF4ADE80),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.weight(1f))
            IconButton(
                onClick = { viewModel.terminalSession?.sendInterrupt() },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(Icons.Default.StopScreenShare, "Interrupt", tint = Color.Yellow, modifier = Modifier.size(16.dp))
            }
        }

        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF404751)))

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            items(lines) { line ->
                val isPromptHost = line.startsWith("$ ")
                val isPromptAlpine = line.startsWith("alpine:~/project$ ")

                if (isPromptHost) {
                    val cmd = line.substringAfter("$ ")
                    Row {
                        Text("$ ", color = Color(0xFF4ADE80), fontFamily = FontFamily.Monospace, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text(cmd, color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                    }
                } else if (isPromptAlpine) {
                    val cmd = line.substringAfter("alpine:~/project$ ")
                    Row {
                        Text("alpine:~/project$ ", color = Color(0xFF4ADE80), fontFamily = FontFamily.Monospace, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text(cmd, color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                    }
                } else {
                    val lineColor = when {
                        line.startsWith("$ ") -> Color(0xFFC0C7D3)
                        line.contains("npm WARN") || line.contains("WARN") -> Color(0xFF8A919D)
                        line.contains("ERROR") || line.contains("failed") || line.contains("Erro") -> Color(0xFFEF4444)
                        line.contains("OK:") || line.contains("vulnerabilities") -> Color(0xFF4ADE80)
                        else -> Color(0xFFFFFFFF)
                    }
                    Text(
                        text = line,
                        color = lineColor,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                }
            }
        }

        val isAlpine = viewModel.terminalSession?.isAlpineMode == true
        val promptColor = Color(0xFF4ADE80)
        val promptText = if (isAlpine) "alpine:~/project$ " else "$ "
        val keys = if (isAlpine) {
            listOf("ls", "neofetch", "apk update", "exit")
        } else {
            listOf("ls", "pwd", "proot -S alpine", "clear")
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 4.dp)
        ) {
            keys.forEach { key ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFF1D2026))
                        .border(1.dp, Color(0xFF404751), RoundedCornerShape(4.dp))
                        .clickable { viewModel.terminalSession?.executeCommand(key) }
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(key, color = Color.White, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                }
            }
        }

        // Extra Keyboard Key Row from styling spec
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF272A31))
                .border(1.dp, Color(0xFF404751))
                .padding(4.dp)
                .horizontalScroll(rememberScrollState())
        ) {
            val keyModifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFF32353C))
                .padding(horizontal = 12.dp, vertical = 6.dp)

            // Esc
            Box(modifier = keyModifier.clickable { commandInput = "" }) {
                Text("Esc", color = Color(0xFFE1E2EB), fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            }
            // Tab
            Box(modifier = keyModifier.clickable {
                val parts = commandInput.split(" ")
                val lastPart = parts.lastOrNull() ?: ""
                if (lastPart.isNotEmpty()) {
                    val projectDir = viewModel.currentProject?.let { File(it.path) }
                    if (projectDir != null && projectDir.exists()) {
                        val matching = projectDir.listFiles()?.firstOrNull { it.name.startsWith(lastPart, ignoreCase = true) }
                        if (matching != null) {
                            val newParts = parts.dropLast(1) + matching.name
                            commandInput = newParts.joinToString(" ")
                        }
                    }
                }
            }) {
                Text("Tab", color = Color(0xFFE1E2EB), fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            }
            // Ctrl
            Box(modifier = keyModifier.clickable { viewModel.terminalSession?.sendInterrupt() }) {
                Text("Ctrl", color = Color(0xFFE1E2EB), fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            }
            // Alt
            Box(modifier = keyModifier.clickable { viewModel.terminalSession?.executeCommand("clear") }) {
                Text("Alt", color = Color(0xFFE1E2EB), fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            }

            Box(modifier = Modifier.width(1.dp).height(20.dp).background(Color(0xFF404751)))

            // Arrow Left
            Box(modifier = keyModifier.clickable { /* no-op or back cursor movement if text editor */ }) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Esquerda", tint = Color(0xFFE1E2EB), modifier = Modifier.size(14.dp))
            }
            // Arrow Down (History Next)
            Box(modifier = keyModifier.clickable {
                if (commandHistory.isNotEmpty() && historyIndex != -1) {
                    if (historyIndex < commandHistory.size - 1) {
                        historyIndex++
                        commandInput = commandHistory[historyIndex]
                    } else {
                        historyIndex = -1
                        commandInput = ""
                    }
                }
            }) {
                Icon(Icons.Default.ArrowDownward, contentDescription = "Baixo", tint = Color(0xFFE1E2EB), modifier = Modifier.size(14.dp))
            }
            // Arrow Up (History Prev)
            Box(modifier = keyModifier.clickable {
                if (commandHistory.isNotEmpty()) {
                    if (historyIndex == -1) {
                        historyIndex = commandHistory.size - 1
                    } else if (historyIndex > 0) {
                        historyIndex--
                    }
                    commandInput = commandHistory[historyIndex]
                }
            }) {
                Icon(Icons.Default.ArrowUpward, contentDescription = "Cima", tint = Color(0xFFE1E2EB), modifier = Modifier.size(14.dp))
            }
            // Arrow Right
            Box(modifier = keyModifier.clickable { /* space or forward cursor */ }) {
                Icon(Icons.Default.ArrowForward, contentDescription = "Direita", tint = Color(0xFFE1E2EB), modifier = Modifier.size(14.dp))
            }

            Box(modifier = Modifier.width(1.dp).height(20.dp).background(Color(0xFF404751)))

            // '/'
            Box(modifier = keyModifier.clickable { commandInput += "/" }) {
                Text("/", color = Color(0xFFE1E2EB), fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            }
            // '-'
            Box(modifier = keyModifier.clickable { commandInput += "-" }) {
                Text("-", color = Color(0xFFE1E2EB), fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(promptText, color = promptColor, fontFamily = FontFamily.Monospace, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.width(4.dp))
            BasicTextField(
                value = commandInput,
                onValueChange = { commandInput = it },
                textStyle = TextStyle(color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 13.sp),
                singleLine = true,
                modifier = Modifier.weight(1f),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(autoCorrectEnabled = false)
            )
            IconButton(
                onClick = {
                    if (commandInput.trim().isNotEmpty()) {
                        val cmd = commandInput
                        viewModel.terminalSession?.executeCommand(cmd)
                        commandHistory.add(cmd)
                        historyIndex = -1
                        commandInput = ""
                    }
                },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(Icons.Default.ArrowForward, null, tint = Color(0xFF4ADE80), modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
fun FullPreviewView(viewModel: IdeViewModel) {
    if (!viewModel.isServerRunning) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Web, null, tint = Color(0xFF404751), modifier = Modifier.size(48.dp))
                Spacer(modifier = Modifier.height(12.dp))
                Text("Servidor Web Desligado", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Text("Inicie tocando no botão RUN no topo.", color = Color(0xFF8A919D), fontSize = 11.sp)
            }
        }
    } else {
        val previewUrl = "http://localhost:${viewModel.serverPort}"
        val webViewRef = remember { object { var value: WebView? = null } }
        var canGoBack by remember { mutableStateOf(false) }
        var canGoForward by remember { mutableStateOf(false) }
        var refreshTrigger by remember { mutableStateOf(0) }

        BackHandler(enabled = canGoBack) {
            webViewRef.value?.goBack()
        }

        Column(modifier = Modifier.fillMaxSize()) {
            // Simulated Browser Address Bar from markup spec
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0B0E14))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                // Navigation controls
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { webViewRef.value?.goBack() },
                        enabled = canGoBack,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Voltar",
                            tint = if (canGoBack) Color(0xFFE1E2EB) else Color(0xFF8A919D).copy(alpha = 0.4f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = { webViewRef.value?.goForward() },
                        enabled = canGoForward,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowForward,
                            contentDescription = "Avançar",
                            tint = if (canGoForward) Color(0xFFE1E2EB) else Color(0xFF8A919D).copy(alpha = 0.4f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = { 
                            refreshTrigger++
                            webViewRef.value?.reload() 
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Atualizar",
                            tint = Color(0xFFE1E2EB),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Address Input Bar
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .weight(1f)
                        .background(Color(0xFF272A31), RoundedCornerShape(4.dp))
                        .border(1.dp, Color(0xFF404751), RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Seguro",
                        tint = Color(0xFF8A919D),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "localhost:${viewModel.serverPort}",
                        color = Color(0xFFE1E2EB),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                // More Vert Option Button
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Mais opções",
                    tint = Color(0xFF8A919D),
                    modifier = Modifier
                        .size(20.dp)
                        .clickable { /* Options Menu */ }
                )
            }

            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF404751)))

            // Simulated Webview View
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color.White)
            ) {
                val loadState = remember {
                    object {
                        var lastLoadedUrl: String = ""
                        var lastRefreshTrigger: Int = 0
                    }
                }

                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.useWideViewPort = true
                            settings.loadWithOverviewMode = true
                            webViewClient = object : WebViewClient() {
                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    canGoBack = view?.canGoBack() ?: false
                                    canGoForward = view?.canGoForward() ?: false
                                }
                            }
                            loadUrl(previewUrl)
                            webViewRef.value = this
                            loadState.lastLoadedUrl = previewUrl
                            loadState.lastRefreshTrigger = refreshTrigger
                        }
                    },
                    update = { view -> 
                        webViewRef.value = view
                        if (loadState.lastLoadedUrl != previewUrl || loadState.lastRefreshTrigger != refreshTrigger) {
                            view.loadUrl(previewUrl)
                            loadState.lastLoadedUrl = previewUrl
                            loadState.lastRefreshTrigger = refreshTrigger
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullDeployGitView(viewModel: IdeViewModel) {
    var tokenInput by remember { mutableStateOf("") }
    var appNameInput by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text("VERCEL & NETLIFY DEPLOY CENTER", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = tokenInput,
            onValueChange = { tokenInput = it },
            placeholder = { Text("Token de Integração API", fontSize = 12.sp, color = Color(0xFF8A919D)) },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = Color(0xFF007ACC)
            ),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
            value = appNameInput,
            onValueChange = { appNameInput = it },
            placeholder = { Text("Nome da aplicação", fontSize = 12.sp, color = Color(0xFF8A919D)) },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = Color(0xFF007ACC)
            ),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { if (appNameInput.isNotEmpty()) viewModel.performDeploy("Vercel", appNameInput) },
                colors = ButtonDefaults.buttonColors(containerColor = Color.Black),
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Vercel", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold)
            }

            Button(
                onClick = { if (appNameInput.isNotEmpty()) viewModel.performDeploy("Netlify", appNameInput) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF007ACC)),
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Netlify", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text("HISTÓRICO DE DEPLOYS", color = Color(0xFFC0C7D3), fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))

        if (viewModel.deploymentsList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .background(Color(0xFF191C22), RoundedCornerShape(8.dp))
                    .border(1.dp, Color(0xFF32353C), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text("Sem deploys registrados no workspace", color = Color(0xFF8A919D), fontSize = 12.sp)
            }
        } else {
            viewModel.deploymentsList.forEach { deploy ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .background(Color(0xFF191C22), RoundedCornerShape(8.dp))
                        .padding(10.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(deploy.projectName, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text(deploy.platform, color = Color(0xFF8A919D), fontSize = 10.sp)
                        if (deploy.status == "deployed") {
                            Text(deploy.url, color = Color(0xFF9FCAFF), fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                        }
                    }
                    Box(
                        modifier = Modifier
                            .background(if (deploy.status == "deployed") Color(0xFF22C55E) else Color(0xFFEAB308), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(deploy.status, color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun FullSettingsView(viewModel: IdeViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text("PREFERÊNCIAS DO DROIDIDE", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Editor Visual Monaco (VS Code)", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text("Usa motor Monaco Web para codificação avançada.", color = Color(0xFF8A919D), fontSize = 11.sp)
            }
            Switch(
                checked = viewModel.useMonacoEditor,
                onCheckedChange = { viewModel.toggleEditorEngine(it) },
                colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF007ACC))
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text("Tamanho da fonte do editor: ${viewModel.editorFontSize}pt", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Slider(
            value = viewModel.editorFontSize.toFloat(),
            onValueChange = { viewModel.updateFontSize(it.toInt()) },
            valueRange = 10f..22f,
            steps = 12,
            colors = SliderDefaults.colors(thumbColor = Color(0xFF007ACC), activeTrackColor = Color(0xFF007ACC))
        )

        Spacer(modifier = Modifier.height(20.dp))

        Text("MODELO INTELIGENTE GEMINI", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))

        val models = listOf(
            "gemini-2.5-flash" to "Gemini 2.5 Flash",
            "gemini-1.5-flash" to "Gemini 1.5 Flash",
            "gemini-1.5-pro" to "Gemini 1.5 Pro"
        )
        models.forEach { (id, label) ->
            val isSelected = viewModel.aiModel == id
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp)
                    .background(Color(0xFF191C22), RoundedCornerShape(8.dp))
                    .border(1.dp, if (isSelected) Color(0xFF007ACC) else Color(0xFF32353C), RoundedCornerShape(8.dp))
                    .clickable { viewModel.updateAiModel(id) }
                    .padding(10.dp)
            ) {
                RadioButton(
                    selected = isSelected,
                    onClick = { viewModel.updateAiModel(id) },
                    colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF007ACC))
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(label, color = if (isSelected) Color.White else Color(0xFF8A919D), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text("REGISTRAR GEMINI API KEY", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(4.dp))
        Text("Gerencie e registre sua chave de uso localmente.", color = Color(0xFF8A919D), fontSize = 11.sp)
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = viewModel.inputApiKey,
            onValueChange = { viewModel.updateApiKey(it) },
            placeholder = { Text("Insira sua Gemini API Key...", fontSize = 12.sp, color = Color(0xFF8A919D)) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = Color(0xFF007ACC)
            ),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
        )
    }
}
