/*
 * Copyright (c) 2013 QNX Software Systems and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.cdt.qt.tests;

import junit.framework.Test;
import junit.framework.TestSuite;

public class AllQtTests extends TestSuite {

	public static Test suite() throws Exception {
		return
			new TestSuite(
				QMakeTests.class,
				QGadgetTests.class,
				QObjectTests.class,
				QtContentAssistantTests.class,
				QtIndexTests.class,
				QtRegressionTests.class);
	}
}
