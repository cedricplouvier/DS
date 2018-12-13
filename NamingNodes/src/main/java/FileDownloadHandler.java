import java.io.*;
import java.net.*;

public class FileDownloadHandler implements Runnable
{
    private String filename;
    private int port;

    public FileDownloadHandler(String filename, int port)
    {
        this.filename = filename;
        this.port = port;
    }

    public void run()
    {
        int bytesRead;
        int current;
        String fullPath;
        FileOutputStream fos = null;
        BufferedOutputStream bos = null;
        Socket TCPsocket = null;
        ServerSocket TCPSsocket = null;

        try{
            fullPath = Constants.replicationFileDirectory.toString() + "/" + filename;

            //Now receive the file
            TCPSsocket = new ServerSocket(port);
            TCPsocket = TCPSsocket.accept();
            System.out.println("Socket made for "+filename);

            byte [] mybytearray  = new byte [6022386];
            InputStream is = TCPsocket.getInputStream();
            fos = new FileOutputStream(fullPath);
            bos = new BufferedOutputStream(fos);
            bytesRead = is.read(mybytearray,0,mybytearray.length);
            current = bytesRead;

            do {
                bytesRead =
                        is.read(mybytearray, current, (mybytearray.length-current));
                if(bytesRead >= 0) current += bytesRead;
            } while(bytesRead > -1);

            bos.write(mybytearray, 0 , current);
            bos.flush();
            System.out.println(filename + " downloaded!");

        }catch(Exception e) {}
        finally {
            try {
                if (fos != null) fos.close();
                if (bos != null) bos.close();
                if (TCPsocket != null) TCPsocket.close();
                if (TCPSsocket != null) TCPSsocket.close();
            }catch (Exception e){}
        }
    }
}
