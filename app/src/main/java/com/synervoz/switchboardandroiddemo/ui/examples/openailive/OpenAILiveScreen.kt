package com.synervoz.switchboardandroiddemo.ui.examples.openailive

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

/** A transcript line: what "You" or "Assistant" said, or a status line when [speaker] is null. */
private data class TranscriptLine(val speaker: String?, val text: String)

@Composable
fun OpenAILiveScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var example by remember { mutableStateOf<OpenAILiveExample?>(null) }
    var isRunning by remember { mutableStateOf(false) }
    val lines = remember { mutableStateListOf<TranscriptLine>() }
    val listState = rememberLazyListState()

    DisposableEffect(Unit) {
        val instance = OpenAILiveExample(context)
        instance.listener = object : OpenAILiveExample.Listener {
            override fun onTranscript(speaker: String, delta: String) {
                val last = lines.lastOrNull()
                if (last != null && last.speaker == speaker) {
                    lines[lines.lastIndex] = last.copy(text = last.text + delta)
                } else {
                    lines.add(TranscriptLine(speaker, delta))
                }
            }

            override fun onStatus(text: String) {
                lines.add(TranscriptLine(null, text))
            }
        }
        example = instance
        onDispose {
            instance.listener = null
            instance.stop()
        }
    }

    LaunchedEffect(lines.size, lines.lastOrNull()?.text?.length) {
        if (lines.isNotEmpty()) listState.animateScrollToItem(lines.lastIndex)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Press Start and talk to the assistant. Ask it to play, pause or stop the music.",
            style = MaterialTheme.typography.bodyMedium,
        )
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(lines) { line ->
                if (line.speaker == null) {
                    Text(
                        text = line.text,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        text = buildAnnotatedString {
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("${line.speaker}: ") }
                            append(line.text)
                        },
                    )
                }
            }
        }
        Button(
            onClick = {
                val instance = example ?: return@Button
                if (isRunning) {
                    instance.stop()
                } else {
                    instance.start()
                }
                isRunning = instance.isRunning
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (isRunning) "Stop" else "Start")
        }
    }
}
