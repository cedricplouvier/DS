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
    private boolean deleteWhenDone = false;

    public FileUploadHandler(String filename, String ip)
    {
        this.filename = filename;
        this.fullPath = directory + filename;
        this.ip = ip;
    }

    //extra constructor for when file also needs to be deleted afterwards (replicationDir)
    public FileUploadHandler(String filename, String ip, boolean deleteWhenDone)
    {
        this.filename = filename;
        this.fullPath = directory + filename;
        this.ip = ip;
        this.deleteWhenDone = deleteWhenDone;
    }

    public void run()
    {
        String deletePath;
        File deleteFile;
        String received;

        FileInputStream fis = null;
        BufferedInputStream bis = null;
        OutputStream os = null;
        byte [] mybytearray = null;

        try{
            //send filename first, so other node knows where to store
            UDPSocket = new DatagramSocket(Constants.UDPFileName_PORT);
            String fileMsg = "f " + filename;
            byte[] UDPbuf = fileMsg.getBytes();
            DatagramPacket UDPpacket = new DatagramPacket(UDPbuf, UDPbuf.length, InetAddress.getByName(ip), Constants.UDPFileName_PORT);
            System.out.println("IP FUH" + ip);
            UDPSocket.send(UDPpacket);
            do {
                DatagramPacket receivingPack = new DatagramPacket(UDPbuf, UDPbuf.length, InetAddress.getByName(ip), Constants.UDPAck_PORT);
                UDPSocket.receive(receivingPack);
                received = new String(receivingPack.getData(), 0, receivingPack.getLength());
                System.out.println("received: " + received);
            }while(!received.equals("ack")); //wait for ack from downloader, to know he is receiving

            System.out.println("ack received");
           //send file with TCP
            TCPsocket = new Socket(ip, Constants.TCP_FILE_PORT);
            File myFile = new File(fullPath);
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

