import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.File;
import java.util.NavigableMap;
import java.util.*;

public class FilesGui extends JFrame {

    public NamingNode node;
    public JPanel rootpanel;
    public JTable table1;
    public JTable table2;
    private JList list1;

    public FilesGui(NamingNode nn){
        node=nn;
        setVisible(true);
        setSize(300, 200);
        add(rootpanel);

        String[] columns = new String[] {"local files"};
        DefaultTableModel defaultModel = new DefaultTableModel(columns,0);
        table1.setModel(defaultModel);
        for (Map.Entry<String, FileProperties> entry : node.filenameMap.entrySet()) {
            defaultModel.addRow(new Object[] {entry.getKey()});
        }
        add(new JScrollPane(table1));


        String[] columns2 = new String[] {"remote files"};
        DefaultTableModel defaultModel2 = new DefaultTableModel(columns2,0);
        table2.setModel(defaultModel2);
        File[] listOfFiles = Constants.replicationFileDirectory.listFiles();
        for (int i = 0; i < listOfFiles.length; i++)
        {
            if (listOfFiles[i].isFile())
            {
                defaultModel2.addRow(new Object[] {listOfFiles[i].getName()});
            }
        }
        add(new JScrollPane(table2));

        setVisible(true);
    }

    /*public void listFiles(){
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


    }*/
}
