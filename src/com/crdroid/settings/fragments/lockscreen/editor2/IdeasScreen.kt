/*
 * Copyright (C) 2026 IchthysOS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.crdroid.settings.fragments.lockscreen.editor2

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.layout.height
import com.android.settings.ichthys.guard.ui.theme.Guard
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.settings.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private enum class IdeasPhase { PICK, WORKING, GRID }

/**
 * IchthysOS lock screen editor v2, Ideas (One UI's "suggestions"): pick an image (the draft's
 * wallpaper, or any photo), press the sparkle button, get a grid of complete lock screens made for
 * it (SuggestionEngine). Choosing one hands it to the editor as the new draft, with the picture as
 * the draft's wallpaper; nothing is applied here.
 */
@Composable
fun IdeasScreen(
    base: LockDesign,
    current: LockPicture,
    currentIsWallpaper: WallpaperDraft,
    onBack: () -> Unit,
    onChoose: (SuggestionEngine.Idea, LockPicture, WallpaperDraft) -> Unit,
) {
    val context = LocalContext.current
    val screen = rememberScreen()
    var source by remember { mutableStateOf(currentIsWallpaper) }
    var preview by remember { mutableStateOf(current) }
    var phase by remember { mutableStateOf(IdeasPhase.PICK) }
    var analysed by remember { mutableStateOf(LockPicture.EMPTY) }
    var ideas by remember { mutableStateOf<List<SuggestionEngine.Idea>>(emptyList()) }
    // The grid items that have played their entrance (cleared for each new set of ideas).
    val shownIdeas = remember { HashSet<Int>() }

    val pick =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
            if (uri != null) source = WallpaperDraft.Photo(uri, home = false)
        }

    // The chosen photo, for the big preview (no subject yet: that is the sparkle button's job).
    LaunchedEffect(source) {
        val s = source
        if (s is WallpaperDraft.Photo && s != currentIsWallpaper) {
            preview = withContext(Dispatchers.IO) {
                LockPictures.photo(context, s.uri, screen.width, screen.height)
            }
        } else if (s == currentIsWallpaper) {
            preview = current
        }
    }

    LaunchedEffect(phase) {
        if (phase != IdeasPhase.WORKING) return@LaunchedEffect
        // The subject (IchthysDepth, a few seconds for a new picture), off the main thread...
        val pic = withContext(Dispatchers.IO) {
            LockPictures.withSubject(context, preview, screen.width, screen.height)
        }
        // ...the ideas on it (they measure real faces: views, so on the main thread).
        analysed = pic
        shownIdeas.clear()
        ideas = SuggestionEngine.make(context, base, pic, screen.width, screen.height)
        phase = IdeasPhase.GRID
    }

    val tk = Guard.tokens
    Column(
        Modifier.fillMaxSize().background(tk.ink).statusBarsPadding().navigationBarsPadding()
    ) {
        // Back with the screen's name, as the editor's own top bar.
        Row(
            Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp)
                .clip(RoundedCornerShape(10.dp))
                .clickable { if (phase == IdeasPhase.GRID) phase = IdeasPhase.PICK else onBack() }
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Canvas(Modifier.size(18.dp)) {
                val u = size.width / 24f
                val chevron = Path().apply {
                    moveTo(15 * u, 5 * u)
                    lineTo(8 * u, 12 * u)
                    lineTo(15 * u, 19 * u)
                }
                drawPath(chevron, tk.text, style = Stroke(1.6.dp.toPx(), cap = StrokeCap.Round,
                    join = StrokeJoin.Round))
            }
            Text(
                if (phase == IdeasPhase.GRID)
                    stringResource(R.string.ichthys_ed2_ideas_count, ideas.size)
                else stringResource(R.string.ichthys_ed2_ideas),
                fontFamily = tk.display, fontWeight = FontWeight.Bold, fontSize = 19.sp, color = tk.text,
                modifier = Modifier.weight(1f).padding(start = 8.dp),
            )
        }
        when (phase) {
            IdeasPhase.PICK, IdeasPhase.WORKING -> {
                Box(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 48.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center) {
                    Box(Modifier.aspectRatio(screen.width.toFloat() / screen.height)
                        .clip(RoundedCornerShape(24.dp))
                        .background(tk.card)) {
                        preview.backdrop?.let {
                            Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        }
                        if (phase == IdeasPhase.WORKING) {
                            Column(Modifier.fillMaxSize().background(Color(0x88000000)),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = tk.accent)
                                Text(stringResource(R.string.ichthys_ed2_ideas_working), fontSize = 13.sp,
                                    color = Color.White, modifier = Modifier.padding(top = 12.dp))
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SourceCard(stringResource(R.string.ichthys_ed2_ideas_current), source == currentIsWallpaper,
                        Modifier.weight(1f)) { source = currentIsWallpaper }
                    SourceCard(stringResource(R.string.ichthys_ed2_ideas_photo),
                        source is WallpaperDraft.Photo && source != currentIsWallpaper, Modifier.weight(1f)) {
                        pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }
                }
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    // The one action here: outlined in the accent (Shoal), faded while it can't run.
                    val ready = phase == IdeasPhase.PICK && preview.source != null
                    val shape = RoundedCornerShape(10.dp)
                    Box(
                        Modifier.height(44.dp).alpha(if (ready) 1f else 0.4f).clip(shape)
                            .border(1.dp, tk.accent, shape)
                            .clickable(enabled = ready) { phase = IdeasPhase.WORKING }
                            .padding(horizontal = 20.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(("✦  " + stringResource(R.string.ichthys_ed2_ideas_make)).uppercase(),
                            fontFamily = tk.mono, fontSize = 12.sp, letterSpacing = 1.sp,
                            fontWeight = FontWeight.SemiBold, color = tk.accent)
                    }
                }
            }
            IdeasPhase.GRID -> {
                if (analysed.cutout == null) {
                    Text(stringResource(R.string.ichthys_ed2_ideas_no_subject), fontSize = 12.sp,
                        color = tk.muted, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                }
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(104.dp),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth().weight(1f),
                ) {
                    items(ideas.size, key = { i -> ideas[i].title + "#" + i }) { i ->
                        val idea = ideas[i]
                        // IchthysOS polish: the ideas come in one after another, each fading up
                        // from a little smaller (once; scrolled back to, they are just there).
                        val appear = remember(idea) { Animatable(if (shownIdeas.contains(i)) 1f else 0f) }
                        LaunchedEffect(idea) {
                            if (appear.value >= 1f) return@LaunchedEffect
                            delay(minOf(i, 11) * 45L)
                            appear.animateTo(1f, spring(dampingRatio = 0.82f, stiffness = 300f))
                            shownIdeas.add(i)
                        }
                        Column(
                            Modifier.graphicsLayer {
                                val a = appear.value
                                alpha = a.coerceIn(0f, 1f)
                                val sc = 0.9f + 0.1f * a
                                scaleX = sc
                                scaleY = sc
                                translationY = (1f - a) * 18 * density
                            }
                                .clickable { onChoose(idea, analysed, source) }
                        ) {
                            MiniLock(idea.design, analysed,
                                Modifier.fillMaxWidth().aspectRatio(screen.width.toFloat() / screen.height))
                            Text(idea.title, fontFamily = tk.mono, fontSize = 10.sp, color = tk.muted, maxLines = 1,
                                modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SourceCard(label: String, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val tk = Guard.tokens
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier.clip(shape)
            .then(if (tk.hairlineSurfaces) Modifier else Modifier.background(tk.card))
            .border(if (on) BorderStroke(1.5.dp, tk.accent) else BorderStroke(1.dp, tk.hairline), shape)
            .clickable(onClick = onClick)
            .padding(14.dp),
    ) {
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (on) tk.accent else tk.text)
    }
}
