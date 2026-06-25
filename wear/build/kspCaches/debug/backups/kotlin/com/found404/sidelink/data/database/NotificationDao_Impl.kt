package com.found404.sidelink.`data`.database

import androidx.room.EntityDeleteOrUpdateAdapter
import androidx.room.EntityInsertAdapter
import androidx.room.RoomDatabase
import androidx.room.coroutines.createFlow
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.performInTransactionSuspending
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteStatement
import javax.`annotation`.processing.Generated
import kotlin.Int
import kotlin.Long
import kotlin.String
import kotlin.Suppress
import kotlin.Unit
import kotlin.collections.List
import kotlin.collections.MutableList
import kotlin.collections.mutableListOf
import kotlin.reflect.KClass
import kotlinx.coroutines.flow.Flow

@Generated(value = ["androidx.room.RoomProcessor"])
@Suppress(names = ["UNCHECKED_CAST", "DEPRECATION", "REDUNDANT_PROJECTION", "REMOVAL"])
public class NotificationDao_Impl(
  __db: RoomDatabase,
) : NotificationDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfNotificationEntity: EntityInsertAdapter<NotificationEntity>

  private val __deleteAdapterOfNotificationEntity: EntityDeleteOrUpdateAdapter<NotificationEntity>
  init {
    this.__db = __db
    this.__insertAdapterOfNotificationEntity = object : EntityInsertAdapter<NotificationEntity>() {
      protected override fun createQuery(): String =
          "INSERT OR REPLACE INTO `notifications` (`id`,`packageName`,`title`,`text`,`timestamp`,`iconBase64`) VALUES (?,?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: NotificationEntity) {
        statement.bindText(1, entity.id)
        statement.bindText(2, entity.packageName)
        val _tmpTitle: String? = entity.title
        if (_tmpTitle == null) {
          statement.bindNull(3)
        } else {
          statement.bindText(3, _tmpTitle)
        }
        val _tmpText: String? = entity.text
        if (_tmpText == null) {
          statement.bindNull(4)
        } else {
          statement.bindText(4, _tmpText)
        }
        statement.bindLong(5, entity.timestamp)
        val _tmpIconBase64: String? = entity.iconBase64
        if (_tmpIconBase64 == null) {
          statement.bindNull(6)
        } else {
          statement.bindText(6, _tmpIconBase64)
        }
      }
    }
    this.__deleteAdapterOfNotificationEntity = object :
        EntityDeleteOrUpdateAdapter<NotificationEntity>() {
      protected override fun createQuery(): String = "DELETE FROM `notifications` WHERE `id` = ?"

      protected override fun bind(statement: SQLiteStatement, entity: NotificationEntity) {
        statement.bindText(1, entity.id)
      }
    }
  }

  public override suspend fun insert(notification: NotificationEntity): Unit =
      performSuspending(__db, false, true) { _connection ->
    __insertAdapterOfNotificationEntity.insert(_connection, notification)
  }

  public override suspend fun delete(notification: NotificationEntity): Unit =
      performSuspending(__db, false, true) { _connection ->
    __deleteAdapterOfNotificationEntity.handle(_connection, notification)
  }

  public override suspend fun syncNotifications(notifications: List<NotificationEntity>): Unit =
      performInTransactionSuspending(__db) {
    super@NotificationDao_Impl.syncNotifications(notifications)
  }

  public override fun getAllNotifications(): Flow<List<NotificationEntity>> {
    val _sql: String = "SELECT * FROM notifications ORDER BY timestamp DESC"
    return createFlow(__db, false, arrayOf("notifications")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfPackageName: Int = getColumnIndexOrThrow(_stmt, "packageName")
        val _columnIndexOfTitle: Int = getColumnIndexOrThrow(_stmt, "title")
        val _columnIndexOfText: Int = getColumnIndexOrThrow(_stmt, "text")
        val _columnIndexOfTimestamp: Int = getColumnIndexOrThrow(_stmt, "timestamp")
        val _columnIndexOfIconBase64: Int = getColumnIndexOrThrow(_stmt, "iconBase64")
        val _result: MutableList<NotificationEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: NotificationEntity
          val _tmpId: String
          _tmpId = _stmt.getText(_columnIndexOfId)
          val _tmpPackageName: String
          _tmpPackageName = _stmt.getText(_columnIndexOfPackageName)
          val _tmpTitle: String?
          if (_stmt.isNull(_columnIndexOfTitle)) {
            _tmpTitle = null
          } else {
            _tmpTitle = _stmt.getText(_columnIndexOfTitle)
          }
          val _tmpText: String?
          if (_stmt.isNull(_columnIndexOfText)) {
            _tmpText = null
          } else {
            _tmpText = _stmt.getText(_columnIndexOfText)
          }
          val _tmpTimestamp: Long
          _tmpTimestamp = _stmt.getLong(_columnIndexOfTimestamp)
          val _tmpIconBase64: String?
          if (_stmt.isNull(_columnIndexOfIconBase64)) {
            _tmpIconBase64 = null
          } else {
            _tmpIconBase64 = _stmt.getText(_columnIndexOfIconBase64)
          }
          _item =
              NotificationEntity(_tmpId,_tmpPackageName,_tmpTitle,_tmpText,_tmpTimestamp,_tmpIconBase64)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun deleteById(id: String) {
    val _sql: String = "DELETE FROM notifications WHERE id = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, id)
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun deleteAll() {
    val _sql: String = "DELETE FROM notifications"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun deleteOldNotifications(limit: Int) {
    val _sql: String =
        "DELETE FROM notifications WHERE id NOT IN (SELECT id FROM notifications ORDER BY timestamp DESC LIMIT ?)"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, limit.toLong())
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public companion object {
    public fun getRequiredConverters(): List<KClass<*>> = emptyList()
  }
}
