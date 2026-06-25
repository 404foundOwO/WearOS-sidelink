package com.found404.sidelink.di;

import com.found404.sidelink.data.database.NotificationDao;
import com.found404.sidelink.data.database.WearDatabase;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.Provider;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;

@ScopeMetadata
@QualifierMetadata
@DaggerGenerated
@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes",
    "KotlinInternal",
    "KotlinInternalInJava",
    "cast",
    "deprecation",
    "nullness:initialization.field.uninitialized"
})
public final class DatabaseModule_ProvideNotificationDaoFactory implements Factory<NotificationDao> {
  private final Provider<WearDatabase> databaseProvider;

  private DatabaseModule_ProvideNotificationDaoFactory(Provider<WearDatabase> databaseProvider) {
    this.databaseProvider = databaseProvider;
  }

  @Override
  public NotificationDao get() {
    return provideNotificationDao(databaseProvider.get());
  }

  public static DatabaseModule_ProvideNotificationDaoFactory create(
      Provider<WearDatabase> databaseProvider) {
    return new DatabaseModule_ProvideNotificationDaoFactory(databaseProvider);
  }

  public static NotificationDao provideNotificationDao(WearDatabase database) {
    return Preconditions.checkNotNullFromProvides(DatabaseModule.INSTANCE.provideNotificationDao(database));
  }
}
