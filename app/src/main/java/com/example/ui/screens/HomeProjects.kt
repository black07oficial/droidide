package com.example.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.database.ProjectEntity
import com.example.ui.IdeViewModel
import java.text.SimpleDateFormat
import java.util.*

data class StackOption(
    val id: String,
    val name: String,
    val description: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val accentColor: Color
)

fun detectProjectStack(path: String): String {
    val dir = java.io.File(path)
    if (!dir.exists() || !dir.isDirectory) return "Workspace"

    val stackIdFile = java.io.File(dir, ".droidide_stack")
    if (stackIdFile.exists()) {
        try {
            val id = stackIdFile.readText().trim()
            val def = com.example.ui.StackRegistry.findById(id)
            if (def != null) {
                return def.name
            }
        } catch (e: Exception) {
            // silent
        }
    }
    
    if (java.io.File(dir, "main.py").exists()) return "Python 3"
    if (java.io.File(dir, "index.js").exists()) return "Node.js Console"
    
    val indexHtml = java.io.File(dir, "index.html")
    if (indexHtml.exists()) {
        try {
            val content = indexHtml.readText()
            if (content.contains("react") || content.contains("ReactDOM")) {
                return "React + Vite"
            }
        } catch (e: Exception) {
            // silent catch
        }
        return "HTML / CSS / JS"
    }
    
    return "Workspace"
}

private fun formatRelativeTime(createdAt: Long): String {
    val diff = System.currentTimeMillis() - createdAt
    if (diff < 0) return "Recentemente"
    val sec = diff / 1000
    if (sec < 60) return "Agora mesmo"
    val min = sec / 60
    if (min < 60) return "Há $min ${if (min == 1L) "minuto" else "minutos"}"
    val hours = min / 60
    if (hours < 24) return "Há $hours ${if (hours == 1L) "hora" else "horas"}"
    val days = hours / 24
    if (days == 1L) return "Ontem"
    if (days < 7) return "Há $days dias"
    val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    return sdf.format(Date(createdAt))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeProjectsScreen(
    viewModel: IdeViewModel,
    projects: List<ProjectEntity>,
    onSelectProject: (ProjectEntity) -> Unit
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val searchFocusRequester = remember { FocusRequester() }

    // State bindings
    var showCreateDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showClearAllConfirm by remember { mutableStateOf(false) }
    var newProjectName by remember { mutableStateOf("") }
    var selectedStack by remember { mutableStateOf("html-css-js") }
    var searchFilter by remember { mutableStateOf("") }

    // SharedPreferences for persistent pinning list
    val sharedPre = remember { context.getSharedPreferences("droidide_pinned_projects", Context.MODE_PRIVATE) }
    var pinnedIds by remember {
        mutableStateOf(sharedPre.getStringSet("pinned_keys", emptySet()) ?: emptySet())
    }

    // Filtered and Sorted projects: pinned projects at the top, then sorted by timestamp desc
    val processedProjects = remember(projects, searchFilter, pinnedIds) {
        val filtered = if (searchFilter.isEmpty()) {
            projects
        } else {
            projects.filter { it.name.contains(searchFilter, ignoreCase = true) }
        }
        filtered.sortedWith(
            compareByDescending<ProjectEntity> { pinnedIds.contains(it.id.toString()) }
                .thenByDescending { it.createdAt }
        )
    }

    // Tailwind colors mapping
    val colorBackground = Color(0xFF10131A)
    val colorSurfaceLowest = Color(0xFF0B0E14)
    val colorSurfaceContainer = Color(0xFF1D2026)
    val colorSurfaceContainerHigh = Color(0xFF272A31)
    val colorSurfaceContainerHighest = Color(0xFF32353C)
    val colorOutlineVariant = Color(0xFF404751)
    val colorOnSurface = Color(0xFFE1E2EB)
    val colorOnSurfaceVariant = Color(0xFFC0C7D3)
    val colorPrimaryContainer = Color(0xFF007ACC)
    val colorOnPrimaryContainer = Color(0xFFFFFFFF)
    val colorPrimary = Color(0xFF9FCAFF)
    val colorTertiary = Color(0xFFFFB784)
    val colorError = Color(0xFFFFB4AB)

    val stackOptions = remember {
        com.example.ui.StackRegistry.all.map { def ->
            val accentColor = when (def.category) {
                com.example.ui.StackCategory.STATIC -> Color(0xFF6366F1)
                com.example.ui.StackCategory.FRONTEND -> Color(0xFF38BDF8)
                com.example.ui.StackCategory.FULLSTACK -> Color(0xFFF43F5E)
                com.example.ui.StackCategory.BACKEND -> Color(0xFF10B981)
                com.example.ui.StackCategory.MOBILE -> Color(0xFFEAB308)
                com.example.ui.StackCategory.DATA_SCIENCE -> Color(0xFFFFB784)
            }
            StackOption(
                id = def.id,
                name = def.name,
                description = def.description,
                icon = def.icon,
                accentColor = accentColor
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colorBackground)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Blueprint visual mesh background
        CanvasBackground()

        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // 1. TopAppBar (Header styled like HTML header)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .background(colorSurfaceLowest)
                    .drawBehind {
                        drawLine(
                            color = colorOutlineVariant,
                            start = Offset(0f, size.height),
                            end = Offset(size.width, size.height),
                            strokeWidth = 1.dp.toPx()
                        )
                    }
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = {
                        Toast.makeText(context, "DroidIDE v1.5 - Ambiente Híbrido de Desenvolvimento Mobile", Toast.LENGTH_LONG).show()
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = "Menu Info",
                        tint = colorOnSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Text(
                    text = "DroidIDE",
                    color = colorOnSurface,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp
                )

                IconButton(
                    onClick = {
                        searchFocusRequester.requestFocus()
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Focar busca",
                        tint = colorOnSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Scrollable Content
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(bottom = 84.dp)
            ) {
                // 2. Search Box Panel
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(colorSurfaceLowest)
                            .drawBehind {
                                drawLine(
                                    color = colorSurfaceContainerHighest.copy(alpha = 0.5f),
                                    start = Offset(0f, size.height),
                                    end = Offset(size.width, size.height),
                                        strokeWidth = 1.dp.toPx()
                                )
                            }
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        BasicTextField(
                            value = searchFilter,
                            onValueChange = { searchFilter = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(38.dp)
                                .focusRequester(searchFocusRequester),
                            textStyle = TextStyle(
                                color = colorOnSurface,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Normal
                            ),
                            singleLine = true,
                            decorationBox = { innerTextField ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(colorSurfaceContainer, RoundedCornerShape(4.dp))
                                        .border(1.dp, colorOutlineVariant, RoundedCornerShape(4.dp))
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = null,
                                        tint = if (searchFilter.isNotEmpty()) colorPrimary else colorOnSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Box(modifier = Modifier.weight(1f)) {
                                        if (searchFilter.isEmpty()) {
                                            Text(
                                                text = "Buscar projetos, arquivos ou comandos...",
                                                color = colorOnSurfaceVariant.copy(alpha = 0.5f),
                                                fontSize = 12.sp,
                                                fontFamily = FontFamily.SansSerif
                                            )
                                        }
                                        innerTextField()
                                    }
                                    if (searchFilter.isNotEmpty()) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Limpar texto",
                                            tint = colorOnSurfaceVariant,
                                            modifier = Modifier
                                                .size(16.dp)
                                                .clickable { searchFilter = "" }
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                    }
                                    // Keyboard Shortcut badges: Cmd + P style
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .background(colorSurfaceContainerHigh, RoundedCornerShape(2.dp))
                                                .border(1.dp, colorOutlineVariant.copy(alpha = 0.3f), RoundedCornerShape(2.dp))
                                                .padding(horizontal = 4.dp, vertical = 1.dp)
                                        ) {
                                            Text("⌘", color = colorOnSurfaceVariant, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                                        }
                                        Box(
                                            modifier = Modifier
                                                .background(colorSurfaceContainerHigh, RoundedCornerShape(2.dp))
                                                .border(1.dp, colorOutlineVariant.copy(alpha = 0.3f), RoundedCornerShape(2.dp))
                                                .padding(horizontal = 4.dp, vertical = 1.dp)
                                        ) {
                                            Text("P", color = colorOnSurfaceVariant, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                                        }
                                    }
                                }
                            }
                        )
                    }
                }

                // 3. Section: Modelos (Templates)
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "MODELOS",
                        color = colorOnSurfaceVariant,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .drawBehind {
                                drawLine(
                                    color = colorSurfaceContainerHighest.copy(alpha = 0.5f),
                                    start = Offset(0f, 0f),
                                    end = Offset(size.width, 0f),
                                    strokeWidth = 1.dp.toPx()
                                )
                                drawLine(
                                    color = colorSurfaceContainerHighest.copy(alpha = 0.5f),
                                    start = Offset(0f, size.height),
                                    end = Offset(size.width, size.height),
                                    strokeWidth = 1.dp.toPx()
                                )
                            }
                    ) {
                        // Four modern templates
                        stackOptions.forEach { option ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedStack = option.id
                                        newProjectName = ""
                                        showCreateDialog = true
                                    }
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = option.icon,
                                        contentDescription = option.name,
                                        tint = option.accentColor,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = option.name,
                                        color = colorOnSurface,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        selectedStack = option.id
                                        newProjectName = ""
                                        showCreateDialog = true
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "Criar com ${option.name}",
                                        tint = colorOnSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            if (option != stackOptions.last()) {
                                Divider(
                                    color = colorSurfaceContainerHighest.copy(alpha = 0.2f),
                                    thickness = 1.dp,
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )
                            }
                        }
                    }
                }

                // 4. Section: Recentes
                item {
                    Spacer(modifier = Modifier.height(20.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "RECENTES",
                            color = colorOnSurfaceVariant,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp
                        )

                        if (projects.isNotEmpty()) {
                            Text(
                                text = "Limpar",
                                color = colorPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier
                                    .clickable { showClearAllConfirm = true }
                                    .padding(vertical = 4.dp, horizontal = 8.dp)
                            )
                        }
                    }
                }

                if (processedProjects.isEmpty()) {
                    item {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp, horizontal = 32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FolderOpen,
                                contentDescription = "Sem projetos",
                                tint = colorOnSurfaceVariant.copy(alpha = 0.3f),
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = if (searchFilter.isEmpty()) "Nenhum workspace localizado" else "Sem correspondências",
                                color = colorOnSurface,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (searchFilter.isEmpty()) "Selecione um modelo acima ou use o FAB de novo projeto para iniciar." else "Nenhum termo correspondente encontrado na busca.",
                                color = colorOnSurfaceVariant.copy(alpha = 0.6f),
                                fontSize = 11.sp,
                                textAlign = TextAlign.Center,
                                lineHeight = 15.sp,
                                modifier = Modifier.widthIn(max = 240.dp)
                            )
                        }
                    }
                } else {
                    items(processedProjects, key = { it.id }) { project ->
                        val isPinned = pinnedIds.contains(project.id.toString())
                        val relativeText = formatRelativeTime(project.createdAt)
                        val detected = detectProjectStack(project.path)

                        val (accentColor, projectIcon) = when (detected) {
                            "React + Tailwind" -> Color(0xFF38BDF8) to Icons.Default.Dashboard
                            "Python Script" -> Color(0xFFFFB784) to Icons.Default.Code
                            "Node.js Console" -> Color(0xFF10B981) to Icons.Default.Terminal
                            else -> Color(0xFF6366F1) to Icons.Default.Article
                        }

                        var showRowDeleteConfirm by remember { mutableStateOf(false) }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectProject(project) }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = projectIcon,
                                        contentDescription = "Project logo",
                                        tint = accentColor,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = project.name,
                                            color = colorOnSurface,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "$detected • $relativeText",
                                            color = colorOnSurfaceVariant.copy(alpha = 0.7f),
                                            fontSize = 10.sp
                                        )
                                    }
                                }

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Persistent Pin
                                    IconButton(
                                        onClick = {
                                            val updated = if (isPinned) {
                                                pinnedIds - project.id.toString()
                                            } else {
                                                pinnedIds + project.id.toString()
                                            }
                                            pinnedIds = updated
                                            sharedPre.edit().putStringSet("pinned_keys", updated).apply()
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                                            contentDescription = "Fixar",
                                            tint = if (isPinned) Color(0xFFF59E0B) else colorOnSurfaceVariant.copy(alpha = 0.5f),
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }

                                    // Delete
                                    IconButton(
                                        onClick = { showRowDeleteConfirm = true },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Remover",
                                            tint = colorOnSurfaceVariant.copy(alpha = 0.5f),
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                }
                            }

                            Divider(
                                color = colorSurfaceContainerHighest.copy(alpha = 0.2f),
                                thickness = 1.dp,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )

                            if (showRowDeleteConfirm) {
                                AlertDialog(
                                    onDismissRequest = { showRowDeleteConfirm = false },
                                    title = {
                                        Text(
                                            "Excluir Workspace?",
                                            color = Color.White,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    },
                                    text = {
                                        Text(
                                            "Esta operação removerá recursivamente todos os arquivos locais no diretório de '${project.name}'.",
                                            color = colorOnSurfaceVariant,
                                            fontSize = 11.sp,
                                            lineHeight = 15.sp
                                        )
                                    },
                                    confirmButton = {
                                        Button(
                                            onClick = {
                                                viewModel.deleteProject(project)
                                                showRowDeleteConfirm = false
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = colorError),
                                            shape = RoundedCornerShape(4.dp),
                                            modifier = Modifier.height(34.dp),
                                            contentPadding = PaddingValues(horizontal = 12.dp)
                                        ) {
                                            Text("Excluir", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                        }
                                    },
                                    dismissButton = {
                                        TextButton(onClick = { showRowDeleteConfirm = false }) {
                                            Text("Cancelar", color = colorOnSurfaceVariant, fontSize = 11.sp)
                                        }
                                    },
                                    containerColor = colorSurfaceContainer,
                                    shape = RoundedCornerShape(8.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // 5. FAB "Novo Projeto" - styled as pill button at bottom right
        Button(
            onClick = {
                selectedStack = "html-css-js"
                newProjectName = ""
                showCreateDialog = true
            },
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(containerColor = colorPrimaryContainer),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 80.dp, end = 16.dp)
                .height(40.dp)
                .border(1.dp, colorOutlineVariant, RoundedCornerShape(18.dp)),
            contentPadding = PaddingValues(horizontal = 14.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                tint = colorOnPrimaryContainer,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "NOVO PROJETO",
                color = colorOnPrimaryContainer,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
        }

        // 6. Bottom Navigation Bar matching HTML Precisely
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(60.dp)
                .background(colorSurfaceLowest)
                .drawBehind {
                    drawLine(
                        color = colorOutlineVariant,
                        start = Offset(0f, 0f),
                        end = Offset(size.width, 0f),
                        strokeWidth = 1.dp.toPx()
                    )
                }
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Tab 1: Explorer (Active)
            Column(
                modifier = Modifier
                    .width(76.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(colorPrimaryContainer)
                    .clickable { /* Already on project list workspace selection */ }
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.FolderOpen,
                    contentDescription = "Explorer",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "Explorer",
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            // Tab 2: Terminal
            Column(
                modifier = Modifier
                    .width(76.dp)
                    .clickable {
                        Toast.makeText(context, "Selecione ou crie um projeto primeiro para executar o Terminal local.", Toast.LENGTH_SHORT).show()
                    }
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Terminal,
                    contentDescription = "Terminal",
                    tint = colorOnSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "Terminal",
                    color = colorOnSurfaceVariant,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            // Tab 3: Debug
            Column(
                modifier = Modifier
                    .width(76.dp)
                    .clickable {
                        Toast.makeText(context, "Selecione ou crie um projeto primeiro para executar ferramentas de Depuração.", Toast.LENGTH_SHORT).show()
                    }
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.BugReport,
                    contentDescription = "Debug",
                    tint = colorOnSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "Debug",
                    color = colorOnSurfaceVariant,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            // Tab 4: Settings (Active Dialog Popup)
            Column(
                modifier = Modifier
                    .width(76.dp)
                    .clickable { showSettingsDialog = true }
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = colorOnSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "Settings",
                    color = colorOnSurfaceVariant,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // 7. Dialog: Configurar Workspace
        if (showCreateDialog) {
            AlertDialog(
                onDismissRequest = { showCreateDialog = false },
                title = {
                    Text(
                        text = "Configurar Workspace",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Defina o nome do seu projeto e selecione a stack inicial:",
                            color = colorOnSurfaceVariant,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )

                        BasicTextField(
                            value = newProjectName,
                            onValueChange = { newProjectName = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(38.dp),
                            textStyle = TextStyle(
                                color = Color.White,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Normal
                            ),
                            singleLine = true,
                            decorationBox = { innerTextField ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(colorBackground, RoundedCornerShape(4.dp))
                                        .border(1.dp, colorOutlineVariant, RoundedCornerShape(4.dp))
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    if (newProjectName.isEmpty()) {
                                        Text(
                                            text = "Nome do Projeto (Ex: WebScraper)",
                                            color = colorOnSurfaceVariant.copy(alpha = 0.5f),
                                            fontSize = 12.sp,
                                            fontFamily = FontFamily.SansSerif
                                        )
                                    }
                                    innerTextField()
                                }
                            }
                        )

                        Text(
                            text = "SELECIONE A STACK",
                            color = colorOnSurfaceVariant,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )

                        Column(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 240.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            stackOptions.forEach { opt ->
                                val isSelected = selectedStack == opt.id
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isSelected) colorSurfaceContainerHigh else colorBackground)
                                        .border(
                                            width = 1.dp,
                                            color = if (isSelected) opt.accentColor else colorOutlineVariant,
                                            shape = RoundedCornerShape(6.dp)
                                        )
                                        .clickable { selectedStack = opt.id }
                                        .padding(8.dp)
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .size(28.dp)
                                            .background(
                                                color = if (isSelected) opt.accentColor.copy(alpha = 0.15f) else colorSurfaceContainer,
                                                shape = RoundedCornerShape(4.dp)
                                            )
                                    ) {
                                        Icon(
                                            imageVector = opt.icon,
                                            contentDescription = null,
                                            tint = if (isSelected) opt.accentColor else colorOnSurfaceVariant,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(10.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = opt.name,
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = opt.description,
                                            color = colorOnSurfaceVariant.copy(alpha = 0.7f),
                                            fontSize = 9.sp,
                                            lineHeight = 12.sp
                                        )

                                        val def = com.example.ui.StackRegistry.findById(opt.id)
                                        if (def != null) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                modifier = Modifier.padding(top = 2.dp)
                                            ) {
                                                Text(
                                                    text = def.category.name,
                                                    color = opt.accentColor,
                                                    fontSize = 7.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier
                                                        .background(opt.accentColor.copy(alpha = 0.1f), RoundedCornerShape(2.dp))
                                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                                )

                                                val internetText = when (def.requiresInternet) {
                                                    com.example.ui.InternetRequirement.NONE -> "Offline"
                                                    com.example.ui.InternetRequirement.FIRST_TIME -> "Setup Online"
                                                    com.example.ui.InternetRequirement.ALWAYS -> "Sempre Online"
                                                }
                                                val internetColor = when (def.requiresInternet) {
                                                    com.example.ui.InternetRequirement.NONE -> Color(0xFF10B981)
                                                    com.example.ui.InternetRequirement.FIRST_TIME -> Color(0xFFEAB308)
                                                    com.example.ui.InternetRequirement.ALWAYS -> Color(0xFFF43F5E)
                                                }
                                                Text(
                                                    text = internetText,
                                                    color = internetColor,
                                                    fontSize = 7.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier
                                                        .background(internetColor.copy(alpha = 0.1f), RoundedCornerShape(2.dp))
                                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                    }

                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Selected",
                                            tint = opt.accentColor,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (newProjectName.trim().isNotEmpty()) {
                                viewModel.createProject(newProjectName, selectedStack)
                                newProjectName = ""
                                showCreateDialog = false
                            } else {
                                Toast.makeText(context, "Insira um nome válido para o workspace!", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = colorPrimaryContainer),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.height(34.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp)
                    ) {
                        Text("Criar Workspace", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCreateDialog = false }) {
                        Text("Cancelar", color = colorOnSurfaceVariant, fontSize = 11.sp)
                    }
                },
                containerColor = colorSurfaceContainer,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.border(1.dp, colorOutlineVariant, RoundedCornerShape(8.dp))
            )
        }

        // 8. Dialog: Limpar Todos os Workspaces
        if (showClearAllConfirm) {
            AlertDialog(
                onDismissRequest = { showClearAllConfirm = false },
                title = {
                    Text(
                        "Limpar Todos os Workspaces?",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Text(
                        "Esta operação é irreversível e excluirá permanentemente todos os seus ${projects.size} workspaces locais e seus arquivos correspondentes do armazenamento offline.",
                        color = colorOnSurfaceVariant,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            projects.forEach { viewModel.deleteProject(it) }
                            showClearAllConfirm = false
                            Toast.makeText(context, "Lista de workspaces limpa com sucesso!", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = colorError),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.height(34.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp)
                    ) {
                        Text("Limpar Tudo", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showClearAllConfirm = false }) {
                        Text("Cancelar", color = colorOnSurfaceVariant, fontSize = 11.sp)
                    }
                },
                containerColor = colorSurfaceContainer,
                shape = RoundedCornerShape(8.dp)
            )
        }

        // 9. Dialog Settings (Configurações Gerais DroidIDE Pop-up)
        if (showSettingsDialog) {
            AlertDialog(
                onDismissRequest = { showSettingsDialog = false },
                title = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Configurações Globais",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(
                            onClick = { showSettingsDialog = false },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Fecar",
                                tint = colorOnSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                },
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Gerencie chaves e mecanismos globais aplicados à sua IDE.",
                            color = colorOnSurfaceVariant.copy(alpha = 0.7f),
                            fontSize = 10.sp
                        )

                        // 9.1 API Key Section
                        Text("Gemini API Key", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        BasicTextField(
                            value = viewModel.inputApiKey,
                            onValueChange = { viewModel.updateApiKey(it) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(38.dp),
                            textStyle = TextStyle(
                                color = Color.White,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Normal
                            ),
                            singleLine = true,
                            decorationBox = { innerTextField ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(colorBackground, RoundedCornerShape(4.dp))
                                        .border(1.dp, colorOutlineVariant, RoundedCornerShape(4.dp))
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    if (viewModel.inputApiKey.isEmpty()) {
                                        Text(
                                            text = "Cole sua Gemini API Key local...",
                                            color = colorOnSurfaceVariant.copy(alpha = 0.4f),
                                            fontSize = 11.sp,
                                            fontFamily = FontFamily.SansSerif
                                        )
                                    }
                                    innerTextField()
                                }
                            }
                        )

                         // 9.2 AI Model Choose
                        Text("Modelo de IA Ativo", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        val models = listOf(
                            "gemini-2.5-flash" to "2.5 Flash",
                            "gemini-1.5-flash" to "1.5 Flash",
                            "gemini-1.5-pro" to "1.5 Pro"
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            models.forEach { (modelId, label) ->
                                val selected = viewModel.aiModel == modelId
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (selected) colorPrimaryContainer else colorSurfaceContainerHigh)
                                        .border(1.dp, if (selected) colorPrimary else colorOutlineVariant, RoundedCornerShape(6.dp))
                                        .clickable { viewModel.updateAiModel(modelId) }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        color = if (selected) Color.White else colorOnSurfaceVariant,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        // 9.3 Editor Settings
                        Text("Configuração do Editor", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(colorBackground, RoundedCornerShape(6.dp))
                                .border(1.dp, colorOutlineVariant, RoundedCornerShape(6.dp))
                                .clickable { viewModel.toggleEditorEngine(!viewModel.useMonacoEditor) }
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Usar Editor Monaco", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                Text("Engenharia web rica com colorização sintática.", color = colorOnSurfaceVariant.copy(alpha = 0.7f), fontSize = 9.sp)
                            }
                            Switch(
                                checked = viewModel.useMonacoEditor,
                                onCheckedChange = { viewModel.toggleEditorEngine(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = colorPrimary,
                                    checkedTrackColor = colorPrimaryContainer,
                                    uncheckedThumbColor = colorOnSurfaceVariant,
                                    uncheckedTrackColor = colorBackground
                                )
                            )
                        }

                        // 9.4 Editor Font Size
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(colorBackground, RoundedCornerShape(6.dp))
                                .border(1.dp, colorOutlineVariant, RoundedCornerShape(6.dp))
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Tamanho da Fonte", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                Text("Tamanho da fonte ativo: ${viewModel.editorFontSize}px", color = colorOnSurfaceVariant.copy(alpha = 0.7f), fontSize = 9.sp)
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = { if (viewModel.editorFontSize > 8) viewModel.updateFontSize(viewModel.editorFontSize - 1) },
                                    modifier = Modifier
                                        .size(28.dp)
                                        .background(colorSurfaceContainerHigh, RoundedCornerShape(4.dp))
                                ) {
                                    Text("-", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                }
                                Box(
                                    modifier = Modifier.width(28.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = viewModel.editorFontSize.toString(),
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                                IconButton(
                                    onClick = { if (viewModel.editorFontSize < 32) viewModel.updateFontSize(viewModel.editorFontSize + 1) },
                                    modifier = Modifier
                                        .size(28.dp)
                                        .background(colorSurfaceContainerHigh, RoundedCornerShape(4.dp))
                                ) {
                                    Text("+", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { showSettingsDialog = false },
                        colors = ButtonDefaults.buttonColors(containerColor = colorPrimaryContainer),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.height(34.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp)
                    ) {
                        Text("Fechar", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                },
                containerColor = colorSurfaceContainer,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.border(1.dp, colorOutlineVariant, RoundedCornerShape(8.dp))
            )
        }
    }
}

@Composable
fun CanvasBackground() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                val step = 45.dp.toPx()
                if (step <= 1f) return@drawBehind // Safety exit to prevent infinite loops
                val lineThickness = 1.dp.toPx()
                val lineColor = Color(0xFF1E293B).copy(alpha = 0.08f)
                
                // Draw horizontal grids
                var y = 0f
                while (y < size.height) {
                    drawLine(
                         color = lineColor,
                         start = Offset(0f, y),
                         end = Offset(size.width, y),
                         strokeWidth = lineThickness
                    )
                    y += step
                }

                // Draw vertical grids
                var x = 0f
                while (x < size.width) {
                     drawLine(
                         color = lineColor,
                         start = Offset(x, 0f),
                         end = Offset(x, size.height),
                         strokeWidth = lineThickness
                     )
                     x += step
                }
            }
    )
}
