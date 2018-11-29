import javax.xml.stream.XMLStreamException;
import java.io.IOException;
import java.rmi.Remote;
import java.rmi.RemoteException;

public interface NamingInterface extends Remote
{
    Integer fileLocator(String filename) throws RemoteException;
    void removeNode(Integer nodeID) throws IOException, XMLStreamException;
    String getIP(Integer nodeID) throws RemoteException;
    String failure(Integer failedNode) throws RemoteException;
}
