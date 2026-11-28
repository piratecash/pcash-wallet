package cash.p.terminal.feature.logging.di

import cash.p.terminal.feature.logging.data.database.LoggingDatabase
import cash.p.terminal.feature.logging.data.repository.LoginRecordRepository
import cash.p.terminal.feature.logging.data.repository.LoginRecordStorage
import cash.p.terminal.feature.logging.detail.LoggingDetailViewModel
import cash.p.terminal.feature.logging.domain.usecase.DeleteLoggingOnDuressUseCase
import cash.p.terminal.feature.logging.domain.usecase.GetZecWalletsUseCase
import cash.p.terminal.feature.logging.domain.usecase.LogLoginAttemptUseCase
import cash.p.terminal.feature.logging.history.LoggingListViewModel
import cash.p.terminal.feature.logging.photo.AndroidLoginPhotoExporter
import cash.p.terminal.feature.logging.photo.AndroidLoginPhotoSource
import cash.p.terminal.feature.logging.photo.LoginPhotoExporter
import cash.p.terminal.feature.logging.photo.LoginPhotoSource
import cash.p.terminal.feature.logging.settings.AndroidLoginCameraAccess
import cash.p.terminal.feature.logging.settings.AndroidLoginJournalSecrets
import cash.p.terminal.feature.logging.settings.LoggingSettingsViewModel
import cash.p.terminal.feature.logging.settings.LoginCameraAccess
import cash.p.terminal.feature.logging.settings.LoginJournalSecrets
import io.horizontalsystems.core.ILoginRecordRepository
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.bind
import org.koin.dsl.module

val featureLoggingModule = module {
    // Database
    single { LoggingDatabase.create(get()) }
    single { get<LoggingDatabase>().loginRecordDao() }

    // Repository
    singleOf(::LoginRecordStorage)
    singleOf(::LoginRecordRepository) bind ILoginRecordRepository::class

    // Platform seams of the shared journal screens
    singleOf(::AndroidLoginPhotoSource) bind LoginPhotoSource::class
    singleOf(::AndroidLoginPhotoExporter) bind LoginPhotoExporter::class
    singleOf(::AndroidLoginJournalSecrets) bind LoginJournalSecrets::class
    singleOf(::AndroidLoginCameraAccess) bind LoginCameraAccess::class

    // Use Cases
    singleOf(::LogLoginAttemptUseCase)
    singleOf(::DeleteLoggingOnDuressUseCase)
    singleOf(::GetZecWalletsUseCase)

    // ViewModels
    viewModelOf(::LoggingSettingsViewModel)
    viewModelOf(::LoggingListViewModel)
    viewModelOf(::LoggingDetailViewModel)
}
