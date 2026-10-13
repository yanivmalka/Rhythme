package com.example.rhythme

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun LessonScreen(
    progressManager: ProgressManager,
    onLessonSelected: (Lesson) -> Unit,
    onBack: () -> Unit
) {
    val allProgress by remember {
        mutableStateOf(progressManager.getAllProgress())
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(
                "Rhythm Lessons",
                fontSize = 24.sp,
                modifier = Modifier.weight(1f)
            )
        }

        // Lessons list
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(LessonCurriculum.lessons) { lesson ->
                val progress = allProgress.find { it.lessonId == lesson.id } ?: LessonProgress(lesson.id)
                LessonCard(lesson, progress) {
                    onLessonSelected(lesson)
                }
            }
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
            containerColor = if (progress.isMastered)
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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = lesson.title,
                        fontSize = 18.sp,
                        color = Color.White
                    )
                    Text(
                        text = lesson.description,
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }

                // Status indicator
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (progress.isMastered) {
                        Text("⭐", fontSize = 28.sp)
                        Text("Mastered", fontSize = 10.sp, color = Color.White)
                    } else if (progress.attempts > 0) {
                        Text(
                            "${(progress.bestAccuracy * 100).toInt()}%",
                            fontSize = 16.sp,
                            color = Color.White
                        )
                        Text("${progress.streak}/3", fontSize = 10.sp, color = Color.White)
                    } else {
                        Text("→", fontSize = 24.sp, color = Color.White)
                    }
                }
            }

            // Progress bar
            if (progress.attempts > 0 && !progress.isMastered) {
                LinearProgressIndicator(
                    progress = progress.bestAccuracy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    color = Color(0.3f, 0.7f, 1f)
                )
            }
        }
    }
}
