package com.found404.sidelink.service;

import com.found404.sidelink.communication.BluetoothCommunicationManager;
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
public final class MirrorMediaBrowserService_MembersInjector implements MembersInjector<MirrorMediaBrowserService> {
  private final Provider<BluetoothCommunicationManager> commManagerProvider;

  private MirrorMediaBrowserService_MembersInjector(
      Provider<BluetoothCommunicationManager> commManagerProvider) {
    this.commManagerProvider = commManagerProvider;
  }

  @Override
  public void injectMembers(MirrorMediaBrowserService instance) {
    injectCommManager(instance, commManagerProvider.get());
  }

  public static MembersInjector<MirrorMediaBrowserService> create(
      Provider<BluetoothCommunicationManager> commManagerProvider) {
    return new MirrorMediaBrowserService_MembersInjector(commManagerProvider);
  }

  @InjectedFieldSignature("com.found404.sidelink.service.MirrorMediaBrowserService.commManager")
  public static void injectCommManager(MirrorMediaBrowserService instance,
      BluetoothCommunicationManager commManager) {
    instance.commManager = commManager;
  }
}
