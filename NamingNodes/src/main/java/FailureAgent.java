import java.io.File;
import java.io.Serializable;

public class FailureAgent implements Runnable, Serializable
{
    private Integer failedNode;
    private Integer thisNodeID;

    public FailureAgent(Integer failedNode, Integer thisNodeID)
    {
        this.failedNode = failedNode;
        this.thisNodeID = thisNodeID;
    }

    public void run()
    {
        File[] listOfFiles = Constants.localFileDirectory.listFiles();
        {
            for (int i = 0; i < listOfFiles.length; i++) {
                if (listOfFiles[i].isFile()) {

                }
            }
        }
    }
}
