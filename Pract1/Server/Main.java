package package1;

import java.io.IOException;
import java.net.ServerSocket;

public class Main {
    public final static int SOCKET_PORT = 6789;  // you may change this

    public static void main(String[] args) throws IOException {
        ServerSocket servsock = null;
        try {
            servsock = new ServerSocket(SOCKET_PORT);
            while (true) {
                System.out.println("Waiting...");
                ClientWorker w;
                try {
                    w = new ClientWorker(servsock.accept());
                    Thread t = new Thread(w);
                    t.start();
                    System.out.println("Accepted connection : " + servsock);
                } catch(IOException e) {
                    System.out.println("Accept failed");
                    System.exit(-1);
                }
            }
        }
        finally {
            if (servsock != null) servsock.close();
        }
    }
}
