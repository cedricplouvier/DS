import javax.xml.stream.XMLStreamException;
import java.io.IOException;
import java.rmi.Remote;
import java.rmi.RemoteException;

public interface NamingInterface extends Remote
{
    String fileLocator(String filename) throws RemoteException;
    void addNode(String hostname,String IP) throws RemoteException, IOException,XMLStreamException;
    void removeNode(String hostname) throws RemoteException,IOException, XMLStreamException;
}