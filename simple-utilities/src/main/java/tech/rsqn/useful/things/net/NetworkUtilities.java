package tech.rsqn.useful.things.net;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;

public class NetworkUtilities {
    private static String cachedShortHostName = null;

    public static String getLocalIPAddress() throws SocketException {
        String ipAddress = null;
        Enumeration<NetworkInterface> eIf = NetworkInterface.getNetworkInterfaces();
        while (ipAddress == null && eIf.hasMoreElements()) {
            NetworkInterface nIf = eIf.nextElement();
            Enumeration<InetAddress> eAddress = nIf.getInetAddresses();
            while (eAddress.hasMoreElements()) {
                InetAddress address = eAddress.nextElement();
                if (!address.isLoopbackAddress()) {
                    ipAddress = address.getHostAddress();
                }
            }
        }
        return ipAddress;
    }


    public static String getLocalIPAddressOnSubnet(String subnet) throws SocketException {
        String ipAddress = null;
        Enumeration<NetworkInterface> eIf = NetworkInterface.getNetworkInterfaces();
        while (eIf.hasMoreElements()) {
            NetworkInterface nIf = eIf.nextElement();
            Enumeration<InetAddress> eAddress = nIf.getInetAddresses();
            while (eAddress.hasMoreElements()) {
                InetAddress address = eAddress.nextElement();
                System.out.println(nIf + " - " + address);
                if (!address.isLoopbackAddress()) {
                    ipAddress = address.getHostAddress();
                    if (subnet == null || subnet.trim().length() == 0) {
                        return ipAddress;
                    } else if ( ipAddress.startsWith(subnet)) {
                        return ipAddress;
                    }
                }
            }
        }
        return null;
    }

    public static String lookup(String hostName) {
        try {
            InetAddress iAddress = InetAddress.getByName(hostName);
            return iAddress.getHostAddress();
        } catch (Exception e) {
            throw new RuntimeException("Exception getting hostname ", e);
        }
    }

    public static String getLocalShortHostName() {
        if ( cachedShortHostName != null) {
            return cachedShortHostName;
        }

        try {
            String hostName;
            InetAddress iAddress = InetAddress.getLocalHost();
            hostName = iAddress.getHostName();
//            String canonicalHostName = iAddress.getCanonicalHostName();
            String[] parts = hostName.split("\\.");
            hostName = parts[0];
            cachedShortHostName = hostName;
            return cachedShortHostName;
        } catch (Exception e) {
            return e.getMessage();
        }
    }

}