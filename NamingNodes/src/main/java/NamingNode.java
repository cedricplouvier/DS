import javax.xml.stream.XMLStreamException;
import java.awt.*;
import java.nio.file.*;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.io.*;
import java.net.*;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import java.net.InetAddress;

import static java.nio.file.LinkOption.NOFOLLOW_LINKS;
import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.OVERFLOW;


public class NamingNode implements AgentInterface
{
    public Integer thisNodeID;
    public Integer nextNodeID;
    private Integer previousNodeID;
    private int amountOfNodes = 0;

    private boolean uploadDone = true;
    private boolean discoveryRunning = true;
    private boolean fileListenerRunning = true;
    private boolean fileAgentHandlerRunning = true;
    private boolean failureCheckRun = true;
    private boolean waitForFileRep = false;
    private static DatagramSocket filenameSocket = null;
    private NamingInterface namingServer;
    private AgentInterface rmiNextNode;
    private Thread fileAgentThr;
    private Registry ClRegistry;
    private Registry SvRegistery;


    public TreeMap<String,FileProperties> filenameMap = new TreeMap<>(); // filename - lock (on 1 - off 0)

    public NamingNode() {}

    public String hello()
    {
        return "hello";
    }

    //-------------------------------------------------------------------------//
    // Infrastructure functions
    //-------------------------------------------------------------------------//

    public Integer calculateHash(String hostname)
    {
        return Math.abs(hostname.hashCode()) % 32768;
    }

    //calculate port based on nodeID
    public int calculatePort(Integer nodeID)
    {
        String nodeIDStr = Integer.toString(nodeID);
        return 20000 + Math.abs(nodeIDStr.hashCode()) % 1000; //TCP port dependant on nodeID, ports between 5000 and 7000
    }

    //sending packets with UDP, unicast/multicast
    public void UDPSend(DatagramSocket sendingSocket, String message, String ip, int port)
    {
        try{
            byte buf[] = message.getBytes();
            DatagramPacket pack = new DatagramPacket(buf, buf.length, InetAddress.getByName(ip),port);
            sendingSocket.send(pack);
        }catch(IOException ie)
        {
            try {failure(namingServer.getNodeID(ip));} catch(Exception e){}
        }
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


    //-------------------------------------------------------------------------//
    // GUI functions
    //-------------------------------------------------------------------------//

    //if user requests to download a file
    public void downloadAndOpenFile(String filename) throws IOException, InterruptedException
    {
        String locationIP;
        Thread FileDwnThr;
        Desktop desktop = Desktop.getDesktop();
        
        if(this.filenameMap.get(filename).getIsLocal())
        {
            File myFile = new File(Constants.localFileDirectory.toString() + filename);
            desktop.open(myFile);
            return; //if local, open file and end function
        }

        if(this.filenameMap.get(filename).getLock() != 0) //if file isn't locked
        {
            this.filenameMap.put(filename, new FileProperties(thisNodeID, false, this.filenameMap.get(filename).getLocalNode())); //lock it with own nodeID, previous if statement implies the file isn't local
        }
        locationIP = namingServer.getIP(namingServer.fileLocator(filename)); //get location where file is stored (replicated)

        UDPSend(filenameSocket,"ack "+filename,locationIP,Constants.UDPFileName_PORT); //send ack to let uploader know you are ready
        FileDownloadHandler FDH = new FileDownloadHandler(filename, calculatePort(namingServer.fileLocator(filename)), this, namingServer.getNodeID(locationIP)); //start TCP socket thread
        FileDwnThr = new Thread(FDH); //will be listening for incoming TCP downloads
        FileDwnThr.start();
        FileDwnThr.join();
        UDPSend(filenameSocket,"rec",locationIP,Constants.UDPFileName_PORT);
        this.filenameMap.put(filename, new FileProperties(0, false, this.filenameMap.get(filename).getLocalNode())); //should be unlocked
        File myFile = new File(Constants.replicationFileDirectory.toString() + filename);
        desktop.open(myFile);
    }

    //if user requests to remove file from entire network
    public void removeFileFromNetwork(String filename) throws RemoteException, InterruptedException
    {
        if(this.filenameMap.get(filename).getLock() != 0) //if file isn't locked
        {
            this.filenameMap.put(filename, new FileProperties(thisNodeID, true, thisNodeID)); //lock it with own nodeID
            FileRemovalAgent frAgent = new FileRemovalAgent(filename);
            Thread fileRemovalAgentThr = new Thread(frAgent);
            fileRemovalAgentThr.start();
            fileRemovalAgentThr.join();
            rmiNextNode.fileRemovalAgentReceiver(frAgent);
        }
    }

    //if user requests to remove file local only
    public void removeFileLocally(String filename)
    {
        File myFile = new File(Constants.localFileDirectory.toString() + filename);
        myFile.delete();
    }

    //-------------------------------------------------------------------------//
    // Shutdown protocol
    //-------------------------------------------------------------------------//
    public void shutdown() throws IOException, XMLStreamException
    {
        System.out.println("Start shutdown");
        BufferedReader reader;
        DatagramSocket sendingSocket = new DatagramSocket();
        String nextIP = namingServer.getIP(nextNodeID);
        String previousIP = namingServer.getIP(previousNodeID);
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
                UDPSend(filenameSocket, "f " + listOfFiles[i].getName(),namingServer.getIP(previousNodeID), Constants.UDPFileName_PORT); //upload will now be handles in filelistener() thread
                do{
                    //nothing
                    System.out.println("UDPsent");
                }while(!uploadDone);
                String filen = listOfFiles[i].getName();
                System.out.println(filen);
                File file = new File(filen);
                if(file.delete()) System.out.println(filen + " deleted"); //if upload done, delete
            }
        }
        //
        /*reader = new BufferedReader(new FileReader("/home/pi/Documents/filelog.txt"));
        String[] lineSplit;
        String line = reader.readLine();
        while(line != null)
        {
            lineSplit = line.split("\\s+");
            String filename = lineSplit[0];
            String ownerNode = lineSplit[1];
            UDPSend(filenameSocket, "f " + filename,ownerNode, Constants.UDPFileName_PORT);
            do{
                //nothing
                System.out.println("UDPsent 2");
            }while(!uploadDone);
            System.out.println("uploadDone");
        }
        reader.close();
        System.out.println("reader closed");*/

        //clear out the filelog before shutting down
        PrintWriter writer = new PrintWriter("/home/pi/Documents/filelog.txt");
        writer.print("");
        writer.close();

        discoveryRunning = false;
        failureCheckRun = false;
        fileListenerRunning = false;
        fileAgentHandlerRunning = false; //turn off remaining threads
        namingServer.removeNode(thisNodeID); //remove node from IPMap on the server
        System.out.println("shutdown complete");
    }

    public void shutdownCheck()
    {
        boolean running  = true;
        Scanner scanner = new Scanner(System.in);
        while(running)
        {
            String input = scanner.nextLine();  // Read user input
            if (input.equals("SH"))
            {
                try {
                    shutdown();
                    running = false; //otherwise keep checking
                } catch (Exception e) {
                }
            }
        }
    }
    //-------------------------------------------------------------------------//
    //Failure
    //-------------------------------------------------------------------------//

    //Failure Check
    public void failureCheck(Integer prevNodeID) throws IOException, InterruptedException
    {
        while(failureCheckRun)
        {
            String prevNodeIP = namingServer.getIP(previousNodeID);
            InetAddress prevHostIP = InetAddress.getByName(prevNodeIP); //every node only checks his neighbour, to prevent too much trafic
            boolean isReachable = prevHostIP.isReachable(10000); //waits for 10s for response and then times out
            if (!isReachable)
            {
                failure(previousNodeID); //if not Reachable => call failure method below
            }
            Thread.sleep(5000); //ping every 5 seconds
        }
    }

    //Failure protocol
    public void failure(Integer failedNode) throws IOException
    {
        DatagramSocket UCsocket = new DatagramSocket(Constants.UDP_PORT);
        String receivedAr[] = namingServer.failure(failedNode).split(" "); //server will return its previous and next node
        //give previous node of the failed one, his new next node
        Integer previousNode = Integer.valueOf(receivedAr[0]);
        Integer nextNode = Integer.valueOf(receivedAr[1]);
        if(previousNode.equals(nextNode)) //you are the only one left in the system
        {
            UDPSend(UCsocket, "pn "+previousNode, namingServer.getIP(previousNodeID), Constants.UDP_PORT);
        }
        String nodeMessage = "n " + nextNode;
        UDPSend(UCsocket, nodeMessage, namingServer.getIP(previousNode), Constants.UDP_PORT);
        //give next node of the failed one, his new previous node
        nodeMessage = "p " + previousNode;
        UDPSend(UCsocket, nodeMessage, namingServer.getIP(nextNode), Constants.UDP_PORT);
        UCsocket.close();
    }

    //-------------------------------------------------------------------------//
    // Discovery
    //-------------------------------------------------------------------------//

    //Listens for UDP unicasts, from server or other nodes, with info over new nodes or when replication file needs to be downloaded
    public void discovery() throws IOException, XMLStreamException {
        String received;
        String[] receivedAr;
        byte buf[] = new byte[1024];
        String nodeMessage;
        int rmiPort;
        String rmiStr;
        AgentInterface rmiPreviousNode;

        Thread updateUpThr;
        Thread failureCheckThr;
        Thread shutdownCheckThr;
        DatagramSocket UCreceivingSocket = new DatagramSocket(Constants.UDP_PORT);
        DatagramSocket UCsendingSocket = new DatagramSocket();
        DatagramPacket receivingPack = new DatagramPacket(buf, buf.length);

        System.out.println("Discovery started");
        while (discoveryRunning)
        {
            try {
                UCreceivingSocket.receive(receivingPack);
                received = new String(receivingPack.getData(), 0, receivingPack.getLength());
                System.out.println("received: " + received);
                if (receivingPack.getAddress().toString().equals("/192.168.0.4")) //if from server IP
                {
                    receivedAr = received.split(" ");
                    amountOfNodes = Integer.parseInt(receivedAr[0]);
                    System.out.println("AoN: " + amountOfNodes);

                    Thread fillFilenameMapThr = new Thread(new Runnable() {
                        @Override
                        public void run() {
                            fillFilenameMap();
                        }
                    });
                    fillFilenameMapThr.start();
                    fillFilenameMapThr.join();

                    if (amountOfNodes == 1)
                    {
                        nextNodeID = thisNodeID;
                        previousNodeID = thisNodeID;
                        System.out.println("nextNode = " + nextNodeID + " , previousNode = " + previousNodeID);
                    }
                    else if (amountOfNodes > 1)
                    {
                        previousNodeID = Integer.parseInt(receivedAr[1]);
                        nextNodeID = Integer.parseInt(receivedAr[2]);

                        if (previousNodeID.equals(nextNodeID))
                        {

                            nodeMessage = "pn " + thisNodeID;
                            UDPSend(UCsendingSocket, nodeMessage, namingServer.getIP(previousNodeID), Constants.UDP_PORT);
                            //Previous node RMI (your client)
                            /*try{rmiStr = Integer.toString(thisNodeID);
                            rmiPort = 1000 + Math.abs(rmiStr.hashCode()) % 1000;
                            rmiPreviousNode = (AgentInterface) UnicastRemoteObject.exportObject(this, 0);
                            SvRegistery = LocateRegistry.createRegistry(rmiPort);
                            SvRegistery.bind("AgentInterface", rmiPreviousNode);
                                System.out.println(rmiPreviousNode);}catch(Exception e){
                                System.err.println("server exc "+e.toString());
                                e.printStackTrace();
                            }*/



                            nodeMessage = "pn " + thisNodeID;
                            UDPSend(UCsendingSocket, nodeMessage, namingServer.getIP(previousNodeID), Constants.UDP_PORT);
                        }
                        else
                        {
                            nodeMessage = "p " + thisNodeID;
                            UDPSend(UCsendingSocket, nodeMessage, namingServer.getIP(previousNodeID), Constants.UDP_PORT);

                            nodeMessage = "n " + thisNodeID;
                            UDPSend(UCsendingSocket, nodeMessage, namingServer.getIP(nextNodeID), Constants.UDP_PORT);
                        }
                        System.out.println("nextNode = " + nextNodeID + " , previousNode = " + previousNodeID);

                        Thread startUpThr = new Thread(new Runnable() {
                            @Override
                            public void run() {
                                try{ fileReplicationStartup(); }catch(Exception e) {}
                            }
                        });
                        startUpThr.start();


                        /*Thread fileAgentHandlerThr = new Thread(new Runnable() {
                            @Override
                            public void run() {
                                try{ fileAgentHandler(); }catch(Exception e) {}
                            }
                        });
                        fileAgentHandlerThr.start();*/

                    }
                    else System.out.println("Error: amount of nodes smaller than 0!");
                }
                else //if from any other IP => node
                {
                    receivedAr = received.split(" ");
                    switch (receivedAr[0])
                    {
                        case "p": //its a previous node message
                            previousNodeID = Integer.valueOf(receivedAr[1]); //his ID is now your previous nodeID
                            System.out.println("nextNode = " + nextNodeID + " , previousNode = " + previousNodeID);
                            break;

                        case "n": //its a next node message
                            nextNodeID = Integer.valueOf(receivedAr[1]); //his ID is now your next nodeID
                            System.out.println("nextNode = " + nextNodeID + " , previousNode = " + previousNodeID);
                            break;

                        case "pn": //next and previous node are the same (amount of nodes = 2)
                            nextNodeID = Integer.valueOf(receivedAr[1]); //his ID is now your next nodeID
                            previousNodeID = Integer.valueOf(receivedAr[1]);
                            System.out.println("nextNode = " + nextNodeID + " , previousNode = " + previousNodeID);
                            amountOfNodes = amountOfNodes + 1; //doesn't respond with the truth. Only does on NamingServer
                            waitForFileRep = true;
                            break;

                        default:
                            break;
                    }
                }

                failureCheckThr = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try{
                            failureCheck(previousNodeID);
                        }catch(Exception e){}
                    }
                });
                failureCheckThr.start();

                shutdownCheckThr = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try{
                            shutdownCheck();
                        }catch(Exception e){}
                    }
                });
                shutdownCheckThr.start();

            } catch (Exception s) {
                //    shutdown(namingServer,thisNodeID,nextNodeID,previousNodeID);
                //break;
            }
        }
    }

    //-------------------------------------------------------------------------//
    // Replication
    //-------------------------------------------------------------------------//

    //listens to the specific filename port
    public void filenameListener() throws IOException, InterruptedException, NotBoundException
    {
        String received;
        String[] receivedAr;
        byte buf[] = new byte[1024];
        Thread FileDwnThr;
        Thread FileUplHThr;
        Integer sendingNode;
        PrintWriter writer = new PrintWriter("/home/pi/Documents/filelog.txt", "UTF-8");

        DatagramPacket receivingPack = new DatagramPacket(buf, buf.length);
        FileDownloadHandler FDH;
        File[] listOfFiles;

        while(fileListenerRunning)
        {
            filenameSocket.receive(receivingPack);

            received = new String(receivingPack.getData(), 0, receivingPack.getLength());
            System.out.println("received: " + received);

            receivedAr = received.split(" ");
            switch(receivedAr[0])
            {
                case "f":

                    writer.println(receivedAr[1] + " " + thisNodeID);
                    sendingNode = namingServer.getNodeID(receivingPack.getAddress().toString().replace("/",""));
                    FDH = new FileDownloadHandler(receivedAr[1], calculatePort(sendingNode),this, sendingNode); //start TCP socket thread
                    FileDwnThr = new Thread(FDH); //will be listening for incoming TCP downloads
                    FileDwnThr.start();
                    UDPSend(filenameSocket,"ack "+receivedAr[1],receivingPack.getAddress().toString().replace("/",""),Constants.UDPFileName_PORT); //send ack to let uploader know you are ready
                    FileDwnThr.join();
                    UDPSend(filenameSocket,"rec",receivingPack.getAddress().toString().replace("/",""),Constants.UDPFileName_PORT);
                    listOfFiles = Constants.localFileDirectory.listFiles();
                    for (int i = 0; i < listOfFiles.length; i++) {
                        if (listOfFiles[i].isFile() && receivedAr[1].equals(listOfFiles[i]))
                        {
                            System.out.println("file is local!");
                            //if file is local on server, send it to your previous node
                            UDPSend(filenameSocket, "f " + receivedAr[1], namingServer.getIP(previousNodeID), Constants.UDPFileName_PORT);
                        }
                    }
                    break;

               /* case "fd":

                    writer.println(receivedAr[1] + " " + thisNodeID);
                    sendingNode = namingServer.getNodeID(receivingPack.getAddress().toString().replace("/",""));
                    FDH = new FileDownloadHandler(receivedAr[1], calculatePort(sendingNode),this, sendingNode); //start TCP socket thread
                    FileDwnThr = new Thread(FDH); //will be listening for incoming TCP downloads
                    FileDwnThr.start();
                    UDPSend(filenameSocket,"ack "+receivedAr[1],receivingPack.getAddress().toString().replace("/",""),Constants.UDPFileName_PORT); //send ack to let uploader know you are ready
                    FileDwnThr.join();
                    UDPSend(filenameSocket,"rec",receivingPack.getAddress().toString().replace("/",""),Constants.UDPFileName_PORT);
                    listOfFiles = Constants.localFileDirectory.listFiles();
                    for (int i = 0; i < listOfFiles.length; i++) {
                        if (listOfFiles[i].isFile() && receivedAr[1].equals(listOfFiles[i]))
                        {
                            System.out.println("file is local!");
                            //if file is local on server, send it to your previous node
                            UDPSend(filenameSocket, "f " + receivedAr[1], namingServer.getIP(previousNodeID), Constants.UDPFileName_PORT);
                        }
                    }

                    break;   */

                case "ack":
                    sendingNode = namingServer.getNodeID(receivingPack.getAddress().toString().replace("/",""));
                    FileUploadHandler FUH = new FileUploadHandler(receivedAr[1], receivingPack.getAddress().toString().replace("/",""), calculatePort(thisNodeID), this, sendingNode);
                    FileUplHThr = new Thread(FUH);
                    FileUplHThr.start();
                    break;

                case "repDone":
                    if(nextNodeID == previousNodeID)
                    {
                        Thread updateUpThr = new Thread(new Runnable() {
                            @Override
                            public void run() {
                                try{fileReplicationUpdate();} catch(Exception e){}
                            }
                        });
                        updateUpThr.start();
                    }
                    else
                    {
                        Thread startUpThr = new Thread(new Runnable() {
                            @Override
                            public void run() {
                                try{ fileReplicationStartup(); }catch(Exception e) {}
                            }
                        });
                        startUpThr.start();
                    }

                    //Next node RMI (your server)
                    /*String rmiStr = Integer.toString(nextNodeID);
                    int rmiPort = 1000 + Math.abs(rmiStr.hashCode()) % 1000;
                    System.out.println(namingServer.getIP(nextNodeID));
                    ClRegistry = LocateRegistry.getRegistry(namingServer.getIP(nextNodeID), rmiPort);
                    rmiNextNode = (AgentInterface) ClRegistry.lookup("AgentInterface");

                    System.out.println("nextrmi: "+rmiNextNode);

                    System.out.println(rmiNextNode.hello());*/
                    break;

                case "rec":
                    uploadDone = true;
            }
        }
        writer.close();
    }

    //at startup, checks local directory for and send files to the correct replication node (happens once)
    public void fileReplicationStartup() throws IOException, NotBoundException
    {
        PrintWriter writer = new PrintWriter("/home/pi/Documents/filelog.txt", "UTF-8");
        System.out.println("Filerep startup start");
        Integer replicationNode = -1;

        File[] listOfFiles = Constants.localFileDirectory.listFiles();
        if(amountOfNodes > 1)
        {
            for (int i = 0; i < listOfFiles.length; i++)
            {
                //determine node where the replicated file will be stored
                if((namingServer.fileLocator(listOfFiles[i].getName())).equals(thisNodeID)) //if replication node is the current node
                {
                    replicationNode = previousNodeID; //replication will be on the previous node
                }
                else replicationNode = namingServer.fileLocator(listOfFiles[i].getName()); //ask NameServer on which node it should be stored

                System.out.println(listOfFiles[i].getName() + " " + replicationNode);
                writer.println(listOfFiles[i].getName() + " " + replicationNode);
                //Start file upload, to replication node, in another thread

                UDPSend(filenameSocket, "f " + listOfFiles[i].getName(),namingServer.getIP(replicationNode), Constants.UDPFileName_PORT); //upload will now be handles in filelistener() thread
                uploadDone = false; //use this variable so other processes know when they can start new upload threads
                while(!uploadDone)
                {
                    //do nothing
                }
            }

        }
        System.out.println("FileRep Startup done!");
        if(!waitForFileRep)
        {


            UDPSend(filenameSocket,"repDone",namingServer.getIP(replicationNode), Constants.UDPFileName_PORT);
        }
        writer.close();
    }

    //gets called when previous node gets added, node checks if its replication files need to be stored on the new node
    public void fileReplicationUpdate() throws IOException
    {
        Integer replicationNode;
        System.out.println("repUpdate start");

        File[] listOfFiles = Constants.replicationFileDirectory.listFiles();
        for (int i = 0; i < listOfFiles.length; i++)
        {
                //if file shouldn't be stored on nn node anymore
                if(!(namingServer.fileLocator(listOfFiles[i].getName())).equals(thisNodeID))
                {
                    //upload to other node and gets deleted on nn one
                    if(uploadDone)
                    {
                        replicationNode = namingServer.fileLocator(listOfFiles[i].getName());

                        UDPSend(filenameSocket, "f " + listOfFiles[i].getName(),namingServer.getIP(replicationNode), Constants.UDPFileName_PORT); //upload will now be handles in filelistener() thread
                        do{
                            //nothing
                        }while(!uploadDone);
                        listOfFiles[i].delete(); //if upload done, delete
                        //if(listOfFiles[i].delete()) System.out.println(listOfFiles[i]+" deleted!");
                    }

            }
        }
    }
    
    public void newLocalFile(String filename) throws RemoteException
    {
        String ip = namingServer.getIP(namingServer.fileLocator(filename));
        UDPSend(filenameSocket, "f "+filename, ip, Constants.UDPFileName_PORT);
        this.filenameMap.put(filename, new FileProperties(0, true, thisNodeID));
    }

    public void fillFilenameMap()
    {
        File[] listOfFiles = Constants.localFileDirectory.listFiles();
        for (int i = 0; i < listOfFiles.length; i++)
        {
            if (listOfFiles[i].isFile()) {
                this.filenameMap.put(listOfFiles[i].toString().replace("/home/pi/Documents/local/",""), new FileProperties(0,true,thisNodeID));
            }
        }
    }

    //-------------------------------------------------------------------------//
    // Agents
    //-------------------------------------------------------------------------//

    //only needed first time because thread is created in Discovery thread and this one can't wait for fileAgentThread to end. It needs to handle other incoming requests
    public void fileAgentHandler()
    {
        try{
            FileAgent fileAgent = new FileAgent(this);
            fileAgentThr = new Thread(fileAgent);
            fileAgentThr.start();
            fileAgentThr.join();
            //send to next node
            rmiNextNode.fileAgentReceiver(fileAgent);
        }catch(Exception re)
        {
            try {failure(nextNodeID);}catch(Exception e) {}
        }
    }

    public void fileAgentReceiver(FileAgent fileAgent) throws InterruptedException
    {
        try{
            fileAgentThr = new Thread(fileAgent);
            fileAgentThr.start();
            fileAgentThr.join();
            //send to next node
            rmiNextNode.fileAgentReceiver(fileAgent);
        }catch(RemoteException re)
        {
            try {failure(nextNodeID);}catch(IOException e) {}
        }

    }

    public void fileRemovalAgentReceiver(FileRemovalAgent frAgent) throws RemoteException, InterruptedException
    {
        Thread fileRemovalAgentThr = new Thread(frAgent);
        fileRemovalAgentThr.start();
        fileRemovalAgentThr.join();
        if(frAgent.isLocalRemoved() == true && frAgent.isReplicationRemoved() == true)
        {
            this.filenameMap.remove(frAgent.getRemovalFile());
            return; //file is removed from network, so stop the agent from running
        }
        rmiNextNode.fileRemovalAgentReceiver(frAgent);
    }

    /*public void failureAgentReceiver(FailureAgent failureAgent) throws InterruptedException, RemoteException
    {
        Thread failureAgentThr;
        if(failureAgent.getstartNodeID() == thisNodeID) return;

        failureAgentThr = new Thread(failureAgent);
        failureAgentThr.start();
        failureAgentThr.join();
        rmiNextNode.failureAgentReceiver(failureAgent);
    }*/


    //-------------------------------------------------------------------------//
    // Main function
    //-------------------------------------------------------------------------//

    public void start()
    {
        //IP
        String hostname;
        String ipString;
        String nameIP;
        AgentInterface rmiPreviousNode;
        int rmiPort;
        String rmiStr;

        //Threads
        Thread UDPListener;
        Thread startUpThr;
        Thread filenameListenerThr;
        final Thread fileAgentHandlerThr;

        try {
            //namingServer RMI
            Registry registry = LocateRegistry.getRegistry(Constants.SERVER_IP, 1099); //server IP and port
            namingServer = (NamingInterface) registry.lookup("NamingInterface");

            System.out.println(namingServer);

            //Bootstrap + Discovery
            File hostnameFile = new File("/home/pi/Documents/hostname.txt");
            BufferedReader BR = new BufferedReader(new FileReader(hostnameFile));
            hostname = BR.readLine();
            System.out.println(hostname + " hostname");
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

           /* //Check for changes in directory
            DirectoryWatcher DW = new DirectoryWatcher(namingServer);
            DirWatcherThr = new Thread(DW);
            DirWatcherThr.start();
            */


        } catch (Exception e) {
            System.err.println("Client exception: " + e.toString());
            e.printStackTrace();
        }
    }
}
