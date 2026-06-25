package com.found404.sidelink.data.repository;

import com.found404.sidelink.data.database.NotificationDao;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Provider;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;

@ScopeMetadata("javax.inject.Singleton")
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
public final class WearMirrorRepository_Factory implements Factory<WearMirrorRepository> {
  private final Provider<NotificationDao> notificationDaoProvider;

  private WearMirrorRepository_Factory(Provider<NotificationDao> notificationDaoProvider) {
    this.notificationDaoProvider = notificationDaoProvider;
  }

  @Override
  public WearMirrorRepository get() {
    return newInstance(notificationDaoProvider.get());
  }

  public static WearMirrorRepository_Factory create(
      Provider<NotificationDao> notificationDaoProvider) {
    return new WearMirrorRepository_Factory(notificationDaoProvider);
  }

  public static WearMirrorRepository newInstance(NotificationDao notificationDao) {
    return new WearMirrorRepository(notificationDao);
  }
}
