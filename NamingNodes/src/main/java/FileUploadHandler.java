import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;

public class FileUploadHandler implements Runnable
{
    private String filename;
    private String fullPath;
    private String ip;
    private Socket TCPsocket;
    private boolean deleteWhenDone = false;
    private int port;

    public FileUploadHandler(String filename, String ip, int port)
    {
        this.filename = filename;
        this.fullPath = Constants.localFileDirectory.toString() +"/"+ filename;
        this.ip = ip;
        this.port = port;
    }

    public void run()
    {
        String deletePath;
        File deleteFile;

        FileInputStream fis = null;
        BufferedInputStream bis = null;
        OutputStream os = null;
        byte [] mybytearray = null;

        try{
           //send file with TCP
            TCPsocket = new Socket(ip, port);
            System.out.println("socket made");
            File myFile = new File(fullPath);
            mybytearray = new byte [(int)myFile.length()];
            fis = new FileInputStream(myFile);
            bis = new BufferedInputStream(fis);
        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            bis.read(mybytearray, 0, mybytearray.length);
            os = TCPsocket.getOutputStream();
            System.out.println("Sending (" + mybytearray.length + " bytes)");
            os.write(mybytearray, 0, mybytearray.length);
            os.flush();
            System.out.println("Done.");
        } catch (IOException e){}
        finally {
            if (bis != null) try  {bis.close();} catch (IOException e) { e.printStackTrace(); }
            if (os != null) try  {os.close();} catch (IOException e) { e.printStackTrace(); }
            if (TCPsocket != null) try  {TCPsocket.close();} catch (IOException e) { e.printStackTrace(); }
        }

        if(deleteWhenDone)
        {
            deletePath = Constants.replicationFileDirectory.toString() + filename;
            deleteFile = new File(deletePath);
            if(deleteFile.delete()) System.out.println(deletePath + " is deleted!");
            else System.out.println(deletePath + " doesn't exist!");
        }
    }
}

