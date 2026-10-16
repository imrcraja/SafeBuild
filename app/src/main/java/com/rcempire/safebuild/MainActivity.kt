package com.rcempire.safebuild

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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

private val Navy = Color(0xFF161922)
private val Panel = Color(0xFF1E222D)
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
    var hostOn by remember { mutableStateOf(context.getSharedPreferences("safebuild_host", android.content.Context.MODE_PRIVATE).getBoolean(LocalHostService.KEY_ENABLED, false)) }
    var dark by remember { mutableStateOf(true) }
    var page by remember { mutableStateOf("Home") }
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var selectedName by remember { mutableStateOf("") }
    var scanning by remember { mutableStateOf(false) }
    var findings by remember { mutableStateOf<List<Finding>>(emptyList()) }
    var status by remember { mutableStateOf("Ready") }
    var targetUrl by remember { mutableStateOf("") }
    var urlAuditing by remember { mutableStateOf(false) }
    val tutorialPrefs = remember { context.getSharedPreferences("safebuild_onboarding", android.content.Context.MODE_PRIVATE) }
    var showTutorial by remember { mutableStateOf(!tutorialPrefs.getBoolean("completed", false)) }
    var tutorialStep by remember { mutableIntStateOf(0) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            selectedUri = uri
            selectedName = uri.lastPathSegment?.substringAfterLast('/') ?: "Selected project"
            findings = emptyList()
            status = "Selected " + selectedName
            page = "Scanner"
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
                            Text(page.uppercase() + " • RC EMPIRE", style = MaterialTheme.typography.labelSmall)
                        }
                    },
                    navigationIcon = {
                        if (page != "Home") IconButton(onClick = { page = "Home" }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back to home")
                        }
                    },
                    actions = {
                        IconButton(onClick = { page = "Settings" }) { Icon(Icons.Default.Settings, contentDescription = "Settings") }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
                )
            },
            bottomBar = {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    listOf("Home", "Scanner", "URL Audit", "Hosting", "Reports").forEach { item ->
                        NavigationBarItem(
                            selected = page == item,
                            onClick = { page = item },
                            icon = {
                                Icon(when (item) {
                                    "Scanner" -> Icons.Default.Shield
                                    "URL Audit" -> Icons.Default.Language
                                    "Hosting" -> Icons.Default.Public
                                    "Reports" -> Icons.Default.Assessment
                                    else -> Icons.Default.Home
                                }, contentDescription = item)
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
                    if (showTutorial) TutorialDialog(step = tutorialStep, onStep = { tutorialStep = it }, onFinish = {
                        showTutorial = false
                        tutorialPrefs.edit().putBoolean("completed", true).apply()
                    })
                }
                item {
                    when (page) {
                        "Home" -> Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Icon(Icons.Default.Security, null, tint = Cyan, modifier = Modifier.size(42.dp))
                                    Text("Build. Scan. Secure.", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                                    Text("Simple security tools. Choose one task to get started.")
                                    Text(status, color = Cyan)
                                }
                            }
                            HomeAction("Scan a project", "Check a ZIP or source file", Icons.Default.FolderOpen) { page = "Scanner" }
                            HomeAction("Audit a website", "Check common HTTP security headers", Icons.Default.Language) { page = "URL Audit" }
                            HomeAction("Local web preview", if (hostOn) "Host marked running" else "Start a private preview server", Icons.Default.Public) { page = "Hosting" }
                            HomeAction("Reports & findings", findings.size.toString() + " finding(s) in latest scan", Icons.Default.Assessment) { page = "Reports" }
                            OutlinedButton(onClick = { page = "Settings" }, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Default.Settings, null); Spacer(Modifier.width(8.dp)); Text("Settings & support")
                            }
                        }
                        "Scanner" -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Project scanner", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            Text("Choose a ZIP or text source. SafeBuild checks common code patterns and does not execute your project.")
                            OutlinedButton(onClick = { picker.launch(arrayOf("application/zip", "text/*", "application/javascript", "application/json", "application/octet-stream")) }, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Default.FolderOpen, null); Spacer(Modifier.width(8.dp))
                                Text(if (selectedName.isBlank()) "Choose project file" else selectedName)
                            }
                            Button(enabled = !scanning, onClick = {
                                val uri = selectedUri
                                if (uri == null) { status = "Choose a ZIP or source file first"; return@Button }
                                scanning = true
                                status = "Scanning locally…"
                                (context as? ComponentActivity)?.lifecycleScope?.launch {
                                    val result = withContext(Dispatchers.IO) { runCatching { scanDocument(context, uri, selectedName) } }
                                    findings = result.getOrElse { listOf(Finding("Info", "Could not read file", selectedName, it.message ?: "File access failed", "Try a supported ZIP or text source file.")) }
                                    scanning = false
                                    status = "File scan complete • " + findings.size + " finding(s)"
                                }
                            }, modifier = Modifier.fillMaxWidth()) {
                                if (scanning) PulsingShield(Modifier.size(22.dp)) else Icon(Icons.Default.Shield, null)
                                Spacer(Modifier.width(8.dp)); Text(if (scanning) "Scanning…" else "Scan project")
                            }
                            Text("Supported ZIP contents: HTML, JS/TS, PHP, Python, JSON, CSS, XML and YAML.", style = MaterialTheme.typography.bodySmall)
                            OutlinedButton(onClick = { page = "Reports" }, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Default.Assessment, null); Spacer(Modifier.width(8.dp)); Text("View scan results")
                            }
                        }
                        "URL Audit" -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Website header audit", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            Text("Use a website you own or have permission to assess. This is a limited header check, not a penetration test.")
                            OutlinedTextField(value = targetUrl, onValueChange = { targetUrl = it }, modifier = Modifier.fillMaxWidth(), label = { Text("https://example.com") }, singleLine = true)
                            Button(enabled = !urlAuditing, onClick = {
                                val raw = targetUrl.trim()
                                val parsed = runCatching { Uri.parse(if (raw.startsWith("https://") || raw.startsWith("http://")) raw else "https://" + raw) }.getOrNull()
                                if (parsed == null || parsed.host.isNullOrBlank() || parsed.scheme !in setOf("https", "http")) {
                                    status = "Enter a valid website URL"
                                } else {
                                    urlAuditing = true
                                    status = "Checking response headers…"
                                    (context as? ComponentActivity)?.lifecycleScope?.launch {
                                        val result = withContext(Dispatchers.IO) { runCatching { auditUrl(parsed.toString()) } }
                                        findings = result.getOrElse { listOf(Finding("Info", "URL audit could not complete", parsed.host ?: raw, it.message ?: "Connection failed", "Check the URL and internet connection.")) }
                                        urlAuditing = false
                                        status = "URL audit complete • " + findings.size + " finding(s)"
                                    }
                                }
                            }, modifier = Modifier.fillMaxWidth()) {
                                if (urlAuditing) PulsingShield(Modifier.size(22.dp)) else Icon(Icons.Default.Language, null)
                                Spacer(Modifier.width(8.dp)); Text(if (urlAuditing) "Auditing…" else "Audit URL")
                            }
                            OutlinedButton(onClick = { page = "Reports" }, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Default.Assessment, null); Spacer(Modifier.width(8.dp)); Text("View audit results")
                            }
                        }
                        "Hosting" -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Local web preview", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            Text("Serves files from SafeBuild's private hosted-site folder on this device only. It does not create a public internet link.")
                            Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(16.dp)) {
                                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Public, null, tint = Cyan, modifier = Modifier.size(32.dp))
                                    Spacer(Modifier.width(12.dp))
                                    Column {
                                        Text(if (hostOn) "Host marked running" else "Host stopped", fontWeight = FontWeight.Bold)
                                        Text("http://127.0.0.1:8088/", color = Cyan)
                                    }
                                }
                            }
                            Button(onClick = {
                                val starting = !hostOn
                                val action = if (starting) LocalHostService.ACTION_START else LocalHostService.ACTION_STOP
                                val intent = Intent(context, LocalHostService::class.java).setAction(action)
                                runCatching {
                                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O && starting) context.startForegroundService(intent) else context.startService(intent)
                                    hostOn = starting
                                    status = if (starting) "Local host start requested" else "Local host stop requested"
                                }.onFailure { status = "Could not change host state: " + it.message }
                            }, modifier = Modifier.fillMaxWidth()) {
                                Icon(if (hostOn) Icons.Default.Stop else Icons.Default.PlayArrow, null)
                                Spacer(Modifier.width(8.dp)); Text(if (hostOn) "Stop local host" else "Start local host")
                            }
                            OutlinedButton(onClick = {
                                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("http://127.0.0.1:8088/"))) }
                                    .onFailure { status = "Could not open browser: " + it.message }
                            }, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Default.OpenInBrowser, null); Spacer(Modifier.width(8.dp)); Text("Open preview in browser")
                            }
                        }
                        "Reports" -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Reports & findings", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            Text(status, color = Cyan)
                            if (findings.isEmpty()) {
                                Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(16.dp)) {
                                    Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Icon(Icons.Default.Assessment, null, tint = Cyan, modifier = Modifier.size(32.dp))
                                        Text("No report yet", fontWeight = FontWeight.Bold)
                                        Text("Run a project scan or URL audit first. Both tools share results here.")
                                        Button(onClick = { page = "Scanner" }, modifier = Modifier.fillMaxWidth()) { Text("Scan a project") }
                                        OutlinedButton(onClick = { page = "URL Audit" }, modifier = Modifier.fillMaxWidth()) { Text("Audit a website") }
                                    }
                                }
                            } else {
                                Text(findings.size.toString() + " finding(s)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                findings.forEach { finding ->
                                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                                        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Text(finding.severity.uppercase() + " • " + finding.title, color = if (finding.severity == "High") Color(0xFFFF6B6B) else Cyan, fontWeight = FontWeight.Bold)
                                            Text(finding.file, style = MaterialTheme.typography.labelSmall)
                                            Text(finding.detail)
                                            Text("Suggested fix: " + finding.fix, style = MaterialTheme.typography.bodyMedium)
                                        }
                                    }
                                }
                                Button(onClick = {
                                    val report = buildString {
                                        appendLine("SafeBuild v1.0 — RC Empire")
                                        appendLine("Local heuristic security report")
                                        appendLine("Project: " + selectedName.ifBlank { "URL audit or selected source" })
                                        appendLine("Findings: " + findings.size)
                                        findings.forEach { appendLine("\n[" + it.severity + "] " + it.title + "\nFile/target: " + it.file + "\nIssue: " + it.detail + "\nSuggested fix: " + it.fix) }
                                        appendLine("\nThis report is not a certification or guarantee of security.")
                                    }
                                    val intent = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_SUBJECT, "SafeBuild security report"); putExtra(Intent.EXTRA_TEXT, report) }
                                    runCatching { context.startActivity(Intent.createChooser(intent, "Share security report")) }
                                        .onFailure { status = "Could not share report: " + it.message }
                                }, modifier = Modifier.fillMaxWidth()) {
                                    Icon(Icons.Default.Share, null); Spacer(Modifier.width(8.dp)); Text("Share report")
                                }
                            }
                        }
                        "Settings" -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Settings & support", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text("Appearance", fontWeight = FontWeight.Bold)
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(if (dark) "Dark theme" else "Light theme", modifier = Modifier.weight(1f))
                                        Switch(checked = dark, onCheckedChange = { dark = it })
                                    }
                                    HorizontalDivider()
                                    Text("SafeBuild v1.0 • Developed by RC Empire", fontWeight = FontWeight.Bold)
                                    Text("support.rcempire@gmail.com", color = Cyan, modifier = Modifier.clickable {
                                        runCatching { context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:support.rcempire@gmail.com?subject=SafeBuild%20Support"))) }
                                            .onFailure { status = "No email app found: " + it.message }
                                    })
                                    Text("YouTube: RC RAJA GAMER 2.0", color = Cyan, modifier = Modifier.clickable {
                                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://youtube.com/@RCRAJAGAMER2.0"))) }
                                            .onFailure { status = "Could not open YouTube: " + it.message }
                                    })
                                }
                            }
                            Text("Scans are heuristic. Review findings yourself; a clean scan does not prove a project is secure.", style = MaterialTheme.typography.bodySmall)
                            OutlinedButton(onClick = {
                                tutorialStep = 0
                                showTutorial = true
                                tutorialPrefs.edit().putBoolean("completed", false).apply()
                            }, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Default.HelpOutline, null); Spacer(Modifier.width(8.dp)); Text("Show quick-start guide")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeAction(title: String, detail: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(18.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = Cyan, modifier = Modifier.size(30.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, fontWeight = FontWeight.Bold)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .7f))
            }
            Icon(Icons.Default.ChevronRight, contentDescription = "Open " + title)
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


@Composable
private fun PulsingShield(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "security-pulse")
    val scale by transition.animateFloat(
        initialValue = 0.82f, targetValue = 1.12f,
        animationSpec = infiniteRepeatable(tween(850, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "shield-scale"
    )
    val alpha by transition.animateFloat(
        initialValue = 0.45f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(850), RepeatMode.Reverse),
        label = "shield-glow"
    )
    Icon(Icons.Default.Shield, contentDescription = "Scanning animation",
        modifier = modifier.scale(scale).alpha(alpha), tint = Cyan)
}

@Composable
private fun TutorialDialog(step: Int, onStep: (Int) -> Unit, onFinish: () -> Unit) {
    val context = LocalContext.current
    val transition = rememberInfiniteTransition(label = "tutorial-glow")
    val glow by transition.animateFloat(
        initialValue = 0.18f, targetValue = 0.5f,
        animationSpec = infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glass-glow"
    )
    val steps = listOf(
        Triple("Welcome to SafeBuild", "Your local-first security workspace by RC Empire. Your project files stay on your device during local scans.", Icons.Default.Security),
        Triple("Choose a project", "Pick a ZIP or supported source file. SafeBuild checks for common patterns and shows findings with suggested fixes.", Icons.Default.FolderOpen),
        Triple("Review every finding", "Static scans are hints, not a security guarantee. Check each result before changing code or deploying.", Icons.Default.FindInPage),
        Triple("Reports & support", "Share your report, switch Day/Night mode, and contact RC Empire whenever you need help.", Icons.Default.Assessment)
    )
    Dialog(onDismissRequest = onFinish, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            shape = RoundedCornerShape(30.dp),
            color = Color(0xEE111A2D),
            border = androidx.compose.foundation.BorderStroke(1.dp, Cyan.copy(alpha = glow)),
            shadowElevation = 24.dp
        ) {
            Column(
                modifier = Modifier.background(Color(0x3317C7F2), RoundedCornerShape(30.dp)).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = RoundedCornerShape(14.dp), color = Cyan.copy(alpha = .15f)) {
                        Icon(Icons.Default.Shield, null, tint = Cyan, modifier = Modifier.padding(12.dp).size(30.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("SAFEBUILD  •  RC EMPIRE", color = Cyan, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        Text("Quick start guide", color = Color.White.copy(alpha = .75f), style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = onFinish) { Text("Skip") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    repeat(steps.size) { i ->
                        Box(Modifier.weight(1f).height(4.dp).background(if (i <= step) Cyan else Color.White.copy(alpha = .16f), RoundedCornerShape(8.dp)))
                    }
                }
                AnimatedContent(targetState = step, transitionSpec = { (fadeIn(tween(300)) + scaleIn(initialScale = .96f, animationSpec = tween(300))).togetherWith(fadeOut(tween(180))) }, label = "tutorial-page") { index ->
                    val data = steps[index]
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Icon(data.third, null, tint = Cyan, modifier = Modifier.size(54.dp))
                        Text(data.first, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color.White)
                        Text(data.second, style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = .78f))
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${step + 1} / ${steps.size}", color = Color.White.copy(alpha = .65f), style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.weight(1f))
                    Button(onClick = { if (step == steps.lastIndex) onFinish() else onStep(step + 1) }) {
                        Text(if (step == steps.lastIndex) "Let's go" else "Continue")
                        Spacer(Modifier.width(6.dp))
                        Icon(if (step == steps.lastIndex) Icons.Default.Check else Icons.Default.ArrowForward, null)
                    }
                }
            }
        }
    }
}

private fun auditUrl(address: String): List<Finding> {
    val url = java.net.URL(address)
    val connection = (url.openConnection() as java.net.HttpURLConnection).apply {
        requestMethod = "GET"; connectTimeout = 7000; readTimeout = 7000
        instanceFollowRedirects = false
        setRequestProperty("User-Agent", "SafeBuild-Local-Audit/1.0")
    }
    try {
        val code = connection.responseCode
        val headers = connection.headerFields.entries.filter { it.key != null }.associate { it.key!!.lowercase() to (it.value?.joinToString("; ") ?: "") }
        val out = mutableListOf<Finding>()
        fun missing(header: String, severity: String, why: String, fix: String) {
            if (headers[header] == null) out += Finding(severity, "Missing " + header + " header", url.host, why, fix)
        }
        missing("content-security-policy", "Medium", "No Content Security Policy was observed.", "Define a CSP suited to the site and test in report-only mode first.")
        missing("strict-transport-security", "Medium", "HSTS was not observed in the response.", "For HTTPS production sites, configure HSTS after confirming HTTPS support across relevant subdomains.")
        missing("x-content-type-options", "Low", "The response does not advertise nosniff protection.", "Add X-Content-Type-Options: nosniff.")
        missing("referrer-policy", "Low", "No Referrer-Policy was observed.", "Consider strict-origin-when-cross-origin after reviewing product needs.")
        missing("permissions-policy", "Info", "No Permissions-Policy was observed.", "Disable browser features the site does not need.")
        if (code in 300..399) out += Finding("Info", "Redirect response", url.host, "Server returned HTTP " + code + "; redirects are not followed automatically.", "Review the Location target and audit the final destination separately.")
        if (address.startsWith("http://", true)) out += Finding("High", "Unencrypted HTTP", url.host, "The URL uses plain HTTP.", "Use HTTPS for public sites; never submit credentials over plain HTTP.")
        if (out.isEmpty()) out += Finding("Info", "Basic header check passed", url.host, "Checked headers were present; this is not a full vulnerability scan.", "Review header values and perform an authorized deeper assessment.")
        return out
    } finally { connection.disconnect() }
}


@Composable
private fun ClaySecurityLoop(modifier: Modifier = Modifier) {
    val motion = rememberInfiniteTransition(label = "clay-security-loop")
    val rotation by motion.animateFloat(0f, 360f, infiniteRepeatable(tween(9000, easing = androidx.compose.animation.core.LinearEasing)), label = "orbit-rotation")
    val breathe by motion.animateFloat(0.94f, 1.04f, infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "clay-breathe")
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val radius = size.minDimension * .27f
        val orbitPaint = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx())
        // Soft neon halo + layered clay orbit (Canvas-only, low GPU cost).
        drawCircle(color = Cyan.copy(alpha = .055f), radius = radius * 2.05f, center = androidx.compose.ui.geometry.Offset(cx, cy))
        drawCircle(color = Cyan.copy(alpha = .09f), radius = radius * 1.72f, center = androidx.compose.ui.geometry.Offset(cx, cy))
        drawCircle(color = Color(0xFF10151F), radius = radius * 1.42f, center = androidx.compose.ui.geometry.Offset(cx, cy), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 17.dp.toPx()))
        drawCircle(color = Color(0xFF394454), radius = radius * 1.42f, center = androidx.compose.ui.geometry.Offset(cx, cy), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 9.dp.toPx()))
        drawCircle(color = Cyan.copy(alpha = .72f), radius = radius * 1.42f, center = androidx.compose.ui.geometry.Offset(cx, cy), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()))
        drawCircle(color = Cyan.copy(alpha = .10f), radius = radius * 1.16f, center = androidx.compose.ui.geometry.Offset(cx, cy), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx()))
        val radians = Math.toRadians(rotation.toDouble())
        val px = cx + kotlin.math.cos(radians).toFloat() * radius * 1.42f
        val py = cy + kotlin.math.sin(radians).toFloat() * radius * .56f
        drawCircle(color = Color(0xFF364253), radius = radius * .36f * breathe, center = androidx.compose.ui.geometry.Offset(cx, cy + 4.dp.toPx()))
        drawCircle(color = Cyan.copy(alpha = .14f), radius = radius * .35f * breathe, center = androidx.compose.ui.geometry.Offset(cx, cy))
        drawCircle(color = Cyan.copy(alpha = .18f), radius = 16.dp.toPx(), center = androidx.compose.ui.geometry.Offset(px, py))
        drawCircle(color = Cyan.copy(alpha = .28f), radius = 11.dp.toPx(), center = androidx.compose.ui.geometry.Offset(px, py))
        drawCircle(color = Cyan, radius = 6.dp.toPx(), center = androidx.compose.ui.geometry.Offset(px, py))
        drawCircle(color = Color.White.copy(alpha = .95f), radius = 2.5.dp.toPx(), center = androidx.compose.ui.geometry.Offset(px - 2.dp.toPx(), py - 2.dp.toPx()))
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        Surface(
            modifier = Modifier.size(68.dp).scale(breathe),
            shape = RoundedCornerShape(22.dp),
            color = Color(0xFF252D3A),
            shadowElevation = 24.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, Cyan.copy(alpha = .82f))
        ) {
            Box(contentAlignment = Alignment.Center) {
                Box(contentAlignment = Alignment.Center) {
                    Surface(
                        modifier = Modifier.size(48.dp),
                        shape = RoundedCornerShape(18.dp),
                        color = Cyan.copy(alpha = .08f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Cyan.copy(alpha = .18f))
                    ) {}
                    Icon(Icons.Default.Shield, contentDescription = "Glowing animated SafeBuild security emblem", tint = Cyan, modifier = Modifier.size(38.dp))
                }
            }
        }
    }
}

