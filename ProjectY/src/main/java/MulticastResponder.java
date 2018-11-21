import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class MulticastResponder implements Runnable
{
    private DatagramSocket socket;
    private NamingServer ns;
    private boolean listening = true;

    public MulticastResponder(DatagramSocket socket, NamingServer ns)
    {
        this.socket = socket;
        this.ns = ns;
    }

    public void run()
    {
        byte[] buf = new byte[1024];
        DatagramPacket packet = new DatagramPacket(buf, buf.length);
        String received;
        String[] receivedAr;

        while(listening)
        {
            try{
                socket.receive(packet);
                received = new String(packet.getData(), 0, packet.getLength());
                receivedAr = received.split(" ");
                ns.addNode(receivedAr[2], receivedAr[1]); //add node with hostname and IP sent with UDP multicast
                String mapSize = Integer.toString(ns.IPmap.size());
                buf = mapSize.getBytes();
                packet = new DatagramPacket(buf, buf.length, InetAddress.getByName(receivedAr[1]), 5000); //send the amount of nodes to the address where the multicast came from (with UDP unicast)
                socket.send(packet);
            }catch (Exception e){
                listening = false;
            }
        }
        socket.close();
    }
}
