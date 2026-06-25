package com.owo.notifmirror.communication;

import android.content.Context;
import com.owo.notifmirror.data.repository.WearMirrorRepository;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Provider;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;

@ScopeMetadata("javax.inject.Singleton")
@QualifierMetadata("dagger.hilt.android.qualifiers.ApplicationContext")
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
public final class BluetoothCommunicationManager_Factory implements Factory<BluetoothCommunicationManager> {
  private final Provider<Context> contextProvider;

  private final Provider<WearMirrorRepository> repositoryProvider;

  private BluetoothCommunicationManager_Factory(Provider<Context> contextProvider,
      Provider<WearMirrorRepository> repositoryProvider) {
    this.contextProvider = contextProvider;
    this.repositoryProvider = repositoryProvider;
  }

  @Override
  public BluetoothCommunicationManager get() {
    return newInstance(contextProvider.get(), repositoryProvider.get());
  }

  public static BluetoothCommunicationManager_Factory create(Provider<Context> contextProvider,
      Provider<WearMirrorRepository> repositoryProvider) {
    return new BluetoothCommunicationManager_Factory(contextProvider, repositoryProvider);
  }

  public static BluetoothCommunicationManager newInstance(Context context,
      WearMirrorRepository repository) {
    return new BluetoothCommunicationManager(context, repository);
  }
}
