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
    var urlAuditing by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    val tutorialPrefs = remember { context.getSharedPreferences("safebuild_onboarding", android.content.Context.MODE_PRIVATE) }
    var showTutorial by remember { mutableStateOf(!tutorialPrefs.getBoolean("completed", false)) }
    var tutorialStep by remember { mutableIntStateOf(0) }

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
                item { if (showTutorial) TutorialDialog(step = tutorialStep, onStep = { tutorialStep = it }, onFinish = {
                    showTutorial = false
                    tutorialPrefs.edit().putBoolean("completed", true).apply()
                }) }
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
                                if (scanning) PulsingShield(Modifier.size(22.dp))
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
                            Button(enabled = !urlAuditing, onClick = {
                                val raw = targetUrl.trim()
                                val parsed = runCatching { Uri.parse(if (raw.startsWith("https://") || raw.startsWith("http://")) raw else "https://" + raw) }.getOrNull()
                                if (parsed == null || parsed.host.isNullOrBlank() || parsed.scheme !in setOf("https", "http")) {
                                    status = "Enter a valid website hostname"
                                } else {
                                    urlAuditing = true
                                    status = "Checking website response headers…"
                                    (context as? ComponentActivity)?.lifecycleScope?.launch {
                                        val result = withContext(Dispatchers.IO) { runCatching { auditUrl(parsed.toString()) } }
                                        findings = result.getOrElse { listOf(Finding("Info", "URL audit could not complete", parsed.host ?: raw, it.message ?: "Connection failed", "Check the URL and connection. Audit only sites you are authorized to test.")) }
                                        urlAuditing = false
                                        status = "URL audit complete • " + findings.size + " finding(s)"
                                    }
                                }
                            }, modifier = Modifier.fillMaxWidth()) {
                                if (urlAuditing) PulsingShield(Modifier.size(22.dp)) else Icon(Icons.Default.Language, null)
                                Spacer(Modifier.width(8.dp))
                                Text(if (urlAuditing) "Auditing website…" else "Audit URL")
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
