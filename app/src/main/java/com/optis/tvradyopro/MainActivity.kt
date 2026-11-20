package com.optis.tvradyopro

import android.app.Activity
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.annotation.OptIn
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.optis.tvradyopro.model.Channel
import com.optis.tvradyopro.player.PlayerHolder

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFF00E5FF),
                    secondary = Color(0xFFFFB300),
                    background = Color(0xFF0A0E17),
                    surface = Color(0xFF131A2A),
                    onBackground = Color.White,
                    onSurface = Color.White
                )
            ) {
                OptisAppScreen(viewModel)
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
fun OptisAppScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val activity = context as? Activity

    val channels by viewModel.filteredChannels.collectAsState()
    val allChannels by viewModel.allChannels.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val favoriteIds by viewModel.favoriteIds.collectAsState()
    val currentChannel by viewModel.currentChannel.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
    val sleepMinutes by viewModel.sleepMinutesRemaining.collectAsState()

    var isFullscreen by remember { mutableStateOf(false) }
    var resizeMode by remember { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    var showUrlDialog by remember { mutableStateOf(false) }
    var showSleepDialog by remember { mutableStateOf(false) }
    var playerError by remember { mutableStateOf<String?>(null) }
    var isBuffering by remember { mutableStateOf(false) }

    val player = remember { PlayerHolder.getOrCreatePlayer(context) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_READY) {
                    playerError = null
                }
            }
            override fun onPlayerError(error: PlaybackException) {
                playerError = "Yayın bağlanamadı (${error.errorCodeName}). Alternatif yayını deneyin."
                isBuffering = false
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    LaunchedEffect(currentChannel) {
        playerError = null
    }

    fun toggleFullscreen(enable: Boolean) {
        isFullscreen = enable
        activity?.requestedOrientation = if (enable) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    BackHandler(enabled = isFullscreen) {
        toggleFullscreen(false)
    }

    if (isFullscreen) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            VideoPlayerSurface(
                player = player,
                currentChannel = currentChannel,
                resizeMode = resizeMode,
                isBuffering = isBuffering,
                playerError = playerError,
                isFullscreen = true,
                onToggleFullscreen = { toggleFullscreen(false) },
                onToggleAspect = {
                    resizeMode = when (resizeMode) {
                        AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                        AspectRatioFrameLayout.RESIZE_MODE_FILL -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                    }
                },
                onPrev = { viewModel.playPreviousChannel() },
                onNext = { viewModel.playNextChannel() },
                onRetry = { currentChannel?.let { viewModel.selectChannel(it) } }
            )
        }
        return
    }

    Scaffold(
        containerColor = Color(0xFF0A0E17),
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0F1624))
                    .statusBarsPadding()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(0xFF00E5FF), Color(0xFF2979FF))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.LiveTv, contentDescription = null, tint = Color.Black)
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            "OPTİS TV & RADYO",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 16.sp,
                            color = Color.White
                        )
                        Text(
                            "${allChannels.size} Canlı Kanal • Arka Plan Radyo",
                            fontSize = 11.sp,
                            color = Color(0xFF8EA2C0)
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (sleepMinutes != null) {
                        Surface(
                            color = Color(0xFFFFB300).copy(alpha = 0.2f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.padding(end = 6.dp)
                        ) {
                            Text(
                                "${sleepMinutes}dk",
                                color = Color(0xFFFFB300),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    }
                    IconButton(onClick = { showSleepDialog = true }) {
                        Icon(Icons.Default.Bedtime, contentDescription = "Uyku Zamanlayıcı", tint = Color(0xFF00E5FF))
                    }
                    IconButton(onClick = { showUrlDialog = true }) {
                        Icon(Icons.Default.CloudDownload, contentDescription = "M3U Link Ekle", tint = Color(0xFF00E5FF))
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(Color.Black)
            ) {
                VideoPlayerSurface(
                    player = player,
                    currentChannel = currentChannel,
                    resizeMode = resizeMode,
                    isBuffering = isBuffering,
                    playerError = playerError,
                    isFullscreen = false,
                    onToggleFullscreen = { toggleFullscreen(true) },
                    onToggleAspect = {
                        resizeMode = when (resizeMode) {
                            AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                            AspectRatioFrameLayout.RESIZE_MODE_FILL -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                            else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                        }
                    },
                    onPrev = { viewModel.playPreviousChannel() },
                    onNext = { viewModel.playNextChannel() },
                    onRetry = { currentChannel?.let { viewModel.selectChannel(it) } }
                )
            }

            statusMessage?.let { msg ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1A2942))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(msg, color = Color(0xFF00E5FF), fontSize = 12.sp)
                    Text(
                        "Kapat",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { viewModel.clearStatus() }
                    )
                }
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.updateSearchQuery(it) },
                placeholder = { Text("TV kanalı, maç veya radyo ara...", fontSize = 13.sp, color = Color(0xFF7B8FA8)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF00E5FF)) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                            Icon(Icons.Default.Close, contentDescription = "Temizle", tint = Color.Gray)
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF00E5FF),
                    unfocusedBorderColor = Color(0xFF23304A),
                    focusedContainerColor = Color(0xFF131A2A),
                    unfocusedContainerColor = Color(0xFF131A2A),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            )

            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                items(viewModel.categories) { category ->
                    val selected = category == selectedCategory
                    FilterChip(
                        selected = selected,
                        onClick = { viewModel.selectCategory(category) },
                        label = {
                            Text(
                                category,
                                fontSize = 12.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF00E5FF),
                            selectedLabelColor = Color.Black,
                            containerColor = Color(0xFF162033),
                            labelColor = Color(0xFFCBD5E1)
                        )
                    )
                }
            }

            if (isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFF00E5FF))
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(channels, key = { it.id }) { channel ->
                        ChannelRowItem(
                            channel = channel,
                            isPlaying = currentChannel?.id == channel.id,
                            isFavorite = favoriteIds.contains(channel.name),
                            onSelect = { viewModel.selectChannel(channel) },
                            onToggleFavorite = { viewModel.toggleFavorite(channel) }
                        )
                    }
                }
            }
        }
    }

    if (showUrlDialog) {
        var inputUrl by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showUrlDialog = false },
            containerColor = Color(0xFF131A2A),
            title = { Text("Online M3U / M3U8 Listesi Yükle", color = Color.White) },
            text = {
                Column {
                    Text(
                        "Yeni bir M3U yayın linki yapıştırın veya mevcut Adıyaman Optis listesine geri dönün.",
                        color = Color(0xFF94A3B8),
                        fontSize = 13.sp
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = inputUrl,
                        onValueChange = { inputUrl = it },
                        placeholder = { Text("https://.../playlist.m3u8") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.importM3uFromUrl(inputUrl)
                        showUrlDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF))
                ) {
                    Text("Yükle", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.resetToDefaultPlaylist()
                    showUrlDialog = false
                }) {
                    Text("Varsayılan Liste", color = Color(0xFFFFB300))
                }
            }
        )
    }

    if (showSleepDialog) {
        AlertDialog(
            onDismissRequest = { showSleepDialog = false },
            containerColor = Color(0xFF131A2A),
            title = { Text("Uyku Zamanlayıcısı (Radyo / TV)", color = Color.White) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(15, 30, 45, 60, 90).forEach { mins ->
                        OutlinedButton(
                            onClick = {
                                viewModel.setSleepTimer(mins)
                                showSleepDialog = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("$mins Dakika Sonra Kapat", color = Color.White)
                        }
                    }
                    TextButton(
                        onClick = {
                            viewModel.setSleepTimer(null)
                            showSleepDialog = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Zamanlayıcıyı İptal Et", color = Color(0xFFFF5252))
                    }
                }
            },
            confirmButton = {}
        )
    }
}

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerSurface(
    player: Player,
    currentChannel: Channel?,
    resizeMode: Int,
    isBuffering: Boolean,
    playerError: String?,
    isFullscreen: Boolean,
    onToggleFullscreen: () -> Unit,
    onToggleAspect: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onRetry: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    useController = true
                    setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                }
            },
            update = { view ->
                view.player = player
                view.resizeMode = resizeMode
            },
            modifier = Modifier.fillMaxSize()
        )

        if (currentChannel?.isRadio == true && playerError == null) {
            RadioVisualizerOverlay(channel = currentChannel)
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Black.copy(alpha = 0.75f), Color.Transparent)
                    )
                )
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = currentChannel?.name ?: "Kanal Seçiniz",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (currentChannel != null) {
                    Text(
                        text = "${currentChannel.smartCategory} • CANLI",
                        color = Color(0xFF00E5FF),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Row {
                IconButton(onClick = onPrev, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.SkipPrevious, contentDescription = "Önceki", tint = Color.White)
                }
                IconButton(onClick = onNext, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.SkipNext, contentDescription = "Sonraki", tint = Color.White)
                }
                IconButton(onClick = onToggleAspect, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.AspectRatio, contentDescription = "En Boy", tint = Color.White)
                }
                IconButton(onClick = onToggleFullscreen, modifier = Modifier.size(32.dp)) {
                    Icon(
                        if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                        contentDescription = "Tam Ekran",
                        tint = Color(0xFF00E5FF)
                    )
                }
            }
        }

        if (isBuffering) {
            CircularProgressIndicator(
                color = Color(0xFF00E5FF),
                modifier = Modifier.align(Alignment.Center)
            )
        }

        if (playerError != null) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color.Black.copy(alpha = 0.85f), RoundedCornerShape(12.dp))
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(playerError, color = Color(0xFFFF8A80), fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = onRetry,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF))
                ) {
                    Text("Tekrar Dene", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun RadioVisualizerOverlay(channel: Channel) {
    val infiniteTransition = rememberInfiniteTransition(label = "eq")
    val bars = (0 until 12).map { idx ->
        infiniteTransition.animateFloat(
            initialValue = 0.25f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 320 + (idx * 55)),
                repeatMode = RepeatMode.Reverse
            ),
            label = "bar$idx"
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    listOf(Color(0xFF14284B), Color(0xFF070B14))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF00E5FF).copy(alpha = 0.15f))
                    .border(1.5.dp, Color(0xFF00E5FF), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Radio, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(30.dp))
            }
            Spacer(Modifier.height(8.dp))
            Text(channel.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text("Arka Planda Dinlenebilir Canlı Radyo", color = Color(0xFF8EA2C0), fontSize = 11.sp)
            Spacer(Modifier.height(10.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.Bottom,
                modifier = Modifier.height(28.dp)
            ) {
                bars.forEach { anim ->
                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .fillMaxHeight(anim.value)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color(0xFF00E5FF))
                    )
                }
            }
        }
    }
}

@Composable
fun ChannelRowItem(
    channel: Channel,
    isPlaying: Boolean,
    isFavorite: Boolean,
    onSelect: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    val bgColor = if (isPlaying) Color(0xFF192B49) else Color(0xFF131A2A)
    val borderColor = if (isPlaying) Color(0xFF00E5FF) else Color(0xFF1F2A40)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable { onSelect() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF0B101B)),
            contentAlignment = Alignment.Center
        ) {
            if (!channel.logoUrl.isNullOrBlank()) {
                AsyncImage(
                    model = channel.logoUrl,
                    contentDescription = channel.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(4.dp)
                )
            } else {
                Icon(
                    if (channel.isRadio) Icons.Default.Radio else Icons.Default.Tv,
                    contentDescription = null,
                    tint = if (channel.isRadio) Color(0xFFFFB300) else Color(0xFF00E5FF)
                )
            }
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = channel.name,
                color = if (isPlaying) Color(0xFF00E5FF) else Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = channel.smartCategory,
                    color = Color(0xFF8EA2C0),
                    fontSize = 11.sp
                )
                if (channel.isRadio) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "• RADYO",
                        color = Color(0xFFFFB300),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        IconButton(onClick = onToggleFavorite) {
            Icon(
                if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = "Favori",
                tint = if (isFavorite) Color(0xFFFF4081) else Color(0xFF64748B)
            )
        }
    }
}
