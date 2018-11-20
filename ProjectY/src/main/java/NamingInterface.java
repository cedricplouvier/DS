import javax.xml.stream.XMLStreamException;
import java.io.IOException;
import java.rmi.Remote;
import java.rmi.RemoteException;

public interface NamingInterface extends Remote
{

    /*
void addMoney(int money, int user) throws RemoteException;
int withdraw(int money,int user) throws RemoteException;
int getBalance(int user) throws RemoteException;
int login(String login, String password) throws RemoteException;
*/
    String sayHello() throws RemoteException;
    String fileLocator(String filename) throws RemoteException;
    void addNode(String hostname,String IP) throws RemoteException, IOException,XMLStreamException;
    void removeNode(String hostname) throws RemoteException,IOException, XMLStreamException;
}
