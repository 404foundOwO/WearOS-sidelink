package com.found404.sidelink.shared

import java.util.UUID

object CommunicationConstants {
    val UUID_NOTIF_MIRROR: UUID = UUID.fromString("8ce255c0-200a-11ec-9621-0242ac130002")
    const val DEFAULT_BLUETOOTH_NAME = "Sidelink"

    // Message Types
    const val TYPE_NOTIFICATION = "notification"
    const val TYPE_DISMISS = "dismiss"
    const val TYPE_REPLY = "reply"
    const val TYPE_MEDIA_STATUS = "media_status"
    const val TYPE_MEDIA_CONTROL = "media_control"
    const val TYPE_MEDIA_CLEARED = "media_cleared"
    const val TYPE_HEARTBEAT = "heartbeat"
    const val TYPE_PING = "ping"
    const val TYPE_PONG = "pong"
    const val TYPE_NOTIF_ACK = "notif_ack"
    const val TYPE_VOLUME_DELTA = "volume_delta"
    const val TYPE_VOLUME_STATE = "volume_state"
    const val TYPE_SYNC_ALL = "sync_all"

    // Action Constants
    const val ACTION_PLAY = "play"
    const val ACTION_PAUSE = "pause"
    const val ACTION_NEXT = "next"
    const val ACTION_PREVIOUS = "previous"

    // JSON Keys
    const val KEY_TYPE = "type"
    const val KEY_ID = "id"
    const val KEY_PACKAGE = "packageName"
    const val KEY_TITLE = "title"
    const val KEY_TEXT = "text"
    const val KEY_TIMESTAMP = "timestamp"
    const val KEY_ICON = "icon"
    const val KEY_ARTIST = "artist"
    const val KEY_ALBUM = "album"
    const val KEY_IS_PLAYING = "isPlaying"
    const val KEY_ARTWORK = "artwork"
    const val KEY_ACTION = "action"
    const val KEY_VALUE = "value"
    const val KEY_DATA = "data"
    const val KEY_HAS_REPLY = "hasReply"
    const val KEY_APP_NAME = "appName"
    const val KEY_BATTERY_LEVEL = "batteryLevel"
    const val TYPE_BATTERY_STATUS = "battery_status"
}
