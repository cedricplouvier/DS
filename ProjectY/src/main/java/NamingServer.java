import java.io.FileWriter;
import java.io.IOException;
import java.net.*;
import java.nio.channels.MembershipKey;
import java.rmi.RemoteException;
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

    private static final String MULTICAST_INTERFACE = "eth0";
    private static final int MULTICAST_PORT = 4321;
    private static final String MULTICAST_IP = "225.4.5.6";

    public NamingServer() {
    }

    public Integer calculateHash(String hostname) {
        return Math.abs(hostname.hashCode()) % 32768;
    }

    //add node to IPmap, recalculate the file distribution and write the IPmap to an XML file
    public void addNode(String hostname, String IP) throws IOException, XMLStreamException {
        Integer nodeID = Math.abs(hostname.hashCode()) % 32768;
        if (!IPmap.containsKey(nodeID)) {
            IPmap.put(nodeID, IP);
            recalculate();
            writeToXML();
        } else System.out.println("Node already in use.");
    }

    //remove node from IPmap, recalculate the file distribution and write the IPmap to an XML file
    public void removeNode(Integer nodeID) throws IOException, XMLStreamException {
        if (IPmap.containsKey(nodeID)) {
            IPmap.remove(nodeID);
            fileOwnerMap.remove(nodeID);
            recalculate();
            writeToXML();
        } else System.out.println("Node not in system.");
    }
    //what node calls, via RMI, to know the IP of the node a file is located on
    public String fileLocator(String filename)
    {
        int fileHash = Math.abs(filename.hashCode()) % 32768;
        Integer closestKey = IPmap.floorKey(fileHash); //returns the greatest key less than or equal to the given key, or null if there is no such key.
        if (closestKey == null) {
            closestKey = IPmap.lastKey(); //returns highest key in this map
        }
        return IPmap.get(closestKey); //returns IP associated with this nodeID
    }

    //distributes the files over all nodes, evenly
    public int filePlacer(String filename) {
        int fileHash = Math.abs(filename.hashCode()) % 32768;
        Integer closestKey = IPmap.floorKey(fileHash); //returns the greatest key less than or equal to the given key, or null if there is no such key.
        if (closestKey == null) {
            closestKey = IPmap.lastKey(); //returns highest key in this map
        }
        return closestKey; //return ID of node where file needs to be stored.
    }

    //assigns files to the different nodes
    public void recalculate()
    {
        for (int i = 0; i < fileArray.length; i++) {
            fileOwnerMap.put((Math.abs(fileArray[i].hashCode()) % 32768), filePlacer(fileArray[i]));
        }
    }

    //Write the IPmap to and XML file
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

    //get the IP of a node in the system
    public String getIP(Integer nodeID) throws RemoteException
    {
        return IPmap.get(nodeID);
    }

    public static void main(String args[]) throws IOException {
        NamingServer ns = new NamingServer();
        byte buf[] = new byte[1024];
        DatagramPacket pack;
        String received;
        String[] receivedAr;
        Integer newNodeID;
        try {
            //RMI
            NamingServer obj = new NamingServer();
            NamingInterface stub = (NamingInterface) UnicastRemoteObject.exportObject(obj, 0);

            // Bind the remote object's stub in the registry
            //Registry registry = LocateRegistry.getRegistry();
            Registry registry = LocateRegistry.createRegistry(1099);
            registry.bind("NamingInterface", stub);

            //Bootstrap + Discovery
            MulticastSocket sendingSocket = new MulticastSocket();
            MulticastSocket receivingSocket = new MulticastSocket();
            receivingSocket.joinGroup(InetAddress.getByName(MULTICAST_IP));
            pack = new DatagramPacket(buf, buf.length, InetAddress.getByName(MULTICAST_IP), MULTICAST_PORT);
            while (true) {
                receivingSocket.receive(pack);
                received = new String(pack.getData(), 0, pack.getLength());
                receivedAr = received.split(" ");
                ns.addNode(receivedAr[1], receivedAr[0]); //add node with hostname and IP sent with UDP
                buf = ByteBuffer.allocate(4).putInt(ns.IPmap.size()).array(); //size of IP map (int) to byte array buffer
                pack = new DatagramPacket(buf, buf.length, InetAddress.getByName(receivedAr[0]), 5000); //send the amount of nodes to the address where the multicast came from
                sendingSocket.send(pack);
            }
        } catch (Exception e) {
            System.err.println("Server exception: " + e.toString());
            e.printStackTrace();
        }
    }
}