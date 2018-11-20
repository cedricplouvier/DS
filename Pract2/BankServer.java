import java.rmi.registry.Registry;
import java.rmi.registry.LocateRegistry;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;

public class BankServer implements BankInterface {
    private int balance = 0;
    private String logins [] = {"Max","Cedric","Roald","Ruben"};
    private String passwords [] = {"pswd1","pswd2","pswd3","pswd4"};
    private int balances [] = {0,0,0,0};
    private boolean online [] = {false,false,false,false};

    public BankServer() {}

    public int login(String login,String password){ //check credentials and if online
        int user = -1;
        for(int i  = 0;i < logins.length; i++){
            if(logins[i].equals(login) && passwords[i].equals(password) && online[i] == false)
            {
                user = i;
                online[i] = true;
            }
            else if(logins[i].equals(login) && passwords[i].equals(password) && online[i] == true) {
                user = -2;
            }
        }
        return user;
    }

    public void logout(int user) { //now other clients can login to the account again
        online[user] = false;
    }

    public void addMoney(int money,int user) {
        balances[user] += money;
    }

    public int withdraw(int money,int user) {
        if((balances[user]-money) < 0) return 0;
        else {
            balances[user] -= money;
            return 1;
        }
    }

    public int getBalance(int user) {
        return balances[user];
    }

    public static void main(String args[]) {

        try {
            BankServer obj = new BankServer();
            BankInterface stub = (BankInterface) UnicastRemoteObject.exportObject(obj, 0);

            // Bind the remote object's stub in the registry
            //Registry registry = LocateRegistry.getRegistry();
            Registry registry = LocateRegistry.createRegistry(1099);
            registry.bind("BankInterface", stub);

            System.err.println("Server ready");
        } catch (Exception e) {
            System.err.println("Server exception: " + e.toString());
            e.printStackTrace();
        }

    }
}