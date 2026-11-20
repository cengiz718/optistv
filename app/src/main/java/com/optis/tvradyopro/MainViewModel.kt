package com.optis.tvradyopro

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.optis.tvradyopro.data.M3uParser
import com.optis.tvradyopro.model.Channel
import com.optis.tvradyopro.player.PlaybackService
import com.optis.tvradyopro.player.PlayerHolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("optis_prefs", Context.MODE_PRIVATE)

    private val _allChannels = MutableStateFlow<List<Channel>>(emptyList())
    val allChannels: StateFlow<List<Channel>> = _allChannels

    private val _selectedCategory = MutableStateFlow("Tümü")
    val selectedCategory: StateFlow<String> = _selectedCategory

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _favoriteIds = MutableStateFlow<Set<String>>(
        prefs.getStringSet("favorites", emptySet()) ?: emptySet()
    )
    val favoriteIds: StateFlow<Set<String>> = _favoriteIds

    private val _currentChannel = MutableStateFlow<Channel?>(null)
    val currentChannel: StateFlow<Channel?> = _currentChannel

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage

    private val _sleepMinutesRemaining = MutableStateFlow<Int?>(null)
    val sleepMinutesRemaining: StateFlow<Int?> = _sleepMinutesRemaining
    private var sleepJob: Job? = null

    val categories = listOf(
        "Tümü",
        "Favoriler",
        "Haber & Ulusal",
        "Spor",
        "Radyo & Müzik",
        "Sinema & Dizi",
        "Belgesel & Doğa",
        "Çocuk & Eğitim",
        "Yerel & Diğer",
        "Almanya"
    )

    val filteredChannels: StateFlow<List<Channel>> = combine(
        _allChannels,
        _selectedCategory,
        _searchQuery,
        _favoriteIds
    ) { channels, category, query, favs ->
        channels.filter { ch ->
            val matchesCat = when (category) {
                "Tümü" -> true
                "Favoriler" -> favs.contains(ch.name)
                else -> ch.smartCategory == category
            }
            val matchesQuery = query.isBlank() || ch.name.contains(query, ignoreCase = true)
            matchesCat && matchesQuery
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        loadInitialPlaylist()
    }

    private fun loadInitialPlaylist() {
        viewModelScope.launch {
            _isLoading.value = true
            val parsed = withContext(Dispatchers.IO) {
                val customFile = File(getApplication<Application>().filesDir, "custom_playlist.m3u8")
                val rawText = if (customFile.exists() && customFile.length() > 100) {
                    customFile.readText()
                } else {
                    getApplication<Application>().assets.open("playlist.m3u8")
                        .bufferedReader().use { it.readText() }
                }
                M3uParser.parse(rawText)
            }
            _allChannels.value = parsed
            _isLoading.value = false
            parsed.firstOrNull { it.name.contains("TRT 1 HD", ignoreCase = true) }?.let {
                selectChannel(it)
            } ?: parsed.firstOrNull()?.let {
                selectChannel(it)
            }
        }
    }

    fun selectCategory(category: String) {
        _selectedCategory.value = category
    }

    fun updateSearchQuery(q: String) {
        _searchQuery.value = q
    }

    fun toggleFavorite(channel: Channel) {
        val current = _favoriteIds.value.toMutableSet()
        if (current.contains(channel.name)) {
            current.remove(channel.name)
        } else {
            current.add(channel.name)
        }
        _favoriteIds.value = current
        prefs.edit().putStringSet("favorites", current).apply()
    }

    fun selectChannel(channel: Channel) {
        _currentChannel.value = channel
        val ctx = getApplication<Application>()
        try {
            ctx.startService(Intent(ctx, PlaybackService::class.java))
        } catch (_: Exception) { }
        PlayerHolder.playChannel(ctx, channel)
    }

    fun playNextChannel() {
        val list = filteredChannels.value.ifEmpty { _allChannels.value }
        if (list.isEmpty()) return
        val idx = list.indexOfFirst { it.id == _currentChannel.value?.id }
        val next = list[(idx + 1) % list.size]
        selectChannel(next)
    }

    fun playPreviousChannel() {
        val list = filteredChannels.value.ifEmpty { _allChannels.value }
        if (list.isEmpty()) return
        val idx = list.indexOfFirst { it.id == _currentChannel.value?.id }
        val prev = if (idx <= 0) list.last() else list[idx - 1]
        selectChannel(prev)
    }

    fun importM3uFromUrl(url: String) {
        if (url.isBlank()) return
        viewModelScope.launch {
            _isLoading.value = true
            _statusMessage.value = "Liste indiriliyor..."
            try {
                val content = withContext(Dispatchers.IO) {
                    val client = OkHttpClient()
                    val req = Request.Builder().url(url.trim()).build()
                    client.newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful) throw Exception("HTTP ${resp.code}")
                        resp.body?.string() ?: throw Exception("Boş yanıt")
                    }
                }
                if (!content.contains("#EXTM3U") && !content.contains("#EXTINF")) {
                    throw Exception("Geçersiz M3U formatı")
                }
                val parsed = withContext(Dispatchers.IO) {
                    File(getApplication<Application>().filesDir, "custom_playlist.m3u8").writeText(content)
                    M3uParser.parse(content)
                }
                _allChannels.value = parsed
                _statusMessage.value = "${parsed.size} kanal başarıyla yüklendi!"
            } catch (e: Exception) {
                _statusMessage.value = "Hata: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun resetToDefaultPlaylist() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val customFile = File(getApplication<Application>().filesDir, "custom_playlist.m3u8")
                if (customFile.exists()) customFile.delete()
            }
            loadInitialPlaylist()
            _statusMessage.value = "Varsayılan Adıyaman Optis listesine dönüldü."
        }
    }

    fun setSleepTimer(minutes: Int?) {
        sleepJob?.cancel()
        if (minutes == null || minutes <= 0) {
            _sleepMinutesRemaining.value = null
            return
        }
        _sleepMinutesRemaining.value = minutes
        sleepJob = viewModelScope.launch {
            var left = minutes
            while (left > 0) {
                delay(60_000L)
                left--
                _sleepMinutesRemaining.value = left
            }
            PlayerHolder.getOrCreatePlayer(getApplication()).pause()
            _sleepMinutesRemaining.value = null
        }
    }

    fun clearStatus() {
        _statusMessage.value = null
    }
}
