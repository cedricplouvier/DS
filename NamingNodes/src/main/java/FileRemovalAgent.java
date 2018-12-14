import java.io.File;
import java.io.Serializable;

public class FileRemovalAgent implements Runnable, Serializable
{
    private String removalFile;
    private boolean localRemoved = false;
    private boolean replicationRemoved = false;

    public FileRemovalAgent(String removalFile)
    {
        this.removalFile = removalFile;
    }

    public void run()
    {
        File[] listOfFiles = Constants.replicationFileDirectory.listFiles();
        {
            for (int i = 0; i < listOfFiles.length; i++)
            {
                if (listOfFiles[i].isFile() && listOfFiles[i].equals(removalFile))
                {
                    File myFile = new File(Constants.replicationFileDirectory.toString() + removalFile); //remove file locally
                    myFile.delete();
                    replicationRemoved = true;
                }
            }
        }

        listOfFiles = Constants.localFileDirectory.listFiles();
        {
            for (int i = 0; i < listOfFiles.length; i++) {
                if (listOfFiles[i].isFile() && listOfFiles[i].equals(removalFile)) {
                    File myFile = new File(Constants.localFileDirectory.toString() + removalFile); //remove file locally
                    myFile.delete();
                    localRemoved = true;
                }
            }
        }
    }

    public boolean isLocalRemoved()
    {
        return localRemoved;
    }

    public boolean isReplicationRemoved()
    {
        return replicationRemoved;
    }

    public String getRemovalFile()
    {
        return removalFile;
    }
}
