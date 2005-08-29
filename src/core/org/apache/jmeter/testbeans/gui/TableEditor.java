package org.apache.jmeter.testbeans.gui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.beans.PropertyDescriptor;
import java.beans.PropertyEditorSupport;
import java.lang.reflect.Method;

import javax.swing.CellEditor;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;

import org.apache.jmeter.util.JMeterUtils;
import org.apache.jorphan.gui.ObjectTableModel;
import org.apache.jorphan.logging.LoggingManager;
import org.apache.jorphan.reflect.Functor;
import org.apache.log.Logger;

public class TableEditor extends PropertyEditorSupport implements FocusListener,TestBeanPropertyEditor,TableModelListener{
	Logger log = LoggingManager.getLoggerForClass();
	
	public static final String CLASSNAME = "tableObject.classname";
	public static final String HEADERS = "table.headers";
	public static final String OBJECT_PROPERTIES = "tableObject.properties";
	
	JTable table;
	ObjectTableModel model;
	Class clazz;
	PropertyDescriptor descriptor;
	JButton addButton,removeButton,clearButton;

	public TableEditor() {
		addButton = new JButton(JMeterUtils.getResString("add"));
		addButton.addActionListener(new AddListener());
		removeButton = new JButton(JMeterUtils.getResString("remove"));
		removeButton.addActionListener(new RemoveListener());
		clearButton = new JButton(JMeterUtils.getResString("clear"));
		clearButton.addActionListener(new ClearListener());
	}

	/* (non-Javadoc)
	 * @see java.beans.PropertyEditor#getAsText()
	 */
	public String getAsText() {
		return null;
	}

	/* (non-Javadoc)
	 * @see java.beans.PropertyEditor#getCustomEditor()
	 */
	public Component getCustomEditor() {
		JComponent pane = makePanel();
		pane.doLayout();
		pane.validate();
		return pane;
	}
	
	private JComponent makePanel()
	{
		JPanel p = new JPanel(new BorderLayout());
		JScrollPane scroller = new JScrollPane(table);
		scroller.setPreferredSize(scroller.getMinimumSize());
		p.add(scroller,BorderLayout.CENTER);
		JPanel south = new JPanel();
		south.add(addButton);
		south.add(removeButton);
		south.add(clearButton);
		p.add(south,BorderLayout.SOUTH);
		return p;
	}

	/* (non-Javadoc)
	 * @see java.beans.PropertyEditor#getValue()
	 */
	public Object getValue() {
		return model.getObjectList();
	}

	/* (non-Javadoc)
	 * @see java.beans.PropertyEditor#setAsText(java.lang.String)
	 */
	public void setAsText(String text) throws IllegalArgumentException {
		//not interested in this method.		
	}

	/* (non-Javadoc)
	 * @see java.beans.PropertyEditor#setValue(java.lang.Object)
	 */
	public void setValue(Object value) {
		if(value != null)
		{
			model.setRows((Iterable)value);
		}
		else model.clearData();
		this.firePropertyChange();
	}

	/* (non-Javadoc)
	 * @see java.beans.PropertyEditor#supportsCustomEditor()
	 */
	public boolean supportsCustomEditor() {
		return true;
	}

	/**
	 * For the table editor, the tag must simply be the name of the class of object it will hold 
	 * where each row holds one object. 
	 */
	public void setDescriptor(PropertyDescriptor descriptor) {
		try {
			this.descriptor = descriptor;
			clazz = Class.forName((String)descriptor.getValue(CLASSNAME));
			initializeModel();
		} catch (ClassNotFoundException e) {
			throw new RuntimeException("The Table Editor requires one TAG be set - the name of the object to represent a row",e);
		}
	}
	
	void initializeModel()
	{
		if(clazz == String.class)
		{
			model = new ObjectTableModel((String[])descriptor.getValue(HEADERS),new Functor[0],new Functor[0],new Class[]{String.class});
			model.addTableModelListener(this);
		}
		else
		{
			String[] props = (String[])descriptor.getValue(OBJECT_PROPERTIES);
			Functor[] writers = new Functor[props.length];
			Functor[] readers = new Functor[props.length];
			Class[] editors = new Class[props.length];
			int count = 0;
			for(String propName : props)
			{
				propName = propName.substring(0,1).toUpperCase() + propName.substring(1);
				writers[count] = createWriter(clazz,propName);
				readers[count] = createReader(clazz,propName);
				editors[count] = getArgForWriter(clazz,propName);
				count++;
			}
			model = new ObjectTableModel((String[])descriptor.getValue(HEADERS),readers,writers,editors);
			model.addTableModelListener(this);
		}
		table = new JTable(model);
		table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		table.addFocusListener(this);
	}
	
	Functor createWriter(Class c,String propName)
	{
		String setter = "set" + propName;
		return new Functor(setter);
	}
	
	Functor createReader(Class c,String propName)
	{
		String getter = "get" + propName;
		try
		{
			c.getMethod(getter,new Class[0]);
			return new Functor(getter);
		}
		catch(Exception e) { return new Functor("is" + propName); }
	}
	
	Class getArgForWriter(Class c,String propName)
	{
		String setter = "set" + propName;
		for(Method m : c.getMethods())
		{
			if(m.getName().equals(setter))
			{
				return m.getParameterTypes()[0];
			}
		}
		return null;
	}

	/* (non-Javadoc)
	 * @see javax.swing.event.TableModelListener#tableChanged(javax.swing.event.TableModelEvent)
	 */
	public void tableChanged(TableModelEvent e) {
		this.firePropertyChange();		
	}

	/* (non-Javadoc)
	 * @see java.awt.event.FocusListener#focusGained(java.awt.event.FocusEvent)
	 */
	public void focusGained(FocusEvent e) {
		
	}

	/* (non-Javadoc)
	 * @see java.awt.event.FocusListener#focusLost(java.awt.event.FocusEvent)
	 */
	public void focusLost(FocusEvent e) {
		CellEditor ce = table.getCellEditor(table.getEditingRow(),table.getEditingColumn());
		Component editor = table.getEditorComponent();
		if(ce != null && (editor == null || editor != e.getOppositeComponent()))
		{
			ce.stopCellEditing();
		}
		else if(editor != null)
		{
			editor.addFocusListener(this);
		}
		this.firePropertyChange();
	}
	
	private class AddListener implements ActionListener
	{
		public void actionPerformed(ActionEvent e)
		{
			try
			{
				model.addRow(clazz.newInstance());
			}catch(Exception err)
			{
				log.error("The class type given to TableEditor was not instantiable. ",err);
			}
		}
	}
	
	private class RemoveListener implements ActionListener
	{
		public void actionPerformed(ActionEvent e)
		{
			model.removeRow(table.getSelectedRow());
		}
	}
	
	private class ClearListener implements ActionListener
	{
		public void actionPerformed(ActionEvent e)
		{
			model.clearData();
		}
	}

}
