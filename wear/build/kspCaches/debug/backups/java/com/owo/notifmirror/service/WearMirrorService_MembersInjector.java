package com.owo.notifmirror.service;

import com.owo.notifmirror.communication.BluetoothCommunicationManager;
import dagger.MembersInjector;
import dagger.internal.DaggerGenerated;
import dagger.internal.InjectedFieldSignature;
import dagger.internal.Provider;
import dagger.internal.QualifierMetadata;
import javax.annotation.processing.Generated;

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
public final class WearMirrorService_MembersInjector implements MembersInjector<WearMirrorService> {
  private final Provider<BluetoothCommunicationManager> commManagerProvider;

  private WearMirrorService_MembersInjector(
      Provider<BluetoothCommunicationManager> commManagerProvider) {
    this.commManagerProvider = commManagerProvider;
  }

  @Override
  public void injectMembers(WearMirrorService instance) {
    injectCommManager(instance, commManagerProvider.get());
  }

  public static MembersInjector<WearMirrorService> create(
      Provider<BluetoothCommunicationManager> commManagerProvider) {
    return new WearMirrorService_MembersInjector(commManagerProvider);
  }

  @InjectedFieldSignature("com.owo.notifmirror.service.WearMirrorService.commManager")
  public static void injectCommManager(WearMirrorService instance,
      BluetoothCommunicationManager commManager) {
    instance.commManager = commManager;
  }
}
