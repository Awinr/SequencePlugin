package vanstudio.sequence.diagram;


import com.intellij.openapi.diagnostic.Logger;

import javax.swing.event.EventListenerList;
import javax.swing.event.SwingPropertyChangeSupport;
import java.beans.PropertyChangeListener;
import java.io.*;

/**
 * 事件源
 * 1. 生成事件对象：事件源在用户操作时生成事件对象，封装有关事件的信息。
 * 2. 通知事件监听器：事件源会将生成的事件对象传递给所有注册的事件监听器，以便它们进行处理。
 */
public class Model {

    private static final Logger LOGGER = Logger.getInstance(Model.class);

    private String _queryString = "";

    private SwingPropertyChangeSupport _changeSupport;

    private File _file = null;
    private boolean _modified = false;

    private final EventListenerList _listenerList = new EventListenerList();

    public Model() {
        _changeSupport = new SwingPropertyChangeSupport(this);
    }

    public void addPropertyChangeListener(String propName, PropertyChangeListener listener) {
        _changeSupport.addPropertyChangeListener(propName, listener);
    }

    public void removePropertyChangeListener(String propName, PropertyChangeListener listener) {
        _changeSupport.removePropertyChangeListener(propName, listener);
    }

    public boolean loadNew() {
        setFile(null);
        internalSetText("", this);
        setModified(false);
        return true;
    }

    public boolean readFromFile(File f) {
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            StringBuilder sb = new StringBuilder(1024);

            String s;
            while ((s = br.readLine()) != null) {
                sb.append(s);
                sb.append("\n");
            }
            setFile(f);
            internalSetText(sb.toString(), this);
            setModified(false);
            return true;
        } catch (IOException ioe) {
            ioe.printStackTrace();
            return false;
        }
    }

    public boolean writeToFile(File f) {
        try (PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(f)))){

            BufferedReader br = new BufferedReader(new StringReader(getText()));
            String s;
            while ((s = br.readLine()) != null) {
                out.println(s);
            }
            setFile(f);
            setModified(false);
            return true;
        } catch (IOException ioe) {
            ioe.printStackTrace();
            return false;
        }
    }

    public boolean isModified() {
        return _modified;
    }

    public void setModified(boolean modified) {
        boolean oldModified = _modified;
        _modified = modified;
        if (LOGGER.isDebugEnabled())
            LOGGER.debug("setModified(...) oldModified " + oldModified + " modified " + modified);
        if (modified != oldModified)
            _changeSupport.firePropertyChange("modified", oldModified, modified);
    }

    private void setFile(File f) {
        File oldFile = _file;
        _file = f;
        _changeSupport.firePropertyChange("file", oldFile, _file);
    }

    public String getText() {
        return _queryString;
    }

    public void setText(String s, Object setter) {
        internalSetText(s, setter);
        setModified(true);
    }

    private void internalSetText(String s, Object setter) {
        _queryString = s;
        fireModelTextEvent(s, setter);
    }

    /**
     * 生成事件 并通知事件监听器
     */
    private synchronized void fireModelTextEvent(String text, Object source) {
        ModelTextEvent event = new ModelTextEvent(source, text);
        // 监听器数组是以 (listenerType, listenerInstance) 的方式成对存储的。
        Object[] listeners = _listenerList.getListenerList();
        for (int i = listeners.length - 2; i >= 0; i -= 2) {
            if (listeners[i] == ModelTextListener.class)
                ((ModelTextListener) listeners[i + 1]).modelTextChanged(event);
        }
    }

    public File getFile() {
        return _file;
    }

    public void addModelTextListener(ModelTextListener l) {
        _listenerList.add(ModelTextListener.class, l);
    }

    public void removeModelTextListener(ModelTextListener l) {
        _listenerList.remove(ModelTextListener.class, l);
    }

}
