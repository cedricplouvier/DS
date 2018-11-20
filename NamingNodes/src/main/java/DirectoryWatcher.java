import static java.nio.file.LinkOption.NOFOLLOW_LINKS;
import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.OVERFLOW;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.rmi.RemoteException;

public class DirectoryWatcher extends Thread
{
    private Path path = null;
    private File fileDirectory = null;
    private NamingInterface stub = null;

    public DirectoryWatcher(File fileDirectory, NamingInterface stub)
    {
        this.fileDirectory = fileDirectory;
        this.path = fileDirectory.toPath();
        this.stub = stub;
    }

    public void run()
    {
        try {
            Boolean isFolder = (Boolean) Files.getAttribute(path,
                    "basic:isDirectory", NOFOLLOW_LINKS);
            if (!isFolder) {
                throw new IllegalArgumentException("Path: " + path
                        + " is not a folder");
            }
        } catch (IOException ioe) {
            // Folder does not exists
            ioe.printStackTrace();
        }

        System.out.println("Watching path: " + path);

        // We obtain the file system of the Path
        FileSystem fs = path.getFileSystem();

        // We create the new WatchService using the new try() block
        try (WatchService service = fs.newWatchService()) {

            // We register the path to the service
            // We watch for creation events
            path.register(service, ENTRY_CREATE,ENTRY_DELETE);

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
                        // Output
                        System.out.println("New file created: " + newPath);
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

        } catch (IOException ioe) {
            ioe.printStackTrace();
        } catch (InterruptedException ie) {
            ie.printStackTrace();
        }

    }
}
