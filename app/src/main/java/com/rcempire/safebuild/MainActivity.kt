package com.rcempire.safebuild

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.util.zip.ZipInputStream

private val Navy = Color(0xFF0B1020)
private val Panel = Color(0xFF151E31)
private val Cyan = Color(0xFF00C7F2)
private val Blue = Color(0xFF1677E8)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { SafeBuildApp() }
    }
}

data class Finding(val severity: String, val title: String, val file: String, val detail: String, val fix: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SafeBuildApp() {
    val context = LocalContext.current
    var dark by remember { mutableStateOf(true) }
    var page by remember { mutableStateOf("Home") }
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var selectedName by remember { mutableStateOf("") }
    var scanning by remember { mutableStateOf(false) }
    var findings by remember { mutableStateOf<List<Finding>>(emptyList()) }
    var status by remember { mutableStateOf("Ready for a local scan") }
    var targetUrl by remember { mutableStateOf("") }
    var showAbout by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            selectedUri = uri
            selectedName = uri.lastPathSegment?.substringAfterLast('/') ?: "Selected project"
            status = "Selected: $selectedName"
            findings = emptyList()
        }
    }

    MaterialTheme(colorScheme = if (dark) darkColorScheme(
        primary = Cyan, secondary = Blue, background = Navy, surface = Panel,
        onBackground = Color(0xFFF4F7FF), onSurface = Color(0xFFF4F7FF)
    ) else lightColorScheme(primary = Blue, secondary = Cyan)) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("SafeBuild", fontWeight = FontWeight.ExtraBold)
                            Text("RC EMPIRE  •  LOCAL SECURITY", style = MaterialTheme.typography.labelSmall)
                        }
                    },
                    actions = {
                        IconButton(onClick = { dark = !dark }) {
                            Icon(if (dark) Icons.Default.LightMode else Icons.Default.DarkMode, "Switch theme")
                        }
                        IconButton(onClick = { page = "About & Support" }) {
                            Icon(Icons.Default.Info, "About and support")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
                )
            },
            bottomBar = {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    listOf("Home", "Scanner", "Reports", "Settings").forEach { item ->
                        NavigationBarItem(
                            selected = page == item,
                            onClick = { page = item },
                            icon = {
                                Icon(when(item) {
                                    "Scanner" -> Icons.Default.Shield
                                    "Reports" -> Icons.Default.Assessment
                                    "Settings" -> Icons.Default.Settings
                                    else -> Icons.Default.Dashboard
                                }, item)
                            },
                            label = { Text(item) }
                        )
                    }
                }
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(top = 18.dp, bottom = 28.dp)
            ) {
                item {
                    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface) {
                        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Surface(shape = RoundedCornerShape(16.dp), color = Blue.copy(alpha = .18f)) {
                                    Icon(Icons.Default.Security, null, tint = Cyan, modifier = Modifier.padding(14.dp).size(36.dp))
                                }
                                Column(Modifier.weight(1f)) {
                                    Text("Build. Scan. Secure.", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                                    Text("Local-first security assistant", color = MaterialTheme.colorScheme.onSurface.copy(alpha = .7f))
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                AssistChip(onClick = {}, label = { Text("Offline file scan") }, leadingIcon = { Icon(Icons.Default.PhoneAndroid, null) })
                                AssistChip(onClick = {}, label = { Text("Privacy first") }, leadingIcon = { Icon(Icons.Default.Lock, null) })
                            }
                            Text(status, style = MaterialTheme.typography.bodyMedium, color = Cyan)
                        }
                    }
                }
                item {
                    Text("Security workspace", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Local project scan", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("Choose a ZIP or source file. Files are read on this device; findings are heuristic and need review.")
                            OutlinedButton(onClick = { picker.launch(arrayOf("application/zip", "text/*", "application/javascript", "application/json", "application/octet-stream")) }, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Default.FolderOpen, null)
                                Spacer(Modifier.width(8.dp))
                                Text(if (selectedName.isBlank()) "Choose project file" else selectedName)
                            }
                            Button(
                                onClick = {
                                    val uri = selectedUri
                                    if (uri == null) { status = "Choose a ZIP or source file first"; return@Button }
                                    scanning = true
                                    status = "Scanning locally…"
                                    (context as? ComponentActivity)?.lifecycleScope?.launch {
                                        val result = withContext(Dispatchers.IO) {
                                            runCatching { scanDocument(context, uri, selectedName) }
                                        }
                                        findings = result.getOrElse { listOf(Finding("Info", "Could not read file", selectedName, it.message ?: "File access failed", "Try a supported ZIP or text source file.")) }
                                        scanning = false
                                        status = "Scan complete • ${findings.size} finding(s)"
                                    }
                                },
                                enabled = !scanning,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                if (scanning) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                else Icon(Icons.Default.Shield, null)
                                Spacer(Modifier.width(8.dp))
                                Text(if (scanning) "Scanning…" else "Scan project")
                            }
                        }
                    }
                }
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Live website header audit", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            OutlinedTextField(value = targetUrl, onValueChange = { targetUrl = it }, modifier = Modifier.fillMaxWidth(), label = { Text("https://example.com") }, singleLine = true)
                            Text("Remote audit requires an internet connection and only checks a small set of HTTP headers.", style = MaterialTheme.typography.bodySmall)
                            Button(onClick = { status = "Live URL audit is a planned next module; local file scanning works independently." }, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Default.Language, null); Spacer(Modifier.width(8.dp)); Text("Audit URL")
                            }
                        }
                    }
                }
                item { Text("Findings", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
                if (findings.isEmpty()) {
                    item {
                        Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(16.dp)) {
                            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CheckCircleOutline, null, tint = Cyan)
                                Spacer(Modifier.width(10.dp))
                                Text("No scan results yet. Select a project and run a scan.")
                            }
                        }
                    }
                } else {
                    items(findings) { finding ->
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(finding.severity.uppercase(), color = if (finding.severity == "High") Color(0xFFFF6B6B) else Cyan, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                    Spacer(Modifier.width(8.dp))
                                    Text(finding.title, fontWeight = FontWeight.Bold)
                                }
                                Text(finding.file, style = MaterialTheme.typography.labelSmall)
                                Text(finding.detail)
                                Text("Suggested fix: ${finding.fix}", color = MaterialTheme.colorScheme.onSurface.copy(alpha = .75f))
                            }
                        }
                    }
                }
                if (page == "Settings") item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Appearance", fontWeight = FontWeight.Bold)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(if (dark) "Night mode" else "Day mode", modifier = Modifier.weight(1f))
                                Switch(checked = dark, onCheckedChange = { dark = it })
                            }
                            Text("SafeBuild v1.0 • RC Empire", color = Cyan)
                        }
                    }
                }
                if (page == "Reports") item {
                    Button(onClick = {
                        val report = buildString {
                            appendLine("SafeBuild v1.0 — RC Empire")
                            appendLine("Local heuristic scan report")
                            appendLine("File: $selectedName")
                            appendLine("Findings: ${findings.size}")
                            findings.forEach { appendLine("\n[${it.severity}] ${it.title}\nFile: ${it.file}\nIssue: ${it.detail}\nSuggested fix: ${it.fix}") }
                            appendLine("\nSupport: support.rcempire@gmail.com")
                            appendLine("YouTube: https://youtube.com/@RCRAJAGAMER2.0")
                            appendLine("This report is not a certification or guarantee of security.")
                        }
                        val intent = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_SUBJECT, "SafeBuild security report"); putExtra(Intent.EXTRA_TEXT, report) }
                        context.startActivity(Intent.createChooser(intent, "Share security report"))
                    }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Share, null); Spacer(Modifier.width(8.dp)); Text("Export / share report") }
                }
                if (page == "About & Support") item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("About & Support", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            Text("SafeBuild v1.0 — Local Autonomous AI Security Auditor & Auto-Healer")
                            Text("Developed & Maintained by RC Empire")
                            Text("Support: support.rcempire@gmail.com", modifier = Modifier.clickable {
                                val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:support.rcempire@gmail.com?subject=SafeBuild%20Support"))
                                context.startActivity(intent)
                            }, color = Cyan)
                            Text("YouTube: RC RAJA GAMER 2.0", modifier = Modifier.clickable {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://youtube.com/@RCRAJAGAMER2.0")))
                            }, color = Cyan)
                            Text("Help desk: Email support for technical assistance or feature requests.")
                        }
                    }
                }
            }
        }
    }
}

private fun scanDocument(context: android.content.Context, uri: Uri, name: String): List<Finding> {
    val collected = mutableListOf<Pair<String, String>>()
    val input = context.contentResolver.openInputStream(uri) ?: error("No permission to open this file")
    input.use { stream ->
        if (name.endsWith(".zip", ignoreCase = true) || context.contentResolver.getType(uri) == "application/zip") {
            ZipInputStream(BufferedInputStream(stream)).use { zip ->
                var count = 0
                var entry = zip.nextEntry
                while (entry != null && count++ < 500) {
                    val safePath = entry.name.replace('\\', '/')
                    if (!entry.isDirectory && !safePath.startsWith("/") && !safePath.split("/").contains("..") &&
                        safePath.substringAfterLast('.', "").lowercase() in setOf("html", "htm", "js", "jsx", "ts", "tsx", "php", "py", "json", "css", "xml", "yml", "yaml")) {
                        val bytes = zip.readNBytes(512_001)
                        if (bytes.size <= 512_000) collected += safePath to bytes.toString(Charsets.UTF_8)
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        } else {
            val bytes = stream.readNBytes(512_001)
            if (bytes.size <= 512_000) collected += name to bytes.toString(Charsets.UTF_8)
            else error("File exceeds the 512 KB text scan limit")
        }
    }
    val results = mutableListOf<Finding>()
    collected.forEach { (file, text) ->
        if (Regex("\\beval\\s*\\(").containsMatchIn(text)) results += Finding("High", "Dynamic code evaluation", file, "eval() was detected; attacker-controlled data reaching it may execute code.", "Replace eval with an explicit allow-list or safe parser.")
        if (Regex("innerHTML\\s*=").containsMatchIn(text)) results += Finding("Medium", "Potential unsafe DOM HTML assignment", file, "innerHTML can cause DOM XSS if assigned untrusted input.", "Prefer textContent or a trusted context-aware sanitizer.")
        if (Regex("Access-Control-Allow-Origin\\s*[:=]\\s*['\"]\\*['\"]", RegexOption.IGNORE_CASE).containsMatchIn(text)) results += Finding("Medium", "Wildcard CORS policy", file, "Wildcard CORS may expose browser-accessible APIs.", "Restrict allowed origins to the specific trusted sites.")
        if (Regex("\\b(?:api[_-]?key|secret|password|token)\\s*[:=]\\s*['\"][^'\"]{8,}['\"]", RegexOption.IGNORE_CASE).containsMatchIn(text)) results += Finding("Info", "Possible hardcoded development value", file, "This is advisory only; local values are not blocked or removed.", "For production, consider environment variables and secret rotation when appropriate.")
        if (Regex("<form\\b", RegexOption.IGNORE_CASE).containsMatchIn(text) && !Regex("csrf|xsrf", RegexOption.IGNORE_CASE).containsMatchIn(text)) results += Finding("Low", "Review CSRF protection", file, "A form was found; static scanning cannot determine whether CSRF applies.", "For cookie-authenticated state-changing requests, use framework CSRF protection.")
    }
    if (collected.isEmpty()) results += Finding("Info", "No supported text files found", name, "The scanner found no supported, readable text source files.", "Try a ZIP containing HTML, JS, TS, PHP, Python, JSON, CSS, XML, or YAML.")
    return results.distinctBy { "${it.file}:${it.title}" }
}
