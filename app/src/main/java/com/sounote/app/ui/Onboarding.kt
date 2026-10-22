@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.sounote.app.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sounote.app.LibraryViewModel
import com.sounote.app.R
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private const val PAGES = 5

/** İlk açılışta tanıtım: dört tanıtım sayfası + kısa kurulum (ad, kalem/parmak, tema). */
@Composable
fun OnboardingScreen(vm: LibraryViewModel) {
    val pager = rememberPagerState(pageCount = { PAGES })
    val scope = rememberCoroutineScope()
    var name by rememberSaveable { mutableStateOf(vm.userName) }
    var fingerDraw by rememberSaveable { mutableStateOf(vm.tools.fingerDraw) }
    val last = pager.currentPage == PAGES - 1

    fun finish() {
        vm.updateUserName(name)
        vm.updateTools(vm.tools.copy(fingerDraw = fingerDraw))
        vm.finishOnboarding()
    }

    fun go(page: Int) {
        scope.launch { pager.animateScrollToPage(page) }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // Üstte hafif marka rengi
        Box(
            Modifier
                .fillMaxWidth()
                .height(320.dp)
                .background(Brush.verticalGradient(listOf(Brand.Blue.copy(alpha = 0.16f), Color.Transparent))),
        )
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            ) {
                AppLogo(32.dp, elevated = false)
                Text(
                    "SouNote",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 10.dp).weight(1f),
                )
                if (!last) TextButton(onClick = { go(PAGES - 1) }) { Text("Atla") }
            }
            HorizontalPager(state = pager, modifier = Modifier.weight(1f).fillMaxWidth()) { page ->
                when (page) {
                    0 -> IntroPage(
                        "SouNote'a hoş geldin",
                        "Ders notların, PDF'lerin ve kalemin tek yerde. Belgelerinin üstüne yaz, çiz, işaretle; " +
                            "kâğıt gibi ama daha akıllı.",
                    ) { WelcomeArt() }
                    1 -> IntroPage(
                        "Kalemle doğal yazı",
                        "Basınca duyarlı kalemler, fosforlu ve silgi. Kalem yazar, parmak sayfayı kaydırır; " +
                            "avucun ekrana değse de çizmez. Çizginin sonunda beklersen düzgün şekle döner.",
                    ) { PenArt() }
                    2 -> IntroPage(
                        "Bütün belgelerin bir arada",
                        "PDF içe aktar ya da ders klasörünü bağla: oraya attığın yeni PDF'ler kendiliğinden görünür. " +
                            "Boş defterler, klasörler, etiketler ve arama.",
                    ) { DocsArt() }
                    3 -> IntroPage(
                        "Daha iyi çalış",
                        "Dersi kaydet, notuna dokununca kayıt o andan çalsın. Ezber bandıyla kendini sına, " +
                            "bilgi kartlarıyla tekrar et, pomodoro ile odaklan.",
                    ) { ToolsArt() }
                    else -> IntroPage(
                        "Hazırlayalım",
                        "Birkaç küçük ayar. Hepsini sonra Ayarlar'dan değiştirebilirsin.",
                        extra = {
                            SetupOptions(
                                name = name,
                                onName = { name = it },
                                fingerDraw = fingerDraw,
                                onFingerDraw = { fingerDraw = it },
                                vm = vm,
                            )
                        },
                    ) { AppLogo(96.dp) }
                }
            }
            Box(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp)) {
                if (pager.currentPage > 0) {
                    TextButton(onClick = { go(pager.currentPage - 1) }, modifier = Modifier.align(Alignment.CenterStart)) {
                        Text("Geri")
                    }
                }
                PagerDots(PAGES, pager.currentPage, Modifier.align(Alignment.Center))
                Button(
                    onClick = { if (last) finish() else go(pager.currentPage + 1) },
                    modifier = Modifier.align(Alignment.CenterEnd),
                ) {
                    Text(if (last) "Başlayalım" else "İleri")
                }
            }
        }
    }
}

@Composable
private fun IntroPage(
    title: String,
    text: String,
    extra: (@Composable () -> Unit)? = null,
    art: @Composable () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 24.dp), contentAlignment = Alignment.Center) {
        val wide = maxWidth > maxHeight && maxWidth > 700.dp
        if (wide) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(48.dp),
                modifier = Modifier.widthIn(max = 1040.dp),
            ) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { art() }
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                ) {
                    PageTexts(title, text, TextAlign.Start)
                    extra?.invoke()
                }
            }
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
                modifier = Modifier.widthIn(max = 560.dp).verticalScroll(rememberScrollState()),
            ) {
                art()
                PageTexts(title, text, TextAlign.Center)
                extra?.invoke()
            }
        }
    }
}

@Composable
private fun PageTexts(title: String, text: String, align: TextAlign) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = align,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 26.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = align,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun PagerDots(count: Int, current: Int, modifier: Modifier = Modifier) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = modifier) {
        repeat(count) { i ->
            val width by animateDpAsState(if (i == current) 24.dp else 8.dp, label = "dot")
            val color by animateColorAsState(
                if (i == current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                label = "dotColor",
            )
            Box(Modifier.height(8.dp).width(width).clip(CircleShape).background(color))
        }
    }
}

// ---- Kurulum ----

@Composable
private fun SetupOptions(
    name: String,
    onName: (String) -> Unit,
    fingerDraw: Boolean,
    onFingerDraw: (Boolean) -> Unit,
    vm: LibraryViewModel,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = name,
            onValueChange = { onName(it.take(30)) },
            singleLine = true,
            label = { Text("Adın (isteğe bağlı)") },
            placeholder = { Text("Ana sayfada seni adınla karşılayalım") },
            modifier = Modifier.fillMaxWidth(),
        )
        Text("Nasıl yazacaksın?", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ChoiceCard(
                selected = !fingerDraw,
                emoji = "✍️",
                title = "Kalemle",
                text = "Kalem yazar, parmak kaydırır. Avuç içi çizmez.",
                onClick = { onFingerDraw(false) },
                modifier = Modifier.weight(1f),
            )
            ChoiceCard(
                selected = fingerDraw,
                emoji = "👆",
                title = "Parmakla",
                text = "Kalemin yoksa: tek parmak çizer, iki parmak kaydırır.",
                onClick = { onFingerDraw(true) },
                modifier = Modifier.weight(1f),
            )
        }
        Text("Görünüm", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LibraryViewModel.ThemeMode.entries.forEach { m ->
                FilterChip(selected = vm.themeMode == m, onClick = { vm.updateTheme(m) }, label = { Text(m.label) })
            }
        }
        Text(
            "🔒 Notların yalnızca bu cihazda saklanır, hiçbir yere gönderilmez.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ChoiceCard(
    selected: Boolean,
    emoji: String,
    title: String,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .clip(shape)
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer)
            .border(
                if (selected) 2.dp else 1.dp,
                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                shape,
            )
            .clickable(onClick = onClick)
            .padding(16.dp),
    ) {
        Text(emoji, fontSize = 28.sp)
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ---- Çizimler ----

@Composable
private fun WelcomeArt() {
    val t = rememberInfiniteTransition(label = "welcome")
    val float by t.animateFloat(
        -8f, 8f,
        infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "float",
    )
    val glow by t.animateFloat(
        0.85f, 1.1f,
        infiniteRepeatable(tween(2400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glow",
    )
    Box(Modifier.size(280.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(260.dp)
                .graphicsLayer {
                    scaleX = glow
                    scaleY = glow
                }
                .clip(CircleShape)
                .background(Brush.radialGradient(listOf(Brand.Blue.copy(alpha = 0.30f), Color.Transparent))),
        )
        AppLogo(150.dp, Modifier.graphicsLayer { translationY = float.dp.toPx() })
    }
}

/** Çizgili sayfaya kendiliğinden yazan kalem, ardından fosforlu. */
@Composable
private fun PenArt() {
    val t = rememberInfiniteTransition(label = "pen")
    val progress by t.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(4200, easing = LinearEasing)),
        label = "progress",
    )
    val shape = RoundedCornerShape(18.dp)
    Box(Modifier.size(width = 320.dp, height = 230.dp).shadow(14.dp, shape).clip(shape).background(Color.White)) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val gap = h / 7f
            for (i in 1..6) {
                drawLine(Color(0xFFDCE4F7), Offset(0f, gap * i), Offset(w, gap * i), strokeWidth = 1.dp.toPx())
            }
            drawLine(Color(0xFFF2B8B5), Offset(w * 0.12f, 0f), Offset(w * 0.12f, h), strokeWidth = 1.dp.toPx())

            // El yazısı gibi halkalar (uzatılmış sikloid)
            val x0 = w * 0.18f
            val x1 = w * 0.86f
            val baseY = gap * 4f
            val loops = 5
            val a = (x1 - x0) / (loops * 2f * PI.toFloat())
            val b = a * 2.2f
            val path = Path()
            val n = 220
            for (k in 0..n) {
                val theta = k / n.toFloat() * loops * 2f * PI.toFloat()
                val x = x0 + a * theta - b * sin(theta)
                val y = baseY - b * (1f - cos(theta)) * 0.85f
                if (k == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            val write = (progress / 0.55f).coerceAtMost(1f)
            val mark = ((progress - 0.6f) / 0.18f).coerceIn(0f, 1f)

            // Fosforlu, yazının altında kalsın diye önce çizilir.
            if (mark > 0f) {
                drawRect(
                    Brand.Yellow.copy(alpha = 0.45f),
                    topLeft = Offset(x0 - 6.dp.toPx(), baseY - b * 1.9f),
                    size = Size((x1 - x0 + 12.dp.toPx()) * mark, b * 2.2f),
                )
            }
            val measure = PathMeasure()
            measure.setPath(path, false)
            val length = measure.length
            val drawn = Path()
            measure.getSegment(0f, length * write, drawn, true)
            drawPath(
                drawn,
                Brand.Deep,
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
            if (write < 1f) {
                val tip = measure.getPosition(length * write)
                rotate(35f, pivot = tip) {
                    val bodyW = 9.dp.toPx()
                    drawRect(Brand.Yellow, Offset(tip.x - bodyW / 2, tip.y - 70.dp.toPx()), Size(bodyW, 56.dp.toPx()))
                    drawRect(Color(0xFFF28B82), Offset(tip.x - bodyW / 2, tip.y - 78.dp.toPx()), Size(bodyW, 8.dp.toPx()))
                    val cone = Path().apply {
                        moveTo(tip.x - bodyW / 2, tip.y - 14.dp.toPx())
                        lineTo(tip.x + bodyW / 2, tip.y - 14.dp.toPx())
                        lineTo(tip.x, tip.y)
                        close()
                    }
                    drawPath(cone, Color(0xFFF5D9AE))
                    drawCircle(Brand.Deep, radius = 2.dp.toPx(), center = tip)
                }
            }
        }
    }
}

/** Yelpaze gibi açılıp kapanan belgeler. */
@Composable
private fun DocsArt() {
    val t = rememberInfiniteTransition(label = "docs")
    val spread by t.animateFloat(
        0.55f, 1f,
        infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "spread",
    )
    Box(Modifier.size(width = 340.dp, height = 250.dp), contentAlignment = Alignment.Center) {
        MiniDoc(
            Color(0xFF3FA36B), "Defter",
            Modifier.graphicsLayer {
                rotationZ = -14f * spread
                translationX = -72.dp.toPx() * spread
                translationY = 10.dp.toPx() * spread
            },
        )
        MiniDoc(
            Color(0xFFE0893A), "Klasör",
            Modifier.graphicsLayer {
                rotationZ = 14f * spread
                translationX = 72.dp.toPx() * spread
                translationY = 10.dp.toPx() * spread
            },
        )
        MiniDoc(Color(0xFFD64545), "PDF", Modifier.graphicsLayer { translationY = -8.dp.toPx() * spread })
    }
}

@Composable
private fun MiniDoc(color: Color, label: String, modifier: Modifier) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        verticalArrangement = Arrangement.spacedBy(9.dp),
        modifier = modifier
            .size(width = 128.dp, height = 170.dp)
            .shadow(10.dp, shape)
            .clip(shape)
            .background(Color.White)
            .padding(14.dp),
    ) {
        Box(Modifier.clip(RoundedCornerShape(6.dp)).background(color).padding(horizontal = 8.dp, vertical = 3.dp)) {
            Text(label, color = Color.White, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        }
        repeat(6) { i ->
            Box(
                Modifier
                    .fillMaxWidth(if (i % 3 == 2) 0.6f else 1f)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color(0xFFE3E8F2)),
            )
        }
    }
}

/** Çalışma araçları: hafifçe nabız gibi atan simgeler. */
@Composable
private fun ToolsArt() {
    val t = rememberInfiniteTransition(label = "tools")
    val phase by t.animateFloat(0f, 1f, infiniteRepeatable(tween(3000, easing = LinearEasing)), label = "phase")
    val tools = listOf(
        Triple(R.drawable.ic_mic, "Ses kaydı", Color(0xFFE5534B)),
        Triple(R.drawable.ic_tape, "Ezber bandı", Color(0xFFE0893A)),
        Triple(R.drawable.ic_lasso, "Lasso", Color(0xFF8E63CE)),
        Triple(R.drawable.ic_highlighter, "Fosforlu", Color(0xFFC9A227)),
        Triple(R.drawable.ic_pages, "Sayfalar", Color(0xFF3FA36B)),
        Triple(R.drawable.ic_text, "Yazı", Color(0xFF3AA6B9)),
    )
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        tools.chunked(3).forEachIndexed { row, chunk ->
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                chunk.forEachIndexed { col, (icon, label, color) ->
                    val index = row * 3 + col
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(96.dp)) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(76.dp)
                                .graphicsLayer {
                                    val s = 1f + 0.07f * sin(2f * PI.toFloat() * (phase + index / 6f))
                                    scaleX = s
                                    scaleY = s
                                }
                                .clip(CircleShape)
                                .background(color.copy(alpha = 0.16f)),
                        ) {
                            Icon(painterResource(icon), contentDescription = null, tint = color, modifier = Modifier.size(36.dp))
                        }
                        Text(
                            label,
                            style = MaterialTheme.typography.labelLarge,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }
        }
    }
}
