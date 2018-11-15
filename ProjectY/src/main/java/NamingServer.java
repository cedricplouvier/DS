import java.io.FileWriter;
import java.io.IOException;
import java.net.*;
import java.nio.channels.MembershipKey;
import java.util.ArrayList;
import java.util.TreeMap;
import java.util.Map;
import java.util.List;
import java.rmi.registry.Registry;
import java.rmi.registry.LocateRegistry;
import java.rmi.server.UnicastRemoteObject;
import javax.xml.stream.*;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;

public class NamingServer implements NamingInterface {
    public TreeMap<Integer, String> IPmap = new TreeMap<>();
    public TreeMap<Integer, Integer> fileOwnerMap = new TreeMap<>();
    String[] fileArray = {"Taak1.docx", "Song.mp3", "Uitgaven.xls", "loon.pdf", "readme.txt", "download.rar"};

    public NamingServer() {
    }

    public void addNode(String hostname, String IP) throws IOException, XMLStreamException {
        Integer nodeID = Math.abs(hostname.hashCode()) % 32768;
        if (!IPmap.containsKey(nodeID)) {
            IPmap.put(nodeID, IP);
            recalculate();
            writeToXML();
        } else System.out.println("Node already in use.");
    }

    public void removeNode(String hostname) throws IOException, XMLStreamException {
        int nodeID = Math.abs(hostname.hashCode()) % 32768;
        if (IPmap.containsKey(nodeID)) {
            IPmap.remove(nodeID);
            fileOwnerMap.remove(nodeID);
            recalculate();
            writeToXML();
        } else System.out.println("Node not in system.");
    }

    public String fileLocator(String filename) //what node calls, via RMI, to know IP of certain file
    {
        int fileHash = Math.abs(filename.hashCode()) % 32768;
        Integer closestKey = IPmap.floorKey(fileHash); //returns the greatest key less than or equal to the given key, or null if there is no such key.
        if (closestKey == null) {
            closestKey = IPmap.lastKey(); //returns highest key in this map
        }
        return IPmap.get(closestKey); //returns IP associated with this nodeID
    }

    public int filePlacer(String filename) {
        int fileHash = Math.abs(filename.hashCode()) % 32768;
        Integer closestKey = IPmap.floorKey(fileHash); //returns the greatest key less than or equal to the given key, or null if there is no such key.
        if (closestKey == null) {
            closestKey = IPmap.lastKey(); //returns highest key in this map
        }
        return closestKey; //return ID of node where file needs to be stored.
    }

    public void recalculate() //assigns files to the different nodes
    {
        for (int i = 0; i < fileArray.length; i++) {
            fileOwnerMap.put((Math.abs(fileArray[i].hashCode()) % 32768), filePlacer(fileArray[i]));
        }
    }

    public void writeToXML() throws IOException, XMLStreamException {
        FileWriter out = new FileWriter("/home/pi/Documents/distributed/map.xml");
        XMLStreamWriter xsw = null;
        try {
            try {
                XMLOutputFactory xof = XMLOutputFactory.newInstance();
                xsw = xof.createXMLStreamWriter(out);
                xsw.writeStartDocument("utf-8", "1.0");
                xsw.writeStartElement("entries");

                // Do the Collection
                for (Map.Entry<Integer, String> e : IPmap.entrySet()) {
                    xsw.writeStartElement("entry");
                    xsw.writeAttribute("key", e.getKey().toString());
                    xsw.writeAttribute("value", e.getValue().toString());
                    xsw.writeEndElement();
                }
                xsw.writeEndElement();
                xsw.writeEndDocument();
            } finally {
                if (out != null) {
                    try {
                        out.close();
                    } catch (IOException e) { /* ignore */ }
                }
            }// end inner finally
        } finally {
            if (xsw != null) {
                try {
                    xsw.close();
                } catch (XMLStreamException e) { /* ignore */ }
            }
        }
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

    public static void main(String args[]) throws IOException {
        ServerSocket servsock = null;
        try {
            //RMI
            NamingServer obj = new NamingServer();
            NamingInterface stub = (NamingInterface) UnicastRemoteObject.exportObject(obj, 0);

            // Bind the remote object's stub in the registry
            //Registry registry = LocateRegistry.getRegistry();
            Registry registry = LocateRegistry.createRegistry(1099);
            registry.bind("NamingInterface", stub);

            System.err.println("Server ready");

            //multicast
            NamingServer ns = new NamingServer();
            String[] nodeMessage;
            while(true)
            {
                System.out.println(ns.multicastReceive());
                nodeMessage = ns.multicastReceive().split(" ");
                ns.addNode(nodeMessage[0],nodeMessage[1]);
                //send message with ns.IPmap.size(); to nodeMessage[0]

            }

        } catch (Exception e) {
            System.err.println("Server exception: " + e.toString());
            e.printStackTrace();
        } finally {
            if (servsock != null) servsock.close();
        }
    }
}