import java.io.*;
import java.net.*;

public class FileUploadHandler implements Runnable
{
    private String filename;
    private String fullPath;
    private String ip;
    private Socket TCPsocket;
    private int port;
    private NamingNode node;
    private Integer nodeID;


    public FileUploadHandler(String filename, String ip, int port, NamingNode node, Integer nodeID)
    {
        this.filename = filename;
        this.fullPath = Constants.localFileDirectory.toString() +"\\"+ filename;
        this.ip = ip;
        this.port = port;
        this.node = node;
        this.nodeID = nodeID;
    }

    public void run()
    {
        FileInputStream fis;
        BufferedInputStream bis = null;
        OutputStream os = null;
        byte [] mybytearray = null;

        try {
            //send file with TCP
            TCPsocket = new Socket(ip, port);
            System.out.println("socket made");
        }catch (IOException e)
        {
            try {node.failure(nodeID);}catch(Exception fe) {}
        }
        try{
            File myFile = new File(fullPath);
            mybytearray = new byte [(int)myFile.length()];
            fis = new FileInputStream(myFile);
            bis = new BufferedInputStream(fis);

            bis.read(mybytearray, 0, mybytearray.length);
            os = TCPsocket.getOutputStream();
            System.out.println("Sending (" + mybytearray.length + " bytes)");
            os.write(mybytearray, 0, mybytearray.length);
            os.flush();
            System.out.println("Done.");
        } catch (IOException ie) {
            ie.printStackTrace();
        }
        finally {
            if (bis != null) try  {bis.close();} catch (IOException e) { e.printStackTrace(); }
            if (os != null) try  {os.close();} catch (IOException e) { e.printStackTrace(); }
            if (TCPsocket != null) try  {TCPsocket.close();} catch (IOException e) { e.printStackTrace(); }
        }
    }
}

