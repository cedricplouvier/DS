import javax.swing.*;
import javax.xml.stream.XMLStreamException;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;

public class NodeGui extends JFrame {
    private JButton button1;
    private JPanel rootPanel;
    private JPanel titelpanel;
    private JPanel rootpanel;
    private JButton button2;
    private JButton button3;
    private JButton button4;

    public NamingNode node;


    public NodeGui(NamingNode nn){

        node=nn;
        setSize(300, 300);
        setTitle("Node ");

        add(rootpanel);


        button1.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                node.start();
            }
        });
        button3.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                try {
                    node.shutdown();
                } catch (IOException e1) {
                    e1.printStackTrace();
                } catch (XMLStreamException e1) {
                    e1.printStackTrace();
                }
            }

        });

        button2.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                FilesGui filesGUI = new FilesGui(node);
            }
        });
        button4.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                RepFilesGui repfilesGUI = new RepFilesGui(node);
            }
        });
    }

}
