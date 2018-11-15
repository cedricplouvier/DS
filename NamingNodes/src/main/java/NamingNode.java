import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.io.*;
import java.net.*;
import java.util.*;
import java.net.InetAddress;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.MembershipKey;


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

    public Integer calculateHash(String hostname)
    {
        return Math.abs(hostname.hashCode()) % 32768;
    }

    public String multicastReceive() throws IOException
    {
        final String MULTICAST_INTERFACE = "eth0";
        final int MULTICAST_PORT = 4321;
        final String MULTICAST_IP = "192.168.0.0";

        DatagramChannel datagramChannel = DatagramChannel
                .open(StandardProtocolFamily.INET);
        NetworkInterface networkInterface = NetworkInterface
                .getByName(MULTICAST_INTERFACE);
        datagramChannel.setOption(StandardSocketOptions
                .SO_REUSEADDR, true);
        datagramChannel.bind(new InetSocketAddress(MULTICAST_PORT));
        datagramChannel.setOption(StandardSocketOptions
                .IP_MULTICAST_IF, networkInterface);
        InetAddress inetAddress = InetAddress.getByName(MULTICAST_IP);
        MembershipKey membershipKey = datagramChannel.join
                (inetAddress, networkInterface);
        System.out.println("Waiting for the message...");
        ByteBuffer byteBuffer = ByteBuffer.allocate(1024);
        datagramChannel.receive(byteBuffer);
        byteBuffer.flip();
        byte[] bytes = new byte[byteBuffer.limit()];
        byteBuffer.get(bytes, 0, byteBuffer.limit());
        membershipKey.drop();
        return new String(bytes);
    }

    public void multicastSend(String message) throws IOException
    {
        final String MULTICAST_INTERFACE = "eth0";
        final int MULTICAST_PORT = 4321;
        final String MULTICAST_IP = "192.168.0.0";

        DatagramChannel datagramChannel=DatagramChannel.open();
        datagramChannel.bind(null);
        NetworkInterface networkInterface=NetworkInterface
                .getByName(MULTICAST_INTERFACE);
        datagramChannel.setOption(StandardSocketOptions
                .IP_MULTICAST_IF,networkInterface);
        ByteBuffer byteBuffer=ByteBuffer.wrap
                (message.getBytes());
        InetSocketAddress inetSocketAddress=new
                InetSocketAddress(MULTICAST_IP,MULTICAST_PORT);
        datagramChannel.send(byteBuffer,inetSocketAddress);
    }

    public static void main(String[] args)
    {
        NamingNode nn = new NamingNode();
        //IP
        String hostname;
        String ipString;
        InetAddress ip = null;
        //Multicast
        String[] nodeMessage;
        Integer newHash = 0;

        try {
            Enumeration e = NetworkInterface.getNetworkInterfaces();
            while (e.hasMoreElements()) {
                NetworkInterface n = (NetworkInterface) e.nextElement();
                Enumeration ee = n.getInetAddresses();
                while (ee.hasMoreElements()) {  //while lus doorheen alle
                    InetAddress i = (InetAddress) ee.nextElement();
                    if (i.getHostAddress().contains("192.168.0")){
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

            //Multicast
            nn.multicastSend(ipString+" "+hostname);

            while(true)
            {
                System.out.println(nn.multicastReceive());
                nodeMessage = nn.multicastReceive().split(" ");

                System.out.println(nn.calculateHash(nodeMessage[1]));
                if()
            }
        }catch(Exception e)
        {
            System.err.println("Client exception: " + e.toString());
            e.printStackTrace();
        }
    }
}
