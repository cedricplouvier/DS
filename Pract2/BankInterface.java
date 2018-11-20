import java.rmi.Remote;
import java.rmi.RemoteException;

public interface BankInterface extends Remote {
    void addMoney(int money, int user) throws RemoteException;
    int withdraw(int money,int user) throws RemoteException;
    int getBalance(int user) throws RemoteException;
    int login(String login, String password) throws RemoteException;
    void logout(int user) throws RemoteException;
}