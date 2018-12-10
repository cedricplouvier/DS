import java.io.File;
import java.io.IOException;
import java.nio.file.*;

import static java.nio.file.LinkOption.NOFOLLOW_LINKS;
import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.OVERFLOW;

public class DirectoryWatcher implements Runnable
{
    private NamingInterface stub;

    public DirectoryWatcher(NamingInterface stub)
    {
        this.stub = stub;
    }

    public void run()
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
                        File myFile = new File(newPath.toString());
                        //newPath.toFile();
                        if (myFile.isFile())
                            System.out.println("New file created: " + newPath);

                        //if a file gets uploaded locally
                        /*replicationNode = stub.fileLocator(newPathString);
                        FileUploadHandler FUH = new FileUploadHandler(newPathString, stub.getIP(replicationNode));
                        FileUplHThr = new Thread(FUH);
                        FileUplHThr.start();*/

                        // Output
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

}
