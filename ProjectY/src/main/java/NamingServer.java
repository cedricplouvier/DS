import sun.reflect.generics.tree.Tree;

import java.io.FileWriter;
import java.io.IOException;
import java.net.*;
import java.rmi.RemoteException;
import java.util.TreeMap;
import java.util.Map;
import java.rmi.registry.Registry;
import java.rmi.registry.LocateRegistry;
import java.rmi.server.UnicastRemoteObject;
import javax.xml.stream.*;

public class NamingServer implements NamingInterface {
    public TreeMap<Integer, String> IPmap = new TreeMap<>();
    public TreeMap<Integer, Integer> fileOwnerMap = new TreeMap<>();
    String[] fileArray = {"Taak1.docx", "Song.mp3", "Uitgaven.xls", "loon.pdf", "readme.txt", "download.rar"};

    public static NamingServer ns = new NamingServer();

    private static final int MULTICAST_PORT = 4321;
    private static final String MULTICAST_IP = "225.4.5.6";

    public NamingServer() {}

    public Integer returnHash(String name)
    {
        return Math.abs(name.hashCode()) % 32768;
    }

    //add node to IPmap, recalculate the file distribution and write the IPmap to an XML file
    public void addNode(String hostname, String IP) throws IOException, XMLStreamException
    {
        Integer nodeID = returnHash(hostname);
        if (!IPmap.containsKey(nodeID)) {
            IPmap.put(nodeID, IP);
            recalculate();
            writeToXML();
            for (Map.Entry<Integer, String> entry : IPmap.entrySet()) {
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
        if (ns.IPmap.containsKey(nodeID)) {

            ns.IPmap.remove(nodeID);
            ns.fileOwnerMap.remove(nodeID);
            if (ns.IPmap.size() > 1)
            {
                recalculate();
                writeToXML();
            }
            System.out.printf("Node removed");
        } else System.out.println("Node not in system.");
    }
    //what node calls, via RMI, to know the IP of the node a file is located on
    public Integer fileLocator(String filename)
    {
        int fileHash = returnHash(filename);
        Integer closestKey = ns.IPmap.floorKey(fileHash); //returns the greatest key less than or equal to the given key, or null if there is no such key.
        if (closestKey == null) {
            closestKey = ns.IPmap.lastKey(); //returns highest key in this map
        }
        return closestKey; //returns IP associated with this nodeID
    }

    //distributes the files over all nodes, evenly
    public int filePlacer(String filename) {
        int fileHash = returnHash(filename);
        Integer closestKey = ns.IPmap.floorKey(fileHash); //returns the greatest key less than or equal to the given key, or null if there is no such key.
        if (closestKey == null) {
            closestKey = ns.IPmap.lastKey(); //returns highest key in this map
        }
        return closestKey; //return ID of node where file needs to be stored.
    }

    //assigns files to the different nodes
    public void recalculate()
    {
        for (int i = 0; i < fileArray.length; i++) {
            ns.fileOwnerMap.put(returnHash(fileArray[i]) , filePlacer(fileArray[i]));
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
                for (Map.Entry<Integer, String> e : ns.IPmap.entrySet()) {
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
        return ns.IPmap.get(nodeID);
    }

    public static void main(String args[]) throws IOException {
        byte MCbuf[] = new byte[1024];
        byte UCbuf[] = new byte[1024];
        DatagramPacket MCpacket;
        DatagramPacket UCpacket;
        String received;
        String[] receivedAr;
        boolean running = true;
        Integer previous;
        Integer next;
        String bootstrapReturnMsg = null;
        MulticastSocket MCreceivingSocket = null;
        DatagramSocket UCreceivingSocket = null;
        DatagramSocket UCsendingSocket = null;

        try {
            //RMI
            NamingInterface stub = (NamingInterface) UnicastRemoteObject.exportObject(ns, 0);

            // Bind the remote object's stub in the registry
            Registry registry = LocateRegistry.createRegistry(1099);
            registry.bind("NamingInterface", stub);

            //create Multicast and unicast sockets
            MCreceivingSocket = new MulticastSocket(MULTICAST_PORT);
            MCreceivingSocket.setNetworkInterface(NetworkInterface.getByInetAddress(InetAddress.getByName("192.168.0.4")));
            UCreceivingSocket = new DatagramSocket(4448);
            UCsendingSocket = new DatagramSocket();

            //join the multicast group
            MCreceivingSocket.joinGroup(InetAddress.getByName(MULTICAST_IP)); //NetworkInterface.getByName(MULTICAST_INTERFACE)
            System.out.println("Joined MC");
            while (running) {
                MCpacket = new DatagramPacket(UCbuf, UCbuf.length);
                MCreceivingSocket.receive(MCpacket);
                received = new String(MCpacket.getData(), 0, MCpacket.getLength());
                receivedAr = received.split(" ");
                ns.addNode(receivedAr[2], receivedAr[1]); //add node with hostname and IP sent with UDP multicast
                if(ns.IPmap.size() == 1)
                {
                    bootstrapReturnMsg  = Integer.toString(ns.IPmap.size());
                }
                else if(ns.IPmap.size() > 1)
                {
                    previous = ns.IPmap.lowerKey(ns.returnHash(receivedAr[2]));
                    next = ns.IPmap.higherKey(ns.returnHash(receivedAr[2]));
                    bootstrapReturnMsg = Integer.toString(ns.IPmap.size()) + Integer.toString(previous) + Integer.toString(next);
                }
                UCbuf = bootstrapReturnMsg.getBytes();
                UCpacket = new DatagramPacket(UCbuf, UCbuf.length, InetAddress.getByName(receivedAr[1]), 4446); //send the amount of nodes to the address where the multicast came from (with UDP unicast)
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