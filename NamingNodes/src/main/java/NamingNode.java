import javax.xml.stream.XMLStreamException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.io.*;
import java.net.*;
import java.util.*;
import java.net.InetAddress;
import java.rmi.server.*;


public class NamingNode
{
    private static final int MULTICAST_PORT = 4321;
    private static final String MULTICAST_IP = "225.4.5.6";
    private static final String MULTICAST_INTERFACE = "eth0";

    public NamingNode() {}

    public Integer calculateHash(String hostname)
    {
        return Math.abs(hostname.hashCode()) % 32768;
    }

    //get the IP from this node
    public InetAddress getThisIP()
    {
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
                while (ee.hasMoreElements()) {  //while through all IPs until we find the matching IP
                    InetAddress i = (InetAddress) ee.nextElement();
                    if (i.getHostAddress().contains("192.168.0")) {
                        return i;
                    }
                }
            }
        }catch(Exception e) {}
        return null;
    }

    //Shutdown protocol
    public void shutdown(DatagramSocket sendingSocket, NamingInterface stub, Integer thisNodeID, Integer nextNodeID, Integer previousNodeID) throws IOException, XMLStreamException
    {
        String nextIP = stub.getIP(nextNodeID);
        String previousIP = stub.getIP(previousNodeID);
        //send nextNodeID to your previous node
        String sendingStr = "n " + nextNodeID;
        UDPSend(sendingSocket, sendingStr, previousIP, 4446);
        //send previousNodeID to your next node
        sendingStr = "p " + previousNodeID;
        UDPSend(sendingSocket, sendingStr, nextIP, 4446);

        stub.removeNode(thisNodeID); //remove node from IPMap on the server
    }

    //sending packets with UDP, unicast/multicast
    public void UDPSend(DatagramSocket sendingSocket, String message, String ip, int port) throws IOException
    {
        byte buf[] = message.getBytes();
        DatagramPacket pack = new DatagramPacket(buf, buf.length, InetAddress.getByName(ip),port);
        sendingSocket.send(pack);
    }

    //Failure protocol
    public void failure(Integer failedNode, NamingInterface stub) throws IOException, XMLStreamException
    {
        DatagramSocket UCsocket = new DatagramSocket(4445);
        String receivedAr[] = stub.failure(failedNode).split(" ");
        //give previous node of the failed one, his new next node
        Integer previousNode = Integer.valueOf(receivedAr[0]);
        Integer nextNode = Integer.valueOf(receivedAr[1]);
        String nodeMessage = "n " + nextNode;
        UDPSend(UCsocket, nodeMessage, stub.getIP(previousNode), 4445);
        //give next node of the failed one, his new previous node
        nodeMessage = "p " + previousNode;
        UDPSend(UCsocket, nodeMessage, stub.getIP(nextNode), 4445);
        UCsocket.close();
    }

    public static void main(String[] args) throws IOException
    {
        NamingNode nn = new NamingNode();
        //IP
        String hostname;
        String ipString;
        //Multicast
        String received;
        String[] receivedAr;
        Integer newNodeID;
        Integer thisNodeID;
        Integer nextNodeID = 33000;
        Integer previousNodeID = 0;
        int amountOfNodes;
        byte buf[] = new byte[1024];
        DatagramPacket receivingPack = new DatagramPacket(buf, buf.length, InetAddress.getByName(MULTICAST_IP),MULTICAST_PORT);;
        String nameIP;
        String nodeMessage = null;

        try {
            //RMI
            Registry registry = LocateRegistry.getRegistry("192.168.0.4", 1099); //server IP and port
            NamingInterface stub = (NamingInterface) registry.lookup("NamingInterface");

            //Bootstrap + Discovery
            ipString = nn.getThisIP().getHostAddress(); // InetAddress to string
            hostname = "Node" + ipString.substring(10); //hostname dependant on last digit of IP
            thisNodeID = nn.calculateHash(hostname);

            //Multicast send IP + hostname to all
            MulticastSocket MCSocket = new MulticastSocket(MULTICAST_PORT);
            DatagramSocket UCsendingSocket = new DatagramSocket();
            DatagramSocket UCreceivingSocket = new DatagramSocket(4446);

            nameIP = "b " + ipString + " " + hostname; //bootstrap message
            nn.UDPSend(MCSocket, nameIP, MULTICAST_IP, MULTICAST_PORT);

            //Multicast receive IP + hostname from other nodes
            MCSocket.joinGroup(InetAddress.getByName(MULTICAST_IP)); //NetworkInterface.getByName(MULTICAST_INTERFACE)
            while(true)
            {
                UCreceivingSocket.receive(receivingPack);
                received = new String(receivingPack.getData(), 0, receivingPack.getLength());
                if(receivingPack.getAddress().toString().equals("/192.168.0.4")) //if from server IP
                {
                    amountOfNodes = Integer.parseInt(received);
                    if(amountOfNodes <= 1)
                    {
                        nextNodeID = thisNodeID;
                        previousNodeID = thisNodeID;
                    }
                    else
                    {
                        System.out.println("Error: amount of nodes smaller than 0!");
                    }
                }
                else //if from any other IP => node
                {
                    receivedAr = received.split(" ");
                    if(receivedAr[0].equals("b")) //b for bootstrap message
                    {
                        newNodeID = nn.calculateHash(receivedAr[1]);
                        if((thisNodeID < newNodeID) && (newNodeID < nextNodeID)) //This is the previous node
                        {
                            nextNodeID = newNodeID;
                            nodeMessage = "p " + thisNodeID; //p for previous nodeID message
                            nn.UDPSend(UCsendingSocket, nodeMessage,receivedAr[0], 4999 );
                        }
                        else if((previousNodeID < newNodeID) && (newNodeID < thisNodeID)) //This is the next node
                        {
                            previousNodeID = newNodeID;
                            nodeMessage = "n " + thisNodeID;
                            nn.UDPSend(UCsendingSocket, nodeMessage,receivedAr[0], 4999 );
                        }
                    }
                    else if(receivedAr[0].equals("p")) //its a previous node message
                    {
                        previousNodeID = Integer.valueOf(receivedAr[1]); //his ID is now your previous nodeID
                    }
                    else if(receivedAr[0].equals("n")) //its a next node message
                    {
                        nextNodeID = Integer.valueOf(receivedAr[1]); //his ID is now your next nodeID
                    }
                }
                //nn.shutdown(UCsendingSocket, stub, thisNodeID, nextNodeID, previousNodeID); //works
                //break;
            }


        } catch (Exception e) {
            System.err.println("Client exception: " + e.toString());
            e.printStackTrace();
        }
    }
}
