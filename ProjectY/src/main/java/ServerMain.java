import java.io.IOException;

public class ServerMain
{
    public static void main(String args[]) throws IOException
    {
        NamingServer ns = new NamingServer();
        ns.start();
    }
}
