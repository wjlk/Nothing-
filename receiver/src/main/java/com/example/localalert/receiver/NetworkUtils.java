package com.example.localalert.receiver;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;

import java.net.Inet4Address;
import java.net.NetworkInterface;
import java.util.Collections;

public final class NetworkUtils {
    private NetworkUtils() {
    }

    public static String getLocalIpv4(Context context) {
        ConnectivityManager manager =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (manager != null) {
            Network network = manager.getActiveNetwork();
            NetworkCapabilities capabilities = manager.getNetworkCapabilities(network);
            if (capabilities != null
                    && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                LinkProperties properties = manager.getLinkProperties(network);
                if (properties != null) {
                    for (LinkAddress linkAddress : properties.getLinkAddresses()) {
                        if (linkAddress.getAddress() instanceof Inet4Address) {
                            return linkAddress.getAddress().getHostAddress();
                        }
                    }
                }
            }
        }
        try {
            for (NetworkInterface networkInterface :
                    Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!networkInterface.isUp() || networkInterface.isLoopback()) {
                    continue;
                }
                for (java.net.InetAddress address :
                        Collections.list(networkInterface.getInetAddresses())) {
                    if (address instanceof Inet4Address
                            && !address.isLoopbackAddress()
                            && !address.isLinkLocalAddress()) {
                        return address.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) {
            // The UI will show an unavailable address instead of failing.
        }
        return "غير متاح";
    }

    public static boolean isWifiConnected(Context context) {
        ConnectivityManager manager =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (manager == null) {
            return false;
        }
        Network network = manager.getActiveNetwork();
        NetworkCapabilities capabilities = manager.getNetworkCapabilities(network);
        return capabilities != null
                && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
    }

    public static boolean isPrivateIpv4(String value) {
        String[] parts = value.trim().split("\\.");
        if (parts.length != 4) {
            return false;
        }
        int[] octets = new int[4];
        try {
            for (int i = 0; i < parts.length; i++) {
                octets[i] = Integer.parseInt(parts[i]);
                if (octets[i] < 0 || octets[i] > 255) {
                    return false;
                }
            }
        } catch (NumberFormatException error) {
            return false;
        }
        return octets[0] == 10
                || (octets[0] == 192 && octets[1] == 168)
                || (octets[0] == 172 && octets[1] >= 16 && octets[1] <= 31);
    }
}