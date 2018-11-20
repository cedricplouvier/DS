import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;

public class UDPClient {

    public final static String SERVER = "127.0.0.1";  // localhost
    public final static String
            FILE_TO_RECEIVED = "C:\\Users\\Ruben Joosen\\Documents\\AntwerpenU\\Semester 5\\Distributed Systems\\OPgave_TCP\\UDPClient.txt";  // you may change this, I give a


    public final static int FILE_SIZE = 65507; // file size temporary hard coded
    // should bigger than the file to be downloaded

    public static void main (String [] args ) throws IOException {


        DatagramSocket socket = new DatagramSocket();
        try {
            byte[] buf = new byte[FILE_SIZE];
            System.out.println("Connecting...");
            InetAddress address = InetAddress.getByName(SERVER);
            DatagramPacket packet = new DatagramPacket(buf, buf.length, address, 4445);
            socket.send(packet);

            packet = new DatagramPacket(buf, buf.length);
            socket.receive(packet);

            // receive file



            String received = new String(packet.getData(), 0, packet.getLength());
            PrintWriter out = new PrintWriter(FILE_TO_RECEIVED);
            out.print(received);
            socket.close();
            out.close();
            System.out.println("package received");
        }
        finally {

            if (socket != null) socket.close();
        }
    }

}