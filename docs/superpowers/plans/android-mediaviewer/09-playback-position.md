# 播放位置存储 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 使用 Room 持久化视频和音频进度，并精确执行 10 秒恢复与 95% 清除规则。

**Architecture:** `PlaybackPositionPolicy` 是纯函数；`RoomPlaybackPositionStore` 只把策略应用于 DAO。媒体键从逻辑媒体 URL 规范化而来，与本次 DNS 解析 IP 完全无关；保存时刻由调用方传入以便测试。

**Tech Stack:** Room 2.8.4、KSP 2.3.10、Coroutines、Robolectric API 29。

## Global Constraints

- 表字段为 `media_key`、`position_ms`、`duration_ms`、`updated_at`。
- 主键由逻辑服务器 URL 与逻辑媒体路径组成，不包含会话 IPv4。
- 不足 10 秒不恢复。
- 播放完成或位置达到总时长 95% 时删除。
- 图片不调用播放位置存储。
- 5 秒周期保存、暂停、退出和后台保存由 PlayerViewModel 在任务 11 调用本接口。

---

### Task 9: Room Schema、策略与 Store

**Files:**

- Modify: `app/build.gradle.kts`
- Create: `app/src/main/java/com/local/mediaviewer/playback/PlaybackPositionEntity.kt`
- Create: `app/src/main/java/com/local/mediaviewer/playback/PlaybackPositionDao.kt`
- Create: `app/src/main/java/com/local/mediaviewer/playback/MediaViewerDatabase.kt`
- Create: `app/src/main/java/com/local/mediaviewer/playback/PlaybackMediaKey.kt`
- Create: `app/src/main/java/com/local/mediaviewer/playback/PlaybackPositionPolicy.kt`
- Create: `app/src/main/java/com/local/mediaviewer/playback/PlaybackPositionStore.kt`
- Create after KSP generation: `app/schemas/com.local.mediaviewer.playback.MediaViewerDatabase/1.json`
- Test: `app/src/test/java/com/local/mediaviewer/playback/PlaybackMediaKeyTest.kt`
- Test: `app/src/test/java/com/local/mediaviewer/playback/PlaybackPositionPolicyTest.kt`
- Test: `app/src/test/java/com/local/mediaviewer/playback/PlaybackPositionStoreTest.kt`
- Test: `app/src/test/java/com/local/mediaviewer/playback/PlaybackPositionDaoTest.kt`

**Interfaces:**

- Consumes: 逻辑媒体 URL 字符串。
- Produces:

```kotlin
interface PlaybackPositionStore {
    suspend fun resumePosition(mediaKey: String): Long?
    suspend fun record(
        mediaKey: String,
        positionMs: Long,
        durationMs: Long,
        updatedAtEpochMs: Long,
        ended: Boolean = false,
    )
    suspend fun clear(mediaKey: String)
}

object PlaybackMediaKey {
    fun fromLogicalUrl(logicalUrl: String): String
}

object PlaybackPositionPolicy {
    fun resumePosition(entity: PlaybackPositionEntity?): Long?
    fun shouldDelete(positionMs: Long, durationMs: Long, ended: Boolean): Boolean
}
```

- [ ] **Step 1: 写媒体键和阈值失败测试**

`PlaybackMediaKeyTest.kt`：

```kotlin
package com.local.mediaviewer.playback

import com.local.mediaviewer.model.SessionEndpoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Test

class PlaybackMediaKeyTest {
    @Test
    fun `DNS 请求 IPv4 变化不改变逻辑媒体键`() {
        val logical =
            "http://media.example:8080/middle/%E5%BD%B1%E7%89%87.mp4"
        val first = SessionEndpoint(
            "http://media.example:8080",
            "http://192.0.2.1:8080",
            "192.0.2.1",
        )
        val second = first.copy(
            requestBaseUrl = "http://192.0.2.2:8080",
            ipv4 = "192.0.2.2",
        )

        assertNotEquals(first.requestUrlFor(logical), second.requestUrlFor(logical))
        val mediaKey = PlaybackMediaKey.fromLogicalUrl(logical)
        assertEquals(logical, mediaKey)
        assertFalse(mediaKey.contains("192.0.2."))
    }
}
```

`PlaybackPositionPolicyTest.kt`：

```kotlin
package com.local.mediaviewer.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackPositionPolicyTest {
    @Test
    fun `不足十秒不恢复而十秒整恢复`() {
        assertNull(
            PlaybackPositionPolicy.resumePosition(entity(9_999, 100_000)),
        )
        assertEquals(
            10_000L,
            PlaybackPositionPolicy.resumePosition(entity(10_000, 100_000)),
        )
    }

    @Test
    fun `完成事件或达到百分之九十五删除`() {
        assertFalse(
            PlaybackPositionPolicy.shouldDelete(94_999, 100_000, false),
        )
        assertTrue(
            PlaybackPositionPolicy.shouldDelete(95_000, 100_000, false),
        )
        assertTrue(
            PlaybackPositionPolicy.shouldDelete(20_000, 100_000, true),
        )
    }

    private fun entity(position: Long, duration: Long) =
        PlaybackPositionEntity("key", position, duration, 1)
}
```

- [ ] **Step 2: 运行纯函数测试并观察预期失败**

Run:

```powershell
.\gradlew.bat testDebugUnitTest `
  --tests 'com.local.mediaviewer.playback.PlaybackMediaKeyTest' `
  --tests 'com.local.mediaviewer.playback.PlaybackPositionPolicyTest'
```

Expected:

```text
Kotlin compilation fails because playback position types are unresolved
```

- [ ] **Step 3: 实现 Room Entity、媒体键和策略**

`PlaybackPositionEntity.kt`：

```kotlin
package com.local.mediaviewer.playback

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "playback_positions")
data class PlaybackPositionEntity(
    @PrimaryKey
    @ColumnInfo(name = "media_key")
    val mediaKey: String,
    @ColumnInfo(name = "position_ms")
    val positionMs: Long,
    @ColumnInfo(name = "duration_ms")
    val durationMs: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAtEpochMs: Long,
)
```

`PlaybackMediaKey.kt`：

```kotlin
package com.local.mediaviewer.playback

import okhttp3.HttpUrl.Companion.toHttpUrl

object PlaybackMediaKey {
    fun fromLogicalUrl(logicalUrl: String): String =
        logicalUrl.toHttpUrl()
            .newBuilder()
            .fragment(null)
            .build()
            .toString()
}
```

`PlaybackPositionPolicy.kt`：

```kotlin
package com.local.mediaviewer.playback

object PlaybackPositionPolicy {
    private const val MINIMUM_RESUME_MS = 10_000L
    private const val COMPLETION_NUMERATOR = 95L
    private const val COMPLETION_DENOMINATOR = 100L

    fun resumePosition(entity: PlaybackPositionEntity?): Long? {
        entity ?: return null
        if (entity.positionMs < MINIMUM_RESUME_MS) return null
        if (shouldDelete(entity.positionMs, entity.durationMs, false)) return null
        return entity.positionMs
    }

    fun shouldDelete(
        positionMs: Long,
        durationMs: Long,
        ended: Boolean,
    ): Boolean {
        if (ended) return true
        if (durationMs <= 0L) return false
        return positionMs * COMPLETION_DENOMINATOR >=
            durationMs * COMPLETION_NUMERATOR
    }
}
```

- [ ] **Step 4: 写 Store 失败测试**

`PlaybackPositionStoreTest.kt`：

```kotlin
package com.local.mediaviewer.playback

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackPositionStoreTest {
    @Test
    fun `记录中途位置并恢复`() = runTest {
        val dao = FakePositionDao()
        val store = RoomPlaybackPositionStore(dao)
        store.record("logical-key", 30_000, 100_000, 123)

        assertEquals(30_000L, store.resumePosition("logical-key"))
        assertEquals(123L, dao.value?.updatedAtEpochMs)
    }

    @Test
    fun `达到完成阈值或结束时删除`() = runTest {
        val dao = FakePositionDao()
        val store = RoomPlaybackPositionStore(dao)
        store.record("logical-key", 50_000, 100_000, 1)
        store.record("logical-key", 95_000, 100_000, 2)
        assertNull(dao.value)

        store.record("logical-key", 20_000, 100_000, 3)
        store.record("logical-key", 20_000, 100_000, 4, ended = true)
        assertNull(dao.value)
    }
}

private class FakePositionDao : PlaybackPositionDao {
    var value: PlaybackPositionEntity? = null
    override suspend fun find(mediaKey: String) =
        value?.takeIf { it.mediaKey == mediaKey }
    override suspend fun upsert(entity: PlaybackPositionEntity) {
        value = entity
    }
    override suspend fun delete(mediaKey: String) {
        if (value?.mediaKey == mediaKey) value = null
    }
}
```

- [ ] **Step 5: 实现 DAO 与 Store**

`PlaybackPositionDao.kt`：

```kotlin
package com.local.mediaviewer.playback

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface PlaybackPositionDao {
    @Query("SELECT * FROM playback_positions WHERE media_key = :mediaKey")
    suspend fun find(mediaKey: String): PlaybackPositionEntity?

    @Upsert
    suspend fun upsert(entity: PlaybackPositionEntity)

    @Query("DELETE FROM playback_positions WHERE media_key = :mediaKey")
    suspend fun delete(mediaKey: String)
}
```

`PlaybackPositionStore.kt`：

```kotlin
package com.local.mediaviewer.playback

interface PlaybackPositionStore {
    suspend fun resumePosition(mediaKey: String): Long?
    suspend fun record(
        mediaKey: String,
        positionMs: Long,
        durationMs: Long,
        updatedAtEpochMs: Long,
        ended: Boolean = false,
    )
    suspend fun clear(mediaKey: String)
}

class RoomPlaybackPositionStore(
    private val dao: PlaybackPositionDao,
) : PlaybackPositionStore {
    override suspend fun resumePosition(mediaKey: String): Long? =
        PlaybackPositionPolicy.resumePosition(dao.find(mediaKey))

    override suspend fun record(
        mediaKey: String,
        positionMs: Long,
        durationMs: Long,
        updatedAtEpochMs: Long,
        ended: Boolean,
    ) {
        if (
            PlaybackPositionPolicy.shouldDelete(
                positionMs,
                durationMs,
                ended,
            )
        ) {
            dao.delete(mediaKey)
        } else {
            dao.upsert(
                PlaybackPositionEntity(
                    mediaKey,
                    positionMs.coerceAtLeast(0L),
                    durationMs.coerceAtLeast(0L),
                    updatedAtEpochMs,
                ),
            )
        }
    }

    override suspend fun clear(mediaKey: String) = dao.delete(mediaKey)
}
```

- [ ] **Step 6: 创建数据库并配置 Schema 导出**

`MediaViewerDatabase.kt`：

```kotlin
package com.local.mediaviewer.playback

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [PlaybackPositionEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class MediaViewerDatabase : RoomDatabase() {
    abstract fun playbackPositionDao(): PlaybackPositionDao
}
```

在 `app/build.gradle.kts` 的 `android {}` 后添加：

```kotlin
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}
```

首次构建后确认生成：

```text
app/schemas/com.local.mediaviewer.playback.MediaViewerDatabase/1.json
```

该 JSON 必须包含表 `playback_positions` 和四个固定列名。

- [ ] **Step 7: 写 API 29 Room DAO 测试**

`PlaybackPositionDaoTest.kt`：

```kotlin
package com.local.mediaviewer.playback

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class PlaybackPositionDaoTest {
    private lateinit var database: MediaViewerDatabase
    private lateinit var dao: PlaybackPositionDao

    @Before
    fun createDatabase() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            MediaViewerDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = database.playbackPositionDao()
    }

    @After fun closeDatabase() = database.close()

    @Test
    fun `upsert 覆盖同一媒体键并可删除`() = runTest {
        dao.upsert(PlaybackPositionEntity("key", 10, 100, 1))
        dao.upsert(PlaybackPositionEntity("key", 20, 100, 2))
        assertEquals(20L, dao.find("key")?.positionMs)
        dao.delete("key")
        assertEquals(null, dao.find("key"))
    }
}
```

- [ ] **Step 8: 运行本任务门禁**

Run:

```powershell
.\gradlew.bat testDebugUnitTest `
  --tests 'com.local.mediaviewer.playback.*'
.\gradlew.bat kspDebugKotlin lintDebug
```

Expected:

```text
Media key, policy, store, and API 29 DAO tests pass
Room schema 1.json is generated
Lint reports 0 errors
```

检查 Schema：

```powershell
$schema = Get-Content -Raw `
  'app\schemas\com.local.mediaviewer.playback.MediaViewerDatabase\1.json'
@('playback_positions','media_key','position_ms','duration_ms','updated_at') |
  ForEach-Object {
    if (-not $schema.Contains($_)) { throw "Room schema missing $_" }
  }
```

- [ ] **Step 9: 提交**

```powershell
git add app/build.gradle.kts app/schemas `
  app/src/main/java/com/local/mediaviewer/playback `
  app/src/test/java/com/local/mediaviewer/playback
git commit -m "feat: persist logical media playback positions"
```
