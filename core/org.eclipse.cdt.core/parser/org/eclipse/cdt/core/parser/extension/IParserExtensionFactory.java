/*******************************************************************************
 * Copyright (c) 2003, 2004 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.cdt.core.parser.extension;

import org.eclipse.cdt.core.parser.ParserFactoryError;
import org.eclipse.cdt.core.parser.ParserMode;


/**
 * @author jcamelon
 */
public interface IParserExtensionFactory {

	public IScannerExtension createScannerExtension() throws ParserFactoryError;
	public IParserExtension createParserExtension() throws ParserFactoryError;
	public IASTFactoryExtension createASTExtension(ParserMode mode);
}
