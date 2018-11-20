import sun.reflect.generics.tree.Tree;

import java.io.FileWriter;
import java.io.IOException;
import java.net.*;
import java.rmi.RemoteException;
import java.util.TreeMap;
import java.util.Map;
import java.util.Map.Entry;
import java.rmi.registry.Registry;
import java.rmi.registry.LocateRegistry;
import java.rmi.server.UnicastRemoteObject;
import javax.xml.stream.*;
import java.nio.ByteBuffer;

public class NamingServer implements NamingInterface {
    public TreeMap<Integer, String> IPmap = new TreeMap<>();
    public TreeMap<Integer, Integer> fileOwnerMap = new TreeMap<>();
    String[] fileArray = {"Taak1.docx", "Song.mp3", "Uitgaven.xls", "loon.pdf", "readme.txt", "download.rar"};

    private static final int MULTICAST_PORT = 4321;
    private static final String MULTICAST_IP = "225.4.5.6";
    private static final String MULTICAST_INTERFACE = "eth0";

    public NamingServer()
    {
    }

    //add node to IPmap, recalculate the file distribution and write the IPmap to an XML file
    public void addNode(String hostname, String IP) throws IOException, XMLStreamException
    {
        Integer nodeID = Math.abs(hostname.hashCode()) % 32768;
        if (!this.IPmap.containsKey(nodeID)) {
            this.IPmap.put(nodeID, IP);
            recalculate();
            writeToXML();
            for (Map.Entry<Integer, String> entry : this.IPmap.entrySet()) {
                System.out.println("Key: " + entry.getKey() + ". Value: " + entry.getValue());
            }
        } else System.out.println("Node already in use.");
    }

    //returns previous and next node of failed node
    public String failure(Integer failedNode)
    {
        Integer previousNode = IPmap.lowerKey(failedNode); //find the previous and next node of the failed node
        Integer nextNode = IPmap.higherKey(failedNode);
        return Integer.toString(previousNode) + Integer.toString(nextNode); //return both
    }

    //remove node from IPmap, recalculate the file distribution and write the IPmap to an XML file
    public void removeNode(Integer nodeID) throws IOException, XMLStreamException
    {
        if (this.IPmap.containsKey(nodeID)) {
            System.out.println("a");
            this.IPmap.remove(nodeID);
            fileOwnerMap.remove(nodeID);
            recalculate();
            writeToXML();
            for (Entry<Integer, String> entry : this.IPmap.entrySet()) {
                Integer key = entry.getKey();
                String value = entry.getValue();

                System.out.printf("%d : %s\n", key, value);
            }
        } else System.out.println("Node not in system.");
    }
    //what node calls, via RMI, to know the IP of the node a file is located on
    public String fileLocator(String filename)
    {
        int fileHash = Math.abs(filename.hashCode()) % 32768;
        Integer closestKey = this.IPmap.floorKey(fileHash); //returns the greatest key less than or equal to the given key, or null if there is no such key.
        if (closestKey == null) {
            closestKey = this.IPmap.lastKey(); //returns highest key in this map
        }
        return this.IPmap.get(closestKey); //returns IP associated with this nodeID
    }

    //distributes the files over all nodes, evenly
    public int filePlacer(String filename) {
        int fileHash = Math.abs(filename.hashCode()) % 32768;
        Integer closestKey = this.IPmap.floorKey(fileHash); //returns the greatest key less than or equal to the given key, or null if there is no such key.
        if (closestKey == null) {
            closestKey = this.IPmap.lastKey(); //returns highest key in this map
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
        FileWriter out = new FileWriter("C:\\Users\\Maximiliaan\\map.xml"); ///home/pi/Documents/distributed/map.xml
        XMLStreamWriter xsw = null;
        try {
            try {
                XMLOutputFactory xof = XMLOutputFactory.newInstance();
                xsw = xof.createXMLStreamWriter(out);
                xsw.writeStartDocument("utf-8", "1.0");
                xsw.writeStartElement("entries");

                // Do the Collection
                for (Map.Entry<Integer, String> e : this.IPmap.entrySet()) {
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
        return this.IPmap.get(nodeID);
    }

    public static void main(String args[]) throws IOException {
        NamingServer ns = new NamingServer();
        byte MCbuf[] = new byte[1024];
        byte UCbuf[] = new byte[1024];
        DatagramPacket MCpacket;
        DatagramPacket UCpacket;
        String received;
        String[] receivedAr;
        boolean running = true;
        MulticastSocket MCreceivingSocket = null;
        DatagramSocket UCreceivingSocket = null;
        DatagramSocket UCsendingSocket = null;

        try {
            //RMI
            NamingServer obj = new NamingServer();
            NamingInterface stub = (NamingInterface) UnicastRemoteObject.exportObject(obj, 0);

            // Bind the remote object's stub in the registry
            //Registry registry = LocateRegistry.getRegistry();
            Registry registry = LocateRegistry.createRegistry(1099);
            registry.bind("NamingInterface", stub);

            //create Multicast and unicast sockets
            MCreceivingSocket = new MulticastSocket(MULTICAST_PORT);
            UCreceivingSocket = new DatagramSocket(4448);
            UCsendingSocket = new DatagramSocket();
            //join the multicast group
            MCreceivingSocket.joinGroup(InetAddress.getByName(MULTICAST_IP)); //NetworkInterface.getByName(MULTICAST_INTERFACE)
            MCpacket = new DatagramPacket(MCbuf, MCbuf.length, InetAddress.getByName(MULTICAST_IP), MULTICAST_PORT);
            System.out.println("Joined MC");
            while (running) {
                UCpacket = new DatagramPacket(UCbuf, UCbuf.length);
                UCsendingSocket.receive(UCpacket);
                received = new String(UCpacket.getData(), 0, UCpacket.getLength());
                receivedAr = received.split(" ");
                ns.addNode(receivedAr[2], receivedAr[1]); //add node with hostname and IP sent with UDP multicast
                String mapSize = Integer.toString(ns.IPmap.size());
                UCbuf = mapSize.getBytes();
                UCpacket = new DatagramPacket(UCbuf, UCbuf.length, InetAddress.getByName(receivedAr[1]), 5000); //send the amount of nodes to the address where the multicast came from (with UDP unicast)
                UCsendingSocket.send(UCpacket);
            }
        } catch (Exception e) {
            System.err.println("Server exception: " + e.toString());
            e.printStackTrace();
            MCreceivingSocket.close();
            UCreceivingSocket.close();
            UCsendingSocket.close();
        }
    }
}