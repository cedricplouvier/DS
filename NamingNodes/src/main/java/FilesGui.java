import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.NavigableMap;
import java.util.*;

public class FilesGui extends JFrame {

    public NamingNode node;
    public JPanel rootpanel;
    public JTable table1;
    private JList list1;

    public FilesGui(NamingNode nn){
        node=nn;

        setVisible(true);
        setSize(300, 200);
        add(rootpanel);
        System.out.println("rootpanel added");
        String[] columns = new String[] {"file"};
        DefaultTableModel defaultModel = new DefaultTableModel(columns,0);
        System.out.println("default table created");
        table1.setModel(defaultModel);
        for (Map.Entry<String, FileProperties> entry : node.filenameMap.entrySet()) {
            System.out.println("table entries added");
            //defaultModel.addColumn(entry.getKey());
            defaultModel.addRow(new Object[] {entry.getKey()});
        }
        rootpanel.add(new JScrollPane(table1));
        setVisible(true);
    }

    public void listFiles(){
        System.out.println("listfiles called");
        NavigableMap nmap = node.filenameMap.descendingMap();
        System.out.println(nmap);
        node.filenameMap.descendingKeySet();

        // Get a set of the entries
        Set set = node.filenameMap.entrySet();

        // Get an iterator
        Iterator it = set.iterator();

        while(it.hasNext()){
            System.out.println("iterating in list");
            Map.Entry me = (Map.Entry)it.next();
            String key = (String) me.getKey();
            System.out.println("print key: ");
            System.out.println(key);
            this.list1.add((Component) node.filenameMap.descendingKeySet());
        }


    }
}
