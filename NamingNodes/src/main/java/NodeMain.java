import java.io.IOException;

public class NodeMain
{
    public static void main(String[] args) throws IOException
    {
        NamingNode nn = new NamingNode();

        NodeGui nodeGui = new NodeGui(nn);
        nodeGui.setVisible(true);
        //nn.start();
    }
}
