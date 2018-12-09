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

import static java.nio.file.LinkOption.NOFOLLOW_LINKS;
import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.OVERFLOW;


public class NamingNode
{
    //private static NamingNode nn;

    private Integer thisNodeID;
    public Integer nextNodeID;
    private Integer previousNodeID;

    private static boolean fileReplicationRunning;


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
    public void failure(Integer failedNode, final NamingInterface stub) throws IOException, XMLStreamException
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
    public void discovery(final NamingInterface stub) throws IOException, XMLStreamException
    {
        String received;
        String[] receivedAr;
        byte buf[] = new byte[1024];
        int amountOfNodes;
        String nodeMessage;
        boolean running = true;

        Thread startUpThr;
        Thread FileDwnThr;
        while(running)
        {
            try {
                DatagramSocket UCreceivingSocket = new DatagramSocket(Constants.UDP_PORT);
                DatagramSocket UCsendingSocket = new DatagramSocket();
                DatagramPacket receivingPack = new DatagramPacket(buf, buf.length, InetAddress.getByName(Constants.MULTICAST_IP), Constants.MULTICAST_PORT);


                UCreceivingSocket.receive(receivingPack);
                received = new String(receivingPack.getData(), 0, receivingPack.getLength());
                if (receivingPack.getAddress().toString().equals("/192.168.0.4")) //if from server IP
                {
                    receivedAr = received.split(" ");
                    amountOfNodes = Integer.parseInt(receivedAr[0]);
                    System.out.println("AoN: "+amountOfNodes);
                    if (amountOfNodes == 1) {
                        nextNodeID = thisNodeID;
                        previousNodeID = thisNodeID;
                        System.out.println("nextNode = " + nextNodeID + " , previousNode = " + previousNodeID);
                    }
                    else if(amountOfNodes > 1)
                    {
                        System.out.println("AoN: "+amountOfNodes);
                        fileReplicationRunning = true; //if there are more than 1 node in the network, start replicating
                        previousNodeID = Integer.parseInt(receivedAr[1]);
                        nextNodeID = Integer.parseInt(receivedAr[2]);
                        nodeMessage = "p " + thisNodeID;
                        UDPSend(UCsendingSocket, nodeMessage, stub.getIP(previousNodeID), Constants.UDP_PORT);
                        nodeMessage = "n " + thisNodeID;
                        UDPSend(UCsendingSocket, nodeMessage, stub.getIP(nextNodeID), Constants.UDP_PORT);
                    /*startUpThr = new Thread(new Runnable() {
                        @Override
                        public void run() {
                            try{ fileReplicationStartup(stub); }catch(Exception e) {}
                        }
                    });
                    startUpThr.start();*/
                    }
                    else System.out.println("Error: amount of nodes smaller than 0!");
                }
                else //if from any other IP => node
                {
                    receivedAr = received.split(" ");

                    switch(receivedAr[0]) {
                        case "p": //its a previous node message
                            previousNodeID = Integer.valueOf(receivedAr[1]); //his ID is now your previous nodeID
                            if(!fileReplicationRunning) fileReplicationRunning = true; //there are two nodes in network, so start replicating
                            System.out.println("nextNode = " + nextNodeID + " , previousNode = " + previousNodeID);
                            break;

                        case "n": //its a next node message
                            nextNodeID = Integer.valueOf(receivedAr[1]); //his ID is now your next nodeID
                            if(!fileReplicationRunning) fileReplicationRunning = true; //there are two nodes in network, so start replicating
                            System.out.println("nextNode = " + nextNodeID + " , previousNode = " + previousNodeID);
                            break;

                        case "f": //its a message with filename
                            UDPSend(UCsendingSocket,"ack",receivingPack.getAddress().toString(),Constants.UDPFileName_PORT); //send ack to let uploader know you are ready
                            FileDownloadHandler FDH = new FileDownloadHandler(receivedAr[1]); //start TCP socket thread
                            FileDwnThr = new Thread(FDH); //will be listening for incoming TCP downloads
                            FileDwnThr.start();
                            break;

                        default:
                            break;
                    }
                }
            }catch (Exception e) {}
            shutdown(stub,thisNodeID,nextNodeID,previousNodeID);
            break;
        }
    }

    //at startup, checks local directory for and send files to the correct replication node (happens once)
    public void fileReplicationStartup(NamingInterface stub) throws RemoteException, IOException
    {
        Integer replicationNode;
        Thread FileUplHThr;
        File[] listOfFiles = Constants.localFileDirectory.listFiles();
        for (int i = 0; i < listOfFiles.length; i++)
        {
            if (listOfFiles[i].isFile())
            {
                //determine node where the replicated file will be stored
                if((stub.fileLocator(listOfFiles[i].getName())).equals(thisNodeID)) //if replication node is the current node
                {
                    replicationNode = previousNodeID; //replication will be on the previous node
                }
                else replicationNode = stub.fileLocator(listOfFiles[i].getName()); //ask NameServer on which node it should be stored

                this.fileOwnerMap.put(listOfFiles[i].getName(), replicationNode);

                //Start file upload, to replication node, in another thread
                FileUploadHandler FUH = new FileUploadHandler(listOfFiles[i].getName(), stub.getIP(replicationNode));
                FileUplHThr = new Thread(FUH);
                FileUplHThr.start();
            }
        }
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
                    FileUploadHandler FUH = new FileUploadHandler(listOfFiles[i].getName(), stub.getIP(replicationNode), true);
                    FileUplHThr = new Thread(FUH);
                    FileUplHThr.start();
                }
            }
        }
    }

    //watches local directory for changes
    public void directoryWatcher(NamingInterface stub)
    {
        String newPathString;
        Integer replicationNode;
        Thread FileUplHThr;

        try {
            Boolean isFolder = (Boolean) Files.getAttribute(Constants.localFileDirectory.toPath(),
                    "basic:isDirectory", NOFOLLOW_LINKS);
            if (!isFolder) {
                throw new IllegalArgumentException("Path: " + Constants.localFileDirectory.toPath()
                        + " is not a folder");
            }
        } catch (IOException ioe) {
            // Folder does not exists
            ioe.printStackTrace();
        }

        System.out.println("Watching path: " + Constants.localFileDirectory.toPath());

        // We obtain the file system of the Path
        FileSystem fs = Constants.localFileDirectory.toPath().getFileSystem();

        // We create the new WatchService using the new try() block
        try (WatchService service = fs.newWatchService()) {

            // We register the path to the service
            // We watch for creation events
            Constants.localFileDirectory.toPath().register(service, ENTRY_CREATE, ENTRY_DELETE);

            // Start the infinite polling loop
            WatchKey key = null;
            while (true) {
                key = service.take();

                // Dequeueing events
                WatchEvent.Kind<?> kind = null;
                for (WatchEvent<?> watchEvent : key.pollEvents()) {
                    // Get the type of the event
                    kind = watchEvent.kind();
                    if (OVERFLOW == kind) {
                        continue; // loop
                    } else if (ENTRY_CREATE == kind) {
                        // A new Path was created
                        Path newPath = ((WatchEvent<Path>) watchEvent)
                                .context();
                        newPathString = newPath.toString();

                        //if a file gets uploaded locally
                        replicationNode = stub.fileLocator(newPathString);
                        FileUploadHandler FUH = new FileUploadHandler(newPathString, stub.getIP(replicationNode));
                        FileUplHThr = new Thread(FUH);
                        FileUplHThr.start();

                        // Output
                        //System.out.println("New file created: " + newPath);
                    } else if (ENTRY_DELETE == kind) {
                        // modified
                        Path newPath = ((WatchEvent<Path>) watchEvent)
                                .context();
                        // Output
                        System.out.println("File deleted: " + newPath);
                    }
                }

                if (!key.reset()) {
                    break; // loop
                }
            }
        }catch(Exception e){}
    }

    public void start() throws IOException
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
            MCSocket.joinGroup(InetAddress.getByName(Constants.MULTICAST_IP));;

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

            //Check for changes in directory
            /*DirWatcherThr = new Thread(new Runnable() {
                @Override
                public void run() {
                    directoryWatcher(stub);
                }
            });
            DirWatcherThr.start();*/
        } catch (Exception e) {
            System.err.println("Client exception: " + e.toString());
            e.printStackTrace();
        }
    }
}
