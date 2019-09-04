import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.File;
import java.util.NavigableMap;
import java.util.*;

public class FilesGui extends JFrame {

    public NamingNode node;
    private JPanel rootpanel;
    private JTable table1;
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
        setVisible(true);
    }
}
