import java.rmi.Remote;
import java.rmi.RemoteException;

public interface AgentInterface extends Remote
{
    void fileAgentReceiver(FileAgent fAgent) throws InterruptedException,RemoteException;
    void fileRemovalAgentReceiver(FileRemovalAgent frAgent) throws RemoteException, InterruptedException;
    //void failureAgentReceiver(FailureAgent failureAgent) throws InterruptedException, RemoteException;
}
