import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.io.*;
import java.net.*;
import java.util.*;
import java.net.InetAddress;


public class NamingNode
{

    public NamingNode() {}

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
        //IP
        String hostname;
        String ipString;
        InetAddress ip = null;
        try {
            Enumeration e = NetworkInterface.getNetworkInterfaces();
            while (e.hasMoreElements()) {
                NetworkInterface n = (NetworkInterface) e.nextElement();
                Enumeration ee = n.getInetAddresses();
                while (ee.hasMoreElements()) {  //while lus doorheen alle
                    InetAddress i = (InetAddress) ee.nextElement();
                    if (i.getHostAddress().contains("192.168.0.")){
                        ip = i;

                    }
                }
            }
            ipString = ip.getHostAddress(); //ip in Stringformaat
            hostname = "Node " + ipString.substring(10); //hostname declareren met laatste getal van ip
            //print commando's
            System.out.println("ip address is " + ipString);
            System.out.println("hostname is " + hostname);
            System.out.println(ip);

            //RMI
            Registry registry = LocateRegistry.getRegistry("192.168.0.4", 1099); //server IP and port
            NamingInterface stub = (NamingInterface) registry.lookup("NamingInterface");

            if (ip != null) {
                stub.addNode(hostname, ipString); //RMI get added to the MAP
            }
        }catch(Exception e)
        {
            System.err.println("Client exception: " + e.toString());
            e.printStackTrace();
        }
    }
}
