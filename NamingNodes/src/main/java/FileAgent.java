import java.io.File;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.TreeMap;

public class FileAgent implements Runnable, Serializable
{
    private TreeMap<String, Integer> agentFilesMap = new TreeMap<>(); //filename, lock request (on 1, off 0)
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
                    filename = listOfFiles[i].toString().replace("/home/pi/Documents/local/","");

                    if(!agentFilesMap.containsKey(filename)) //if new file
                    {
                        agentFilesMap.put(filename, 0); //put filename in map and lock request off
                    }
                    else //if existing file
                    {
                        if(agentFilesMap.get(filename) == node.thisNodeID && node.filenameMap.get(filename) == 0) //if file is locked by this node, but node says its unlocked => unlock it
                        {
                            agentFilesMap.put(filename, 0);
                        }
                        else if(node.filenameMap.get(filename) == node.thisNodeID) //if this node says a file is locked by his ID => update
                        {
                            agentFilesMap.put(filename, node.thisNodeID);
                        }
                        else //if anything else, just copy the agent lock to this node map
                        {
                            node.filenameMap.put(filename, agentFilesMap.get(filename));
                        }
                    }

                }
            }
        }
    }

}
