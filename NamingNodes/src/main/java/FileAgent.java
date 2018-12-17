import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;
import java.util.TreeMap;

public class FileAgent implements Runnable, Serializable
{
    private TreeMap<String, FileProperties> agentFilesMap = new TreeMap<>(); //filename, lock request (on 1, off 0)
    private NamingNode node;

    public FileAgent(NamingNode node)
    {
        this.node = node;
    }

    public void run()
    {
        String filename;
        File[] listOfFiles = Constants.localFileDirectory.listFiles();
        {
            for (int i = 0; i < listOfFiles.length; i++)
            {
                if (listOfFiles[i].isFile())
                {
                    filename = listOfFiles[i].toString().replace("C:\\Users\\Public\\test\\local\\","");

                    if(!this.agentFilesMap.containsKey(filename)) //if new file
                    {
                        this.agentFilesMap.put(filename, new FileProperties(0, false, node.filenameMap.get(filename).getLocalNode())); //put filename in map and lock request off
                        try {node.newLocalFile(filename);}catch(IOException e) {} //a new local file has been added, so check local files and start replication process
                    }
                    else //if existing file
                    {
                        if(this.agentFilesMap.get(filename).getLock() == node.thisNodeID && node.filenameMap.get(filename).getLock() == 0) //if file is locked by this node, but node says its unlocked => unlock it
                        {
                            this.agentFilesMap.put(filename, new FileProperties(0, false, node.filenameMap.get(filename).getLocalNode()));
                        }
                        else if(node.filenameMap.get(filename).getLock() == node.thisNodeID) //if this node says a file is locked by his ID => update
                        {
                            this.agentFilesMap.put(filename, new FileProperties(node.thisNodeID, false, node.filenameMap.get(filename).getLocalNode()));
                        }
                        else //if anything else, just copy the agent lock to this node map
                        {
                            node.filenameMap.put(filename, new FileProperties(this.agentFilesMap.get(filename).getLock(), true, node.thisNodeID));
                        }
                    }
                }
            }
        }

        //check for removed files
        this.agentFilesMap.descendingMap();
        for (Map.Entry<String, FileProperties> entry : this.agentFilesMap.entrySet())
        {
            if(entry.getValue().getLocalNode() == node.thisNodeID && !node.filenameMap.containsKey(entry.getKey())) //if its local node is this node and the key(filename) in the agent map
            {                                                                                      //doesn't appear in the localnode map, the file has been removed
                this.agentFilesMap.remove(entry.getKey());
            }
        }
    }
}
