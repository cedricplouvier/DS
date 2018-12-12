import java.io.*;
import java.net.*;

public class FileDownloadHandler implements Runnable
{
    private String filename;
    private String previousIP;
    private DatagramSocket filenameSocket;

    public FileDownloadHandler(String filename, DatagramSocket filenameSocket)
    {
        this.filename = filename;
        this.filenameSocket = filenameSocket;
    }

    public FileDownloadHandler(String filename, String previousIP, DatagramSocket filenameSocket)
    {
        this.filename = filename;
        this.previousIP = previousIP;
        this.filenameSocket = filenameSocket;
    }

    public void run()
    {
        int bytesRead;
        int current = 0;
        String fullPath;
        FileOutputStream fos = null;
        BufferedOutputStream bos = null;
        Socket TCPsocket = null;
        ServerSocket TCPSsocket = null;
        DatagramSocket UDPSocket;

        try{
            fullPath = Constants.replicationFileDirectory + filename;

            //Now receive the file
            TCPSsocket = new ServerSocket(Constants.TCP_FILE_PORT);
            TCPsocket = TCPSsocket.accept();

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

        }catch(Exception e) {}
        try {
            if (fos != null) fos.close();
            if (bos != null) bos.close();
            if (TCPsocket != null) TCPsocket.close();
            if (TCPSsocket != null) TCPSsocket.close();
        }catch (Exception e){}
    }
}
