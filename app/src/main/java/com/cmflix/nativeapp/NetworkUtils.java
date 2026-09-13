package com.cmflix.nativeapp;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;

public final class NetworkUtils {

    private NetworkUtils() {
    }

    public static boolean isOnline(
            Context context
    ) {
        if (context == null) {
            return false;
        }

        try {
            ConnectivityManager manager =
                    (ConnectivityManager)
                            context.getSystemService(
                                    Context.CONNECTIVITY_SERVICE
                            );

            if (manager == null) {
                return false;
            }

            Network network =
                    manager.getActiveNetwork();

            if (network == null) {
                return false;
            }

            NetworkCapabilities capabilities =
                    manager.getNetworkCapabilities(
                            network
                    );

            if (capabilities == null) {
                return false;
            }

            return capabilities.hasCapability(
                    NetworkCapabilities
                            .NET_CAPABILITY_INTERNET
            ) &&
                    capabilities.hasCapability(
                            NetworkCapabilities
                                    .NET_CAPABILITY_VALIDATED
                    );

        } catch (Exception ignored) {
            return false;
        }
    }
}
