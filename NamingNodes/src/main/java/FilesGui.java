import javax.swing.*;
import java.awt.*;
import java.util.NavigableMap;
import java.util.*;

public class FilesGui extends JFrame {

    public NamingNode node;
    private JPanel rootpanel;
    private JList list1;

    public FilesGui(NamingNode nn){
        node=nn;

        setVisible(true);
        setSize(300, 200);
        add(rootpanel);

        listFiles();
        JList list1 = new JList((ListModel) node.filenameMap);


    }

    public void listFiles(){
        NavigableMap nmap = node.filenameMap.descendingMap();
        System.out.println(nmap);
        node.filenameMap.descendingKeySet();

        // Get a set of the entries
        Set set = node.filenameMap.entrySet();

        // Get an iterator
        Iterator it = set.iterator();

        while(it.hasNext()){
            Map.Entry me = (Map.Entry)it.next();
            String key = (String) me.getKey();
            System.out.println(key);
            list1.add((Component) node.filenameMap.descendingKeySet());
        }


    }
}
