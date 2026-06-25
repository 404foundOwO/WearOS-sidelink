package com.owo.notifmirror.ui;

import android.app.Application;
import com.owo.notifmirror.data.repository.WearMirrorRepository;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
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
public final class WearViewModel_Factory implements Factory<WearViewModel> {
  private final Provider<Application> applicationProvider;

  private final Provider<WearMirrorRepository> repositoryProvider;

  private WearViewModel_Factory(Provider<Application> applicationProvider,
      Provider<WearMirrorRepository> repositoryProvider) {
    this.applicationProvider = applicationProvider;
    this.repositoryProvider = repositoryProvider;
  }

  @Override
  public WearViewModel get() {
    return newInstance(applicationProvider.get(), repositoryProvider.get());
  }

  public static WearViewModel_Factory create(Provider<Application> applicationProvider,
      Provider<WearMirrorRepository> repositoryProvider) {
    return new WearViewModel_Factory(applicationProvider, repositoryProvider);
  }

  public static WearViewModel newInstance(Application application,
      WearMirrorRepository repository) {
    return new WearViewModel(application, repository);
  }
}
