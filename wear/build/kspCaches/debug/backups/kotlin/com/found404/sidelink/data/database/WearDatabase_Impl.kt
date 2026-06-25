package com.found404.sidelink.`data`.database

import androidx.room.InvalidationTracker
import androidx.room.RoomOpenDelegate
import androidx.room.migration.AutoMigrationSpec
import androidx.room.migration.Migration
import androidx.room.util.TableInfo
import androidx.room.util.TableInfo.Companion.read
import androidx.room.util.dropFtsSyncTriggers
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import javax.`annotation`.processing.Generated
import kotlin.Lazy
import kotlin.String
import kotlin.Suppress
import kotlin.collections.List
import kotlin.collections.Map
import kotlin.collections.MutableList
import kotlin.collections.MutableMap
import kotlin.collections.MutableSet
import kotlin.collections.Set
import kotlin.collections.mutableListOf
import kotlin.collections.mutableMapOf
import kotlin.collections.mutableSetOf
import kotlin.reflect.KClass

@Generated(value = ["androidx.room.RoomProcessor"])
@Suppress(names = ["UNCHECKED_CAST", "DEPRECATION", "REDUNDANT_PROJECTION", "REMOVAL"])
public class WearDatabase_Impl : WearDatabase() {
  private val _notificationDao: Lazy<NotificationDao> = lazy {
    NotificationDao_Impl(this)
  }

  protected override fun createOpenDelegate(): RoomOpenDelegate {
    val _openDelegate: RoomOpenDelegate = object : RoomOpenDelegate(1,
        "b4e0a1c4d4a713e0014d2aeec97684c2", "1eadccf0acf3788380adef9e9992bf86") {
      public override fun createAllTables(connection: SQLiteConnection) {
        connection.execSQL("CREATE TABLE IF NOT EXISTS `notifications` (`id` TEXT NOT NULL, `packageName` TEXT NOT NULL, `title` TEXT, `text` TEXT, `timestamp` INTEGER NOT NULL, `iconBase64` TEXT, PRIMARY KEY(`id`))")
        connection.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
        connection.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'b4e0a1c4d4a713e0014d2aeec97684c2')")
      }

      public override fun dropAllTables(connection: SQLiteConnection) {
        connection.execSQL("DROP TABLE IF EXISTS `notifications`")
      }

      public override fun onCreate(connection: SQLiteConnection) {
      }

      public override fun onOpen(connection: SQLiteConnection) {
        internalInitInvalidationTracker(connection)
      }

      public override fun onPreMigrate(connection: SQLiteConnection) {
        dropFtsSyncTriggers(connection)
      }

      public override fun onPostMigrate(connection: SQLiteConnection) {
      }

      public override fun onValidateSchema(connection: SQLiteConnection):
          RoomOpenDelegate.ValidationResult {
        val _columnsNotifications: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsNotifications.put("id", TableInfo.Column("id", "TEXT", true, 1, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsNotifications.put("packageName", TableInfo.Column("packageName", "TEXT", true, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsNotifications.put("title", TableInfo.Column("title", "TEXT", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsNotifications.put("text", TableInfo.Column("text", "TEXT", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsNotifications.put("timestamp", TableInfo.Column("timestamp", "INTEGER", true, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsNotifications.put("iconBase64", TableInfo.Column("iconBase64", "TEXT", false, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysNotifications: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesNotifications: MutableSet<TableInfo.Index> = mutableSetOf()
        val _infoNotifications: TableInfo = TableInfo("notifications", _columnsNotifications,
            _foreignKeysNotifications, _indicesNotifications)
        val _existingNotifications: TableInfo = read(connection, "notifications")
        if (!_infoNotifications.equals(_existingNotifications)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |notifications(com.found404.sidelink.data.database.NotificationEntity).
              | Expected:
              |""".trimMargin() + _infoNotifications + """
              |
              | Found:
              |""".trimMargin() + _existingNotifications)
        }
        return RoomOpenDelegate.ValidationResult(true, null)
      }
    }
    return _openDelegate
  }

  protected override fun createInvalidationTracker(): InvalidationTracker {
    val _shadowTablesMap: MutableMap<String, String> = mutableMapOf()
    val _viewTables: MutableMap<String, Set<String>> = mutableMapOf()
    return InvalidationTracker(this, _shadowTablesMap, _viewTables, "notifications")
  }

  public override fun clearAllTables() {
    super.performClear(false, "notifications")
  }

  protected override fun getRequiredTypeConverterClasses(): Map<KClass<*>, List<KClass<*>>> {
    val _typeConvertersMap: MutableMap<KClass<*>, List<KClass<*>>> = mutableMapOf()
    _typeConvertersMap.put(NotificationDao::class, NotificationDao_Impl.getRequiredConverters())
    return _typeConvertersMap
  }

  public override fun getRequiredAutoMigrationSpecClasses(): Set<KClass<out AutoMigrationSpec>> {
    val _autoMigrationSpecsSet: MutableSet<KClass<out AutoMigrationSpec>> = mutableSetOf()
    return _autoMigrationSpecsSet
  }

  public override
      fun createAutoMigrations(autoMigrationSpecs: Map<KClass<out AutoMigrationSpec>, AutoMigrationSpec>):
      List<Migration> {
    val _autoMigrations: MutableList<Migration> = mutableListOf()
    return _autoMigrations
  }

  public override fun notificationDao(): NotificationDao = _notificationDao.value
}
