package com.example.rhythme

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LessonScreen(
    progressManager: ProgressManager,
    onLessonSelected: (Lesson) -> Unit,
    onBack: () -> Unit
) {
    var allProgress by remember { mutableStateOf(progressManager.getAllProgress()) }
    fun refresh() { allProgress = progressManager.getAllProgress() }

    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var confirmResetAll by remember { mutableStateOf(false) }
    val resetDone = stringResource(R.string.lesson_reset_done)
    val undoLabel = stringResource(R.string.undo)

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar, modifier = Modifier.navigationBarsPadding()) }, // above the nav buttons
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .safeDrawingPadding() // keep clear of status and navigation bars
                .padding(16.dp)
        ) {
            // Header: back, title, reset all
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                }
                Text(
                    stringResource(R.string.lessons_title),
                    fontSize = 24.sp,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { confirmResetAll = true }) {
                    Icon(Icons.Filled.RestartAlt, contentDescription = stringResource(R.string.reset_all))
                }
            }
            Text(
                stringResource(R.string.swipe_reset_hint),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp, bottom = 12.dp)
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(LessonCurriculum.lessons, key = { it.id }) { lesson ->
                    val progress = allProgress.find { it.lessonId == lesson.id } ?: LessonProgress(lesson.id)
                    // Swipe either way to reset this lesson (with Undo); untouched lessons can't be swiped
                    val dismissState = rememberSwipeToDismissBoxState(
                        confirmValueChange = { value ->
                            if (value != SwipeToDismissBoxValue.Settled && progress.attempts > 0) {
                                progressManager.reset(lesson.id)
                                refresh()
                                scope.launch {
                                    snackbar.currentSnackbarData?.dismiss()
                                    val result = snackbar.showSnackbar(resetDone, undoLabel, duration = SnackbarDuration.Long) // time to undo
                                    if (result == SnackbarResult.ActionPerformed) {
                                        progressManager.restore(progress)
                                        refresh()
                                    }
                                }
                            }
                            false // the card springs back; its content shows the reset state
                        }
                    )
                    SwipeToDismissBox(
                        state = dismissState,
                        enableDismissFromStartToEnd = progress.attempts > 0,
                        enableDismissFromEndToStart = progress.attempts > 0,
                        backgroundContent = { ResetBackground() }
                    ) {
                        LessonCard(lesson, progress) { onLessonSelected(lesson) }
                    }
                }
            }
        }
    }

    if (confirmResetAll) {
        AlertDialog(
            onDismissRequest = { confirmResetAll = false },
            title = { Text(stringResource(R.string.reset_all_confirm_title)) },
            text = { Text(stringResource(R.string.reset_all_confirm_text)) },
            confirmButton = {
                TextButton(onClick = {
                    progressManager.reset()
                    refresh()
                    confirmResetAll = false
                }) { Text(stringResource(R.string.reset), color = Color(0xFFFF6B6B)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmResetAll = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}

@Composable
private fun ResetBackground() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFB3261E)),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.RestartAlt, contentDescription = null, tint = Color.White)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.reset), color = Color.White, fontSize = 16.sp)
        }
    }
}

@Composable
fun LessonCard(
    lesson: Lesson,
    progress: LessonProgress,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(4.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (progress.stars == LessonProgress.MAX_STARS)
                Color(0.2f, 0.6f, 0.3f, 1f)
            else
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                NoteGlyph(lesson.targetNoteLength, Color.White, height = 40.dp)
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(lesson.titleRes),
                            fontSize = 18.sp,
                            color = Color.White
                        )
                        lesson.timeSignature?.let { MeterBadge(it) }
                    }
                    Text(
                        text = stringResource(lesson.descriptionRes),
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }

                // Status: stars earned, best score; arrow if not tried yet
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (progress.attempts > 0) {
                        StarsRow(progress.stars, size = 20.dp)
                        Text(
                            "${(progress.bestAccuracy * 100).toInt()}%",
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    } else {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = Color.White)
                    }
                }
            }

            // Progress toward the next star
            progress.nextStarStreak?.let { streak ->
                if (progress.attempts > 0) {
                    LinearProgressIndicator(
                        progress = { streak.toFloat() / LessonProgress.STREAK_FOR_STAR },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp),
                        color = StarGold,
                        trackColor = Color.White.copy(alpha = 0.15f)
                    )
                }
            }
        }
    }
}
