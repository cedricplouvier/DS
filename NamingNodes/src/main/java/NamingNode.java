import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.io.*;
import java.net.*;
import java.util.*;
import java.net.InetAddress;
import java.rmi.server.*;


public class NamingNode
{

    public NamingNode() {}

    public void downloadFile(String filename) throws IOException
    {
        Socket csock = null;
        byte [] mybytearray  = new byte [6022386];
        int bytesRead;
        int current = 0;
        FileOutputStream fos = null;
        BufferedOutputStream bos = null;

        try
        {
            csock = new Socket("localhost",6789); //server IP and port
            InputStream is = csock.getInputStream();
            fos = new FileOutputStream(filename); //where file is placed
            bos = new BufferedOutputStream(fos);
            bytesRead = is.read(mybytearray,0,mybytearray.length);
            current = bytesRead;
            do {
                bytesRead =
                        is.read(mybytearray, current, (mybytearray.length-current));
                if(bytesRead >= 0) current += bytesRead;
            } while(bytesRead > -1);

            bos.write(mybytearray, 0 , current);
            bos.flush();
        }
        finally
        {
            if (fos != null) fos.close();
            if (bos != null) bos.close();
            if (csock != null) csock.close();
        }
    }

    public static void main(String[] args)
    {
        /*
        String host = (args.length < 1) ? null : args[0];
        try {
            boolean loginRunning = true;
            boolean running = true;
            int user = 0;
            Registry registry = LocateRegistry.getRegistry( "localhost",1099); //169.254.56.139
            BankInterface stub = (BankInterface) registry.lookup("BankInterface");

            System.out.println("Enter login and password separated by space or type 'logout' to quit: \n");
            Scanner scan = new Scanner(System.in);

            while(loginRunning){
                String[] userInput = new String[2];
                userInput = scan.nextLine().split(" ");
                if(userInput[0].equals("logout")) break;
                user = stub.login(userInput[0],userInput[1]);
                if(user != -2) { //if user not already logged in
                    if(user > -1){ //and the credentials are right
                        System.out.println("Enter action: Withdraw/Deposit/Balance, then the amount: \n");
                        while(running)
                        {
                            int amount = 0;
                            userInput = scan.nextLine().split(" ");
                            if(userInput[0].equals("Quit")){
                                running = false;
                                loginRunning = false;
                            }
                            if(userInput.length > 1){ //check if userInput[1] exists
                                if(userInput[1].matches("[0-9]+")) //[0-9] means check for these digits, the + means one or more
                                {
                                    switch(userInput[0])
                                    {
                                        case "Withdraw":
                                            amount = Integer.valueOf(userInput[1]);
                                            if(stub.withdraw(amount,user) == 0)System.out.println("Insufficient funds!");
                                            else System.out.println("Success!");
                                            break;
                                        case "Deposit":
                                            amount = Integer.valueOf(userInput[1]);
                                            stub.addMoney(amount,user);
                                            System.out.println("Success!");
                                            break;
                                        case "Balance":
                                            System.out.println(stub.getBalance(user));
                                            break;
                                        default:
                                            System.out.println("Unknown command, try again:");
                                    }
                                }
                                else System.out.println("Unknown command, try again:");;
                            }
                            else System.out.println("Unknown command, try again:");
                        }
                    }
                    else {
                        System.out.println("Login failed, try again: ");
                    }
                }
                else System.out.println("Already logged in");
            }
            stub.logout(user);
        } catch (Exception e) {
            System.err.println("Client exception: " + e.toString());
            e.printStackTrace();
        }
        */

        //IP
        String hostname;
        String ipString;
        InetAddress ip = null;

        try {

            //RMI connection
            Registry registry = LocateRegistry.getRegistry("192.168.23.1"); //connect to right interface of namingserver (normally 192.168.0.4)
            NamingInterface stub = (NamingInterface) registry.lookup("NamingInterface");
            String response = stub.sayHello();
            System.out.println(response);

            //add node
            Enumeration e = NetworkInterface.getNetworkInterfaces();
            while (e.hasMoreElements()) {
                NetworkInterface n = (NetworkInterface) e.nextElement();
                Enumeration ee = n.getInetAddresses();
                while (ee.hasMoreElements()) {  //while lus doorheen alle
                    InetAddress i = (InetAddress) ee.nextElement();

                    if (i.getHostAddress().contains("192.168.1.")){ //watch out for right IP range in pi : (192.168.0.)

                        ip = i;
                        System.out.println(ip.getHostAddress());

                    }
                }
            }
            ipString = ip.getHostAddress(); //ip in Stringformat
            hostname = "Node " + ipString.substring(11); //declare hostname with last digit of IP

            if (ip != null) {
                stub.addNode(hostname, ipString); //RMI get added to the MAP
            }


        } catch (Exception e) {
            System.err.println("Client exception: " + e.toString());
            e.printStackTrace();
        }
    }
}
