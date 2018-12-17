import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class NodeHandler implements Runnable
{
    private NamingServer node;
    private String received;

    public NodeHandler(NamingServer node, String received)
    {
        this.node = node;
        this.received = received;
    }

    public void run()
    {
        DatagramPacket UCpacket;
        byte UCbuf[];
        String[] receivedAr;
        Integer previous;
        Integer next;
        String bootstrapReturnMsg = null;

        try{
            DatagramSocket UCsendingSocket = new DatagramSocket();
            receivedAr = received.split(" ");
            node.addNode(receivedAr[2], receivedAr[1]); //add node with hostname and IP sent with UDP multicast
            System.out.println(received);
            System.out.println("map size: " + node.IPmap.size());
            if(node.IPmap.size() == 1)
            {
                bootstrapReturnMsg  = Integer.toString(node.IPmap.size());
            }
            else if(node.IPmap.size() > 1)
            {

                if (node.IPmap.lowerKey(node.returnHash(receivedAr[2])) == null){
                    previous = node.IPmap.lastKey();

                }
                else {
                    previous = node.IPmap.lowerKey(node.returnHash(receivedAr[2]));
                }
                if (node.IPmap.higherKey(node.returnHash(receivedAr[2])) == null){
                    next = node.IPmap.firstKey();
                }
                else{
                    next = node.IPmap.higherKey(node.returnHash(receivedAr[2]));
                }
                bootstrapReturnMsg = Integer.toString(node.IPmap.size()) + " " + Integer.toString(previous)+ " " + Integer.toString(next);
            }
            UCbuf = bootstrapReturnMsg.getBytes();
            UCpacket = new DatagramPacket(UCbuf, UCbuf.length, InetAddress.getByName(receivedAr[1]), 4446); //send the amount of nodes to the address where the multicast came from (with UDP unicast)
            UCsendingSocket.send(UCpacket);
        }catch (Exception e){}
    }
}
