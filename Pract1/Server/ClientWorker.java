package package1;
import java.net.*;
import java.io.*;
import java.lang.*;

public class ClientWorker implements Runnable {
    private Socket client;

    public ClientWorker(Socket client)
    {
        this.client = client;
    }

    public void run()
    {
        FileInputStream fis = null;
        BufferedInputStream bis = null;
        OutputStream os = null;
        byte [] mybytearray = null;

        try{
            File myFile = new File ("C:\\Users\\Maximiliaan\\testServer.txt");
            mybytearray  = new byte [(int)myFile.length()];
            fis = new FileInputStream(myFile);
            bis = new BufferedInputStream(fis);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        while(true) {
            try {
                bis.read(mybytearray, 0, mybytearray.length);
                os = client.getOutputStream();
                System.out.println("Sending (" + mybytearray.length + " bytes)");
                os.write(mybytearray, 0, mybytearray.length);
                os.flush();
                System.out.println("Done.");
            } catch (IOException e){}
            finally {
                if (bis != null) try  {bis.close();} catch (IOException e) { e.printStackTrace(); }
                if (os != null) try  {os.close();} catch (IOException e) { e.printStackTrace(); }
                if (client != null) try  {client.close();} catch (IOException e) { e.printStackTrace(); }
            }
        }
    }
}
