import java.io.File;
import java.io.Serializable;

public class FailureAgent implements Runnable, Serializable
{
    private Integer failedNode;
    private Integer startNodeID;

    public FailureAgent(Integer failedNode, Integer thisNodeID)
    {
        this.failedNode = failedNode;
        startNodeID = thisNodeID;
    }

    public void run()
    {
        File[] listOfFiles = Constants.localFileDirectory.listFiles();
        {
            for (int i = 0; i < listOfFiles.length; i++) {
                if (listOfFiles[i].isFile()) {
                    //is already handled by fileReplicationThread
                }
            }
        }
    }

    public Integer getstartNodeID() {return startNodeID;}
}
