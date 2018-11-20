import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.Scanner;
import java.util.regex.Pattern;

public class BankClient {

    private BankClient() {}

    public boolean stringContainsNumber( String s )
    {
        return Pattern.compile( "[0-9]" ).matcher( s ).find();
    }

    public static void main(String[] args) {
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
    }
}