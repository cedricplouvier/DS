import javax.xml.stream.XMLStreamException;
import java.io.IOException;
import java.rmi.RemoteException;

public class TestNodes
{
    public static void main(String[] args) throws RemoteException, IOException, XMLStreamException
    {
        NamingNode node1 = new NamingNode("abc/abc/abc","1.1.1.1");
        NamingNode node2 = new NamingNode("hgf/hgh/fdds","2.2.2.2");
        NamingNode node3 = new NamingNode("gbds/hfds/hdffe","3.3.3.3");

        System.out.println("Taak1: "+node1.stub.fileLocator("Taak1.docx"));
        System.out.println("readme: "+node2.stub.fileLocator("readme.txt"));

        node3.stub.removeNode("gbds/hfds/hdffe");
    }
}
