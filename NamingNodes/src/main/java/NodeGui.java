import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

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

        setSize(300, 200);
        setTitle("Node ");

        add(rootPanel);


        button1.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                node.start();
            }
        });


        button2.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                FilesGui filesGui = new FilesGui(node);
            }
        });
    }

}
