import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.net.*;
import java.rmi.RemoteException;
import java.util.Objects;
import java.util.TreeMap;
import java.util.Map;
import java.rmi.registry.Registry;
import java.rmi.registry.LocateRegistry;
import java.rmi.server.UnicastRemoteObject;
import javax.xml.stream.*;

public class NamingServer implements NamingInterface {
    public TreeMap<Integer, String> IPmap = new TreeMap<>();
    public TreeMap<Integer, Integer> fileOwnerMap = new TreeMap<>();

    private static final int MULTICAST_PORT = 4321;
    private static final String MULTICAST_IP = "225.4.5.6";

    public Integer returnHash(String name) {
        return Math.abs(name.hashCode()) % 32768;
    }

    //add node to IPmap, recalculate the file distribution and write the IPmap to an XML file
    public void addNode(String hostname, String IP) throws IOException, XMLStreamException {
        Integer nodeID = returnHash(hostname);
        if (!IPmap.containsKey(nodeID)) {
            IPmap.put(nodeID, IP);
            //writeToXML();
            for (Map.Entry<Integer, String> entry : IPmap.entrySet()) {
                System.out.println("Key: " + entry.getKey() + ". Value: " + entry.getValue());
            }
        } else System.out.println("Node already in use.");
    }

    //returns previous and next node of failed node
    public String failure(Integer failedNode){
        Integer previousNode = IPmap.lowerKey(failedNode); //find the previous and next node of the failed node
        Integer nextNode = IPmap.higherKey(failedNode);
        try{
            removeNode(failedNode);
        }catch(Exception e){}
        return Integer.toString(previousNode) + Integer.toString(nextNode); //return both
    }

    //remove node from IPmap, recalculate the file distribution and write the IPmap to an XML file
    public void removeNode(Integer nodeID) throws IOException, XMLStreamException {
        if (this.IPmap.containsKey(nodeID)) {

            this.IPmap.remove(nodeID);
            this.fileOwnerMap.remove(nodeID);
            if (this.IPmap.size() > 1) {
                //writeToXML();
                for (Map.Entry<Integer, String> entry : IPmap.entrySet()) {
                    System.out.println("Key: " + entry.getKey() + ". Value: " + entry.getValue());
                }
            }
            System.out.printf("Node removed");
        } else System.out.println("Node not in system.");
    }

    //what node calls, via RMI, to know the IP of the node a file is located on
    public Integer fileLocator(String filename) {

        int fileHash = returnHash(filename);
        Integer closestKey = this.IPmap.floorKey(fileHash); //returns the greatest key less than or equal to the given key, or null if there is no such key.
        if (closestKey == null) {
            closestKey = this.IPmap.lastKey(); //returns highest key in this map
        }
        return closestKey; //returns IP associated with this nodeID
    }

    //Write the IPmap to and XML file
    public void writeToXML() throws IOException, XMLStreamException {
        FileOutputStream outPC = new FileOutputStream("C:\\Users\\Ruben Joosen\\Documents\\AntwerpenU\\Semester 5\\Distributed Systems\\ProjectY\\DS\\ProjectY\\map.xml");
        FileOutputStream out = new FileOutputStream("/home/pi/Documents/DS/ProjectY/map.xml"); // "/home/pi/Documents/DS/ProjectY/map.xml"

        XMLStreamWriter xsw = null;
        try {
            try {
                XMLOutputFactory xof = XMLOutputFactory.newInstance();
                xsw = xof.createXMLStreamWriter(out, "UTF-8");
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
    public String getIP(Integer nodeID) {
        return this.IPmap.get(nodeID);
    }

    public Integer getNodeID(String ip) {
        for (Map.Entry<Integer, String> entry : this.IPmap.entrySet()) {
            if (Objects.equals(ip, entry.getValue())) {
                return entry.getKey();
            }
        }
        return null;
    }

    /*public void nodeHandler(DatagramPacket MCpacket) throws IOException, XMLStreamException
    {
        DatagramPacket UCpacket;
        byte UCbuf[];
        String received;
        String[] receivedAr;
        Integer previous;
        Integer next;
        String bootstrapReturnMsg = null;
        DatagramSocket UCsendingSocket = new DatagramSocket();


        receivedAr = received.split(" ");
        addNode(receivedAr[2], receivedAr[1]); //add node with hostname and IP sent with UDP multicast
        System.out.println("map size: " + this.IPmap.size());
        if(this.IPmap.size() == 1)
        {
            bootstrapReturnMsg  = Integer.toString(this.IPmap.size());
        }
        else if(this.IPmap.size() > 1)
        {

            if (this.IPmap.lowerKey(this.returnHash(receivedAr[2])) == null){
                previous = this.IPmap.lastKey();

            }
            else {
                previous = this.IPmap.lowerKey(this.returnHash(receivedAr[2]));
            }
            if (this.IPmap.higherKey(this.returnHash(receivedAr[2])) == null){
                next = this.IPmap.firstKey();
            }
            else{
                next = this.IPmap.higherKey(this.returnHash(receivedAr[2]));
            }
            bootstrapReturnMsg = Integer.toString(this.IPmap.size()) + " " + Integer.toString(previous)+ " " + Integer.toString(next);
        }
        UCbuf = bootstrapReturnMsg.getBytes();
        UCpacket = new DatagramPacket(UCbuf, UCbuf.length, InetAddress.getByName(receivedAr[1]), 4446); //send the amount of nodes to the address where the multicast came from (with UDP unicast)
        UCsendingSocket.send(UCpacket);
    }*/

    public void start() throws IOException {
        byte MCbuf[] = new byte[1024];
        final DatagramPacket MCpacket  = new DatagramPacket(MCbuf, MCbuf.length);;
        boolean running = true;
        MulticastSocket MCreceivingSocket = null;
        Thread nodeHandlerThr;
        String received;

        try {
            //RMI
            NamingInterface stub = (NamingInterface) UnicastRemoteObject.exportObject(this, 0);

            // Bind the remote object's stub in the registry
            Registry registry = LocateRegistry.createRegistry(1099);
            registry.bind("NamingInterface", stub);

            //create Multicast
            MCreceivingSocket = new MulticastSocket(MULTICAST_PORT);
            MCreceivingSocket.setNetworkInterface(NetworkInterface.getByInetAddress(InetAddress.getByName("192.168.0.4")));

            //join the multicast group
            MCreceivingSocket.joinGroup(InetAddress.getByName(MULTICAST_IP));
            System.out.println("Joined MC");

            while (running) {
                MCpacket.setData(new byte[1024]);
                MCreceivingSocket.receive(MCpacket);
                received = new String(MCpacket.getData(), 0, MCpacket.getLength());
                NodeHandler NH = new NodeHandler(this,received);
                nodeHandlerThr = new Thread(NH);
                nodeHandlerThr.start();
            }
        } catch (Exception e) {
            System.err.println("Server exception: " + e.toString());
            e.printStackTrace();
            MCreceivingSocket.close();
        }
    }
}
