package com.example.filemanagementapp.preview

import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

import androidx.media3.datasource.DefaultHttpDataSource

class AudioPlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        
        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setDefaultRequestProperties(mapOf("ngrok-skip-browser-warning" to "69420"))
        
        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(androidx.media3.exoplayer.source.DefaultMediaSourceFactory(dataSourceFactory))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true
            )
            .build()
            
        val forwardingPlayer = object : androidx.media3.common.ForwardingPlayer(player) {
            override fun getAvailableCommands(): androidx.media3.common.Player.Commands {
                return super.getAvailableCommands().buildUpon()
                    .remove(androidx.media3.common.Player.COMMAND_SEEK_TO_NEXT)
                    .remove(androidx.media3.common.Player.COMMAND_SEEK_TO_PREVIOUS)
                    .remove(androidx.media3.common.Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                    .remove(androidx.media3.common.Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                    .add(androidx.media3.common.Player.COMMAND_SEEK_FORWARD)
                    .add(androidx.media3.common.Player.COMMAND_SEEK_BACK)
                    .build()
            }
        }
            
        mediaSession = MediaSession.Builder(this, forwardingPlayer).build()
        
        val seekBackBtn = androidx.media3.session.CommandButton.Builder()
            .setPlayerCommand(androidx.media3.common.Player.COMMAND_SEEK_BACK)
            .setIconResId(com.example.filemanagementapp.R.drawable.ic_replay_10)
            .setDisplayName("Rewind")
            .build()
            
        val seekForwardBtn = androidx.media3.session.CommandButton.Builder()
            .setPlayerCommand(androidx.media3.common.Player.COMMAND_SEEK_FORWARD)
            .setIconResId(com.example.filemanagementapp.R.drawable.ic_forward_10)
            .setDisplayName("Forward")
            .build()
            
        mediaSession?.setCustomLayout(listOf(seekBackBtn, seekForwardBtn))
        
        val provider = object : androidx.media3.session.DefaultMediaNotificationProvider(this) {
            override fun getMediaButtons(
                session: MediaSession,
                playerCommands: androidx.media3.common.Player.Commands,
                customLayout: com.google.common.collect.ImmutableList<androidx.media3.session.CommandButton>,
                showPauseButton: Boolean
            ): com.google.common.collect.ImmutableList<androidx.media3.session.CommandButton> {
                val playPauseCmd = if (showPauseButton) {
                    androidx.media3.session.CommandButton.Builder()
                        .setPlayerCommand(androidx.media3.common.Player.COMMAND_PLAY_PAUSE)
                        .setIconResId(com.example.filemanagementapp.R.drawable.ic_pause)
                        .setDisplayName("Pause")
                        .build()
                } else {
                    androidx.media3.session.CommandButton.Builder()
                        .setPlayerCommand(androidx.media3.common.Player.COMMAND_PLAY_PAUSE)
                        .setIconResId(com.example.filemanagementapp.R.drawable.ic_play)
                        .setDisplayName("Play")
                        .build()
                }
                
                return com.google.common.collect.ImmutableList.of(seekBackBtn, playPauseCmd, seekForwardBtn)
            }
        }
        setMediaNotificationProvider(provider)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: android.content.Intent?) {
        super.onTaskRemoved(rootIntent)
        
        val currentUri = mediaSession?.player?.currentMediaItem?.localConfiguration?.uri?.toString()
        val extension = currentUri?.substringAfterLast('.', "")?.lowercase()
        val isVideo = extension in listOf("mp4", "mkv", "webm", "avi")
        
        if (isVideo) {
            sendBroadcast(android.content.Intent("com.example.filemanagementapp.CLOSE_PIP"))
            mediaSession?.player?.stop()
            stopSelf()
        }
    }

    override fun onDestroy() {
        mediaSession?.player?.release()
        mediaSession?.release()
        mediaSession = null
        super.onDestroy()
    }
}
