/**********************************************************************
 * Copyright (c) 2003 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors: 
 * IBM - Initial API and implementation
 **********************************************************************/
package org.eclipse.cdt.internal.core.build.managed;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


import org.eclipse.cdt.core.build.managed.BuildException;
import org.eclipse.cdt.core.build.managed.IOption;
import org.eclipse.cdt.core.build.managed.IOptionCategory;
import org.eclipse.cdt.core.build.managed.ITool;
import org.eclipse.core.runtime.IConfigurationElement;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * 
 */
public class OptionReference implements IOption {

	private IOption option;
	private ToolReference owner;
	private Object value;
	private String defaultEnumName;
	private String command;
	private Map enumCommands;

	/**
	 * Created internally.
	 * 
	 * @param owner
	 * @param option
	 */
	public OptionReference(ToolReference owner, IOption option) {
		this.owner = owner;
		this.option = option;
		
		owner.addOptionReference(this);
	}

	/**
	 * Created from extension.
	 * 
	 * @param owner
	 * @param element
	 */
	public OptionReference(ToolReference owner, IConfigurationElement element) {
		this.owner = owner;
		option = owner.getTool().getOption(element.getAttribute("id"));
		
		owner.addOptionReference(this);

		// value
		enumCommands = new HashMap();
		switch (option.getValueType()) {
			case IOption.BOOLEAN:
				value = new Boolean(element.getAttribute("defaultValue"));
				break;
			case IOption.STRING:
				value = element.getAttribute("defaultValue");
				break;
			case IOption.ENUMERATED:
				List enumList = new ArrayList();
				IConfigurationElement[] enumElements = element.getChildren("optionEnum");
				for (int i = 0; i < enumElements.length; ++i) {
					String optName = enumElements[i].getAttribute("name");
					String optCommand = enumElements[i].getAttribute("command"); 
					enumList.add(optName);
					enumCommands.put(optName, optCommand);
					Boolean isDefault = new Boolean(enumElements[i].getAttribute("isDefault"));
					if (isDefault.booleanValue()) {
							defaultEnumName = optName; 
					}
				}
				value = enumList;
				break;
			case IOption.STRING_LIST:
				List valueList = new ArrayList();
				IConfigurationElement[] valueElements = element.getChildren("optionValue");
				for (int i = 0; i < valueElements.length; ++i) {
					valueList.add(valueElements[i].getAttribute("value"));
				}
				value = valueList;
				break;
		}
	}

	/**
	 * Created from project file.
	 * 
	 * @param owner
	 * @param element
	 */
	public OptionReference(ToolReference owner, Element element) {
		this.owner = owner;	
		option = owner.getTool().getOption(element.getAttribute("id"));
		
		owner.addOptionReference(this);

		// value
		switch (option.getValueType()) {
			case IOption.BOOLEAN:
			case IOption.STRING:
				value = element.getAttribute("value");
				break;
			case IOption.STRING_LIST:
				List valueList = new ArrayList();
				NodeList nodes = element.getElementsByTagName("optionValue");
				for (int i = 0; i < nodes.getLength(); ++i) {
					valueList.add(((Element)nodes.item(i)).getAttribute("value"));
				}
				value = valueList;
				break;
		}

	}
	
	/**
	 * Write out to project file.
	 * 
	 * @param doc
	 * @param element
	 */
	public void serealize(Document doc, Element element) {
		element.setAttribute("id", option.getId());
		
		// value
		switch (option.getValueType()) {
			case IOption.BOOLEAN:
			case IOption.STRING:
				element.setAttribute("value", (String)value);
				break;
			case IOption.STRING_LIST:
				List stringList = (List)value;
				for (int i = 0; i < stringList.size(); ++i) {
					Element valueElement = doc.createElement("optionValue");
					valueElement.setAttribute("value", (String)stringList.get(i));
					element.appendChild(valueElement);
				}
				break;
			case IOption.ENUMERATED:
				List enumList = (List)value;
				for (int i = 0; i < enumList.size(); ++i) {
					Element valueElement = doc.createElement("optionEnum");
					valueElement.setAttribute("value", (String)enumList.get(i));
					element.appendChild(valueElement);
				}
				break;
		}
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.cdt.core.build.managed.IOption#getApplicableValues()
	 */
	public String[] getApplicableValues() {
		return option.getApplicableValues();
	}

	/* (non-Javadoc)
	 * @see org.eclipse.cdt.core.build.managed.IOption#getCategory()
	 */
	public IOptionCategory getCategory() {
		return option.getCategory();
	}

	/* (non-Javadoc)
	 * @see org.eclipse.cdt.core.build.managed.IOption#getCommand()
	 */
	public String getCommand() {
		return option.getCommand();
	}

	/* (non-Javadoc)
	 * @see org.eclipse.cdt.core.build.managed.IOption#getDefaultEnumValue()
	 */
	public String getDefaultEnumName() {
		if (value == null) {
			return option.getDefaultEnumName();
		} else {
			return defaultEnumName;
		}
	}

	/* (non-Javadoc)
	 * @see org.eclipse.cdt.core.build.managed.IOption#getEnumCommand(java.lang.String)
	 */
	public String getEnumCommand(String name) {
		if (value == null) {
			return option.getEnumCommand(name);
		} else {
			return (String)enumCommands.get(name);
		}
	}

	/* (non-Javadoc)
	 * @see org.eclipse.cdt.core.build.managed.IBuildObject#getId()
	 */
	public String getId() {
		return option.getId();
	}

	/* (non-Javadoc)
	 * @see org.eclipse.cdt.core.build.managed.IBuildObject#getName()
	 */
	public String getName() {
		return option.getName();
	}

	/* (non-Javadoc)
	 * @see org.eclipse.cdt.core.build.managed.IOption#getBooleanValue()
	 */
	public boolean getBooleanValue() throws BuildException {
		if (value == null){
			return option.getBooleanValue();
		} 
		else if (getValueType() == IOption.BOOLEAN) {
			Boolean bool = (Boolean) value;
			return bool.booleanValue();
		} else {
			throw new BuildException("bad value type");
		}
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.cdt.core.build.managed.IOption#getStringListValue()
	 */
	public String[] getStringListValue() throws BuildException {
		if (value == null)
			return option.getStringListValue();
		else if (getValueType() == IOption.STRING_LIST)
			return (String[])value;
		else
			throw new BuildException("bad value type");
	}

	/* (non-Javadoc)
	 * @see org.eclipse.cdt.core.build.managed.IOption#getStringValue()
	 */
	public String getStringValue() throws BuildException {
		if (value == null)
			return option.getStringValue();
		else if (getValueType() == IOption.STRING)
			return (String)value;
		else
			throw new BuildException("bad value type");
	}

	/* (non-Javadoc)
	 * @see org.eclipse.cdt.core.build.managed.IOption#getTool()
	 */
	public ITool getTool() {
		return owner;
	}

	public ToolReference getToolReference() {
		return owner;
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.cdt.core.build.managed.IOption#getValueType()
	 */
	public int getValueType() {
		return option.getValueType();
	}

	public boolean references(IOption target) {
		if (equals(target))
			// we are the target
			return true;
		else if (option instanceof OptionReference)
			// check the reference we are overriding
			return ((OptionReference)option).references(target);
		else
			// the real reference
			return option.equals(target);
	}

	public void setValue(String value) throws BuildException {
		if (getValueType() == IOption.STRING)
			this.value = value;
		else
			throw new BuildException("bad value type");
	}
	
	public void setValue(String [] value) throws BuildException {
		if (getValueType() == IOption.STRING_LIST)
			this.value = value;
		else
			throw new BuildException("bad value type");
	}
}
