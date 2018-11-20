import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;

public class FileUploadHandler implements Runnable
{
    private String directory = "/home....";
    private String filename;
    private String fullPath;
    private String ip;
    private Socket TCPsocket;
    private DatagramSocket UDPSocket;

    public FileUploadHandler(String filename, String ip)
    {
        this.filename = filename;
        this.fullPath = directory + filename;
        this.ip = ip;
    }

    public void run()
    {

        FileInputStream fis = null;
        BufferedInputStream bis = null;
        OutputStream os = null;
        byte [] mybytearray = null;

        try{
            //send filename first, so other node knows where to store
            UDPSocket = new DatagramSocket(Constants.UDPFileName_PORT);
            byte[] UDPbuf = filename.getBytes();
            DatagramPacket UDPpacket = new DatagramPacket(UDPbuf, UDPbuf.length, InetAddress.getByName(ip), Constants.UDPFileName_PORT);
            UDPSocket.send(UDPpacket);

           //send file with TCP
            TCPsocket = new Socket(ip, Constants.TCP_FILE_PORT);
            File myFile = new File(fullPath);
            fis = new FileInputStream(myFile);
            bis = new BufferedInputStream(fis);
        } catch (Exception e) {
            e.printStackTrace();
        }

        while(true)
        {
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
        }
    }
}

