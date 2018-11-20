import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.io.*;
import java.net.*;
import java.util.*;
import java.net.InetAddress;
import java.rmi.server.*;


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

            //RMI connection
            Registry registry = LocateRegistry.getRegistry("192.168.23.1"); //connect to right interface of namingserver (normally 192.168.0.4)
            NamingInterface stub = (NamingInterface) registry.lookup("NamingInterface");
            String response = stub.sayHello();
            System.out.println(response);

            //add node
            Enumeration e = NetworkInterface.getNetworkInterfaces();
            while (e.hasMoreElements()) {
                NetworkInterface n = (NetworkInterface) e.nextElement();
                Enumeration ee = n.getInetAddresses();
                while (ee.hasMoreElements()) {  //while lus doorheen alle
                    InetAddress i = (InetAddress) ee.nextElement();

                    if (i.getHostAddress().contains("192.168.1.")){ //watch out for right IP range in pi : (192.168.0.)

                        ip = i;
                        System.out.println(ip.getHostAddress());

                    }
                }
            }
            ipString = ip.getHostAddress(); //ip in Stringformat
            hostname = "Node " + ipString.substring(11); //declare hostname with last digit of IP

            if (ip != null) {
                stub.addNode(hostname, ipString); //RMI get added to the MAP
            }


        } catch (Exception e) {
            System.err.println("Client exception: " + e.toString());
            e.printStackTrace();
        }
    }
}
