import javax.xml.stream.XMLStreamException;
import java.nio.file.*;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.io.*;
import java.net.*;
import java.util.*;
import java.net.InetAddress;
import java.rmi.server.*;
import java.util.concurrent.TimeoutException;

import static java.nio.file.LinkOption.NOFOLLOW_LINKS;
import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.OVERFLOW;


public class NamingNode
{
    private Integer thisNodeID;
    public Integer nextNodeID;
    private Integer previousNodeID;

    private static boolean fileReplicationRunning;
    private boolean uploadDone = true;
    private static DatagramSocket filenameSocket= null;


    private TreeMap<String,Integer> fileOwnerMap = new TreeMap<>(); //key is the filename, value is the nodeID it is stored on

    public NamingNode() {}

    public Integer calculateHash(String hostname)
    {
        return Math.abs(hostname.hashCode()) % 32768;
    }

    //get the IP from nn node
    public InetAddress getThisIP()
    {
        try {
            Enumeration e = NetworkInterface.getNetworkInterfaces();
            while (e.hasMoreElements()) {
                NetworkInterface n = (NetworkInterface) e.nextElement();
                Enumeration ee = n.getInetAddresses();
                while (ee.hasMoreElements()) {  //while through all IPs until we find the matching IP
                    InetAddress i = (InetAddress) ee.nextElement();
                    if (i.getHostAddress().contains("192.168.0.")) {
                        return i;
                    }
                }
            }
        }catch(Exception e) {}
        return null;
    }

    //Shutdown protocol
    public void shutdown(NamingInterface stub, Integer thisNodeID, Integer nextNodeID, Integer previousNodeID) throws IOException, XMLStreamException
    {
        DatagramSocket sendingSocket = new DatagramSocket();
        File[] listOfFiles = Constants.replicationFileDirectory.listFiles();
        Thread FileUplHThr;
        String nextIP = stub.getIP(nextNodeID);
        String previousIP = stub.getIP(previousNodeID);
        //send nextNodeID to your previous node
        String sendingStr = "n " + nextNodeID;
        UDPSend(sendingSocket, sendingStr, previousIP, Constants.UDP_PORT);
        //send previousNodeID to your next node
        sendingStr = "p " + previousNodeID;
        UDPSend(sendingSocket, sendingStr, nextIP, Constants.UDP_PORT);

        //send all files from replicationDir to the previous node and delete them, locally, when done
        /*for (int i = 0; i < listOfFiles.length; i++)
        {
            if (listOfFiles[i].isFile())
            {
                FileUploadHandler FUH = new FileUploadHandler(listOfFiles[i].getName(), previousIP, true);
                FileUplHThr = new Thread(FUH);
                FileUplHThr.start();
            }
        }*/
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
    public void failure(Integer failedNode, final NamingInterface stub) throws IOException
    {
        DatagramSocket UCsocket = new DatagramSocket(Constants.UDP_PORT);
        String receivedAr[] = stub.failure(failedNode).split(" ");
        //give previous node of the failed one, his new next node
        Integer previousNode = Integer.valueOf(receivedAr[0]);
        Integer nextNode = Integer.valueOf(receivedAr[1]);
        String nodeMessage = "n " + nextNode;
        UDPSend(UCsocket, nodeMessage, stub.getIP(previousNode), Constants.UDP_PORT);
        //give next node of the failed one, his new previous node
        nodeMessage = "p " + previousNode;
        UDPSend(UCsocket, nodeMessage, stub.getIP(nextNode), Constants.UDP_PORT);
        UCsocket.close();
    }

    //Listens for UDP unicasts, from server or other nodes, with info over new nodes or when replication file needs to be downloaded
    public void discovery(final NamingInterface stub) throws IOException, XMLStreamException {
        String received;
        String[] receivedAr;
        byte buf[] = new byte[1024];
        int amountOfNodes;
        String nodeMessage;
        boolean running = true;

        Thread startUpThr;
        Thread FileDwnThr;
        DatagramSocket UCreceivingSocket = new DatagramSocket(Constants.UDP_PORT);
        DatagramSocket UCsendingSocket = new DatagramSocket();
        DatagramPacket receivingPack = new DatagramPacket(buf, buf.length);
        while (running) {
            try {
                System.out.println("ready to receive");
                UCreceivingSocket.receive(receivingPack);
                received = new String(receivingPack.getData(), 0, receivingPack.getLength());
                System.out.println(received);
                if (receivingPack.getAddress().toString().equals("/192.168.0.4")) //if from server IP
                {
                    System.out.println(received);
                    receivedAr = received.split(" ");
                    amountOfNodes = Integer.parseInt(receivedAr[0]);
                    System.out.println("AoN: " + amountOfNodes);
                    if (amountOfNodes == 1)
                    {
                        nextNodeID = thisNodeID;
                        previousNodeID = thisNodeID;
                        System.out.println("nextNode = " + nextNodeID + " , previousNode = " + previousNodeID);
                    }
                    else if (amountOfNodes > 1)
                    {
                        System.out.println("AoN: " + amountOfNodes);
                        fileReplicationRunning = true; //if there are more than 1 node in the network, start replicating
                        previousNodeID = Integer.parseInt(receivedAr[1]);
                        nextNodeID = Integer.parseInt(receivedAr[2]);

                        if (previousNodeID.equals(nextNodeID))
                        {
                            nodeMessage = "pn " + thisNodeID;
                            UDPSend(UCsendingSocket, nodeMessage, stub.getIP(previousNodeID), Constants.UDP_PORT);
                            System.out.println(nodeMessage + " pn send");

                        }
                        else
                        {
                            nodeMessage = "p " + thisNodeID;
                            System.out.println("ip vorige : " + stub.getIP(previousNodeID));
                            UDPSend(UCsendingSocket, nodeMessage, stub.getIP(previousNodeID), Constants.UDP_PORT);
                            nodeMessage = "n " + thisNodeID;
                            UDPSend(UCsendingSocket, nodeMessage, stub.getIP(nextNodeID), Constants.UDP_PORT);
                        }
                        System.out.println("nextNode = " + nextNodeID + " , previousNode = " + previousNodeID);

                        startUpThr = new Thread(new Runnable() {
                            @Override
                            public void run() {
                                try{ fileReplicationStartup(stub); }catch(Exception e) {}
                            }
                        });
                        startUpThr.start();
                    }
                    else System.out.println("Error: amount of nodes smaller than 0!");
                }
                else //if from any other IP => node
                {
                    System.out.println(received + " goed aangekomen");
                    receivedAr = received.split(" ");
                    System.out.println(receivedAr);
                    switch (receivedAr[0])
                    {
                        case "p": //its a previous node message
                            previousNodeID = Integer.valueOf(receivedAr[1]); //his ID is now your previous nodeID
                            if (!fileReplicationRunning)
                                fileReplicationRunning = true; //there are two nodes in network, so start replicating
                            System.out.println("nextNode = " + nextNodeID + " , previousNode = " + previousNodeID);
                            break;

                        case "n": //its a next node message
                            nextNodeID = Integer.valueOf(receivedAr[1]); //his ID is now your next nodeID
                            if (!fileReplicationRunning)
                                fileReplicationRunning = true; //there are two nodes in network, so start replicating
                            System.out.println("nextNode = " + nextNodeID + " , previousNode = " + previousNodeID);
                            break;
                        case "pn":
                            nextNodeID = Integer.valueOf(receivedAr[1]); //his ID is now your next nodeID
                            previousNodeID = Integer.valueOf(receivedAr[1]);
                            if (!fileReplicationRunning)
                                fileReplicationRunning = true; //there are two nodes in network, so start replicating
                            System.out.println("nextNode = " + nextNodeID + " , previousNode = " + previousNodeID);
                            startUpThr = new Thread(new Runnable() {
                                @Override
                                public void run() {
                                    try{ fileReplicationStartup(stub); }catch(Exception e) {}
                                }
                            });
                            startUpThr.start();
                            break;

                        default:
                            break;
                    }
                }

            } catch (Exception s) {
                //    shutdown(stub,thisNodeID,nextNodeID,previousNodeID);
                //break;
            }
        }
    }

    //listens to the specific filename port
    public void filenameListener(NamingInterface stub) throws IOException, InterruptedException
    {
        String received;
        String[] receivedAr;
        byte buf[] = new byte[1024];
        Thread FileDwnThr;
        boolean running = true;
        Thread FileUplHThr;

        DatagramPacket receivingPack = new DatagramPacket(buf, buf.length);

        while(running)
        {
            System.out.println("ready to receive file");
            filenameSocket.receive(receivingPack);

            received = new String(receivingPack.getData(), 0, receivingPack.getLength());
            System.out.println("pakket received: " + received);

            receivedAr = received.split(" ");
            switch(receivedAr[0])
            {
                case "f":
                    File[] listOfFiles = Constants.localFileDirectory.listFiles();
                    for (int i = 0; i < listOfFiles.length; i++) {
                        if (listOfFiles[i].isFile()) //if( !listOfFiles[i].isDirectory())
                        {
                            receivedAr[1].equals(listOfFiles[i]);
                            UDPSend(filenameSocket, "IP " + stub.getIP(previousNodeID)+receivedAr[1], receivingPack.getAddress().toString().replace("/",""), Constants.UDPFileName_PORT);
                            break;                                  //send back IP where it should be replicated and the filename
                        }
                    }
                    System.out.println(receivingPack.getAddress().toString());
                    UDPSend(filenameSocket,"ack"+receivedAr[1],receivingPack.getAddress().toString().replace("/",""),Constants.UDPFileName_PORT); //send ack to let uploader know you are ready
                    System.out.println("ACK sent"); //send back ack and the filename
                    FileDownloadHandler FDH = new FileDownloadHandler(receivedAr[1], filenameSocket); //start TCP socket thread
                    System.out.println("filename: "+receivedAr[1]);
                    FileDwnThr = new Thread(FDH); //will be listening for incoming TCP downloads
                    FileDwnThr.start();
                    break;

                case "ack":
                    uploadDone = false; //use this variable so other processes know when they can start new upload threads
                    FileUploadHandler FUH = new FileUploadHandler(receivedAr[1], receivingPack.getAddress().toString().replace("/",""));
                    FileUplHThr = new Thread(FUH);
                    FileUplHThr.start();
                    FileUplHThr.join();
                    uploadDone = true;
                    break;

                case "IP":
                    UDPSend(filenameSocket, "f " + receivedAr[2], receivedAr[1], Constants.UDPFileName_PORT); //should be replicated to new node, so send out new f request
            }                                                                                                          //and wait for ack
        }
    }

    //at startup, checks local directory for and send files to the correct replication node (happens once)
    public void fileReplicationStartup(NamingInterface stub) throws IOException
    {
        Integer replicationNode;

        File[] listOfFiles = Constants.localFileDirectory.listFiles();
        for (int i = 0; i < listOfFiles.length; i++)
        {
            System.out.println("a  " + listOfFiles[i]);
            if (listOfFiles[i].isFile()) //if( !listOfFiles[i].isDirectory())
            {
                //determine node where the replicated file will be stored
                if((stub.fileLocator(listOfFiles[i].getName())).equals(thisNodeID)) //if replication node is the current node
                {
                    System.out.println("zelfde ID");
                    replicationNode = previousNodeID; //replication will be on the previous node
                }
                else replicationNode = stub.fileLocator(listOfFiles[i].getName()); //ask NameServer on which node it should be stored

                System.out.println(replicationNode);
                this.fileOwnerMap.put(listOfFiles[i].getName(), replicationNode);

                //Start file upload, to replication node, in another thread

                UDPSend(filenameSocket, "f " + listOfFiles[i].getName(),stub.getIP(replicationNode), Constants.UDPFileName_PORT);
                do{
                    //nothing
                }while(!uploadDone);
            }
        }
        System.out.println("FileRep Startup done!");
    }

    //gets called when previous node gets added, node checks if its replication files need to be stored on the new node
    public void fileReplicationUpdate(NamingInterface stub) throws IOException
    {
        Integer replicationNode;
        Thread FileUplHThr;

        File[] listOfFiles = Constants.replicationFileDirectory.listFiles();
        for (int i = 0; i < listOfFiles.length; i++)
        {
            if (listOfFiles[i].isFile())
            {
                //if file shouldn't be stored on nn node anymore
                if(!(stub.fileLocator(listOfFiles[i].getName())).equals(thisNodeID))
                {
                    //upload to other node and gets deleted on nn one
                    replicationNode = stub.fileLocator(listOfFiles[i].getName());
                   // FileUploadHandler FUH = new FileUploadHandler(listOfFiles[i].getName(), stub.getIP(replicationNode), filenameSocket,true);
                   // FileUplHThr = new Thread(FUH);
                    //FileUplHThr.start();
                }
            }
        }
    }

    public void start()
    {
        //IP
        String hostname;
        String ipString;
        String nameIP;

        //Discovery
        Thread UDPListener;

        //File replication
        fileReplicationRunning = false;
        Thread DirWatcherThr;


        try {
            //RMI
            Registry registry = LocateRegistry.getRegistry(Constants.SERVER_IP, 1099); //server IP and port
            final NamingInterface stub = (NamingInterface) registry.lookup("NamingInterface");

            //Bootstrap + Discovery
            ipString = this.getThisIP().getHostAddress(); // InetAddress to string
            hostname = "Node" + ipString.substring(10); //hostname dependant on last digit of IP
            thisNodeID = calculateHash(hostname);

            //Multicast send IP + hostname to all
            MulticastSocket MCSocket = new MulticastSocket(Constants.MULTICAST_PORT);
            MCSocket.setNetworkInterface(NetworkInterface.getByInetAddress(InetAddress.getByName(ipString)));
            MCSocket.joinGroup(InetAddress.getByName(Constants.MULTICAST_IP));

            filenameSocket = new DatagramSocket(Constants.UDPFileName_PORT);

            //bootstrap message
            nameIP = "b " + ipString + " " + hostname;
            this.UDPSend(MCSocket, nameIP, Constants.MULTICAST_IP, Constants.MULTICAST_PORT);

            //Create threads for incoming UDP unicast messages
            UDPListener = new Thread(new Runnable() {
                @Override
                public void run() {
                    try{discovery(stub);}catch(Exception e){}
                }
            });
            UDPListener.start();
            Thread filenameListenerThr = new Thread(new Runnable() {
                @Override
                public void run() {
                    try{filenameListener(stub);}catch(Exception e){}
                }
            });
            filenameListenerThr.start();

           /* //Check for changes in directory
            DirectoryWatcher DW = new DirectoryWatcher(stub);
            DirWatcherThr = new Thread(DW);
            DirWatcherThr.start();
            */


        } catch (Exception e) {
            System.err.println("Client exception: " + e.toString());
            e.printStackTrace();
        }
    }
}
