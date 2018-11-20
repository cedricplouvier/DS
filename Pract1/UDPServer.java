import java.io.*;
import java.net.*;

public class UDPServer {


    public final static String FILE_TO_SEND = "C:\\Users\\Ruben Joosen\\Documents\\AntwerpenU\\Semester 5\\Distributed Systems\\OPgave_TCP\\UDPServer.txt";  // you may change this

    public static void main (String [] args ) throws IOException {
        FileInputStream fis = null;
        BufferedInputStream bis = null;
        DatagramSocket socket = null;
        try{
            socket = new DatagramSocket(4445);

            while (true) {
                System.out.println("Waiting...");
                try {
                    File myFile = new File (FILE_TO_SEND);
                    byte[] buf = new byte[(int)myFile.length()];
                    DatagramPacket packet = new DatagramPacket(buf, buf.length);
                    socket.receive(packet);
                    System.out.println("Accepted connection : " + socket);
                    // send file

                    fis = new FileInputStream(myFile);
                    bis = new BufferedInputStream(fis);
                    bis.read(buf,0,buf.length);
                    //select destination
                    InetAddress address = packet.getAddress();
                    int port = packet.getPort();
                    packet = new DatagramPacket(buf, buf.length, address, port);
                    socket.send(packet);
                    System.out.println("Done.");

                    //socket.close();
                } catch (IOException e) {
                    //e.printStackTrace();
                }
                finally {
                    if (bis != null) bis.close();
                    //if (socket!=null) socket.close();
                }
            }
        }
        finally {
            //if (socket != null) socket.close();
        }
    }
}
