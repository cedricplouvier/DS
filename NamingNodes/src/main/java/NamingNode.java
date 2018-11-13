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
        String ip = null;
        InetAddress i = null;

        try
        {
            Enumeration e = NetworkInterface.getNetworkInterfaces();
            while(e.hasMoreElements()) //iterate through all network interfaces
            {
                NetworkInterface n = (NetworkInterface) e.nextElement();
                Enumeration ee = n.getInetAddresses();
                while (ee.hasMoreElements())
                {
                    i =  (InetAddress) ee.nextElement();
                    if(i.getHostAddress().contains("192.168.0.")) //if address contains our local adress, use it
                    {
                        ip = i.getHostAddress();
                    }
                }
            }
            hostname = "Node" + i.getHostAddress().substring(i.getHostAddress().lastIndexOf(".") + 1); //if IP ends in '1', hostname will be Node1

            //RMI
            Registry registry = LocateRegistry.getRegistry("192.168.0.4", 1099); //server IP and port
            NamingInterface stub = (NamingInterface) registry.lookup("NamingInterface");

            if (ip != null) {
                stub.addNode(hostname, ip); //RMI get added to the MAP
            }
        }catch(Exception e)
        {
            System.err.println("Client exception: " + e.toString());
            e.printStackTrace();
        }
    }
}
