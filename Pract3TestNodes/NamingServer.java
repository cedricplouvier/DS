import java.io.FileWriter;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.TreeMap;
import java.util.Map;
import java.util.List;
import java.rmi.registry.Registry;
import java.rmi.registry.LocateRegistry;
import java.rmi.server.UnicastRemoteObject;
import javax.xml.stream.*;

public class NamingServer implements NamingInterface
{
    public TreeMap<Integer, String> IPmap = new TreeMap<>();
    public TreeMap<Integer,List<String>> fileOwnerMap = new TreeMap<>();
    String [] fileArray = {"Taak1.docx","Song.mp3","Uitgaven.xls","loon.pdf","readme.txt","download.rar"};

    public NamingServer() {}

    public void addNode(String hostname, String IP) throws IOException,XMLStreamException
    {
        Integer nodeID = Math.abs(hostname.hashCode()) % 32768;
        if(!IPmap.containsKey(nodeID))
        {
            IPmap.put(nodeID,IP);
            recalculate();
            printAll();
        }
        else System.out.println("Node already in use.");
    }

    public void removeNode(String hostname) throws IOException,XMLStreamException
    {
        int nodeID = Math.abs(hostname.hashCode()) % 32768;
        if(IPmap.containsKey(nodeID))
        {
            IPmap.remove(nodeID);
            fileOwnerMap.remove(nodeID);
            recalculate();
            printAll();
        }
        else System.out.println("Node not in system.");
    }

    public String fileLocator(String filename) //what node calls, via RMI, to know IP of certain file
    {
        int fileHash = Math.abs(filename.hashCode()) % 32768;
        Integer closestKey = IPmap.floorKey(fileHash); //returns the greatest key less than or equal to the given key, or null if there is no such key.
        if(closestKey == null)
        {
            closestKey = IPmap.lastKey(); //returns highest key in this map
        }
        return IPmap.get(closestKey); //returns IP associated with this nodeID
    }

    public int filePlacer(String filename)
    {
        int fileHash = Math.abs(filename.hashCode()) % 32768;
        Integer closestKey = IPmap.floorKey(fileHash); //returns the greatest key less than or equal to the given key, or null if there is no such key.
        if(closestKey == null)
        {
            closestKey = IPmap.lastKey(); //returns highest key in this map
        }
        return closestKey; //return ID of node where file needs to be stored.
    }

    public void recalculate() //assigns files to the different nodes
    {
        for (Map.Entry<Integer, String> entry : IPmap.entrySet()) //creates empty array for each node
        {
            fileOwnerMap.put(entry.getKey(), new ArrayList<>());
        }
        for(int i = 0; i < fileArray.length; i++)
        {
            fileOwnerMap.get(filePlacer(fileArray[i])).add(fileArray[i]);
        }
    }

    public void printAll() throws IOException, XMLStreamException
    {
        for (Map.Entry<Integer, String> entry : IPmap.entrySet()) {
            System.out.println("Key: " + entry.getKey() + ". Value: " + entry.getValue());
        }
        System.out.println("\n");
        for(Map.Entry<Integer,List<String>> entry : fileOwnerMap.entrySet())
        {
            System.out.println(entry.getKey() + " = " + entry.getValue());
        }
        System.out.println("\n");
        writeToXML();
    }

    public void writeToXML() throws IOException,XMLStreamException
    {
        FileWriter out = new FileWriter("C:\\Users\\Maximiliaan\\map.xml");
        XMLStreamWriter xsw = null;
        try {
            try {
                XMLOutputFactory xof = XMLOutputFactory.newInstance();
                xsw = xof.createXMLStreamWriter(out);
                xsw.writeStartDocument("utf-8", "1.0");
                xsw.writeStartElement("entries");

                // Do the Collection
                for(Map.Entry<Integer, String> e : IPmap.entrySet()) {
                    xsw.writeStartElement("entry");
                    xsw.writeAttribute("key", e.getKey().toString());
                    xsw.writeAttribute("value", e.getValue().toString());
                    xsw.writeEndElement();
                }
                xsw.writeEndElement();
                xsw.writeEndDocument();
            }
            finally {
                if (out != null) {
                    try { out.close() ; } catch(IOException e) { /* ignore */ }
                }
            }// end inner finally
        }
        finally {
            if (xsw != null) {
                try { xsw.close() ; } catch(XMLStreamException e) { /* ignore */ }
            }
        }
    }

    public static void main(String args[]) throws  IOException
    {
        ServerSocket servsock = null;
        try
        {
            /*TCP
            servsock = new ServerSocket(6789);
            ClientWorker w;
            try
            {
                w = new ClientWorker(servsock.accept());
                Thread t = new Thread(w);
                t.start();
                System.out.println("Accepted connection : " + servsock);
            } catch(IOException e)
            {
                System.out.println("Accept failed");
                System.exit(-1);
            }*/

            //RMI
            NamingServer obj = new NamingServer();
            NamingInterface stub = (NamingInterface) UnicastRemoteObject.exportObject(obj, 0);

            // Bind the remote object's stub in the registry
            //Registry registry = LocateRegistry.getRegistry();
            Registry registry = LocateRegistry.createRegistry(1099);
            registry.bind("NamingInterface", stub);

            System.err.println("Server ready");
        } catch (Exception e)
        {
            System.err.println("Server exception: " + e.toString());
            e.printStackTrace();
        }
        finally
        {
            if (servsock != null) servsock.close();
        }
    }
}
