import java.io.IOException;
import javax.swing.*;

public class NodeMain
{
    public static void main(String[] args) throws IOException
    {

        NamingNode nn = new NamingNode();

        NodeGUI nodeGUI = new NodeGUI(nn);
        nodeGUI.setVisible(true);

        //nn.start();
    }
}
