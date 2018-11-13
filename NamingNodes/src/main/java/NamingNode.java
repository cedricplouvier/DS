import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.io.*;
import java.net.*;
import java.util.*;
import java.net.InetAddress;


public class NamingNode
{

    public NamingNode() {}

    public String getInterfaceIP(String interfaceName) {
        String ip = null;
        try
        {
            NetworkInterface networkInterface = NetworkInterface.getByName(interfaceName);
            Enumeration<InetAddress> inetAddress = networkInterface.getInetAddresses();
            InetAddress currentAddress;
            currentAddress = inetAddress.nextElement();
            while (inetAddress.hasMoreElements()) {
                System.out.println(currentAddress);
                if (currentAddress instanceof Inet4Address && !currentAddress.isLoopbackAddress())
                {
                    ip = currentAddress.toString();
                    break;
                }
                currentAddress = inetAddress.nextElement();
            }
        }catch(Exception e) {}
        return ip;
    }

    public void downloadFile(String filename) throws IOException
    {
        Socket csock = null;
        byte [] mybytearray  = new byte [6022386];
        int bytesRead;
        int current = 0;
        FileOutputStream fos = null;
        BufferedOutputStream bos = null;

        try
        {
            csock = new Socket("localhost",6789); //server IP and port
            InputStream is = csock.getInputStream();
            fos = new FileOutputStream(filename); //where file is placed
            bos = new BufferedOutputStream(fos);
            bytesRead = is.read(mybytearray,0,mybytearray.length);
            current = bytesRead;
            do {
                bytesRead =
                        is.read(mybytearray, current, (mybytearray.length-current));
                if(bytesRead >= 0) current += bytesRead;
            } while(bytesRead > -1);

            bos.write(mybytearray, 0 , current);
            bos.flush();
        }
        finally
        {
            if (fos != null) fos.close();
            if (bos != null) bos.close();
            if (csock != null) csock.close();
        }
    }

    public static void main(String[] args)
    {
        NamingNode obj = new NamingNode();
        //IP
        String hostname;
        String interfaceName = "eth0";
        String ip;
        InetAddress ipA;

        try
        {
            ip = obj.getInterfaceIP(interfaceName); //get IP of the right interface
            ipA = InetAddress.getByName(ip);
            hostname = ipA.getHostName();

            //RMI
            Registry registry = LocateRegistry.getRegistry("192.168.0.4", 1099); //server IP and port
            NamingInterface stub = (NamingInterface) registry.lookup("NamingInterface");

            stub.addNode(hostname,ip); //RMI get added to the MAP

        }catch(Exception e)
        {
            System.err.println("Client exception: " + e.toString());
            e.printStackTrace();
        }
    }
}
