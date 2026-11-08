package com.panocapture.guided360.ui.screens

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.panocapture.guided360.domain.model.Session
import com.panocapture.guided360.domain.model.TargetStatus
import com.panocapture.guided360.storage.SessionRepository
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun SessionListScreen(
    repository: SessionRepository,
    onStartNewSession: (sceneName: String) -> Unit,
    onResumeSession: (session: Session) -> Unit
) {
    val context = LocalContext.current
    var sessions by remember { mutableStateOf(repository.loadAllSessions()) }
    var showNewDialog by remember { mutableStateOf(false) }
    var newSceneName by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            Surface(tonalElevation = 3.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "360° Panorama Sessions",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showNewDialog = true },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = "New Scene", tint = Color.White)
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (sessions.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No capture sessions yet.\nTap + to start one.", color = Color.Gray)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(sessions, key = { it.id }) { s ->
                        SessionCard(
                            session = s,
                            onClick = { onResumeSession(s) },
                            onDelete = {
                                repository.deleteSession(s.id)
                                sessions = repository.loadAllSessions()
                            },
                            onShare = {
                                shareSession(context, repository, s)
                            }
                        )
                    }
                }
            }
        }
    }

    if (showNewDialog) {
        AlertDialog(
            onDismissRequest = { showNewDialog = false },
            title = { Text("New 360° Scene") },
            text = {
                OutlinedTextField(
                    value = newSceneName,
                    onValueChange = { newSceneName = it },
                    label = { Text("Scene name (e.g., Living Room)") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newSceneName.isNotBlank()) {
                            val name = newSceneName
                            showNewDialog = false
                            newSceneName = ""
                            onStartNewSession(name)
                        }
                    }
                ) {
                    Text("Start")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun SessionCard(
    session: Session,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit
) {
    val dateStr = remember(session.createdAtEpochMs) {
        SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(Date(session.createdAtEpochMs))
    }
    val capturedCount = session.targets.count { it.status == TargetStatus.CAPTURED }

    Card(
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(session.sceneName, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text(dateStr, fontSize = 12.sp, color = Color.Gray)
                Text(
                    text = "$capturedCount / ${session.targets.size} targets captured",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }
            Row {
                IconButton(onClick = onShare) {
                    Icon(Icons.Default.Share, contentDescription = "Share", tint = Color.Gray)
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFE53935))
                }
            }
        }
    }
}

private fun shareSession(context: android.content.Context, repository: SessionRepository, session: Session) {
    val sessionDir = repository.getSessionDir(session.id)
    val photosDir = File(sessionDir, "photos")
    val photoFiles = photosDir.listFiles { f -> f.extension.equals("jpg", ignoreCase = true) } ?: return

    val uris = photoFiles.map {
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", it)
    }

    val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
        type = "image/jpeg"
        putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Share 360° Session Photos"))
}
