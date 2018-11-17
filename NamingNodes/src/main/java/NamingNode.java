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
    private static final String MULTICAST_INTERFACE = "eth0";
    private static final int MULTICAST_PORT = 4321;
    private static final String MULTICAST_IP = "225.4.5.6";

    public NamingNode() {}

    public Integer calculateHash(String hostname)
    {
        return Math.abs(hostname.hashCode()) % 32768;
    }

    public InetAddress findIP()
    {
        try {
            Enumeration e = NetworkInterface.getNetworkInterfaces();
            while (e.hasMoreElements()) {
                NetworkInterface n = (NetworkInterface) e.nextElement();
                Enumeration ee = n.getInetAddresses();
                while (ee.hasMoreElements()) {  //while lus doorheen alle
                    InetAddress i = (InetAddress) ee.nextElement();
                    if (i.getHostAddress().contains("192.168.0")) {
                        return i;
                    }
                }
            }
        }catch(Exception e) {}
        return null;
    }

    public static void main(String[] args)
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
        byte buf[];
        DatagramPacket pack;
        String nameIP;
        String nodeMessage = null;

        try {
            ipString = nn.findIP().getHostAddress(); // InetAddress to string
            hostname = "Node " + ipString.substring(10); //hostname dependant on last digit of IP
            thisNodeID = nn.calculateHash(hostname);

            //Multicast send IP + hostname to all
            MulticastSocket sendingSock = new MulticastSocket(); //sending and receiving socket are best split
            MulticastSocket receivingSocket = new MulticastSocket();

            nameIP = "b " + ipString + " " + hostname; //bootstrap message
            buf = nameIP.getBytes();
            pack = new DatagramPacket(buf, buf.length, InetAddress.getByName(MULTICAST_IP), MULTICAST_PORT);
            sendingSock.send(pack);

            //Multicast receive IP + hostname from other nodes
            receivingSocket.joinGroup(InetAddress.getByName(MULTICAST_IP));
            while(true)
            {
                receivingSocket.receive(pack);
                received = new String(pack.getData(), 0, pack.getLength());
                if(pack.getAddress().toString().equals("192")) //if from server IP
                {
                    amountOfNodes = Integer.parseInt(received);
                    if(amountOfNodes < 1)
                    {
                        newNodeID = thisNodeID;
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
                            nodeMessage = "p " + thisNodeID + " " + nextNodeID; //p for previous nodeID message
                            buf = nodeMessage.getBytes();
                            pack = new DatagramPacket(buf, buf.length, InetAddress.getByName(receivedAr[0]), 4999); //respond to the starting node(IP: ReceivedAr[0]) with the current ID and the next ID
                            sendingSock.send(pack);
                        }
                        else if((previousNodeID < newNodeID) && (newNodeID < thisNodeID)) //This is the next node
                        {
                            previousNodeID = newNodeID;
                            nodeMessage = "n " + thisNodeID + " " + previousNodeID;
                            buf = nodeMessage.getBytes();
                            pack = new DatagramPacket(buf, buf.length, InetAddress.getByName(receivedAr[0]), 4999);
                            sendingSock.send(pack);
                        }
                    }
                    else if(receivedAr[0].equals("p")) //its a previous node message
                    {
                        if(Integer.valueOf(receivedAr[2]).equals(thisNodeID)) //check if his nextID is correct with your ID
                        {
                            previousNodeID = Integer.valueOf(receivedAr[1]); //his ID is now your previous nodeID
                        }
                    }
                    else if(receivedAr[0].equals("n")) //its a next node message
                    {
                        if(Integer.valueOf(receivedAr[2]).equals(thisNodeID)) //check if his previousID is correct with your ID
                        {
                            nextNodeID = Integer.valueOf(receivedAr[1]); //his ID is now your next nodeID
                        }
                    }
                }
            }
        }catch(Exception e)
        {
            System.err.println("Client exception: " + e.toString());
            e.printStackTrace();
        }
    }
}
