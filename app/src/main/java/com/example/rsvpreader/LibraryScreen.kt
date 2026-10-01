package com.example.rsvpreader

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    store: DocumentStore,
    importer: FileImporter,
    onOpenDocument: (String) -> Unit,
    onSettings: () -> Unit
) {
    var documents by remember { mutableStateOf(store.listDocuments()) }
    var pasteDialog by remember { mutableStateOf(false) }
    var pasteBusy by remember { mutableStateOf(false) }
    var importMenu by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<DocumentEntry?>(null) }
    var deleteTarget by remember { mutableStateOf<DocumentEntry?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    fun refresh() { documents = store.listDocuments() }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            busy = true
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    importer.extract(uri).fold(
                        onSuccess = { extracted ->
                            store.addDocument(
                                requestedTitle = extracted.originalName,
                                sourceType = extracted.sourceType,
                                originalName = extracted.originalName,
                                rawText = extracted.text,
                                sourceToc = extracted.toc
                            )
                        },
                        onFailure = { ImportResult.Failure(it.message ?: "문서를 가져오지 못했습니다.") }
                    )
                }
                busy = false
                when (result) {
                    is ImportResult.Success -> {
                        refresh()
                        if (result.duplicateOf != null) snackbar.showSnackbar("이미 서재에 있는 문서입니다: ${result.entry.title}")
                        else snackbar.showSnackbar("서재에 추가했습니다.")
                    }
                    is ImportResult.Failure -> snackbar.showSnackbar(result.message)
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("내 서재") },
                actions = { IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, "설정") } }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            Box {
                FloatingActionButton(onClick = { importMenu = true }) { Icon(Icons.Default.Add, "문서 추가") }
                DropdownMenu(expanded = importMenu, onDismissRequest = { importMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("텍스트 붙여넣기") },
                        leadingIcon = { Icon(Icons.Default.Edit, null) },
                        onClick = { importMenu = false; pasteDialog = true }
                    )
                    DropdownMenuItem(
                        text = { Text("TXT / PDF / EPUB 가져오기") },
                        leadingIcon = { Icon(Icons.Default.FileOpen, null) },
                        onClick = {
                            importMenu = false
                            launcher.launch(arrayOf("text/plain", "application/pdf", "application/epub+zip", "application/octet-stream"))
                        }
                    )
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (documents.isEmpty()) {
                Column(
                    modifier = Modifier.align(Alignment.Center).padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("아직 저장된 문서가 없습니다.", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text("오른쪽 아래 + 버튼에서 텍스트를 붙여넣거나 파일을 가져올 수 있습니다.", style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)
                ) {
                    items(documents, key = { it.id }) { entry ->
                        LibraryCard(
                            entry = entry,
                            onOpen = { onOpenDocument(entry.id) },
                            onRename = { renameTarget = entry },
                            onDelete = { deleteTarget = entry }
                        )
                    }
                }
            }
            if (busy) {
                LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
            }
        }
    }

    if (pasteDialog) {
        PasteDialog(
            isAdding = pasteBusy,
            onDismiss = { if (!pasteBusy) pasteDialog = false },
            onAdd = { title, text ->
                if (!pasteBusy) {
                    pasteBusy = true
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            runCatching { store.addDocument(title, SourceType.PASTE, null, text) }
                                .getOrElse { ImportResult.Failure(it.message ?: "텍스트를 추가하지 못했습니다.") }
                        }
                        pasteBusy = false
                        pasteDialog = false
                        refresh()
                        when (result) {
                            is ImportResult.Success -> if (result.duplicateOf != null) snackbar.showSnackbar("이미 서재에 있는 문서입니다: ${result.entry.title}")
                            is ImportResult.Failure -> snackbar.showSnackbar(result.message)
                        }
                    }
                }
            }
        )
    }

    renameTarget?.let { target ->
        RenameDialog(
            current = target.title,
            onDismiss = { renameTarget = null },
            onRename = {
                store.rename(target.id, it)
                renameTarget = null
                refresh()
            }
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("문서를 삭제할까요?") },
            text = { Text("${target.title}\n\n저장된 읽기 위치와 내부 사본도 함께 삭제됩니다.") },
            confirmButton = {
                TextButton(onClick = {
                    store.delete(target.id)
                    deleteTarget = null
                    refresh()
                }) { Text("삭제") }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("취소") } }
        )
    }
}

@Composable
private fun LibraryCard(entry: DocumentEntry, onOpen: () -> Unit, onRename: () -> Unit, onDelete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(entry.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(3.dp))
                    Text(
                        when (entry.sourceType) {
                            SourceType.PASTE -> "붙여넣은 텍스트"
                            SourceType.TXT -> "TXT"
                            SourceType.PDF -> "PDF"
                            SourceType.EPUB -> "EPUB"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "문서 메뉴") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("이름 변경") }, leadingIcon = { Icon(Icons.Default.Edit, null) }, onClick = { menu = false; onRename() })
                        DropdownMenuItem(text = { Text("삭제") }, leadingIcon = { Icon(Icons.Default.Delete, null) }, onClick = { menu = false; onDelete() })
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(progress = { entry.progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(5.dp))
            Text("${(entry.progress * 100).roundToInt()}%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PasteDialog(isAdding: Boolean, onDismiss: () -> Unit, onAdd: (String?, String) -> Unit) {
    var title by remember { mutableStateOf("") }
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { if (!isAdding) onDismiss() },
        title = { Text("텍스트 붙여넣기") },
        text = {
            Column {
                TextField(value = title, onValueChange = { title = it }, label = { Text("제목 (선택)") }, singleLine = true, enabled = !isAdding, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
                TextField(value = text, onValueChange = { text = it }, label = { Text("본문") }, minLines = 8, maxLines = 14, enabled = !isAdding, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(onClick = { onAdd(title.takeIf { it.isNotBlank() }, text) }, enabled = text.isNotBlank() && !isAdding) {
                Text(if (isAdding) "추가 중…" else "추가")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !isAdding) { Text("취소") } }
    )
}

@Composable
private fun RenameDialog(current: String, onDismiss: () -> Unit, onRename: (String) -> Unit) {
    var value by remember(current) { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("이름 변경") },
        text = { TextField(value = value, onValueChange = { value = it }, singleLine = true) },
        confirmButton = { Button(onClick = { onRename(value) }, enabled = value.isNotBlank()) { Text("저장") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } }
    )
}
