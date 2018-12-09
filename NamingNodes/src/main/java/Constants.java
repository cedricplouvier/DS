import java.io.File;

public final class Constants
{
    public static final int MULTICAST_PORT = 4321;
    public static final String MULTICAST_IP = "225.4.5.6";
    public static final String MULTICAST_INTERFACE = "eth0";

    public static final String SERVER_IP = "192.168.0.4";

    public static final int UDPFileName_PORT = 4200;
    public static final int UDP_PORT = 4446;

    public static final int TCP_FILE_PORT = 6789;

    public static final File localFileDirectory = new File("/home/pi/Documents/local");
    public static final File replicationFileDirectory = new File("/home/pi/Documents/replication");
}
