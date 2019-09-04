import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.File;
import java.util.NavigableMap;
import java.util.*;

public class RepFilesGui extends JFrame {

    public NamingNode node;
    private JPanel rootpanel;
    private JTable table1;


    public RepFilesGui(NamingNode nn){
        node=nn;
        setVisible(true);
        setSize(300, 200);
        add(rootpanel);

        String[] columns2 = new String[] {"remote files"};
        DefaultTableModel defaultModel2 = new DefaultTableModel(columns2,0);
        table1.setModel(defaultModel2);
        File[] listOfFiles = Constants.replicationFileDirectory.listFiles();
        for (int i = 0; i < listOfFiles.length; i++)
        {
            if (listOfFiles[i].isFile())
            {
                defaultModel2.addRow(new Object[] {listOfFiles[i].getName()});
            }
        }
        add(new JScrollPane(table1));

        setVisible(true);
    }
}
