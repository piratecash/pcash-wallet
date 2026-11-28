package cash.p.terminal.shared.desktop.journal

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.paging.map
import cash.p.terminal.feature.logging.journal.JournalRecord
import cash.p.terminal.feature.logging.journal.JournalRecordKind
import cash.p.terminal.feature.logging.journal.JournalStore
import cash.p.terminal.feature.logging.photo.LoginPhoto
import cash.p.terminal.feature.logging.photo.LoginPhotoSource
import cash.p.terminal.shared.desktop.vault.DesktopVault
import io.horizontalsystems.core.DispatcherProvider
import io.horizontalsystems.core.ICurrentDateProvider
import io.horizontalsystems.core.ILoginRecord
import io.horizontalsystems.core.ILoginRecordRepository
import io.horizontalsystems.core.entities.AutoDeletePeriod
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** A level sees its own journal only; the records it imported from the mailbox are part of it. */
@OptIn(ExperimentalCoroutinesApi::class)
class DesktopLoginRecordRepository(
    private val vault: DesktopVault,
    private val dateProvider: ICurrentDateProvider,
    private val dispatcherProvider: DispatcherProvider,
) : ILoginRecordRepository, LoginPhotoSource {

    override fun getPager(level: Int): Flow<PagingData<ILoginRecord>> = journalFlow(level) { journal ->
        journal?.count?.flatMapLatest {
            Pager(PagingConfig(pageSize = PAGE_SIZE, enablePlaceholders = false)) { JournalPagingSource(journal) }.flow
        } ?: flowOf(PagingData.empty())
    }.map { page -> page.map { JournalLoginRecord(it) } }

    override fun observeHasRecords(level: Int): Flow<Boolean> =
        journalFlow(level) { journal -> journal?.count?.map { it > 0 } ?: flowOf(false) }

    override suspend fun deleteAll(level: Int) {
        io { journal(level)?.deleteAll() }
    }

    override suspend fun deleteById(id: Long) {
        io { rootJournal()?.deleteById(id) }
    }

    /** Desktop photos never live in files; the journal writes its records itself. */
    override suspend fun insert(
        timestamp: Long,
        isSuccessful: Boolean,
        userLevel: Int,
        accountId: String,
        photoPath: String?,
    ): Long = io {
        check(photoPath == null) { "A desktop login photo is never a file" }
        val kind = if (isSuccessful) JournalRecordKind.SUCCESS else JournalRecordKind.FAILED
        checkNotNull(journal(userLevel)) { "The journal is not open" }.insert(timestamp, kind, accountId, null)
    }

    override suspend fun deleteExpired(level: Int, period: AutoDeletePeriod) {
        val cutoff = period.cutoff(dateProvider.currentDate.time) ?: return
        io { journal(level)?.deleteOlderThan(cutoff) }
    }

    override suspend fun getAll(level: Int): List<ILoginRecord> =
        io { journal(level)?.all().orEmpty().map(::JournalLoginRecord) }

    override suspend fun read(photo: LoginPhoto): ByteArray? =
        photo.key.toLongOrNull()?.let { id -> io { rootJournal()?.photo(id) } }

    private fun journal(level: Int): JournalStore? = vault.openSession()?.chain?.safeOrNull(level)?.journal

    private fun rootJournal(): JournalStore? = vault.openSession()?.chain?.root?.journal

    private fun <T> journalFlow(level: Int, flow: (JournalStore?) -> Flow<T>): Flow<T> =
        vault.sessions.flatMapLatest { flow(it?.chain?.safeOrNull(level)?.journal) }

    private suspend fun <T> io(block: suspend () -> T): T = withContext(dispatcherProvider.io) { block() }

    private companion object {
        const val PAGE_SIZE = 20
    }
}

/** A duress entry shows as Android's record of a duress login; everything else as the level's own. */
private class JournalLoginRecord(record: JournalRecord) : ILoginRecord {
    override val id = record.id
    override val accountId = record.accountId
    override val timestamp = record.timestamp
    override val isSuccessful = record.kind != JournalRecordKind.FAILED
    override val userLevel = if (record.kind == JournalRecordKind.DURESS_ENTRY) 1 else 0
    override val photo = LoginPhoto(record.id.toString()).takeIf { record.hasPhoto && !record.photoRemoved }
}

private class JournalPagingSource(private val journal: JournalStore) : PagingSource<Int, JournalRecord>() {
    override fun getRefreshKey(state: PagingState<Int, JournalRecord>): Int? = null

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, JournalRecord> {
        val offset = params.key ?: 0
        val records = journal.page(params.loadSize, offset)
        return LoadResult.Page(
            data = records,
            prevKey = null,
            nextKey = if (records.size < params.loadSize) null else offset + records.size,
        )
    }
}
