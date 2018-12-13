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
    public Integer thisNodeID;
    public Integer nextNodeID;
    private Integer previousNodeID;
    private int amountOfNodes = 0;

    private boolean uploadDone = true;
    private boolean discoveryRunning = true;
    private boolean fileListenerRunning = true;
    private boolean fileAgentHandlerRunning = true;
    private static DatagramSocket filenameSocket = null;
    private NamingInterface stub;
    private Thread fileAgentThr;


    public TreeMap<String,Integer> filenameMap = new TreeMap<>(); // filename - lock (on 1 - off 0)

    public NamingNode() {}

    public Integer calculateHash(String hostname)
    {
        return Math.abs(hostname.hashCode()) % 32768;
    }

    //calculate port based on nodeID
    public int calculatePort(Integer nodeID)
    {
        String nodeIDStr = Integer.toString(nodeID);
        return 5000 + Math.abs(nodeIDStr.hashCode()) % 2000; //TCP port dependant on nodeID, ports between 5000 and 7000
    }

    //if user requests to download a file
    public void downloadFile(String filename) throws IOException, InterruptedException
    {
        String locationIP;
        Thread FileDwnThr;

        if(filenameMap.get(filename) != 0) //if file isn't locked
        {
            filenameMap.put(filename, thisNodeID); //lock it with own nodeID
        }
        locationIP = stub.getIP(stub.fileLocator(filename)); //get location where file is stored (replicated)

        UDPSend(filenameSocket,"ack "+filename,locationIP,Constants.UDPFileName_PORT); //send ack to let uploader know you are ready
        FileDownloadHandler FDH = new FileDownloadHandler(filename, calculatePort(stub.fileLocator(filename))); //start TCP socket thread
        FileDwnThr = new Thread(FDH); //will be listening for incoming TCP downloads
        FileDwnThr.start();
        FileDwnThr.join();
        filenameMap.put(filename, 0); //should be unlocked
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
    public void shutdown(Integer thisNodeID, Integer nextNodeID, Integer previousNodeID) throws IOException, XMLStreamException
    {
        DatagramSocket sendingSocket = new DatagramSocket();
        Thread FileUplHThr;
        String nextIP = stub.getIP(nextNodeID);
        String previousIP = stub.getIP(previousNodeID);
        //send nextNodeID to your previous node
        String sendingStr = "n " + nextNodeID;
        UDPSend(sendingSocket, sendingStr, previousIP, Constants.UDP_PORT);
        //send previousNodeID to your next node
        sendingStr = "p " + previousNodeID;
        UDPSend(sendingSocket, sendingStr, nextIP, Constants.UDP_PORT);

        File[] listOfFiles = Constants.replicationFileDirectory.listFiles();
        //send all files from replicationDir to the previous node and delete them, locally, when done
        for (int i = 0; i < listOfFiles.length; i++)
        {
            if (listOfFiles[i].isFile())
            {
                UDPSend(filenameSocket, "f " + listOfFiles[i].getName(),stub.getIP(previousNodeID), Constants.UDPFileName_PORT); //upload will now be handles in filelistener() thread
                do{
                    //nothing
                }while(!uploadDone);
                listOfFiles[i].delete(); //if upload done, delete
            }
        }
        discoveryRunning = false;
        fileListenerRunning = false;
        fileAgentHandlerRunning = false; //turn off remaining threads
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
    public void failure(Integer failedNode) throws IOException
    {
        DatagramSocket UCsocket = new DatagramSocket(Constants.UDP_PORT);
        String receivedAr[] = stub.failure(failedNode).split(" ");
        //give previous node of the failed one, his new next node
        Integer previousNode = Integer.valueOf(receivedAr[0]);
        Integer nextNode = Integer.valueOf(receivedAr[1]);
        if(previousNode == nextNode)
        {
            UDPSend(UCsocket, "pn "+previousNode, stub.getIP(previousNodeID), Constants.UDP_PORT);
        }
        String nodeMessage = "n " + nextNode;
        UDPSend(UCsocket, nodeMessage, stub.getIP(previousNode), Constants.UDP_PORT);
        //give next node of the failed one, his new previous node
        nodeMessage = "p " + previousNode;
        UDPSend(UCsocket, nodeMessage, stub.getIP(nextNode), Constants.UDP_PORT);
        UCsocket.close();
    }

    //Listens for UDP unicasts, from server or other nodes, with info over new nodes or when replication file needs to be downloaded
    public void discovery() throws IOException, XMLStreamException {
        String received;
        String[] receivedAr;
        byte buf[] = new byte[1024];
        String nodeMessage;

        Thread updateUpThr;
        DatagramSocket UCreceivingSocket = new DatagramSocket(Constants.UDP_PORT);
        DatagramSocket UCsendingSocket = new DatagramSocket();
        DatagramPacket receivingPack = new DatagramPacket(buf, buf.length);
        while (discoveryRunning)
        {
            try {
                System.out.println("ready to receive amount of nodes");
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
                        previousNodeID = Integer.parseInt(receivedAr[1]);
                        nextNodeID = Integer.parseInt(receivedAr[2]);

                        if (previousNodeID.equals(nextNodeID))
                        {
                            nodeMessage = "pn " + thisNodeID;
                            UDPSend(UCsendingSocket, nodeMessage, stub.getIP(previousNodeID), Constants.UDP_PORT);
                            System.out.println(nodeMessage);
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
                        FileAgent fAgent = new FileAgent(this);
                        fileAgentThr = new Thread(fAgent);
                        fileAgentThr.start();
                    }
                    else System.out.println("Error: amount of nodes smaller than 0!");
                }
                else //if from any other IP => node
                {
                    receivedAr = received.split(" ");
                    //System.out.println(receivedAr);
                    switch (receivedAr[0])
                    {
                        case "p": //its a previous node message
                            previousNodeID = Integer.valueOf(receivedAr[1]); //his ID is now your previous nodeID
                            System.out.println("nextNode = " + nextNodeID + " , previousNode = " + previousNodeID);
                            updateUpThr = new Thread(new Runnable() {
                                @Override
                                public void run() {
                                    try{fileReplicationUpdate();} catch(Exception e){}
                                }
                            });
                            updateUpThr.start();
                            break;

                        case "n": //its a next node message
                            nextNodeID = Integer.valueOf(receivedAr[1]); //his ID is now your next nodeID
                            System.out.println("nextNode = " + nextNodeID + " , previousNode = " + previousNodeID);
                            break;

                        case "pn": //next and previous node are the same (amount of nodes = 2)
                            nextNodeID = Integer.valueOf(receivedAr[1]); //his ID is now your next nodeID
                            previousNodeID = Integer.valueOf(receivedAr[1]);
                            System.out.println("nextNode = " + nextNodeID + " , previousNode = " + previousNodeID);
                            updateUpThr = new Thread(new Runnable() {
                                @Override
                                public void run() {
                                    try{fileReplicationUpdate();} catch(Exception e){}
                                }
                            });
                            updateUpThr.start();
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
    public void filenameListener() throws IOException, InterruptedException
    {
        String received;
        String[] receivedAr;
        byte buf[] = new byte[1024];
        Thread FileDwnThr;
        Thread FileUplHThr;
        Integer sendingNode;

        DatagramPacket receivingPack = new DatagramPacket(buf, buf.length);

        while(fileListenerRunning)
        {
            System.out.println("ready to receive file");
            filenameSocket.receive(receivingPack);

            received = new String(receivingPack.getData(), 0, receivingPack.getLength());
            System.out.println("pakket received: " + received);

            receivedAr = received.split(" ");
            switch(receivedAr[0])
            {
                case "f":
                    System.out.println(receivingPack.getAddress().toString());
                    UDPSend(filenameSocket,"ack "+receivedAr[1],receivingPack.getAddress().toString().replace("/",""),Constants.UDPFileName_PORT); //send ack to let uploader know you are ready
                    System.out.println("ACK sent"); //send back ack and the filename
                    sendingNode = stub.getNodeID(receivingPack.getAddress().toString().replace("/",""));
                    FileDownloadHandler FDH = new FileDownloadHandler(receivedAr[1], calculatePort(sendingNode)); //start TCP socket thread
                    System.out.println("filename: "+receivedAr[1]);
                    FileDwnThr = new Thread(FDH); //will be listening for incoming TCP downloads
                    FileDwnThr.start();
                    FileDwnThr.join();
                    File[] listOfFiles = Constants.localFileDirectory.listFiles();
                    for (int i = 0; i < listOfFiles.length; i++) {
                        if (listOfFiles[i].isFile())
                        {
                            //if file is local on server, send it to your previous node
                            receivedAr[1].equals(listOfFiles[i]);
                            UDPSend(filenameSocket, "f " + receivedAr[1], stub.getIP(previousNodeID), Constants.UDPFileName_PORT);
                        }
                    }
                    break;

                case "ack":
                    System.out.println("ack received");
                    uploadDone = false; //use this variable so other processes know when they can start new upload threads

                    FileUploadHandler FUH = new FileUploadHandler(receivedAr[1], receivingPack.getAddress().toString().replace("/",""), calculatePort(thisNodeID));
                    FileUplHThr = new Thread(FUH);
                    FileUplHThr.start();
                    FileUplHThr.join();
                    uploadDone = true;
                    break;
            }
        }
    }

    //at startup, checks local directory for and send files to the correct replication node (happens once)
    public void fileReplicationStartup() throws IOException
    {
        Integer replicationNode;

        File[] listOfFiles = Constants.localFileDirectory.listFiles();
        if(amountOfNodes == 1) //if only node in the network just copy files to own replication folder
        {
            for (int i = 0; i < listOfFiles.length; i++) {
                System.out.println("a  " + listOfFiles[i]);
                if (listOfFiles[i].isFile())
                {
                    Files.copy(listOfFiles[i].toPath(), Paths.get(listOfFiles[i].toString().replace("local", "replication")));
                    filenameMap.put(listOfFiles[i].toString().replace("/home/pi/Documents/local/",""), 0); //put filename in map and lock off
                }
            }
        }
        else
        {
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

                    //Start file upload, to replication node, in another thread

                    UDPSend(filenameSocket, "f " + listOfFiles[i].getName(),stub.getIP(replicationNode), Constants.UDPFileName_PORT); //upload will now be handles in filelistener() thread
                    do{
                        //nothing
                    }while(!uploadDone);
                }
            }
        }
        System.out.println("FileRep Startup done!");
    }

    //gets called when previous node gets added, node checks if its replication files need to be stored on the new node
    public void fileReplicationUpdate() throws IOException
    {
        Integer replicationNode;

        File[] listOfFiles = Constants.replicationFileDirectory.listFiles();
        for (int i = 0; i < listOfFiles.length; i++)
        {
            if (listOfFiles[i].isFile())
            {
                //if file shouldn't be stored on nn node anymore
                if(!(stub.fileLocator(listOfFiles[i].getName())).equals(thisNodeID))
                {
                    //upload to other node and gets deleted on nn one
                    if(uploadDone)
                    {
                        replicationNode = stub.fileLocator(listOfFiles[i].getName());

                        UDPSend(filenameSocket, "f " + listOfFiles[i].getName(),stub.getIP(replicationNode), Constants.UDPFileName_PORT); //upload will now be handles in filelistener() thread
                        do{
                            //nothing
                        }while(!uploadDone);
                        listOfFiles[i].delete();
                    }
                }
            }
        }
    }

    //only needed first time because thread is created in Discovery thread and this one can't wait for fileAgentThread to end. It needs to handle other incoming requests
    public void fileAgentHandler()
    {
        while(fileAgentHandlerRunning)
        {
            if(!fileAgentThr.isAlive())
            {
                //send to next node
                fileAgentHandlerRunning = false;
            }
        }
    }

    public void fileAgentReceiver(FileAgent fAgent) throws InterruptedException
    {
        fileAgentThr = new Thread(fAgent);
        fileAgentThr.start();
        fileAgentThr.join();
        //send to next node
    }

    public void start()
    {
        //IP
        String hostname;
        String ipString;
        String nameIP;

        //Threads
        Thread UDPListener;
        Thread startUpThr;
        Thread filenameListenerThr;
        final Thread fileAgentHandlerThr;

        try {
            //RMI
            Registry registry = LocateRegistry.getRegistry(Constants.SERVER_IP, 1099); //server IP and port
            stub = (NamingInterface) registry.lookup("NamingInterface");

            //Bootstrap + Discovery
            File hostnameFile = new File("/home/pi/Documents/hostname.txt");
            BufferedReader BR = new BufferedReader(new FileReader(hostnameFile));
            hostname = BR.readLine();
            ipString = this.getThisIP().getHostAddress(); // InetAddress to string
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
                    try{discovery();}catch(Exception e){}
                }
            });
            UDPListener.start();

            filenameListenerThr = new Thread(new Runnable() {
                @Override
                public void run() {
                    try{filenameListener();}catch(Exception e){}
                }
            });
            filenameListenerThr.start();

            startUpThr = new Thread(new Runnable() {
                @Override
                public void run() {
                    try{ fileReplicationStartup(); }catch(Exception e) {}
                }
            });
            startUpThr.start();

            fileAgentHandlerThr = new Thread(new Runnable() {
                @Override
                public void run() {
                    fileAgentHandler();
                }
            });
            fileAgentHandlerThr.start();

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
