package bibliotekas_informacijas_sistema;

import java.awt.*;
import java.awt.event.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.Callable;
import javax.swing.*;
import org.junit.*;
import static org.junit.Assert.*;
import bibliotekas_informacijas_sistema.dao.*;
import bibliotekas_informacijas_sistema.model.*;

public class QuickGuiFlowTest {
    LibInfoSaskarne app;
    javax.swing.Timer dialogs;
    int errors;
    int infoMessages;
    static Object field(Object o,String n) throws Exception { Field f=o.getClass().getDeclaredField(n); f.setAccessible(true); return f.get(o); }
    <T> T edt(Callable<T> c) throws Exception { Object[] v=new Object[1]; Throwable[] e=new Throwable[1]; SwingUtilities.invokeAndWait(()->{try{v[0]=c.call();}catch(Throwable t){e[0]=t;}}); if(e[0]!=null)throw new AssertionError(e[0]); return (T)v[0]; }
    void click(String n) throws Exception { edt(()->{((AbstractButton)field(app,n)).doClick();return null;}); }
    void text(String n,String s) throws Exception { edt(()->{((JTextField)field(app,n)).setText(s);return null;}); }
    void visible(String n) throws Exception { edt(()->{Window target=n.equals("MainFrame")?app:(Window)field(app,n);assertTrue(target.isVisible()); long count=Arrays.stream(Window.getWindows()).filter(w->w.isVisible()&&(w instanceof JDialog||w instanceof JFrame)).count();assertEquals(1,count);return null;}); }
    void select(String n,int id) throws Exception { edt(()->{JTable t=(JTable)field(app,n);t.getRowSorter().setSortKeys(java.util.List.of(new RowSorter.SortKey(1,SortOrder.DESCENDING)));for(int r=0;r<t.getModel().getRowCount();r++)if(((Number)t.getModel().getValueAt(r,0)).intValue()==id){int v=t.convertRowIndexToView(r);t.setRowSelectionInterval(v,v);return null;}throw new AssertionError("Missing row "+id);}); }
    void dismiss(Container root) {for(Component c:root.getComponents()){if(c instanceof JOptionPane p){if(p.getMessageType()==JOptionPane.ERROR_MESSAGE)errors++;if(p.getMessageType()==JOptionPane.INFORMATION_MESSAGE)infoMessages++;p.setValue(p.getOptionType()==JOptionPane.YES_NO_OPTION?JOptionPane.NO_OPTION:JOptionPane.OK_OPTION);}else if(c instanceof Container x)dismiss(x);}}
    @Before public void setup() throws Exception {
        Path db=Files.createTempDirectory("libinfo-gui-test-").resolve("db");System.setProperty("libinfo.db.path",db.toString());
        edt(()->{dialogs=new javax.swing.Timer(50,e->{for(Window w:Window.getWindows())if(w.isVisible())dismiss(w);});dialogs.start();app=new LibInfoSaskarne();return null;});
    }
    @After public void cleanup() throws Exception {edt(()->{if(dialogs!=null)dialogs.stop();for(Window w:Window.getWindows())w.dispose();return null;});}
    @Test(timeout=30000) public void selectEditSaveAndValidation() throws Exception {
        visible("LoginDialog"); text("UsernameTextField","admin");text("PasswordTextField","wrong");click("LoginButton");visible("LoginDialog");assertEquals(1,errors);
        text("PasswordTextField","admin123");click("LoginButton");visible("MainFrame");
        edt(()->{assertNotNull(app.getJMenuBar());assertEquals("",((JTextField)field(app,"UsernameTextField")).getText());assertEquals(0,((JPasswordField)field(app,"PasswordTextField")).getPassword().length);assertEquals(app,SwingUtilities.getWindowAncestor((Component)field(app,"BookListButton")));return null;});
        Lietotajs admin=(Lietotajs)field(app,"currentUser");GramataDAO books=new GramataDAO();
        Gramata form=new Gramata();form.setNosaukums("QA unknown year");Gramata saved=books.saveBook(form,-1,false,admin);
        click("BookListButton");visible("BookListDialog");
        int infoBefore=infoMessages;text("SearchTextField","QA unknown year");click("SearchButton");assertEquals(infoBefore+1,infoMessages);
        edt(()->{assertEquals(1,((JTable)field(app,"BookTable")).getRowCount());return null;});
        infoBefore=infoMessages;text("SearchTextField","NO_MATCH_QA_456");click("SearchButton");assertEquals(infoBefore+1,infoMessages);
        edt(()->{assertEquals(0,((JTable)field(app,"BookTable")).getRowCount());return null;});
        text("SearchTextField","");click("SearchButton");infoBefore=infoMessages;
        edt(()->{((JComboBox)field(app,"FilterComboBox")).setSelectedItem("Nepieejamās");return null;});assertEquals(infoBefore+1,infoMessages);
        select("BookTable",saved.getGramatasId());click("EditButton");visible("BookEditDialog");
        text("BookNameTextField","QA renamed");click("SaveBookButton");visible("BookListDialog");
        Gramata after=books.findBookById(saved.getGramatasId(),admin);assertEquals("QA renamed",after.getNosaukums());assertNull("Changing title must preserve unknown year",after.getIzdosanasGads());
        select("BookTable",saved.getGramatasId());click("EditButton");
        edt(()->{JSpinner sp=(JSpinner)field(app,"BookYearSpinner");((JSpinner.DefaultEditor)sp.getEditor()).getTextField().setText("invalid");return null;});
        click("SaveBookButton");visible("BookEditDialog");assertEquals(2,errors);
        edt(()->{((JSpinner)field(app,"BookYearSpinner")).setValue(2100);return null;});click("SaveBookButton");assertEquals(Integer.valueOf(2100),books.findBookById(saved.getGramatasId(),admin).getIzdosanasGads());
        click("CloseButton2");click("UserListButton");visible("UserListFrameDialog");
        LietotajsDAO users=new LietotajsDAO();Lietotajs reader=users.registerUser("Testa","User","quick_gui_reader","Test123!","Test123!","quick_gui_reader@example.com");
        click("UserSearchButton");select("UserTable",reader.getLietotajaId());click("EditUserButton");visible("UserEditDialog");text("UserNameTextField","Updated");click("SaveUserButton");visible("UserListFrameDialog");assertEquals("Updated",users.findUserById(reader.getLietotajaId(),admin).getVards());assertNotNull(users.authenticate("quick_gui_reader","Test123!"));
        click("ReturnButton2");visible("MainFrame");
        click("BookListButton");select("BookTable",saved.getGramatasId());click("EditButton");
        edt(()->{Window d=(Window)field(app,"BookEditDialog");d.dispatchEvent(new WindowEvent(d,WindowEvent.WINDOW_CLOSING));assertEquals("",((JTextField)field(app,"BookNameTextField")).getText());return null;});visible("MainFrame");
        click("UserListButton");select("UserTable",reader.getLietotajaId());click("EditUserButton");
        edt(()->{Window d=(Window)field(app,"UserEditDialog");d.dispatchEvent(new WindowEvent(d,WindowEvent.WINDOW_CLOSING));assertEquals("",((JTextField)field(app,"UserNameTextField")).getText());return null;});visible("MainFrame");edt(()->{JFrame d=app;d.dispatchEvent(new WindowEvent(d,WindowEvent.WINDOW_CLOSING));return null;});visible("MainFrame");assertEquals(2,errors);
    }
}
